package com.example.balancemeal_advanced.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import android.app.UiAutomation
import android.content.res.Configuration
import org.junit.Assert.assertTrue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordRecreationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun draftFoodAndAmountSurviveActivityRecreationWithoutSaving() {
        rule.onNodeWithTag("nav_RECORD").performClick()
        rule.waitUntil(10000) { rule.onAllNodes(hasTestTag("record_add_food") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        val selectedDate=rule.onNodeWithTag("record_date").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") {it.text}
        capture(rule,"record-inputs","p06-captures")
        rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag("record_add_food"))
        rule.onNodeWithTag("record_add_food").performClick()
        rule.onNodeWithTag("food_search").performTextInput("쌀밥")
        rule.waitUntil(10000) { rule.onAllNodes(hasStateDescription("1개 검색 결과")).fetchSemanticsNodes().isNotEmpty() }
        val riceResult=hasText("쌀밥") and !hasSetTextAction()
        rule.onNodeWithTag("food_picker_list").performScrollToNode(riceResult)
        rule.onNode(riceResult).performClick()
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_amount"))
        rule.onNodeWithTag("food_add_amount").performTextInput("150.5")
        capture(rule,"food-picker-quantity","p06-captures","food_picker_list")
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_confirm"))
        rule.onNodeWithTag("food_add_confirm").performClick()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("food_picker_list").fetchSemanticsNodes().isNotEmpty()) {
            androidx.test.espresso.Espresso.pressBack()
            rule.waitForIdle()
        }
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag("record_date"))
        rule.onNodeWithTag("record_date").assertTextContains(selectedDate)
        rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasText("쌀밥"))
        rule.onNodeWithText("쌀밥").assertIsDisplayed()
        rule.onNodeWithText("150.5").assertExists()
        @Suppress("DEPRECATION")
        val originalRotation=rule.activity.windowManager.defaultDisplay.rotation
        try {
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
            rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasText("쌀밥"))
            rule.onNodeWithText("150.5").assertExists()
            assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_0))
            rule.waitUntil(10000) {rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
            rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag("record_date"))
            rule.onNodeWithTag("record_date").assertTextContains(selectedDate)
            rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasText("쌀밥"))
            rule.onNodeWithText("150.5").assertExists()
        } finally {
            automation.setRotation(originalRotation)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
        rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag("record_persistence"))
        rule.onNodeWithTag("record_persistence").assertIsEnabled()
        capture(rule,"record-after-recreation","p06-captures")
    }
}
