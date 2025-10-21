package com.stanelie.transmote2

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
                        Text("${selectedFileIndices.size} selected", modifier = Modifier.padding(end = 16.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                        FileTreeItem(
                            node = node,
                            level = 0,
                            isSelected = node.fileIndex?.let { selectedFileIndices.contains(it) } ?: false,
                            isSelectionModeActive = isSelectionModeActive,
                            isExpanded = expandedNodePaths.value.contains(node.path),
                            onToggleExpansion = { path ->
                                expandedNodePaths.value = if (expandedNodePaths.value.contains(path)) {
                                    expandedNodePaths.value - path
                                } else {
                                    expandedNodePaths.value + path
                                }
                            },
                            onFileClick = { fileIndex ->
                                if (isSelectionModeActive) {
                                    torrentDetailViewModel.toggleFileSelection(fileIndex)
                                }
                            },
                            onFileLongClick = { torrentDetailViewModel.startSelection(it) },
                            selectedFileIndices = selectedFileIndices,
                            expandedNodePaths = expandedNodePaths.value
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTreeItem(
    node: FileTreeNode,
    level: Int,
    isSelected: Boolean,
    isSelectionModeActive: Boolean,
    isExpanded: Boolean,
    onToggleExpansion: (String) -> Unit,
    onFileClick: (Int) -> Unit,
    onFileLongClick: (Int) -> Unit,
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
                .combinedClickable(
                    onClick = {
                        if (node.fileIndex != null && isSelectionModeActive) {
                            onFileClick(node.fileIndex)
                        } else if (node.isDirectory) {
                            onToggleExpansion(node.path)
                        }
                    },
                    onLongClick = {
                        if (node.fileIndex != null) {
                            onFileLongClick(node.fileIndex)
                        }
                    }
                )
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
                Text(node.name, fontWeight = FontWeight.Bold)
            } else {
                Text(node.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.width(8.dp))
                node.files?.firstOrNull()?.let { file ->
                    Text(
                        text = "${formatBytes(file.bytesCompleted)} / ${formatBytes(file.length)}",
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
                        isSelectionModeActive = isSelectionModeActive,
                        isExpanded = expandedNodePaths.contains(childNode.path),
                        onToggleExpansion = onToggleExpansion,
                        onFileClick = onFileClick,
                        onFileLongClick = onFileLongClick,
                        selectedFileIndices = selectedFileIndices,
                        expandedNodePaths = expandedNodePaths
                    )
                }
            }
        }
    }
}
