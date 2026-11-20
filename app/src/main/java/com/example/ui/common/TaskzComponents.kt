package com.example.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SyncMetadata
import com.example.core.model.SyncStatus
import com.example.ui.theme.DotMatrixFontFamily
import com.example.ui.theme.NothingAmber
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskzPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    testTag: String = ""
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    )
                )
            }
        }
    }
}

@Composable
fun TaskzOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    testTag: String = ""
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, NothingTheme.colors.border),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
fun TaskzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    testTag: String = ""
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            isError = !errorMessage.isNullOrBlank(),
            singleLine = singleLine,
            maxLines = maxLines,
            leadingIcon = leadingIcon?.let {
                { Icon(imageVector = it, contentDescription = null, tint = NothingTheme.colors.textSecondary) }
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("${testTag}_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = NothingTheme.colors.textSecondary
                        )
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = NothingTheme.colors.border,
                focusedContainerColor = NothingTheme.colors.surface,
                unfocusedContainerColor = NothingTheme.colors.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        )

        AnimatedVisibility(visible = !errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage.orEmpty(),
                color = NothingRed,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun TaskzStatusBadge(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label, icon) = when (status) {
        SyncStatus.SYNCED -> Quad(
            NothingGreen.copy(alpha = 0.14f),
            NothingGreen,
            "SYNCED",
            Icons.Default.CloudDone
        )
        SyncStatus.OFFLINE -> Quad(
            NothingTheme.colors.surfaceElevated,
            NothingTheme.colors.textSecondary,
            "OFFLINE",
            Icons.Default.CloudOff
        )
        SyncStatus.SYNCING -> Quad(
            NothingTheme.colors.surfaceElevated,
            MaterialTheme.colorScheme.onSurface,
            "SYNCING",
            Icons.Default.CloudSync
        )
        SyncStatus.PENDING -> Quad(
            NothingAmber.copy(alpha = 0.14f),
            NothingAmber,
            "PENDING",
            Icons.Default.HourglassEmpty
        )
        SyncStatus.PENDING_VERIFICATION -> Quad(
            NothingAmber.copy(alpha = 0.16f),
            NothingAmber,
            "PENDING CHECK",
            Icons.Default.Warning
        )
        SyncStatus.ERROR -> Quad(
            NothingRed.copy(alpha = 0.14f),
            NothingRed,
            "SYNC ERROR",
            Icons.Default.ErrorOutline
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = TechMonoFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun SyncStatusCard(
    syncMetadata: SyncMetadata,
    onRefreshClick: () -> Unit,
    onClearFailedClick: (() -> Unit)? = null,
    externalErrorMessage: String? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }

    NothingHairlineCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (syncMetadata.status) {
                                SyncStatus.SYNCED -> NothingGreen
                                SyncStatus.SYNCING -> MaterialTheme.colorScheme.onSurface
                                SyncStatus.OFFLINE -> NothingTheme.colors.textSecondary
                                SyncStatus.PENDING, SyncStatus.PENDING_VERIFICATION -> NothingAmber
                                SyncStatus.ERROR -> NothingRed
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DATA SYNC",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = NothingTheme.colors.textSecondary
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = NothingTheme.colors.surfaceElevated,
                border = BorderStroke(1.dp, NothingTheme.colors.border),
                modifier = Modifier.testTag("auto_sync_status_pill")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.8.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SYNCING",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = "Automatic synchronization active",
                            tint = NothingGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "AUTO-SYNC",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STATUS",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                TaskzStatusBadge(status = syncMetadata.status)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "PENDING QUEUE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${syncMetadata.pendingSyncCount}",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = DotMatrixFontFamily,
                        fontSize = 22.sp
                    ),
                    color = if (syncMetadata.pendingSyncCount > 0) NothingAmber else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        NothingDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LAST FETCH",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
                Text(
                    text = syncMetadata.lastServerFetch?.let { dateFormatter.format(Date(it)) } ?: "NEVER",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "LAST SYNC",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
                Text(
                    text = syncMetadata.lastSuccessfulSync?.let { dateFormatter.format(Date(it)) } ?: "NEVER",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = TechMonoFontFamily,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val effectiveMessage = externalErrorMessage?.takeIf { it.isNotBlank() }
            ?: syncMetadata.statusMessage?.takeIf { it.isNotBlank() && syncMetadata.status != SyncStatus.SYNCED }
        if (!effectiveMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effectiveMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (syncMetadata.status == SyncStatus.ERROR || !externalErrorMessage.isNullOrBlank()) NothingRed else NothingTheme.colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (syncMetadata.status == SyncStatus.ERROR && onClearFailedClick != null) {
                    TextButton(onClick = onClearFailedClick) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AccountInactiveDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingTheme.colors.surface,
        title = {
            Text(
                text = "Account Inactive",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "Your Taskz account is currently inactive. Please contact the administrator to reactivate your access."
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.testTag("inactive_dialog_ok_button")
            ) {
                Text("Understood")
            }
        }
    )
}
