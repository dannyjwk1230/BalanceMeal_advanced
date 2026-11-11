package com.example.balancemeal_advanced.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.ui.theme.BalanceColors

/** Compact outlined input with a floating label shared by all keyboard entry fields. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    suffix: String? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = BalanceColors.InputOutlineFocused,
        unfocusedBorderColor = BalanceColors.InputOutline,
        disabledBorderColor = BalanceColors.InputOutline.copy(alpha = .45f),
        errorBorderColor = MaterialTheme.colorScheme.error,
    )
    BasicTextField(
        value = value, onValueChange = onValueChange,
        enabled = enabled, singleLine = true,
        keyboardOptions = keyboardOptions,
        interactionSource = interactions,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
                isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }),
        cursorBrush = SolidColor(if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
        modifier = modifier.padding(top = 8.dp).heightIn(min = 48.dp),
        decorationBox = { input ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value, innerTextField = input,
                enabled = enabled, singleLine = true, isError = isError,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
                label = { Text(label) },
                suffix = suffix?.let { unit -> { Text(unit) } },
                colors = colors,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled, isError = isError, interactionSource = interactions,
                        colors = colors, shape = RoundedCornerShape(12.dp), focusedBorderThickness = 1.dp,
                    )
                },
            )
        },
    )
}
