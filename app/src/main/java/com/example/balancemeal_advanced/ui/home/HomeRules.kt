package com.example.balancemeal_advanced.ui.home

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.*
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import java.time.*
import java.util.Locale

object HomeRules {
    const val FIRST_MEAL="오늘의 첫 식단을 기록하고 피드백을 받아보세요."
    fun greeting(time:LocalTime):String=when(time.hour) {
        in 6..10->"좋은 아침이에요! 오늘 첫 끼니는 어떤 메뉴인가요?"
        in 11..16->"오늘 점심은 맛있게 드셨나요? 잊기 전에 기록해보세요."
        else->"오늘 하루 어떤 음식을 드셨나요? 식단을 남기고 영양 성분을 점검해보세요."
    }
    /** Wake at the next greeting/day boundary, polling at most once a minute for clock/zone edits. */
    fun nextRefreshMillis(now:LocalDateTime):Long {
        val next=listOf(6,11,17).map {now.toLocalDate().atTime(it,0)}.plus(now.toLocalDate().plusDays(1).atStartOfDay()).first {it>now}
        return Duration.between(now,next).toMillis().coerceIn(1,60_000)
    }
    fun present(now:LocalDateTime,records:List<MealRecord>,body:BodySettings?,foods:Map<String,Food>):HomeUiState {
        require(records.all {it.date==now.toLocalDate()})
        val total=MealNutrition.total(records.flatMap {it.items},foods)
        val targets=body?.takeIf { it.calculationEnabled }?.let {EnergyTargets.calculate(it,now.toLocalDate())}
        val assessment=if(records.isNotEmpty() && targets!=null) DailyNutritionAssessment.evaluate(total,targets) else null
        val primary=setOf(MealType.BREAKFAST,MealType.LUNCH,MealType.DINNER)
        return HomeUiState(date=now.toLocalDate(),loading=false,total=total,nutrition=NutritionUiState.from(total),
            hasRecords=records.isNotEmpty(),showAddMeal=!records.map {it.mealType}.toSet().containsAll(primary),needsBody=body==null,
            calculationDisabled=body?.calculationEnabled==false,
            targets=targets,targetEnergyLabel=targets?.let {String.format(Locale.KOREA,"%.0f",it.energyKcal)},
            mainComment=assessment?.mainComment ?: if(records.isEmpty()) FIRST_MEAL else if(body?.calculationEnabled==false) "신체 정보 비활성화 상태입니다. 활성화 후 피드백을 받아보세요." else "신체 정보를 입력하고 나에게 맞는 피드백을 받아보세요.",
            subComment=assessment?.subComment ?: if(records.isEmpty()) greeting(now.toLocalTime()) else "")
    }
}
