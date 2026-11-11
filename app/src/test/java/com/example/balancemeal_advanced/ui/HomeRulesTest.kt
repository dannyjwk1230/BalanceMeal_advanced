package com.example.balancemeal_advanced.ui

import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.*
import com.example.balancemeal_advanced.core.time.ClockTimeSource
import com.example.balancemeal_advanced.ui.home.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HomeRulesTest {
    private val now=LocalDateTime.of(2026,11,11,20,30)
    private val food=Food("fixture","test-only",FoodUnit.G,NutrientAmounts(50.0,30.0,20.0,450.0))
    private val foods=mapOf(food.code to food)
    private val body=BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE)
    private fun meal(type:MealType)=MealRecord(type.name,now.toLocalDate(),type,listOf(MealItem(food.code,100.0)))
    @Test fun breakfastSnackPrimaryThreeAndEmptyKeepActualTotalsAndCompletion() {
        for(types in listOf(emptyList(),listOf(MealType.BREAKFAST),listOf(MealType.SNACK),listOf(MealType.BREAKFAST,MealType.LUNCH,MealType.DINNER),MealType.entries)) {
            val s=HomeRules.present(now,types.map(::meal),null,foods)
            assertEquals(types.size*450.0,s.total!!.energyKcal,0.0)
            assertEquals(types.isNotEmpty(),s.hasRecords)
            assertEquals(!types.containsAll(listOf(MealType.BREAKFAST,MealType.LUNCH,MealType.DINNER)),s.showAddMeal)
            if(types.isNotEmpty())assertEquals(listOf(.5f,.3f,.2f),s.nutrition.nutrients.map {it.fraction}) else {assertTrue(s.nutrition.isEmpty);assertEquals("0",s.nutrition.energyKcal)}
        }
    }
    @Test fun bodyAndRecordsFourStatesNeverEvaluateUnrecordedAsDeficient() {
        for(b in listOf(null,body))for(records in listOf(emptyList(),listOf(meal(MealType.SNACK)))) {
            val s=HomeRules.present(now,records,b,foods)
            assertEquals(b==null,s.needsBody);assertEquals(b!=null,s.targets!=null)
            if(records.isEmpty()) {assertEquals(HomeRules.FIRST_MEAL,s.mainComment);assertEquals(HomeRules.greeting(now.toLocalTime()),s.subComment)}
            else if(b==null) {assertEquals("",s.subComment);assertFalse(s.mainComment.contains("부족"))}
            else {assertEquals("오늘의 칼로리가 목표보다 부족해요.",s.mainComment);assertTrue(s.subComment.startsWith("내일은"))}
        }
    }
    @Test fun zeroMassStillKeepsEnergyAndBodyChangeKeepsIntake() {
        val zeroFood=food.copy(nutrientsPer100=NutrientAmounts(0.0,0.0,0.0,123.0))
        val s=HomeRules.present(now,listOf(meal(MealType.SNACK)),body,mapOf(food.code to zeroFood))
        assertTrue(s.nutrition.isEmpty);assertEquals("123",s.nutrition.energyKcal)
        val changed=HomeRules.present(now,listOf(meal(MealType.SNACK)),body.copy(weightKg=90.0),mapOf(food.code to zeroFood))
        assertEquals(s.total,changed.total);assertNotEquals(s.targets,changed.targets)
    }
    @Test fun disabledBodyKeepsIntakeButRemovesTargetsAndAssessment() {
        val records=listOf(meal(MealType.BREAKFAST))
        val enabled=HomeRules.present(now,records,body,foods)
        val disabled=HomeRules.present(now,records,body.copy(calculationEnabled=false),foods)
        assertEquals(enabled.total,disabled.total)
        assertFalse(disabled.needsBody)
        assertTrue(disabled.calculationDisabled)
        assertNull(disabled.targets)
        assertNull(disabled.targetEnergyLabel)
        assertTrue(homeIntakeRows(disabled).all {it.range==null && it.status==null})
        assertFalse(disabled.mainComment.contains("부족"))
        val restored=HomeRules.present(now,records,body.copy(calculationEnabled=true),foods)
        assertEquals(enabled,restored)
    }
    @Test fun calorie85And115AreInclusiveWithoutRounding() {
        for((value,status) in listOf(Math.nextDown(1700.0) to IntakeStatus.LOW,1700.0 to IntakeStatus.ADEQUATE,2000.0 to IntakeStatus.ADEQUATE,2300.0 to IntakeStatus.ADEQUATE,Math.nextUp(2300.0) to IntakeStatus.HIGH)) {
            assertEquals(status,DailyNutritionAssessment.calorieStatus(value,2000.0))
        }
    }
    @Test fun allNutrientRangesIncludeBothExactBounds() {
        val targets=EnergyTargets.fromEnergy(2000.0)
        for(range in listOf(targets.carbohydrate,targets.protein,targets.fat)) {
            assertEquals(IntakeStatus.LOW,DailyNutritionAssessment.nutrientStatus(Math.nextDown(range.lowerG),range))
            for(g in listOf(range.lowerG,(range.lowerG+range.upperG)/2,range.upperG))assertEquals(IntakeStatus.ADEQUATE,DailyNutritionAssessment.nutrientStatus(g,range))
            assertEquals(IntakeStatus.HIGH,DailyNutritionAssessment.nutrientStatus(Math.nextUp(range.upperG),range))
        }
    }
    @Test fun exactCommentsAndNutrientOrderingForAllFourCombinations() {
        val target=EnergyTargets.fromEnergy(2000.0)
        fun sub(c:Double,p:Double,f:Double)=DailyNutritionAssessment.evaluate(NutrientAmounts(c,p,f,2000.0),target).subComment
        assertEquals("완벽한 영양 밸런스입니다!",sub(250.0,50.0,50.0))
        assertEquals("내일은 탄수화물, 단백질, 지방 섭취에 주의해주세요.",sub(400.0,200.0,100.0))
        assertEquals("내일은 탄수화물, 단백질, 지방 섭취에 신경 써보세요.",sub(1.0,1.0,1.0))
        assertEquals("내일은 단백질의 섭취를 줄이고 탄수화물, 지방 섭취에 신경 써보세요.",sub(1.0,200.0,1.0))
        assertEquals("내일은 탄수화물, 지방의 섭취를 줄이고 단백질 섭취에 신경 써보세요.",sub(400.0,1.0,100.0))
        for((energy,message) in listOf(1600.0 to "오늘의 칼로리가 목표보다 부족해요.",2000.0 to "오늘의 목표 칼로리를 달성했어요!",2400.0 to "오늘의 목표 칼로리를 초과했어요."))assertEquals(message,DailyNutritionAssessment.evaluate(NutrientAmounts(250.0,50.0,50.0,energy),target).mainComment)
    }
    @Test fun greetingsUseFixedLocalClocksAtEveryRequestedBoundary() {
        val morning="좋은 아침이에요! 오늘 첫 끼니는 어떤 메뉴인가요?"
        val noon="오늘 점심은 맛있게 드셨나요? 잊기 전에 기록해보세요."
        val night="오늘 하루 어떤 음식을 드셨나요? 식단을 남기고 영양 성분을 점검해보세요."
        for((time,expected) in listOf("05:59" to night,"06:00" to morning,"10:59" to morning,"11:00" to noon,"16:59" to noon,"17:00" to night)) {
            val clock=ClockTimeSource(Clock.fixed(now.toLocalDate().atTime(LocalTime.parse(time)).atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul")))
            assertEquals(expected,HomeRules.present(clock.now(),emptyList(),body,foods).subComment)
        }
        assertEquals(1000,HomeRules.nextRefreshMillis(now.toLocalDate().atTime(23,59,59)))
    }
}
