package com.example.balancemeal_advanced.ui.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors

/** Team boundary: no ViewModel, repository collection, average/period computation or local tab state. */
@Composable
fun AnalysisScreen(state: AnalysisUiState, onEvent: (AnalysisEvent) -> Unit, modifier: Modifier = Modifier) {
    AdaptivePage("destination_ANALYSIS",modifier,header={
         Text("영양 분석", style = MaterialTheme.typography.headlineLarge) 
         PeriodSelector(state.period, { onEvent(AnalysisEvent.PeriodSelected(it)) }, { onEvent(AnalysisEvent.CalendarRequested) }) 
         
    },primary={
GlassCard(Modifier.fillMaxWidth()) {
            Text("하루 평균 영양소", style = MaterialTheme.typography.titleMedium)
            Text(state.averageBasis, style = MaterialTheme.typography.bodySmall, color = BalanceColors.SecondaryText)
            NutritionChartAndLegend(state.average)
            HorizontalDivider(color = BalanceColors.SurfaceLow)
            EnergyValue("하루 평균 칼로리", state.average.energyKcal)
        } 
         GlassCard(Modifier.fillMaxWidth()) {
            Text(state.commentTitle, style = MaterialTheme.typography.titleMedium)
            Text(state.commentBody, style = MaterialTheme.typography.bodyMedium)
            if (state.showBodySettingsCta) TextButton(onClick = { onEvent(AnalysisEvent.BodySettingsRequested) }) {
                Text("신체 정보 입력하기")
            }
        } 
         
    },secondary={
GlassCard(Modifier.fillMaxWidth()) {
            EnergyValue("기간 총 섭취 칼로리", state.periodEnergyLabel)
        } 
        GlassCard(Modifier.fillMaxWidth()) {
         Text("식사 종류별 영양 분석", style = MaterialTheme.typography.headlineSmall)
        state.meals.forEach { meal ->
            Column(Modifier.fillMaxWidth().background(BalanceColors.SurfaceLow,RoundedCornerShape(16.dp)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(meal.label+" · "+meal.nutrition.energyKcal+" kcal", style = MaterialTheme.typography.titleMedium)
                Text("기간 합계 · 기록 " + meal.recordCountLabel + "회", style = MaterialTheme.typography.labelMedium)
                Text(meal.nutrition.nutrients.joinToString(" · ") {it.name+" "+it.grams+"g"},style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(BalanceColors.Track)) {
                    val colors=listOf(BalanceColors.Mint,BalanceColors.Protein,BalanceColors.Fat)
                    meal.nutrition.nutrients.forEachIndexed {index,nutrient ->
                        if(nutrient.fraction>0f)Box(Modifier.weight(nutrient.fraction).fillMaxHeight().background(colors[index]))
                    }
                }
            }
        }
        }
         Text(state.bodyNotice, style = MaterialTheme.typography.bodySmall, color = BalanceColors.SecondaryText) 
    
    })
}
