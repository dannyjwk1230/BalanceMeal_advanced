package com.example.balancemeal_advanced.core.nutrition

import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.model.NutrientRange

enum class IntakeStatus { LOW, ADEQUATE, HIGH }
data class DailyAssessment(val calorieStatus:IntakeStatus,val mainComment:String,val subComment:String)

/** One day's saved intake only. Callers must handle unrecorded days before comparing. */
object DailyNutritionAssessment {
    fun calorieStatus(kcal:Double,eer:Double):IntakeStatus {
        require(kcal.isFinite() && kcal>=0 && eer.isFinite() && eer>0)
        return when {kcal<eer*.85->IntakeStatus.LOW;kcal>eer*1.15->IntakeStatus.HIGH;else->IntakeStatus.ADEQUATE}
    }
    fun nutrientStatus(grams:Double,range:NutrientRange):IntakeStatus {
        require(grams.isFinite() && grams>=0)
        return when {grams<range.lowerG->IntakeStatus.LOW;grams>range.upperG->IntakeStatus.HIGH;else->IntakeStatus.ADEQUATE}
    }
    fun evaluate(intake:NutrientAmounts,targets:EnergyTargets):DailyAssessment {
        val status=calorieStatus(intake.energyKcal,targets.energyKcal)
        val main=when(status) {
            IntakeStatus.LOW->"오늘의 칼로리가 목표보다 부족해요."
            IntakeStatus.ADEQUATE->"오늘의 목표 칼로리를 달성했어요!"
            IntakeStatus.HIGH->"오늘의 목표 칼로리를 초과했어요."
        }
        val nutrients=listOf("탄수화물" to nutrientStatus(intake.carbohydrateG,targets.carbohydrate),
            "단백질" to nutrientStatus(intake.proteinG,targets.protein),"지방" to nutrientStatus(intake.fatG,targets.fat))
        val high=nutrients.filter {it.second==IntakeStatus.HIGH}.joinToString(", ") {it.first}
        val low=nutrients.filter {it.second==IntakeStatus.LOW}.joinToString(", ") {it.first}
        val sub=when {
            high.isEmpty() && low.isEmpty()->"완벽한 영양 밸런스입니다!"
            high.isNotEmpty() && low.isNotEmpty()->"내일은 ${high}의 섭취를 줄이고 $low 섭취에 신경 써보세요."
            high.isNotEmpty()->"내일은 $high 섭취에 주의해주세요."
            else->"내일은 $low 섭취에 신경 써보세요."
        }
        return DailyAssessment(status,main,sub)
    }
}
