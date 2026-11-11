package com.example.balancemeal_advanced.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.balancemeal_advanced.ui.components.DatePickerMode
import com.example.balancemeal_advanced.ui.components.WheelDatePicker
import com.example.balancemeal_advanced.ui.components.balanceBackground
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import java.time.LocalDate

@Preview(name = "생년월일 휠 피커", widthDp = 390, heightDp = 844)
@Preview(name = "큰 글자 휠 피커", widthDp = 320, heightDp = 700, fontScale = 1.5f)
@Composable
fun BirthWheelDatePickerPreview() {
    BalanceMeal_advancedTheme {
        Box(Modifier.fillMaxSize().balanceBackground()) {
            WheelDatePicker(
                mode = DatePickerMode.BIRTH,
                initialDate = LocalDate.of(2022, 3, 23),
                today = LocalDate.of(2026, 11, 11),
                onSelect = {}, onDismissRequest = {},
            )
        }
    }
}
