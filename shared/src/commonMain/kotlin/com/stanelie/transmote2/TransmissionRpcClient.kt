package com.stanelie.transmote2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName

@Serializable
data class FileStat(
    val bytesCompleted: Long,
    val wanted: Boolean,
    val priority: Int
)

// This class is for the main torrent list, without file details
@Serializable
data class TorrentInfo(
    val id: Int,
    val name: String,
    val status: Int,
    val rateDownload: Long,
    val rateUpload: Long,
    val sizeWhenDone: Long,
    val uploadedEver: Long,
    val percentDone: Double,
    val eta: Long,
    val error: Int,
    val errorString: String,
    val isFinished: Boolean,
    val isStalled: Boolean,
    val queuePosition: Int,
    val addedDate: Long,
    val peersConnected: Int = 0
)

@Serializable
data class TorrentFile(
    val bytesCompleted: Long,
    val length: Long,
    val name: String,
    var priority: Int = 0
)

@Serializable
data class RpcRequest<T>(
    val method: String,
    val arguments: T,
    val tag: Int? = null
)

@Serializable
data class RpcResponse<T>(
    val result: String,
    val arguments: T
)

@Serializable
data class GetTorrentsArgs(
    val fields: List<String>,
    val ids: List<Int>? = null
)

// Response for the main torrent list
@Serializable
data class GetTorrentsResponseArgs(
    val torrents: List<TorrentInfo>
)

// Specialized response classes for getTorrentFiles to avoid deserialization conflicts
@Serializable
data class TorrentFilesResponse(
    val id: Int,
    val name: String,
    val files: List<TorrentFile>? = null,
    @SerialName("fileStats") val fileStats: List<FileStat>? = null
)

@Serializable
data class GetTorrentFilesResponseArgs(
    val torrents: List<TorrentFilesResponse>
)

@Serializable
data class TorrentSetArgs(
    val ids: List<Int>? = null,
    @SerialName("files-wanted") val filesWanted: List<Int>? = null,
    @SerialName("files-unwanted") val filesUnwanted: List<Int>? = null,
    @SerialName("priority-high-ids") val priorityHighIds: List<Int>? = null,
    @SerialName("priority-normal-ids") val priorityNormalIds: List<Int>? = null,
    @SerialName("priority-low-ids") val priorityLowIds: List<Int>? = null
)

@Serializable
data class TorrentActionArgs(
    val ids: List<Int>
)

@Serializable
data class TorrentRemoveArgs(
    val ids: List<Int>,
    @SerialName("delete-local-data") val deleteLocalData: Boolean = false
)

@Serializable
data class TorrentAddArgs(
    val filename: String? = null,
    val metainfo: String? = null
)

@Serializable
data class TorrentRenameArgs(
    val ids: List<Int>,
    val path: String,
    val name: String
)

class TransmissionRpcClient(private val settings: ConnectionSettings) {

    private fun newClient(): HttpClient = createHttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    // Rebuildable on purpose. On iOS this wraps an NSURLSession, which can be
    // left permanently unusable after a network change or a long suspend -
    // every later request then fails the same way and only restarting the app
    // clears it. resetTransport() is the in-app equivalent of that restart.
    private var client: HttpClient = newClient()

    private fun resetTransport() {
        Logger.d(TAG, "rebuilding HTTP transport after a connection failure")
        runCatching { client.close() }
        client = newClient()
        sessionId = null
    }

    private var sessionId: String? = null
    private val TAG = "TransmissionRpcClient"

    private fun buildRpcUrl(): String {
        val protocol = if (settings.useSsl) "https" else "http"
        return "$protocol://${settings.serverAddress}:${settings.port}/transmission/rpc"
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend inline fun <reified T, reified R> makeRpcCall(method: String, arguments: T): RpcResponse<R> {
        val requestBody = RpcRequest(method, arguments)
        val rpcUrl = buildRpcUrl()

        var lastTransportFailure: Exception? = null

        // 3 attempts: one spare for a stale session id, one for rebuilding a
        // dead transport.
        for (attempt in 1..3) {
            val response: HttpResponse = try {
                client.post(rpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    headers {
                        settings.username.takeIf { it.isNotEmpty() }?.let {
                            val auth = "${settings.username}:${settings.password}"
                            val encodedAuth = Base64.encode(auth.encodeToByteArray())
                            append(HttpHeaders.Authorization, "Basic $encodedAuth")
                        }
                        sessionId?.let { append("X-Transmission-Session-Id", it) }
                    }
                }
            } catch (e: CancellationException) {
                throw e                       // never swallow cancellation
            } catch (e: Exception) {
                lastTransportFailure = e
                if (attempt < 3) {
                    resetTransport()
                    continue
                }
                throw IllegalStateException(
                    "${e::class.simpleName}: ${e.message ?: "connection failed"}", e
                )
            }

            // Transmission answers 409 with a fresh session id whenever the
            // old one expires. Refresh on any attempt but the last.
            if (response.status == HttpStatusCode.Conflict && attempt < 3) {
                val refreshed = response.headers["X-Transmission-Session-Id"]
                if (refreshed != null) {
                    sessionId = refreshed
                    continue
                }
                throw IllegalStateException("Got 409 with no X-Transmission-Session-Id header")
            }

            if (!response.status.isSuccess()) {
                throw Exception("RPC call failed with status: ${response.status}")
            }

            // A refused request still comes back as HTTP 200 with the reason
            // in "result"; without this check failures pass silently.
            val parsed: RpcResponse<R> = response.body()
            if (parsed.result != "success") {
                throw IllegalStateException("$method refused by Transmission: ${parsed.result}")
            }
            return parsed
        }

        lastTransportFailure?.let {
            throw IllegalStateException("${it::class.simpleName}: ${it.message}", it)
        }
        throw IllegalStateException("RPC call failed after multiple attempts")
    }

    suspend fun getTorrents(): List<TorrentInfo> {
        val fields = listOf(
            "id", "name", "status", "rateDownload", "rateUpload", "sizeWhenDone",
            "uploadedEver", "percentDone", "eta", "error", "errorString",
            "isFinished", "isStalled", "queuePosition", "addedDate", "peersConnected"
        )
        val args = GetTorrentsArgs(fields = fields)
        val response: RpcResponse<GetTorrentsResponseArgs> = makeRpcCall("torrent-get", args)
        return response.arguments.torrents
    }

    suspend fun getTorrentFiles(torrentId: Int): List<TorrentFile> {
        // Request only the essential fields to ensure fileStats is returned
        val fields = listOf("id", "name", "files", "fileStats")
        val args = GetTorrentsArgs(ids = listOf(torrentId), fields = fields)
        // Use the specialized response type
        val response: RpcResponse<GetTorrentFilesResponseArgs> = makeRpcCall("torrent-get", args)
        
        val torrentDetails = response.arguments.torrents.firstOrNull()
        val files = torrentDetails?.files ?: emptyList()
        val fileStats = torrentDetails?.fileStats ?: emptyList()

        Logger.d(TAG, "getTorrentFiles: Received fileStats for torrent $torrentId: $fileStats")

        return files.mapIndexed { index, file ->
            val stat = fileStats.getOrNull(index)
            val assignedPriority = if (stat?.wanted == false) {
                -1 // Unwanted
            } else {
                stat?.priority ?: 0 // Use priority if wanted, otherwise default to normal
            }
            Logger.d(TAG, "getTorrentFiles: File ${file.name} (index $index) wanted=${stat?.wanted}, priority=${stat?.priority} -> assignedPriority=$assignedPriority")
            file.copy(priority = assignedPriority)
        }
    }

    suspend fun setFilePriorities(torrentId: Int, fileIndices: List<Int>, priority: Int): RpcResponse<Unit> {
        Logger.d(TAG, "Attempting to set file priorities for torrent $torrentId, files: $fileIndices, target priority: $priority")

        val args = when (priority) {
            -1 -> TorrentSetArgs(
                ids = listOf(torrentId),
                filesUnwanted = fileIndices,
                priorityLowIds = fileIndices
            )
            0 -> TorrentSetArgs(
                ids = listOf(torrentId),
                filesWanted = fileIndices,
                priorityNormalIds = fileIndices
            )
            1 -> TorrentSetArgs(
                ids = listOf(torrentId),
                filesWanted = fileIndices,
                priorityHighIds = fileIndices
            )
            else -> throw IllegalArgumentException("Invalid priority value: $priority")
        }
        
        Logger.d(TAG, "Sending torrent-set with arguments: $args")
        return makeRpcCall("torrent-set", args)
    }

    suspend fun startTorrents(torrentIds: List<Int>): RpcResponse<Unit> {
        val args = TorrentActionArgs(ids = torrentIds)
        return makeRpcCall("torrent-start", args)
    }

    suspend fun stopTorrents(torrentIds: List<Int>): RpcResponse<Unit> {
        val args = TorrentActionArgs(ids = torrentIds)
        return makeRpcCall("torrent-stop", args)
    }

    suspend fun removeTorrents(torrentIds: List<Int>, deleteData: Boolean): RpcResponse<Unit> {
        val args = TorrentRemoveArgs(ids = torrentIds, deleteLocalData = deleteData)
        return makeRpcCall("torrent-remove", args)
    }

    suspend fun renameTorrent(torrentId: Int, currentName: String, newName: String): RpcResponse<Unit> {
        val args = TorrentRenameArgs(ids = listOf(torrentId), path = currentName, name = newName)
        return makeRpcCall("torrent-rename-path", args)
    }

    // Transmission's "filename" takes anything it can fetch itself: a magnet
    // URI or an http(s) URL pointing at a .torrent. Only raw base64 torrent
    // bytes go in "metainfo". RSS <link> elements are http(s) URLs, so sending
    // them as metainfo made the server reject them as a corrupt torrent.
    suspend fun addTorrent(magnetOrBase64: String): RpcResponse<Unit> {
        val source = magnetOrBase64.trim()
        val isFetchableUrl = source.startsWith("magnet:", ignoreCase = true) ||
            source.startsWith("http://", ignoreCase = true) ||
            source.startsWith("https://", ignoreCase = true)
        val args = if (isFetchableUrl) {
            TorrentAddArgs(filename = source)
        } else {
            TorrentAddArgs(metainfo = source)
        }
        return makeRpcCall("torrent-add", args)
    }
}