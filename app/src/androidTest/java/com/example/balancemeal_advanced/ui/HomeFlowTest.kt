package com.example.balancemeal_advanced.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.time.TimeSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class HomeFlowTest {
    private lateinit var f:P07Fixture
    @Volatile private var now=LocalDateTime.of(2026,11,11,20,30)
    private val rule=createAndroidComposeRule<P07TestActivity>()
    private val fixture=object:ExternalResource() {
        override fun before(){f=P07Fixture(TimeSource {now});P07TestHost.dependencies=f}
        override fun after(){P07TestHost.dependencies=null;f.close()}
    }
    @get:Rule val chain:TestRule=RuleChain.outerRule(fixture).around(rule)
    private fun waitFor(condition:()->Boolean)=rule.waitUntil(10000,condition)
    private fun homeReady(){waitFor{rule.onAllNodesWithTag("home_main_comment").fetchSemanticsNodes().isNotEmpty()}}
    private fun scrollHome(tag:String){rule.onNodeWithTag("destination_HOME").performScrollToNode(hasTestTag(tag))}
    private fun energy(value:String){homeReady();scrollHome("home_energy");waitFor {rule.onAllNodes(hasTestTag("home_energy") and hasText("$value kcal")).fetchSemanticsNodes().isNotEmpty()}}
    private fun savedMeal(type:MealType,amount:Double=150.0)=runBlocking {
        val rice=f.foods.getAll().single {it.name=="쌀밥"}
        f.meals.createMeal(now.toLocalDate(),type,listOf(MealItem(rice.code,amount)))
    }

    @Test fun homeActivationButtonEnablesSavedBodyWithoutLeavingHome() {
        val body=BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE,calculationEnabled=false)
        runBlocking { f.settings.saveBodySettings(body) }
        savedMeal(MealType.BREAKFAST)
        energy("249")
        scrollHome("body_enable_cta")
        rule.onNodeWithTag("body_enable_cta").assertTextContains("신체 정보 활성화").performClick()
        waitFor { runBlocking { f.settings.bodySettings.first() }?.calculationEnabled==true }
        assertEquals(body.copy(calculationEnabled=true),runBlocking { f.settings.bodySettings.first() })
        rule.onNodeWithTag("destination_HOME").assertIsDisplayed()
        rule.onNodeWithTag("body_enable_cta").assertDoesNotExist()
        rule.onNodeWithTag("home_carbohydrate_status").assertExists()
        energy("249")
    }

    @Test fun recordDraftExcludedSavedMutationsObservedAndResumeRollsLocalDate(){
        energy("0");scrollHome("home_add_meal");rule.onNodeWithTag("home_add_meal").performClick();rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag("record_add_food"));rule.onNodeWithTag("record_add_food").performClick()
        rule.onNodeWithTag("food_search").performTextInput("쌀밥")
        waitFor{rule.onAllNodes(hasStateDescription("1개 검색 결과")).fetchSemanticsNodes().isNotEmpty()}
        val rice=hasText("쌀밥") and !hasSetTextAction() and hasAnyAncestor(hasTestTag("food_picker_list"))
        rule.onNodeWithTag("food_picker_list").performScrollToNode(rice);rule.onNode(rice).performClick()
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_amount"));rule.onNodeWithTag("food_add_amount").performTextInput("150")
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_confirm"));rule.onNodeWithTag("food_add_confirm").performClick()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("food_picker_list").fetchSemanticsNodes().isNotEmpty()) {
            androidx.test.espresso.Espresso.pressBack()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("nav_HOME").performClick();rule.onNodeWithTag("confirmation_confirm").performClick();energy("0")
        assertNull(runBlocking {f.meals.getMeal(now.toLocalDate(),MealType.BREAKFAST)})
        val snack=savedMeal(MealType.SNACK);energy("249");rule.onNodeWithTag("home_add_meal").assertExists()
        rule.onNodeWithTag("home_carbohydrate_track").assertExists();rule.onNodeWithTag("body_settings_cta").assertExists()
        val primary=listOf(MealType.BREAKFAST,MealType.LUNCH,MealType.DINNER).map {savedMeal(it)};energy("996");rule.onNodeWithTag("home_add_meal").assertDoesNotExist()
        runBlocking {f.meals.deleteMeal(primary.first().id)};energy("747");rule.onNodeWithTag("home_add_meal").assertExists()
        runBlocking {f.meals.updateMeal(snack.copy(items=listOf(snack.items.single().copy(amount=50.0))))};energy("581")
        scrollHome("home_date");capture(rule,"home-intake-without-body","p08-captures")
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        now=LocalDateTime.of(2026,11,12,6,0)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        energy("0");scrollHome("home_date");rule.onNodeWithTag("home_date").assertTextEquals("2026년 11월 12일 목요일")
        scrollHome("home_sub_comment");rule.onNodeWithTag("home_sub_comment").assertTextEquals("좋은 아침이에요!\n오늘 첫 끼니는 어떤 메뉴인가요?")
        capture(rule,"home-empty-morning","p08-captures")
    }
}