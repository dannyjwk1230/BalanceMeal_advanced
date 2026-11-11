package com.example.balancemeal_advanced.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.ui.components.balanceBackground
import com.example.balancemeal_advanced.ui.home.HomeIntakeCard
import com.example.balancemeal_advanced.ui.home.HomeUiState
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import java.time.LocalDate

@Preview(name = "권장 범위와 세 가지 상태", widthDp = 390, heightDp = 780)
@Preview(name = "좁은 화면과 큰 글자", widthDp = 280, heightDp = 1100, fontScale = 1.5f)
@Composable
fun HomeIntakeRangePreview() {
    BalanceMeal_advancedTheme {
        Box(Modifier.balanceBackground().padding(20.dp)) {
            HomeIntakeCard(HomeUiState(
                date = LocalDate.of(2026, 11, 11), loading = false,
                total = NutrientAmounts(150.0, 75.0, 80.0, 2000.0),
                targets = EnergyTargets.fromEnergy(2000.0), targetEnergyLabel = "2000",
            ), {})
        }
    }
}

@Preview(name = "신체 정보 미입력", widthDp = 390, heightDp = 780)
@Composable
fun HomeIntakeWithoutBodyPreview() {
    BalanceMeal_advancedTheme {
        Box(Modifier.balanceBackground().padding(20.dp)) {
            HomeIntakeCard(HomeUiState(
                date = LocalDate.of(2026, 11, 11), loading = false,
                total = NutrientAmounts(120.0, 50.0, 30.0, 1500.0),
            ), {})
        }
    }
}
