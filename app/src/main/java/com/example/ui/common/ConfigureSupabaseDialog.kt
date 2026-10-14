package com.example.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.network.SupabaseConfig

@Composable
fun ConfigureSupabaseDialog(
    onDismiss: () -> Unit,
    onSave: (url: String, anonKey: String) -> Unit
) {
    var url by remember { mutableStateOf(SupabaseConfig.getActiveUrl()) }
    var key by remember { mutableStateOf(SupabaseConfig.getActiveKey()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Supabase Backend Config",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Configure your Supabase project URL and anon public key. You can also specify these in your root .env file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                TaskzTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        errorText = null
                    },
                    label = "Supabase Project URL",
                    placeholder = "https://your-project.supabase.co",
                    testTag = "supabase_url_input"
                )
                Spacer(modifier = Modifier.height(12.dp))
                TaskzTextField(
                    value = key,
                    onValueChange = {
                        key = it
                        errorText = null
                    },
                    label = "Supabase Anon Key",
                    placeholder = "eyJhbGciOi...",
                    testTag = "supabase_key_input"
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isBlank() || !url.startsWith("http")) {
                        errorText = "Please enter a valid HTTP/HTTPS URL"
                        return@Button
                    }
                    if (key.isBlank()) {
                        errorText = "Please enter the Supabase Anon key"
                        return@Button
                    }
                    SupabaseConfig.runtimeUrl = url.trim()
                    SupabaseConfig.runtimeAnonKey = key.trim()
                    onSave(url.trim(), key.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("save_config_button")
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
