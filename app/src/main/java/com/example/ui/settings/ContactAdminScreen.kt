package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.common.NothingDotMatrixText
import com.example.ui.common.NothingSectionHeader
import com.example.ui.common.TaskzAmbientBackground
import com.example.ui.common.TaskzGlassSurface
import com.example.ui.common.TaskzHaptics
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

const val ADMIN_SUPPORT_EMAIL = "munesh.y@tcs.com"

enum class SupportCategory(val label: String) {
    ACCOUNT_ACCESS("Account Access"),
    BUG_REPORT("Bug Report"),
    APP_UPDATE("App Update"),
    GENERAL_SUPPORT("General Support")
}

fun defaultSupportSubject(category: SupportCategory = SupportCategory.GENERAL_SUPPORT): String {
    return if (category == SupportCategory.GENERAL_SUPPORT) {
        "Taskz Support - Version ${BuildConfig.VERSION_NAME}"
    } else {
        "Taskz Support (${category.label}) - Version ${BuildConfig.VERSION_NAME}"
    }
}

fun buildDiagnosticsSummary(
    employeeId: String? = null,
    userEmail: String? = null,
    roleName: String? = null
): String {
    return buildString {
        appendLine("--- App & Device Info ---")
        appendLine("App Version: ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})")
        appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        appendLine("Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        if (!employeeId.isNullOrBlank()) appendLine("Employee ID: $employeeId")
        if (!userEmail.isNullOrBlank()) appendLine("Email: $userEmail")
        if (!roleName.isNullOrBlank()) appendLine("Active Role: $roleName")
    }.trim()
}

fun buildSupportMailtoUri(
    category: SupportCategory,
    subject: String,
    message: String,
    includeDiagnostics: Boolean,
    diagnosticsText: String
): Uri {
    val cleanSubject = subject.trim().ifBlank { defaultSupportSubject(category) }
    val body = buildString {
        appendLine("Category: ${category.label}")
        appendLine()
        appendLine(message.trim().ifBlank { "(Describe your issue or request here)" })
        if (includeDiagnostics && diagnosticsText.isNotBlank()) {
            appendLine()
            appendLine(diagnosticsText)
        }
    }.trim()

    val encodedSubject = Uri.encode(cleanSubject)
    val encodedBody = Uri.encode(body)
    return Uri.parse("mailto:$ADMIN_SUPPORT_EMAIL?subject=$encodedSubject&body=$encodedBody")
}

fun launchSupportEmailIntent(
    context: Context,
    mailtoUri: Uri
): Boolean {
    val sendToIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = mailtoUri
    }
    return runCatching {
        context.startActivity(sendToIntent)
        true
    }.getOrDefault(false)
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

@Composable
fun ContactAdminScreen(
    initialCategory: SupportCategory = SupportCategory.GENERAL_SUPPORT,
    employeeId: String? = null,
    userEmail: String? = null,
    roleName: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = NothingTheme.colors

    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    var subject by remember(initialCategory) { mutableStateOf(defaultSupportSubject(initialCategory)) }
    var message by remember { mutableStateOf("") }
    var includeDiagnostics by remember { mutableStateOf(true) }
    var noMailAppNotice by remember { mutableStateOf(false) }

    val diagnosticsText = remember(employeeId, userEmail, roleName) {
        buildDiagnosticsSummary(employeeId, userEmail, roleName)
    }

    TaskzAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("contact_admin_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        TaskzHaptics.tap(view)
                        onBack()
                    },
                    modifier = Modifier.testTag("contact_admin_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                NothingDotMatrixText(
                    text = "CONTACT ADMIN",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TaskzGlassSurface {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = "Support Administrator",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Administrator Support",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = ADMIN_SUPPORT_EMAIL,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = TechMonoFontFamily),
                                        color = colors.textSecondary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    TaskzHaptics.tap(view)
                                    copyToClipboard(context, "Admin Email", ADMIN_SUPPORT_EMAIL)
                                },
                                modifier = Modifier.testTag("copy_admin_email_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy Administrator Email",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }

                    NothingSectionHeader(title = "SUPPORT CATEGORY")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SupportCategory.entries.forEach { cat ->
                            val selected = cat == selectedCategory
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    TaskzHaptics.tap(view)
                                    val wasDefault = subject == defaultSupportSubject(selectedCategory)
                                    selectedCategory = cat
                                    if (wasDefault) subject = defaultSupportSubject(cat)
                                },
                                label = { Text(cat.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentPrimary,
                                    selectedLabelColor = colors.onAccentPrimary
                                )
                            )
                        }
                    }

                    TaskzGlassSurface {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Subject") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_admin_subject_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Message") },
                            placeholder = { Text("Describe what happened, steps to reproduce, or what access help you need...") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_admin_message_input")
                        )
                    }

                    TaskzGlassSurface {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Include App & Device Info",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "App version, Android OS & device model. No passwords or tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Switch(
                                checked = includeDiagnostics,
                                onCheckedChange = {
                                    TaskzHaptics.tap(view)
                                    includeDiagnostics = it
                                }
                            )
                        }
                        if (includeDiagnostics) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = diagnosticsText,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                                color = colors.textSecondary
                            )
                        }
                    }

                    if (noMailAppNotice) {
                        TaskzGlassSurface(elevated = true) {
                            Text(
                                text = "No email app found on this device. Use Copy Support Details below and email $ADMIN_SUPPORT_EMAIL.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.amber
                            )
                        }
                    }

                    Button(
                        onClick = {
                            TaskzHaptics.confirm(view)
                            val uri = buildSupportMailtoUri(
                                category = selectedCategory,
                                subject = subject,
                                message = message,
                                includeDiagnostics = includeDiagnostics,
                                diagnosticsText = diagnosticsText
                            )
                            val launched = launchSupportEmailIntent(context, uri)
                            noMailAppNotice = !launched
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPrimary,
                            contentColor = colors.onAccentPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("contact_admin_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compose Email to $ADMIN_SUPPORT_EMAIL")
                    }

                    OutlinedButton(
                        onClick = {
                            TaskzHaptics.tap(view)
                            val fullCopy = buildString {
                                appendLine("To: $ADMIN_SUPPORT_EMAIL")
                                appendLine("Subject: ${subject.trim().ifBlank { defaultSupportSubject(selectedCategory) }}")
                                appendLine("Category: ${selectedCategory.label}")
                                appendLine()
                                appendLine(message.trim())
                                if (includeDiagnostics) {
                                    appendLine()
                                    appendLine(diagnosticsText)
                                }
                            }
                            copyToClipboard(context, "Support details", fullCopy)
                        },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("contact_admin_copy_details_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Support Details")
                    }
                }
            }
        }
    }
}

@Composable
fun ContactAdminDialog(
    initialCategory: SupportCategory = SupportCategory.ACCOUNT_ACCESS,
    employeeId: String? = null,
    userEmail: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = NothingTheme.colors
    var category by remember(initialCategory) { mutableStateOf(initialCategory) }
    var subject by remember(initialCategory) { mutableStateOf(defaultSupportSubject(initialCategory)) }
    var message by remember { mutableStateOf("") }
    var mailMissing by remember { mutableStateOf(false) }

    val diagnostics = remember(employeeId, userEmail) {
        buildDiagnosticsSummary(employeeId = employeeId, userEmail = userEmail)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        icon = { Icon(Icons.Default.Email, contentDescription = "Contact Administrator") },
        title = { Text("Contact Administrator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Support Email: $ADMIN_SUPPORT_EMAIL",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = TechMonoFontFamily),
                    color = colors.textSecondary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                TaskzHaptics.tap(view)
                                category = cat
                                subject = defaultSupportSubject(cat)
                            },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mailMissing) {
                    Text(
                        text = "No mail client detected. Copy the administrator email or details below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.amber
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    TaskzHaptics.confirm(view)
                    val uri = buildSupportMailtoUri(
                        category = category,
                        subject = subject,
                        message = message,
                        includeDiagnostics = true,
                        diagnosticsText = diagnostics
                    )
                    val launched = launchSupportEmailIntent(context, uri)
                    if (launched) onDismiss() else mailMissing = true
                }
            ) {
                Text("Email Admin")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        copyToClipboard(context, "Admin Email", ADMIN_SUPPORT_EMAIL)
                    }
                ) {
                    Text("Copy Email")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
