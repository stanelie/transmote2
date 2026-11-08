package com.stanelie.transmote2

import com.stanelie.transmote2.ui.AppIcons

import android.Manifest
import android.content.Context
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
import com.stanelie.transmote2.ui.AppIcons.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
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
                val preferencesDataStore = remember { PreferencesDataStore() }
                val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.provideFactory(preferencesDataStore))
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.provideFactory(preferencesDataStore))
                val rssViewModel: RssViewModel = viewModel(factory = RssViewModel.provideFactory(preferencesDataStore, RssReader()))

                val connectionStatus by mainViewModel.connectionStatus.collectAsState()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                if (currentRoute == Routes.RSS_SCREEN) {
                                    TopBarTitle("RSS Feed")
                                } else {
                                    ConnectionStatusTitle(connectionStatus)
                                }
                            },
                            navigationIcon = {
                                if (currentRoute == Routes.RSS_SCREEN) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            colors = transmoteTopAppBarColors(),
                            actions = {
                                if (currentRoute == Routes.MAIN_SCREEN) {
                                    IconButton(onClick = { navController.navigate(Routes.RSS_SCREEN) }) {
                                        Icon(AppIcons.RssFeed, contentDescription = "RSS Feed")
                                    }
                                    IconButton(onClick = { navController.navigate(Routes.SETTINGS_SCREEN) }) {
                                        Icon(AppIcons.Settings, contentDescription = "Settings")
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

                // Handle incoming intent. Deferred to a LaunchedEffect: the RSS-notification
                // branches call navController.navigate(), and Scaffold composes its content
                // (and thus NavHost, which attaches the graph) in a later subcomposition
                // pass, so navigating too early throws "Navigation graph has not been set".
                LaunchedEffect(navController) {
                    while (runCatching { navController.graph }.isFailure) {
                        withFrameNanos {}
                    }
                    handleIntent(navController, mainViewModel)
                }
            }
        }
    }

    private fun handleIntent(navController: NavController, viewModel: MainViewModel) {
        val currentIntent = intent ?: return
        val intentData = currentIntent.data
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
            currentIntent.data = null
        } else when (currentIntent.action) {
            NotificationHelper.ACTION_SHOW_RSS -> {
                // Tapping a grouped notification's summary: go see which items are new.
                navController.navigate(Routes.RSS_SCREEN)
            }
            NotificationHelper.ACTION_ADD_RSS_TORRENT -> {
                // Tapping a single item's notification (or one expanded out of a group):
                // add it straight away and land on the downloads list to confirm it landed.
                val link = currentIntent.getStringExtra(NotificationHelper.EXTRA_RSS_LINK)
                if (link != null) {
                    viewModel.addTorrent(link)
                    currentIntent.getStringExtra(NotificationHelper.EXTRA_RSS_GUID)?.let { markRssGuidSeen(it) }
                    navController.navigate(Routes.MAIN_SCREEN) {
                        popUpTo(Routes.MAIN_SCREEN) { inclusive = true }
                    }
                    Toast.makeText(this, "Adding torrent from RSS feed...", Toast.LENGTH_SHORT).show()
                }
            }
        }
        currentIntent.action = null
    }

    private fun markRssGuidSeen(guid: String) {
        val prefs = getSharedPreferences(RssPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(RssPrefs.SEEN_GUIDS_KEY, emptySet()) ?: emptySet()
        prefs.edit().putStringSet(RssPrefs.SEEN_GUIDS_KEY, seen + guid).apply()
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
