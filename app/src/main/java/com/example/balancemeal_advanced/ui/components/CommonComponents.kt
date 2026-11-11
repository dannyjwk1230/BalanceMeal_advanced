package com.example.balancemeal_advanced.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import com.example.balancemeal_advanced.R
import com.example.balancemeal_advanced.ui.presentation.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors
import com.example.balancemeal_advanced.ui.theme.BalanceSpacing

fun Modifier.balanceBackground(): Modifier = background(BalanceColors.Background).drawBehind {
    val glowRadius = maxOf(size.width * .85f, 360.dp.toPx())
    drawRect(Brush.radialGradient(
        listOf(BalanceColors.MintGlow.copy(alpha = .65f), Color.Transparent),
        center = Offset(size.width * .08f, size.height * .08f), radius = glowRadius,
    ))
    drawRect(Brush.radialGradient(
        listOf(BalanceColors.VioletGlow.copy(alpha = .6f), Color.Transparent),
        center = Offset(size.width * .95f, size.height * .48f), radius = glowRadius,
    ))
    drawRect(Brush.radialGradient(
        listOf(BalanceColors.MintGlow.copy(alpha = .3f), Color.Transparent),
        center = Offset(size.width * .25f, size.height), radius = glowRadius,
    ))
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    opacity: Float = .42f,
    padding: Dp = BalanceSpacing.Margin,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.dropShadow(shape, Shadow(
            radius = 24.dp, color = Color(0xFF657C77).copy(alpha = .12f),
            offset = DpOffset(0.dp, 10.dp),
        )).clip(shape).background(Color.White.copy(alpha = opacity))
            .drawBehind {
                // Reflections stay behind the content so text and charts remain crisp.
                drawRect(Brush.linearGradient(
                    0f to Color.White.copy(alpha = .58f),
                    .32f to Color.White.copy(alpha = .08f),
                    .52f to Color.Transparent,
                    1f to BalanceColors.VioletGlow.copy(alpha = .14f),
                    start = Offset.Zero, end = Offset(size.width, size.height),
                ))
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .35f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = maxOf(size.width * .7f, 1f),
                ))
            }
            .border(1.dp, Brush.linearGradient(
                0f to Color.White,
                .35f to Color.White.copy(alpha = .65f),
                .65f to BalanceColors.MintDark.copy(alpha = .12f),
                1f to Color.White.copy(alpha = .85f),
            ), shape).padding(padding),
        verticalArrangement = Arrangement.spacedBy(BalanceSpacing.Medium),
        content = content,
    )
}

@Composable
fun MealPhotoPlaceholder(modifier: Modifier = Modifier, showLabel: Boolean = false, showCamera: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (showCamera) Color.White else BalanceColors.SurfaceLow)
            .semantics { contentDescription = "식단 사진 없음" },
        contentAlignment = Alignment.Center,
    ) {
        if (showCamera) Icon(painterResource(R.drawable.bm_ic_camera), null,
            Modifier.size(40.dp), tint = Color(0xFF9CA7AF))
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🍽", fontSize = 28.sp)
            if (showLabel) Text("식단 사진", style = MaterialTheme.typography.labelMedium, color = BalanceColors.SecondaryText)
        }
    }
}

@Composable
fun NutritionTotals(state: NutritionUiState, modifier: Modifier = Modifier, showPercent: Boolean = false) {
    val colors = listOf(BalanceColors.Mint, BalanceColors.Protein, BalanceColors.Fat)
    val largeText = LocalDensity.current.fontScale > 1.3f
    @Composable fun Metric(index: Int, cellModifier: Modifier) {
        val item = state.nutrients[index]
        Column(
            cellModifier.clip(RoundedCornerShape(12.dp)).background(BalanceColors.SurfaceLow.copy(alpha = .8f)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(colors[index]))
                Text(item.name, style = MaterialTheme.typography.labelMedium)
            }
            Text(item.grams + " g", style = MaterialTheme.typography.titleMedium)
            if (showPercent) Text(item.percent + "%", style = MaterialTheme.typography.labelMedium, color = colors[index])
        }
    }
    if (largeText) Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.nutrients.indices.forEach { Metric(it, Modifier.fillMaxWidth()) }
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.nutrients.indices.forEach { Metric(it, Modifier.weight(1f)) }
    }
}

@Composable
fun MacronutrientChart(state: NutritionUiState, modifier: Modifier = Modifier, diameter: Dp = 192.dp) {
    val colors = listOf(BalanceColors.Mint, BalanceColors.Protein, BalanceColors.Fat)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = if (!state.isKnown) "영양소 수치 미표시" else if (state.isEmpty) "영양소 합계 0, 빈 차트" else "영양소 중량 비율 차트"
        }) {
            val stroke = size.width * .09375f
            val topLeft = Offset(size.width * .1f, size.height * .1f)
            val arcSize = Size(size.width * .8f, size.height * .8f)
            drawArc(BalanceColors.Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            state.nutrients.forEachIndexed { index, nutrient ->
                val sweep = nutrient.fraction * 360f
                if (sweep > 0f) drawArc(colors[index], start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("영양소 중량", style = MaterialTheme.typography.labelMedium, color = BalanceColors.SecondaryText)
            Text(state.totalGrams + " g", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
fun EnergyValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if(label.isNotEmpty())Text(label, style = MaterialTheme.typography.bodySmall, color = BalanceColors.SecondaryText)
        Text(buildAnnotatedString {
            append(value);append('\u00a0')
            withStyle(SpanStyle(fontSize=MaterialTheme.typography.bodyMedium.fontSize)) {append("kcal")}
        }, style = MaterialTheme.typography.displayMedium)
    }
}

/** Analysis/detail use the archived ring plus three metric rows, with a large-text fallback. */
@Composable
fun NutritionChartAndLegend(state: NutritionUiState, modifier: Modifier = Modifier) {
    val colors = listOf(BalanceColors.Mint, BalanceColors.Protein, BalanceColors.Fat)
    @Composable fun Legend(legendModifier: Modifier) {
        Column(legendModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.nutrients.forEachIndexed { index, item ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BalanceColors.SurfaceLow).padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(colors[index]))
                        Text(item.name, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.grams + " g", style = MaterialTheme.typography.titleSmall)
                        Text(item.percent + "%", style = MaterialTheme.typography.labelSmall, color = colors[index])
                    }
                }
            }
        }
    }
    val largeText = LocalDensity.current.fontScale > 1.3f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 300.dp && !largeText) Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MacronutrientChart(state, diameter = 144.dp)
            Legend(Modifier.weight(1f))
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacronutrientChart(state, diameter = 144.dp)
            Legend(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun PeriodSelector(
    state: PeriodUiState,
    onPeriod: (PeriodTab) -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().background(BalanceColors.SurfaceLow,RoundedCornerShape(20.dp)).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            PeriodTab.entries.forEach { period ->
                FilterChip(
                    selected = state.selected == period, onClick = { onPeriod(period) },
                    modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(16.dp),border=null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BalanceColors.Mint, selectedLabelColor = Color.White,
                        containerColor = Color.Transparent,
                    ),
                    label={Text(period.label, style = MaterialTheme.typography.labelMedium)},
                )
            }
        }
        GlassCard(radius = 12.dp, padding = 12.dp, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.rangeLabel, style = MaterialTheme.typography.titleSmall)
                    Text(state.description, style = MaterialTheme.typography.labelMedium, color = BalanceColors.SecondaryText)
                }
                IconButton(onClick = onCalendar) {
                    Icon(painterResource(R.drawable.bm_ic_calendar), "기간 달력 열기")
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CommonConfirmationDialog(
        "미저장 내용 확인", "저장하지 않은 내용이 있습니다. 이동하면 작성 중인 내용이 삭제됩니다.",
        "이동하기", onConfirm, onDismiss, dismissLabel = "계속 작성",
    )
}

@Composable
fun CommonConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "취소",
) {
    Dialog(onDismissRequest = onDismiss) {
      BoxWithConstraints {
        GlassCard(Modifier.fillMaxWidth().heightIn(max=maxHeight)
            .imePadding().verticalScroll(rememberScrollState()).testTag("confirmation_dialog"), opacity = .94f, padding = 24.dp) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("confirmation_cancel")) { Text(dismissLabel) }
                Button(onClick = onConfirm, colors = if (confirmLabel.contains("삭제")) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(), modifier = Modifier.weight(1f).testTag("confirmation_confirm")) { Text(confirmLabel) }
            }
        }
      }
    }
}

