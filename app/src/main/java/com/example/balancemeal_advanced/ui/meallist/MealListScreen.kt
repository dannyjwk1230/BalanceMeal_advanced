package com.example.balancemeal_advanced.ui.meallist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.R
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors
import com.example.balancemeal_advanced.ui.theme.BalanceSpacing

/** Team boundary: renders supplied state and emits intent; never queries, filters or expands locally. */
@Composable
fun MealListScreen(state: MealListUiState, onEvent: (MealListEvent) -> Unit, modifier: Modifier = Modifier) {
    AdaptivePage("destination_LIST",modifier,secondaryInCompact=false,header={
        Text("식단 목록", style = MaterialTheme.typography.headlineLarge)
        PeriodSelector(state.period, { onEvent(MealListEvent.PeriodSelected(it)) }, { onEvent(MealListEvent.CalendarRequested) })
    },primary={
        if (state.content != MealListContent.DATES) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (state.content == MealListContent.INITIAL) "기간을 선택해 식단을 확인해보세요." else "선택한 기간에 기록된 식단이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        state.dates.forEach { day ->
            MealDateCard(day, onEvent)
        }
    },secondary={
        // Team-owned selection/detail data is deliberately not connected.
        GlassCard(Modifier.fillMaxWidth().testTag("list_detail_placeholder")) {
            Text("식단 상세",style=MaterialTheme.typography.titleMedium)
            MealPhotoPlaceholder(Modifier.fillMaxWidth().aspectRatio(4f/3f))
            Text("목록에서 식단을 선택하면 상세 내용을 확인할 수 있습니다.")
        }
    })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun MealDateCard(state: MealDateUiState, onEvent: (MealListEvent) -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), radius = 20.dp) {
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(state.label, style = MaterialTheme.typography.titleMedium)
            Text(state.summary, style = MaterialTheme.typography.bodySmall, color = BalanceColors.SecondaryText)
        }
        EnergyValue("", state.energyLabel)
        if (state.meals.isEmpty()) {
            MealPhotoPlaceholder(Modifier.fillMaxWidth().height(96.dp))
            Text("기록된 식단이 없습니다.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onEvent(MealListEvent.RecordForDateRequested(state.date)) }) {
                Text("이 날짜에 식단 기록하기")
            }
        } else state.meals.forEach { meal ->
            MealRow(meal, onEvent)
        }
    }
}

@Composable
private fun MealRow(state: MealRowUiState, onEvent: (MealListEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min=48.dp).clickable { onEvent(MealListEvent.MealExpansionRequested(state.mealId)) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(state.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(state.energyLabel + " kcal", style = MaterialTheme.typography.labelMedium)
            Icon(
                painterResource(R.drawable.bm_ic_back),
                if (state.expanded) "끼니 접기" else "끼니 펼치기",
                modifier = Modifier.size(24.dp).rotate(if (state.expanded) -90f else 180f),
            )
        }
        if (state.expanded) {
            GlassCard(
                Modifier.fillMaxWidth().clickable { onEvent(MealListEvent.MealSelected(state.mealId)) },
                radius = 16.dp, opacity = .6f, padding = 12.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MealPhotoPlaceholder(Modifier.size(64.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.foodSummary, style = MaterialTheme.typography.titleSmall)
                        Text(state.nutrientSummary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Display-only detail matching the archived cc552... screen. No destination/event handler is installed. */
@Composable
fun MealDetailReference(state: MealDetailUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { MealPhotoPlaceholder(Modifier.fillMaxWidth().aspectRatio(4f / 3f), showLabel = true) }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text(state.dateLabel, style = MaterialTheme.typography.titleMedium)
            Text(state.mealLabel, style = MaterialTheme.typography.labelLarge)
            EnergyValue("총 칼로리", state.nutrition.energyKcal)
        } }
        item { GlassCard(Modifier.fillMaxWidth()) {
            Text("영양소 균형 합계", style = MaterialTheme.typography.titleMedium)
            NutritionChartAndLegend(state.nutrition)
        } }
        items(state.items, key = { it.id }) { item -> GlassCard(Modifier.fillMaxWidth()) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(item.amountLabel, style = MaterialTheme.typography.bodySmall)
            NutritionTotals(item.nutrition)
        } }
    }
}

