package com.streamvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    initialInstanceUrl: String,
    onSaveInstanceUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var instanceUrl by remember { mutableStateOf(initialInstanceUrl) }
    var savedBanner by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Stream Backend Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Channel browsing works out-of-the-box via public YouTube Atom XML feeds. To resolve adaptive/progressive video streams from your own self-hosted Piped API instance, enter its base URL below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
        )

        OutlinedTextField(
            value = instanceUrl,
            onValueChange = {
                instanceUrl = it
                savedBanner = false
            },
            label = { Text("Piped-Compatible API Base URL (Optional)") },
            placeholder = { Text("https://pipedapi.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveInstanceUrl(instanceUrl)
                savedBanner = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Configuration")
        }

        if (savedBanner) {
            Text(
                text = "Configuration saved.",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Built-in Capabilities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = """
                • Zero third-party ad SDKs
                • Media3 ExoPlayer with 144p–2160p track selection
                • Playback speed control (0.25x – 2.0x)
                • Local Room database for Favorites & Offline Downloads
            """.trimIndent(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
