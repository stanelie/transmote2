package com.stanelie.transmote2

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stanelie.transmote2.ui.theme.Transmote2Theme
import java.io.IOException

// Define routes for navigation
object Routes {
    const val MAIN_SCREEN = "main_screen"
    const val SETTINGS_SCREEN = "settings_screen"
    const val SEARCH_SCREEN = "search_screen"
    const val RSS_SCREEN = "rss_screen"
    const val TORRENT_DETAIL_SCREEN = "torrent_detail_screen/{torrentId}"
    fun torrentDetailRoute(torrentId: Int) = "torrent_detail_screen/$torrentId"
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        enableEdgeToEdge()
        setContent {
            Transmote2Theme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val preferencesDataStore = remember { PreferencesDataStore(context) }
                val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.provideFactory(preferencesDataStore, context.applicationContext))
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(preferencesDataStore))
                val searchViewModel: SearchViewModel = viewModel()
                val rssViewModel: RssViewModel = viewModel(factory = RssViewModel.provideFactory(preferencesDataStore, RssReader(), context.applicationContext))

                val connectionStatus by mainViewModel.connectionStatus.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { 
                                if (currentRoute == Routes.SEARCH_SCREEN) {
                                    Text("Search 1337x.to")
                                } else if (currentRoute == Routes.RSS_SCREEN) {
                                    Text("RSS Feed")
                                } else {
                                    Text(connectionStatus, style = MaterialTheme.typography.titleMedium)
                                }
                            },
                            navigationIcon = {
                                if (currentRoute == Routes.SEARCH_SCREEN || currentRoute == Routes.RSS_SCREEN) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            actions = {
                                if (currentRoute == Routes.MAIN_SCREEN) {
                                    IconButton(onClick = { navController.navigate(Routes.RSS_SCREEN) }) {
                                        Icon(Icons.Filled.RssFeed, contentDescription = "RSS Feed")
                                    }
                                    IconButton(onClick = { navController.navigate(Routes.SEARCH_SCREEN) }) {
                                        Icon(Icons.Filled.Search, contentDescription = "Search")
                                    }
                                    IconButton(onClick = { navController.navigate(Routes.SETTINGS_SCREEN) }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    NavHost(navController = navController, startDestination = Routes.MAIN_SCREEN, modifier = Modifier.padding(innerPadding)) {
                        composable(Routes.MAIN_SCREEN) {
                            MainScreen(navController = navController, mainViewModel = mainViewModel)
                        }
                        composable(Routes.SETTINGS_SCREEN) {
                            SettingsScreen(navController = navController, settingsViewModel = settingsViewModel)
                        }
                        composable(Routes.SEARCH_SCREEN) {
                            SearchScreen(
                                navController = navController,
                                searchViewModel = searchViewModel,
                                onAddTorrent = { magnetLink ->
                                    mainViewModel.addTorrent(magnetLink)
                                }
                            )
                        }
                        composable(Routes.RSS_SCREEN) {
                            RssScreen(
                                navController = navController,
                                rssViewModel = rssViewModel,
                                onAddTorrent = { magnetLink ->
                                    mainViewModel.addTorrent(magnetLink)
                                }
                            )
                        }
                        composable(
                            route = Routes.TORRENT_DETAIL_SCREEN,
                            arguments = listOf(navArgument("torrentId") { type = NavType.IntType })
                        ) {
                            val torrentId = it.arguments?.getInt("torrentId") ?: -1
                            val torrentDetailViewModel: TorrentDetailViewModel = viewModel(factory = TorrentDetailViewModel.provideFactory(torrentId, preferencesDataStore))
                            TorrentDetailScreen(navController = navController, torrentDetailViewModel = torrentDetailViewModel)
                        }
                    }
                }

                // Handle incoming intent
                handleIntent(mainViewModel)
            }
        }
    }

    private fun handleIntent(viewModel: MainViewModel) {
        val intentData = intent?.data
        if (intentData != null) {
            when {
                intentData.scheme == "magnet" -> {
                    viewModel.addTorrent(intentData.toString())
                    Toast.makeText(this, "Adding torrent from magnet link...", Toast.LENGTH_SHORT).show()
                }
                intentData.scheme == "http" || intentData.scheme == "https" -> {
                    Toast.makeText(this, "Downloading .torrent file from link (not implemented yet)...", Toast.LENGTH_LONG).show()
                }
                intentData.scheme == "content" -> { // For file manager attachments
                    try {
                        val inputStream = contentResolver.openInputStream(intentData)
                        val torrentBytes = inputStream?.readBytes()
                        inputStream?.close()

                        if (torrentBytes != null) {
                            val base64String = Base64.encodeToString(torrentBytes, Base64.DEFAULT)
                            viewModel.addTorrent(base64String)
                            Toast.makeText(this, "Adding torrent from file...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Error: Could not read torrent file.", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: IOException) {
                        Toast.makeText(this, "Error reading torrent file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            intent.data = null
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Transmote2Theme {
        val navController = rememberNavController()
        Text("Preview of MainActivity is more complex with navigation. Please run the app.")
    }
}
