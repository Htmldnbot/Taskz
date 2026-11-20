package com.example.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import com.example.core.network.SupabaseConfig
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme

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
        containerColor = NothingTheme.colors.surface,
        title = {
            NothingDotMatrixText(
                text = "BACKEND CONFIG",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Set your Supabase project URL and anon public key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
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
                        color = NothingRed,
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
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("save_config_button")
            ) {
                Text("Save Configuration", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
