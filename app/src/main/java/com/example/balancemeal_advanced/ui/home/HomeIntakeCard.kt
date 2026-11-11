package com.example.balancemeal_advanced.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.core.model.NutrientRange
import com.example.balancemeal_advanced.core.nutrition.DailyNutritionAssessment
import com.example.balancemeal_advanced.core.nutrition.IntakeStatus
import com.example.balancemeal_advanced.ui.components.GlassCard
import com.example.balancemeal_advanced.ui.theme.BalanceColors
import java.util.Locale

internal data class HomeIntakeRow(
    val key: String, val label: String, val unit: String,
    val current: Double?, val range: NutrientRange?, val status: IntakeStatus?,
) {
    val scale: Double get() = if (unit == "g") 500.0 else 4000.0

    // Saturate drawing coordinates while preserving actual intake and status values.
    fun position(value: Double): Float = (value / scale).coerceIn(0.0, 1.0).toFloat()
}

internal fun homeIntakeRows(state: HomeUiState): List<HomeIntakeRow> {
    val total = state.total.takeIf { !state.loading && state.error == null }
    val targets = state.targets
    fun nutrient(key: String, label: String, current: Double?, range: NutrientRange?) =
        HomeIntakeRow(key, label, "g", current, range,
            if (current != null && range != null) DailyNutritionAssessment.nutrientStatus(current, range) else null)
    return listOf(
        nutrient("carbohydrate", "탄수화물", total?.carbohydrateG, targets?.carbohydrate),
        nutrient("protein", "단백질", total?.proteinG, targets?.protein),
        nutrient("fat", "지방", total?.fatG, targets?.fat),
        HomeIntakeRow("energy", "칼로리", "kcal", total?.energyKcal,
            targets?.let { NutrientRange(it.energyKcal * .85, it.energyKcal * 1.15) },
            if (total != null && targets != null)
                DailyNutritionAssessment.calorieStatus(total.energyKcal, targets.energyKcal) else null),
    )
}

private fun amount(value: Double, unit: String): String =
    String.format(Locale.KOREA, if (unit == "g") "%.1f" else "%.0f", value)

private val IntakeStatus.label: String get() = when (this) {
    IntakeStatus.LOW -> "부족"
    IntakeStatus.ADEQUATE -> "적정"
    IntakeStatus.HIGH -> "과다"
}
private val IntakeStatus.tint: Color get() = when (this) {
    IntakeStatus.LOW -> Color(0xFF647E9B)
    IntakeStatus.ADEQUATE -> Color(0xFF28856B)
    IntakeStatus.HIGH -> Color(0xFFC56467)
}

@Composable
internal fun HomeIntakeCard(state: HomeUiState, onBody: () -> Unit, onEnableBody: () -> Unit = {}) {
    val rows = homeIntakeRows(state)
    val colors = listOf(BalanceColors.Mint, BalanceColors.Protein, BalanceColors.Fat, Color(0xFF568ACB))
    GlassCard(Modifier.fillMaxWidth().testTag("home_nutrition_card"), radius = 16.dp) {
        Text("오늘의 영양 밸런스", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        if (state.targets != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(16.dp).height(6.dp).background(Color(0xFFCFDCD9), CircleShape))
                Text("일일 권장 범위",
                    style = MaterialTheme.typography.labelSmall, color = BalanceColors.SecondaryText)
            }
        }
        Column {
            rows.forEachIndexed { index, row ->
                IntakeRangeRow(row, colors[index])
                if (index < rows.lastIndex) HorizontalDivider(
                    color = Color(0xFF94A3B8).copy(alpha = .16f), thickness = .5.dp)
            }
        }
        if (state.targets != null) {
            Text("칼로리 적정 범위: 일일 권장 에너지의 85~115%",
                style = MaterialTheme.typography.labelSmall, color = BalanceColors.SecondaryText)
        } else if (!state.loading && state.error == null) {
            HorizontalDivider(color = BalanceColors.Outline.copy(alpha = .12f))
            if (state.calculationDisabled) {
                Button(onClick = onEnableBody, enabled = !state.activatingBody,
                    modifier = Modifier.fillMaxWidth().testTag("body_enable_cta")) {
                    Text("신체 정보 활성화")
                }
                state.activationError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    Text("신체 정보를 입력하고 권장 범위를 확인하세요",
                        style = MaterialTheme.typography.bodySmall, color = BalanceColors.SecondaryText)
                    TextButton(onClick = onBody, modifier = Modifier.testTag("body_settings_cta")) { Text("설정") }
                }
            }
        }
    }
}

@Composable
private fun IntakeRangeRow(row: HomeIntakeRow, nutrientColor: Color) {
    val currentText = row.current?.let { amount(it, row.unit) + " " + row.unit } ?: "— ${row.unit}"
    val rangeText = row.range?.let {
        "권장 ${amount(it.lowerG, row.unit)}~${amount(it.upperG, row.unit)}\u00a0${row.unit}"
    }
    val accent = row.status?.tint ?: nutrientColor
    val fontScale = LocalDensity.current.fontScale
    @Composable fun Label(modifier: Modifier = Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(nutrientColor, CircleShape))
            Text(row.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
        }
    }
    @Composable fun Values(modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(currentText, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag(if (row.key == "energy") "home_energy" else "home_${row.key}_value"))
            if (rangeText != null) Text("/ $rangeText",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF65748A))
        }
    }
    @Composable fun Badge() {
        row.status?.let { status ->
            Text(status.label, color = accent,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.background(accent.copy(alpha = .10f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp).testTag("home_${row.key}_status"))
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("home_${row.key}_row"),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 300.dp && fontScale <= 1.15f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(Modifier.width(76.dp))
                    Values(Modifier.weight(1f))
                    Badge()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Label(Modifier.weight(1f))
                        Badge()
                    }
                    Values()
                }
            }
        }
        RangeTrack(row, accent, currentText, rangeText)
    }
}

@Composable
private fun RangeTrack(row: HomeIntakeRow, accent: Color, currentText: String, rangeText: String?) {
    val guide = Color(0xFFCBDAD5).copy(alpha = .75f)
    val guideEdge = Color(0xFF718A80).copy(alpha = .45f)
    Canvas(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
        .testTag("home_${row.key}_track").semantics {
            contentDescription = "${row.label}, $currentText, ${rangeText ?: "권장 범위 미설정"}, ${row.status?.label ?: "상태 미판정"}"
        }) {
        fun x(value: Double) = size.width * row.position(value)
        drawRect(Color(0xFFF1F5F9))
        // The band sits inside the capsule, underneath the current intake fill.
        row.range?.let { range ->
            drawRect(guide, Offset(x(range.lowerG), 0f),
                Size((x(range.upperG) - x(range.lowerG)).coerceAtLeast(0f), size.height))
        }
        row.current?.takeIf { it > 0 }?.let { current ->
            drawRoundRect(accent.copy(alpha = .90f),
                size = Size(x(current), size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        // Subtle boundaries stay visible through a filled recommended zone.
        row.range?.let { range ->
            for (edge in listOf(x(range.lowerG), x(range.upperG))) {
                drawLine(guideEdge, Offset(edge, 0f), Offset(edge, size.height), .75.dp.toPx())
            }
        }
    }
}
