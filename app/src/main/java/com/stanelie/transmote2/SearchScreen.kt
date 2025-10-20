package com.stanelie.transmote2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
    navController: NavController,
    searchViewModel: SearchViewModel,
    onAddTorrent: (String) -> Unit
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val selectedCategory by searchViewModel.selectedCategory.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val error by searchViewModel.error.collectAsState()

    var isCategoryMenuExpanded by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically // Use CenterVertically for the whole row
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchViewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search query", fontSize = 14.sp) }, // Use placeholder instead of label
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(fontSize = 14.sp),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        searchViewModel.executeSearch()
                    }
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExposedDropdownMenuBox(
                expanded = isCategoryMenuExpanded,
                onExpandedChange = { isCategoryMenuExpanded = !isCategoryMenuExpanded },
                modifier = Modifier.weight(0.8f)
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryMenuExpanded) },
                    modifier = Modifier.menuAnchor(),
                    textStyle = TextStyle(fontSize = 14.sp),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = isCategoryMenuExpanded,
                    onDismissRequest = { isCategoryMenuExpanded = false }
                ) {
                    searchViewModel.categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                searchViewModel.onCategorySelected(category)
                                isCategoryMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    searchViewModel.executeSearch()
                },
                enabled = searchQuery.isNotBlank() && !isLoading
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator()
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .simpleVerticalScrollbar(listState)
        ) {
            items(searchResults) { result ->
                SearchResultItem(
                    result = result,
                    onClick = {
                        searchViewModel.getMagnetLink(result.detailsUrl) { magnetLink ->
                            onAddTorrent(magnetLink)
                            navController.popBackStack()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SearchResultItem(result: SearchResult, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(result.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Seeders: ${result.seeders}", style = MaterialTheme.typography.bodySmall)
                Text("Leechers: ${result.leechers}", style = MaterialTheme.typography.bodySmall)
                Text(result.size, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
