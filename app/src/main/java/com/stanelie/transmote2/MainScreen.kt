package com.stanelie.transmote2

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.stanelie.transmote2.ui.theme.Transmote2Theme
import com.stanelie.transmote2.formatBytes
import com.stanelie.transmote2.formatEta
import com.stanelie.transmote2.formatTimestamp
import com.stanelie.transmote2.getTorrentStatusString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, mainViewModel: MainViewModel) {
    val torrents by mainViewModel.torrents.collectAsState()
    val isRefreshing by mainViewModel.isRefreshing.collectAsState()

    val listState = rememberLazyListState()

    LaunchedEffect(mainViewModel) {
        mainViewModel.scrollToTopEvent.collect {
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { mainViewModel.manualRefresh() },
                modifier = Modifier.weight(1f)
            ) {
                if (torrents.isEmpty() && !isRefreshing) {
                    Text(
                        text = "No torrents found or server not configured.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .simpleVerticalScrollbar(listState)
                    ) {
                        items(torrents, key = { it.id }) {
                            TorrentItem(
                                torrentInfo = it,
                                onTorrentClick = { torrentId ->
                                    navController.navigate(Routes.torrentDetailRoute(torrentId))
                                },
                                onResumeClick = { mainViewModel.startTorrent(it.id) },
                                onPauseClick = { mainViewModel.stopTorrent(it.id) },
                                onRemoveClick = { mainViewModel.removeTorrent(it.id, true) },
                                onRemoveTorrentClick = { mainViewModel.removeTorrent(it.id, false) },
                                onRenameClick = { newName -> mainViewModel.renameTorrent(it.id, it.name, newName) },
                                onMenuVisibilityChanged = { mainViewModel.onMenuVisibilityChanged(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TorrentItem(
    torrentInfo: TorrentInfo,
    onTorrentClick: (Int) -> Unit,
    onResumeClick: () -> Unit,
    onPauseClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onRemoveTorrentClick: () -> Unit,
    onRenameClick: (String) -> Unit,
    onMenuVisibilityChanged: (Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(torrentInfo.name) { mutableStateOf(torrentInfo.name) }

    val cardColors = when (torrentInfo.status) {
        0 -> CardDefaults.cardColors( // Stopped
            containerColor = Color.DarkGray,
            contentColor = Color.White
        )
        1, 2, 3, 4 -> CardDefaults.cardColors( // Downloading, Check waiting, Checking, Download waiting
            containerColor = Color(0xFF388E3C), // Dark Green
            contentColor = Color.White
        )
        5, 6 -> CardDefaults.cardColors( // Seed waiting, Seeding
            containerColor = Color(0xFF0277BD), // Dark Blue
            contentColor = Color.White
        )
        else -> CardDefaults.cardColors() // Default theme colors for error, etc.
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = { onTorrentClick(torrentInfo.id) },
                onLongClick = { 
                    showMenu = true
                    onMenuVisibilityChanged(true)
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = torrentInfo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1 // Restrict to a single line
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = torrentInfo.percentDone.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    torrentInfo.percentDone >= 1.0 -> Color(0xFFC8E6C9) // Completed
                    torrentInfo.status == 0 || torrentInfo.status == 1 -> Color.LightGray
                    torrentInfo.status == 2 || torrentInfo.status == 3 -> Color.Blue
                    torrentInfo.status == 4 -> Color.Green
                    torrentInfo.status == 5 -> Color.Cyan
                    torrentInfo.status == 6 -> Color.Magenta
                    else -> Color.Red
                },
                trackColor = if (torrentInfo.status == 0 || torrentInfo.status == 1) Color.Gray else Color.LightGray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ETA: ${formatEta(torrentInfo.eta)}", style = MaterialTheme.typography.bodySmall)
                if (torrentInfo.status == 4) { // Actively downloading
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("DL: ${formatBytes(torrentInfo.rateDownload)}/s", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Peers: ${torrentInfo.peersConnected}", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (torrentInfo.status == 6) { // Seeding
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("UP: ${formatBytes(torrentInfo.rateUpload)}/s", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Peers: ${torrentInfo.peersConnected}", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // Show general status for other states
                    Text("Status: ${getTorrentStatusString(torrentInfo.status)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (torrentInfo.error != 0) {
                Text("Error: ${torrentInfo.errorString}", color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { 
                showMenu = false
                onMenuVisibilityChanged(false)
            }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    renameText = torrentInfo.name
                    showRenameDialog = true
                    showMenu = false
                    onMenuVisibilityChanged(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Resume") },
                onClick = {
                    onResumeClick()
                    showMenu = false
                    onMenuVisibilityChanged(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Pause") },
                onClick = {
                    onPauseClick()
                    showMenu = false
                    onMenuVisibilityChanged(false)
                }
            )
            DropdownMenuItem(
                text = { Text("Remove torrent") },
                onClick = {
                    onRemoveTorrentClick()
                    showMenu = false
                    onMenuVisibilityChanged(false)
                }
            )
            DropdownMenuItem(
                text = { Text("delete files") },
                onClick = {
                    showDeleteConfirmation = true
                    showMenu = false
                    onMenuVisibilityChanged(false)
                }
            )
        }
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete this torrent and its data?") },
                confirmButton = {
                    Button(
                        onClick = {
                            onRemoveClick()
                            showDeleteConfirmation = false
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        if (showRenameDialog) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename torrent") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Name") }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRenameClick(renameText)
                            showRenameDialog = false
                        },
                        enabled = renameText.isNotBlank() && renameText != torrentInfo.name
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    Button(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TorrentItemPreview() {
    val sampleTorrent = TorrentInfo(
        id = 1,
        name = "BigBuckBunny.mp4",
        status = 4,
        rateDownload = 1234567,
        rateUpload = 98765,
        sizeWhenDone = 1234567890L,
        uploadedEver = 123456789L,
        percentDone = 0.75,
        eta = 3600,
        error = 0,
        errorString = "",
        isFinished = false,
        isStalled = false,
        queuePosition = 1,
        addedDate = 1678886400L, // March 15, 2023 12:00:00 PM GMT
        peersConnected = 15
    )
    Transmote2Theme {
        TorrentItem(
            torrentInfo = sampleTorrent,
            onTorrentClick = {},
            onResumeClick = {},
            onPauseClick = {},
            onRemoveClick = {},
            onRemoveTorrentClick = {},
            onRenameClick = {},
            onMenuVisibilityChanged = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Transmote2Theme {
        val navController = rememberNavController()
        // For preview, you might want to create a mock MainViewModel or just show a message
        Text("Preview of MainScreen with data requires a ViewModel. Please run the app.")
    }
}
