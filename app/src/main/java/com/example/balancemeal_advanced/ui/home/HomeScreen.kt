package com.example.balancemeal_advanced.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.balancemeal_advanced.ui.components.*
import com.example.balancemeal_advanced.ui.theme.BalanceColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun feedbackLines(text: String): String = text.replace(Regex("(?<=[.!?])\\s+"), "\n")

@Composable
fun HomeScreen(state:HomeUiState,onRecord:()->Unit,onBody:()->Unit,onRetry:()->Unit,onEnableBody:()->Unit = {}) {
    AdaptivePage("destination_HOME",header={
        Text(state.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE",Locale.KOREA)),style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("home_date"))
        if(state.loading) Text("오늘의 기록을 불러오는 중입니다.",modifier=Modifier.testTag("home_loading"))
        state.error?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onRetry){Text("다시 시도")}}
    },primary={
        HomeIntakeCard(state, onBody, onEnableBody)
    },secondary={
        if(!state.loading && state.error==null) GlassCard(Modifier.fillMaxWidth()) {
            Text("영양 피드백",style=MaterialTheme.typography.titleMedium)
            Text(feedbackLines(state.mainComment),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("home_main_comment"))
            if(state.subComment.isNotEmpty())Text(feedbackLines(state.subComment),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("home_sub_comment"))
            if(state.showAddMeal) {
                HorizontalDivider(color = BalanceColors.Outline.copy(alpha = .12f))
                Button(onClick = onRecord, modifier = Modifier.fillMaxWidth().testTag("home_add_meal")) {
                    Text("식단 추가")
                }
            }
        }
    })
}
