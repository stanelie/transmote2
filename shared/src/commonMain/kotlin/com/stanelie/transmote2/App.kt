package com.stanelie.transmote2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.stanelie.transmote2.ui.AppIcons
import com.stanelie.transmote2.ui.theme.Transmote2Theme

/**
 * The whole app UI: theme, top bar and navigation graph. Hosted by
 * MainActivity on Android and by MainViewController on iOS.
 *
 * [onReady] fires once the navigation graph is attached. Android uses it to
 * act on the launching Intent; it must not run earlier, because Scaffold
 * composes NavHost in a later subcomposition pass and navigating before the
 * graph exists throws "Navigation graph has not been set".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(onReady: suspend (NavController, MainViewModel) -> Unit = { _, _ -> }) {
    Transmote2Theme {
        val navController = rememberNavController()
        val preferencesDataStore = remember { PreferencesDataStore() }
        val settingsViewModel: SettingsViewModel =
            viewModel(factory = SettingsViewModel.provideFactory(preferencesDataStore))
        val mainViewModel: MainViewModel =
            viewModel(factory = MainViewModel.provideFactory(preferencesDataStore))
        val rssViewModel: RssViewModel =
            viewModel(factory = RssViewModel.provideFactory(preferencesDataStore, RssReader()))

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
            NavHost(
                navController = navController,
                startDestination = Routes.MAIN_SCREEN,
                modifier = Modifier.padding(innerPadding)
            ) {
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
                        onAddTorrent = { magnetLink -> mainViewModel.addTorrent(magnetLink) }
                    )
                }
                composable(
                    route = Routes.TORRENT_DETAIL_SCREEN,
                    arguments = listOf(navArgument("torrentId") { type = NavType.IntType })
                ) {
                    // arguments is a SavedState in common code, not an Android Bundle,
                    // so it is read through the savedstate reader rather than getInt().
                    val torrentId = it.arguments?.read { getIntOrNull("torrentId") } ?: -1
                    val torrentDetailViewModel: TorrentDetailViewModel =
                        viewModel(factory = TorrentDetailViewModel.provideFactory(torrentId, preferencesDataStore))
                    TorrentDetailScreen(navController = navController, torrentDetailViewModel = torrentDetailViewModel)
                }
            }
        }

        LaunchedEffect(navController) {
            while (runCatching { navController.graph }.isFailure) {
                withFrameNanos {}
            }
            onReady(navController, mainViewModel)
        }

        // A tapped push notification asks for a particular screen.
        val pendingScreen by PushRouting.pendingScreen.collectAsState()
        LaunchedEffect(pendingScreen) {
            val target = pendingScreen ?: return@LaunchedEffect
            while (runCatching { navController.graph }.isFailure) {
                withFrameNanos {}
            }
            navController.navigate(target) {
                popUpTo(Routes.MAIN_SCREEN) { inclusive = target == Routes.MAIN_SCREEN }
            }
            PushRouting.consume()
        }
    }
}
