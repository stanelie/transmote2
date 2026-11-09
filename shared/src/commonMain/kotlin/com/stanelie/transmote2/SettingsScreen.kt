package com.stanelie.transmote2

import com.stanelie.transmote2.ui.AppIcons

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, settingsViewModel: SettingsViewModel) {
    val serverAddress by settingsViewModel.serverAddress.collectAsState()
    val port by settingsViewModel.port.collectAsState()
    val username by settingsViewModel.username.collectAsState()
    val password by settingsViewModel.password.collectAsState()
    val useSsl by settingsViewModel.useSsl.collectAsState()
    val refreshFrequency by settingsViewModel.refreshFrequency.collectAsState()
    val rssFeedUrl by settingsViewModel.rssFeedUrl.collectAsState()
    val webDownloadUrl by settingsViewModel.webDownloadUrl.collectAsState()
    val rssPollingIntervalHours by settingsViewModel.rssPollingIntervalHours.collectAsState()
    val pushServerUrl by settingsViewModel.pushServerUrl.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TopBarTitle("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        settingsViewModel.saveSettings()
                        navController.popBackStack()
                    }) {
                        Icon(AppIcons.Check, contentDescription = "Save Settings")
                    }
                },
                colors = transmoteTopAppBarColors()
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = serverAddress,
                    onValueChange = { settingsViewModel.updateServerAddress(it) },
                    label = { Text("Server Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { settingsViewModel.updatePort(it) },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { settingsViewModel.updateUsername(it) },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { settingsViewModel.updatePassword(it) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Use SSL")
                    Switch(
                        checked = useSsl,
                        onCheckedChange = { settingsViewModel.updateUseSsl(it) }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = refreshFrequency,
                    onValueChange = { settingsViewModel.updateRefreshFrequency(it) },
                    label = { Text("Refresh Frequency (seconds)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = rssFeedUrl,
                    onValueChange = { settingsViewModel.updateRssFeedUrl(it) },
                    label = { Text("RSS Feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = webDownloadUrl,
                    onValueChange = { settingsViewModel.updateWebDownloadUrl(it) },
                    label = { Text("Web Download URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = rssPollingIntervalHours,
                    onValueChange = { settingsViewModel.updateRssPollingIntervalHours(it) },
                    label = { Text("RSS polling interval (hours)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = pushServerUrl,
                    onValueChange = { settingsViewModel.updatePushServerUrl(it) },
                    label = { Text("Push server URL - NOT ACTIVE YET") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            "TODO: RSS push. Nothing uses this yet - setting it has no " +
                                "effect. Download-complete notifications do not need it."
                        )
                    },
                    keyboardOptions = KeyboardOptions(autoCorrect = false)
                )

                // iOS only: the APNs token the push server needs. Android
                // never registers for APNs, so this stays hidden there.
                val pushToken by PushRegistration.token.collectAsState()
                val pushError by PushRegistration.error.collectAsState()
                if (pushToken != null || pushError != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Push notifications", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pushToken ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(if (pushError != null) "Device token (unavailable)" else "Device token") },
                        supportingText = {
                            Text(pushError ?: "Copy this into the push server's config.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.ime))
        }
    }
}
