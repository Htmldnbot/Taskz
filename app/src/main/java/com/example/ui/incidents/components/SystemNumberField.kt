package com.example.ui.incidents.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SystemIncident

@Composable
fun SystemNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    testTag: String = "system_number_field",
    onImeAction: () -> Unit = {}
) {
    // Extract digits from current value (e.g. "C059" -> "059", "59" -> "59")
    val digitsOnly = remember(value) {
        val raw = if (value.startsWith("C", ignoreCase = true)) value.drop(1) else value
        raw.filter { it.isDigit() }.take(3)
    }

    var localText by remember(digitsOnly) { mutableStateOf(digitsOnly) }

    fun normalizeAndCommit() {
        if (localText.isNotBlank()) {
            val normalized = SystemIncident.normalizeSystemNumber(localText)
            onValueChange(normalized)
        } else {
            onValueChange("")
        }
    }

    OutlinedTextField(
        value = localText,
        onValueChange = { input ->
            // Allow user to paste with "C" or type digits only
            val cleaned = if (input.startsWith("C", ignoreCase = true)) input.drop(1) else input
            val numbers = cleaned.filter { it.isDigit() }.take(3)
            localText = numbers
            if (numbers.length == 3) {
                onValueChange("C$numbers")
            } else {
                onValueChange(if (numbers.isEmpty()) "" else "C$numbers")
            }
        },
        enabled = enabled,
        isError = isError,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        placeholder = { Text("005", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        prefix = {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        supportingText = {
            if (isError && !errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "e.g. 5 \u2192 C005, 105 \u2192 C105",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                normalizeAndCommit()
                onImeAction()
            },
            onDone = {
                normalizeAndCommit()
                onImeAction()
            }
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        ),
        modifier = modifier
            .testTag(testTag)
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    normalizeAndCommit()
                }
            }
    )
}
