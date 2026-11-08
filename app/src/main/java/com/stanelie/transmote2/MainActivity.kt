package com.stanelie.transmote2


import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import java.io.IOException

// Define routes for navigation
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

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
            App { navController, mainViewModel -> handleIntent(navController, mainViewModel) }
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
