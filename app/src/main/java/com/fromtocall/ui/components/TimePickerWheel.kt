package com.fromtocall.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TimePickerWheel(
    hour: Int,
    minute: Int,
    onTimeChange: (hour: Int, minute: Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeNumberPicker(
            value = hour,
            range = 0..23,
            onValueChange = { onTimeChange(it, minute) }
        )

        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        TimeNumberPicker(
            value = minute,
            range = 0..59,
            onValueChange = { onTimeChange(hour, it) }
        )
    }
}

@Composable
private fun TimeNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toString().padStart(2, '0')) }
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = textValue,
        onValueChange = { newValue ->
            if (newValue.length <= 2) {
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.isEmpty() || (filtered.toIntOrNull() != null && filtered.toInt() in range)) {
                    textValue = filtered.padStart(if (isFocused) 0 else 2, '0')
                    filtered.toIntOrNull()?.let { onValueChange(it) }
                }
            }
        },
        textStyle = TextStyle(
            fontSize = 48.sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontWeight = MaterialTheme.typography.displayMedium.fontWeight
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .width(80.dp)
            .height(64.dp)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (!focusState.isFocused && textValue.length == 1) {
                    textValue = textValue.padStart(2, '0')
                }
            },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.Center) {
                if (textValue.isEmpty()) {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        }
    )
}
