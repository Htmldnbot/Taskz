package com.example.ui.incidents.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.SystemIncident
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTheme
import com.example.ui.theme.TechMonoFontFamily

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
    val digitsOnly = remember(value) {
        val raw = if (value.startsWith("C", ignoreCase = true)) value.drop(1) else value
        raw.filter { it.isDigit() }.take(3)
    }

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(digitsOnly, TextRange(digitsOnly.length)))
    }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(digitsOnly, isFocused) {
        if (!isFocused && digitsOnly != textFieldValue.text) {
            textFieldValue = TextFieldValue(digitsOnly, TextRange(digitsOnly.length))
        } else if (digitsOnly.isEmpty() && textFieldValue.text.isNotEmpty()) {
            textFieldValue = TextFieldValue("", TextRange(0))
        }
    }

    fun normalizeAndCommit() {
        val rawDigits = textFieldValue.text.filter { it.isDigit() }.take(3)
        if (rawDigits.isNotBlank()) {
            val normalized = SystemIncident.normalizeSystemNumber(rawDigits)
            val normDigits = if (normalized.startsWith("C", ignoreCase = true)) normalized.drop(1) else normalized
            textFieldValue = TextFieldValue(normDigits, TextRange(normDigits.length))
            onValueChange(normalized)
        } else {
            onValueChange("")
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { incoming ->
            val cleaned = if (incoming.text.startsWith("C", ignoreCase = true)) incoming.text.drop(1) else incoming.text
            val numbers = cleaned.filter { it.isDigit() }.take(3)
            val adjustedCursor = incoming.selection.end
                .coerceIn(0, incoming.text.length)
                .let { sel ->
                    incoming.text.substring(0, sel).count { it.isDigit() }.coerceAtMost(numbers.length)
                }
            textFieldValue = TextFieldValue(numbers, TextRange(adjustedCursor))
            onValueChange(if (numbers.isEmpty()) "" else "C$numbers")
        },
        enabled = enabled,
        isError = isError,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = { Text("005", color = NothingTheme.colors.textSecondary.copy(alpha = 0.6f)) },
        prefix = {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .background(
                        color = NothingTheme.colors.accentPrimary,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "C",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = TechMonoFontFamily,
                        color = NothingTheme.colors.onAccentPrimary
                    )
                )
            }
        },
        supportingText = {
            if (isError && !errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = NothingRed,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "5 \u2192 C005 \u00B7 105 \u2192 C105",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = TechMonoFontFamily),
                    color = NothingTheme.colors.textSecondary
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = NothingTheme.colors.border,
            focusedContainerColor = NothingTheme.colors.surface,
            unfocusedContainerColor = NothingTheme.colors.surface
        ),
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
            fontFamily = TechMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        ),
        modifier = modifier
            .testTag(testTag)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused) {
                    normalizeAndCommit()
                }
            }
    )
}
