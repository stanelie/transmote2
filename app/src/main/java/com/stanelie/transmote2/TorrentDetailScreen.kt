package com.stanelie.transmote2

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// Represents a node in the file/folder tree
data class FileTreeNode(
    val name: String,
    val path: String, // Unique path for state tracking
    val isDirectory: Boolean,
    val fileIndex: Int? = null,
    val files: List<TorrentFile>? = null,
    val children: MutableList<FileTreeNode> = mutableListOf(),
    val depth: Int = 0,
    val priority: Int? = null
)

fun longestCommonPrefix(strings: List<String>): String {
    if (strings.isEmpty()) return ""
    var prefix = strings[0]
    for (s in strings.drop(1)) {
        while (!s.startsWith(prefix)) {
            prefix = prefix.dropLast(1)
            if (prefix.isEmpty()) return ""
        }
    }
    return prefix
}

sealed class RenameSegment
data class CommonRenameSegment(val text: String) : RenameSegment()
data class VariableRenameSegment(val values: List<String>) : RenameSegment()

// Splits a string into alternating word / separator parts, e.g.
// "foo_bar baz" → ["foo", "_", "bar", " ", "baz"]
private val SEP_REGEX = Regex("[ \\-_]+")
fun splitIntoParts(s: String): List<String> {
    val result = mutableListOf<String>()
    var last = 0
    SEP_REGEX.findAll(s).forEach { m ->
        if (m.range.first > last) result.add(s.substring(last, m.range.first))
        result.add(m.value)
        last = m.range.last + 1
    }
    if (last < s.length) result.add(s.substring(last))
    return result
}

// Primary algorithm: one {N} per word-group that differs across files.
// Falls back to longest-common-substring when file word counts differ.
fun computeRenameSegments(strings: List<String>): List<RenameSegment> {
    if (strings.isEmpty()) return emptyList()

    val partsPerFile = strings.map { splitIntoParts(it) }
    val count = partsPerFile[0].size

    // Fallback when word counts differ across files
    if (partsPerFile.any { it.size != count }) {
        return computeRenameSegmentsFallback(strings)
    }

    fun appendCommon(result: MutableList<RenameSegment>, text: String) {
        val last = result.lastOrNull()
        if (last is CommonRenameSegment) result[result.lastIndex] = CommonRenameSegment(last.text + text)
        else result.add(CommonRenameSegment(text))
    }

    val result = mutableListOf<RenameSegment>()
    for (i in 0 until count) {
        val parts = partsPerFile.map { it[i] }
        val isSep = parts[0].all { c -> c == ' ' || c == '-' || c == '_' }
        if (isSep || parts.all { it == parts[0] }) {
            // Separator (always treated as common) or identical word → merge into common text
            appendCommon(result, parts[0])
        } else {
            result.add(VariableRenameSegment(parts))
        }
    }
    return result
}

// Fallback: longest-common-substring recursion for mismatched word counts.
private fun longestCommonSubstring(strings: List<String>): String {
    if (strings.isEmpty()) return ""
    val shortest = strings.minByOrNull { it.length } ?: return ""
    for (len in shortest.length downTo 2) {
        for (start in 0..shortest.length - len) {
            val sub = shortest.substring(start, start + len)
            if (strings.all { it.contains(sub) }) return sub
        }
    }
    return ""
}

private fun computeRenameSegmentsFallback(strings: List<String>): List<RenameSegment> {
    if (strings.isEmpty()) return emptyList()
    val prefix = longestCommonPrefix(strings)
    val tails = strings.map { it.removePrefix(prefix) }
    val result = mutableListOf<RenameSegment>()
    if (prefix.isNotEmpty()) result.add(CommonRenameSegment(prefix))
    if (tails.all { it.isEmpty() }) return result
    val anchor = longestCommonSubstring(tails)
    if (anchor.isEmpty()) { result.add(VariableRenameSegment(tails)); return result }
    val beforeAnchor = tails.map { it.substring(0, it.indexOf(anchor)) }
    if (beforeAnchor.any { it.isNotEmpty() }) result.add(VariableRenameSegment(beforeAnchor))
    result.add(CommonRenameSegment(anchor))
    val afterAnchor = tails.map { it.substring(it.indexOf(anchor) + anchor.length) }
    if (afterAnchor.any { it.isNotEmpty() }) result.addAll(computeRenameSegmentsFallback(afterAnchor))
    return result
}

// Turns segments into an editable template: common text as-is, variables as {1}, {2}, …
fun buildRenameTemplate(segments: List<RenameSegment>): String {
    var idx = 0
    return segments.joinToString("") { seg ->
        when (seg) {
            is CommonRenameSegment -> seg.text
            is VariableRenameSegment -> "{${++idx}}"
        }
    }
}

fun buildFileTree(torrentFiles: List<TorrentFile>): List<FileTreeNode> {
    val rootNodes = mutableListOf<FileTreeNode>()

    torrentFiles.forEachIndexed { originalIndex, torrentFile ->
        val pathParts = torrentFile.name.split("/").filter { it.isNotEmpty() }
        var currentParentNodes = rootNodes
        var currentPath = ""

        pathParts.forEachIndexed { index, part ->
            currentPath += if (currentPath.isEmpty()) part else "/$part"

            var existingNode = currentParentNodes.find { it.path == currentPath }

            if (existingNode == null) {
                existingNode = FileTreeNode(
                    name = part,
                    path = currentPath,
                    isDirectory = index < pathParts.lastIndex,
                    fileIndex = if (index == pathParts.lastIndex) originalIndex else null,
                    files = if (index == pathParts.lastIndex) listOf(torrentFile) else null,
                    depth = index,
                    priority = if (index == pathParts.lastIndex) torrentFile.priority else null
                )
                currentParentNodes.add(existingNode)
                currentParentNodes.sortBy { it.name.lowercase() }
            }
            currentParentNodes = existingNode.children
        }
    }
    return rootNodes
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TorrentDetailScreen(navController: NavController, torrentDetailViewModel: TorrentDetailViewModel) {
    val torrentFiles by torrentDetailViewModel.torrentFiles.collectAsState()
    val isLoading by torrentDetailViewModel.isLoading.collectAsState()
    val errorMessage by torrentDetailViewModel.errorMessage.collectAsState()
    val selectedFileIndices by torrentDetailViewModel.selectedFileIndices.collectAsState()

    val isSelectionModeActive = selectedFileIndices.isNotEmpty()
    val expandedNodePaths = remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var renameSegments by remember { mutableStateOf<List<RenameSegment>>(emptyList()) }
    var renameTemplate by remember { mutableStateOf("") }
    var renameTemplateOriginal by remember { mutableStateOf("") }
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Torrent Files") },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isSelectionModeActive) {
                            torrentDetailViewModel.clearSelection()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (isSelectionModeActive) "Clear Selection" else "Back")
                    }
                },
                actions = {
                    if (isSelectionModeActive) {
                        Text(
                            "${selectedFileIndices.size} selected",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    } else {
                        TextButton(onClick = { torrentDetailViewModel.selectAll() }) {
                            Text("Select all", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = isSelectionModeActive) {
                BottomAppBar(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { torrentDetailViewModel.setPriorityForSelectedFiles(-1) }) { Text("Unwanted") }
                        TextButton(onClick = { torrentDetailViewModel.setPriorityForSelectedFiles(0) }) { Text("Normal") }
                        TextButton(onClick = { torrentDetailViewModel.setPriorityForSelectedFiles(1) }) { Text("High") }
                        TextButton(onClick = {
                            val sortedIndices = selectedFileIndices.sorted()
                            val fileNames = sortedIndices.map { torrentFiles[it].name.split("/").last() }
                            val segs = computeRenameSegments(fileNames)
                            val tmpl = buildRenameTemplate(segs)
                            renameSegments = segs
                            renameTemplate = tmpl
                            renameTemplateOriginal = tmpl
                            showBatchRenameDialog = true
                        }) { Text("Rename") }
                        // Download button is only enabled when exactly one file is selected
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val selectedFile = torrentFiles.getOrNull(selectedFileIndices.first())
                                    if (selectedFile != null) {
                                        val url = torrentDetailViewModel.createDownloadUrl(selectedFile.name)
                                        if (url != null) {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        }
                                    }
                                }
                            },
                            enabled = selectedFileIndices.size == 1
                        ) {
                            Text("Download file")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (errorMessage != null) {
                Text("Error: ${errorMessage}", color = Color.Red)
            } else if (torrentFiles.isEmpty()) {
                Text("No files found for this torrent.")
            } else {
                val fileTree = remember(torrentFiles) { buildFileTree(torrentFiles) }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .simpleVerticalScrollbar(listState)
                ) {
                    items(fileTree) { node ->
                        // Collect inside each item so LazyColumn item subcompositions
                        // directly observe selection state and recompose on change.
                        val itemSelectedIndices by torrentDetailViewModel.selectedFileIndices.collectAsState()
                        FileTreeItem(
                            node = node,
                            level = 0,
                            isSelected = node.fileIndex?.let { itemSelectedIndices.contains(it) } ?: false,
                            isExpanded = expandedNodePaths.value.contains(node.path),
                            onToggleExpansion = { path ->
                                expandedNodePaths.value = if (expandedNodePaths.value.contains(path)) {
                                    expandedNodePaths.value - path
                                } else {
                                    expandedNodePaths.value + path
                                }
                            },
                            onFileClick = { fileIndex ->
                                torrentDetailViewModel.toggleFileSelection(fileIndex)
                            },
                            selectedFileIndices = itemSelectedIndices,
                            expandedNodePaths = expandedNodePaths.value
                        )
                    }
                }
            }
        }

        if (showBatchRenameDialog) {
            val sortedIndices = selectedFileIndices.sorted()
            AlertDialog(
                onDismissRequest = { showBatchRenameDialog = false },
                title = { Text("Rename ${sortedIndices.size} files") },
                text = {
                    Column {
                        Text(
                            "{1}, {2}, … mark the parts unique to each file. Edit any text around them freely.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameTemplate,
                            onValueChange = { renameTemplate = it },
                            singleLine = true,
                            label = { Text("Name template") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val varValuesPerFile = sortedIndices.mapIndexed { listIdx, _ ->
                                renameSegments
                                    .filterIsInstance<VariableRenameSegment>()
                                    .map { it.values[listIdx] }
                            }
                            torrentDetailViewModel.batchRenameFiles(
                                sortedIndices, renameTemplate, varValuesPerFile
                            )
                            showBatchRenameDialog = false
                        },
                        enabled = renameTemplate != renameTemplateOriginal && renameTemplate.isNotBlank()
                    ) { Text("Rename") }
                },
                dismissButton = {
                    Button(onClick = { showBatchRenameDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeItem(
    node: FileTreeNode,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpansion: (String) -> Unit,
    onFileClick: (Int) -> Unit,
    selectedFileIndices: Set<Int>,
    expandedNodePaths: Set<String>
) {
    val startPadding = (level * 16).dp

    val defaultBackgroundColor = when (node.priority) {
        -1 -> Color.DarkGray
        0 -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        1 -> Color.Red.copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else defaultBackgroundColor

    Column(modifier = Modifier.padding(start = startPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .clickable {
                    if (node.isDirectory) {
                        onToggleExpansion(node.path)
                    } else {
                        node.fileIndex?.let { onFileClick(it) }
                    }
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(node.name, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
            } else {
                Text(node.name, modifier = Modifier.weight(1f).basicMarquee(), maxLines = 1)
                Spacer(modifier = Modifier.width(8.dp))
                node.files?.firstOrNull()?.let { file ->
                    Text(
                        text = "${formatBytes(file.bytesCompleted, false)} / ${formatBytes(file.length, false)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Column {
                node.children.forEach { childNode ->
                    FileTreeItem(
                        node = childNode,
                        level = level + 1,
                        isSelected = childNode.fileIndex?.let { selectedFileIndices.contains(it) } ?: false,
                        isExpanded = expandedNodePaths.contains(childNode.path),
                        onToggleExpansion = onToggleExpansion,
                        onFileClick = onFileClick,
                        selectedFileIndices = selectedFileIndices,
                        expandedNodePaths = expandedNodePaths
                    )
                }
            }
        }
    }
}
