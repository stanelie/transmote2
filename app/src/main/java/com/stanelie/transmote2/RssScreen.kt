package com.stanelie.transmote2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun RssScreen(
    navController: NavController,
    rssViewModel: RssViewModel,
    onAddTorrent: (String) -> Unit
) {
    val rssItems by rssViewModel.rssItems.collectAsState()
    val isLoading by rssViewModel.isLoading.collectAsState()
    val error by rssViewModel.error.collectAsState()
    val seenItemGuids by rssViewModel.seenItemGuids.collectAsState()

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!isLoading && error == null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .simpleVerticalScrollbar(listState)
                    .padding(16.dp)
            ) {
                items(rssItems) { item ->
                    val isSeen = seenItemGuids.contains(item.guid)
                    RssItemView(
                        item = item,
                        isSeen = isSeen,
                        onClick = {
                            onAddTorrent(item.link)
                            // Optionally, pop back or show a toast
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RssItemView(item: RssItem, isSeen: Boolean, onClick: () -> Unit) {
    val contentColor = if (isSeen) Color.Gray else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
        }
    }
}
