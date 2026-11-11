package com.stanelie.transmote2

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    navController: NavController,
    rssViewModel: RssViewModel,
    onAddTorrent: (String) -> Unit
) {
    val rssItems by rssViewModel.rssItems.collectAsState()
    val isRefreshing by rssViewModel.isRefreshing.collectAsState()
    val error by rssViewModel.error.collectAsState()
    val seenItemGuids by rssViewModel.seenItemGuids.collectAsState()

    val listState = rememberLazyListState()

    // Baseline first, then fetch: refreshFeed marks everything it fetches as
    // seen, so reading the stored set afterwards would leave nothing new.
    LaunchedEffect(Unit) {
        rssViewModel.startVisit()
        rssViewModel.refreshFeed()
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { rssViewModel.refreshFeed() },
        modifier = Modifier.fillMaxSize()
    ) {
        if (error != null) {
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            )
        }

        if (rssItems.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .simpleVerticalScrollbar(listState)
                    .padding(horizontal = 16.dp)
            ) {
                items(rssItems) { item ->
                    // "New" means: not yet seen as of the moment this visit
                    // began. Read from the StateFlow, not a plain field, or
                    // the list would not recompose when the baseline moves.
                    val isNew = !seenItemGuids.contains(item.guid)
                    RssItemView(
                        item = item,
                        isNew = isNew,
                        onClick = {
                            onAddTorrent(item.downloadUrl)
                            navController.navigate(Routes.MAIN_SCREEN)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RssItemView(item: RssItem, isNew: Boolean, onClick: () -> Unit) {
    // New items are rendered with the default text color, old items are greyed out.
    val contentColor = if (isNew) MaterialTheme.colorScheme.onSurface else Color.Gray

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
