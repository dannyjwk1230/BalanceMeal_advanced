package com.example.balancemeal_advanced.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.balancemeal_advanced.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.time.LocalDate

class MyEditingFlowTest {
    private lateinit var f:P07Fixture
    private val body=BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE)
    private val rule=createAndroidComposeRule<P07TestActivity>()
    private val fixture=object:ExternalResource() {
        override fun before() { f=P07Fixture();runBlocking {f.settings.saveBodySettings(body)};P07TestHost.dependencies=f }
        override fun after() { P07TestHost.dependencies=null;f.close() }
    }
    @get:Rule val chain:TestRule=RuleChain.outerRule(fixture).around(rule)
    private fun enter() {
        rule.onNodeWithTag("nav_MY").performClick()
        rule.waitUntil(10000) {rule.onAllNodes(hasTestTag("body_height") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
    }
    private fun scroll(tag:String) {rule.onNodeWithTag("destination_MY").performScrollToNode(hasTestTag(tag))}

    @Test fun unsavedBodyWarnsOnTabsAndBackAndIsDiscardedOnlyOnConfirmation() {
        enter()
        val actions=listOf<()->Unit>(
            {rule.onNodeWithTag("nav_HOME").performClick()},
            {rule.onNodeWithTag("nav_RECORD").performClick()},
            {rule.onNodeWithTag("nav_LIST").performClick()},
            {rule.onNodeWithTag("nav_ANALYSIS").performClick()},
            {rule.runOnUiThread {rule.activity.onBackPressedDispatcher.onBackPressed()}},
        )
        actions.forEach { action ->
            scroll("body_height");rule.onNodeWithTag("body_height").performTextReplacement("181")
            action()
            rule.onNodeWithText("미저장 내용 확인").assertExists()
            rule.onNodeWithTag("confirmation_cancel").performClick()
            rule.onNodeWithTag("destination_MY").assertIsDisplayed()
            scroll("body_height");rule.onNodeWithTag("body_height").assertTextContains("181")
            action();rule.onNodeWithTag("confirmation_confirm").performClick()
            assertEquals(body,runBlocking {f.settings.bodySettings.first()})
            enter();scroll("body_height");rule.onNodeWithTag("body_height").assertTextContains("175.0")
        }
        rule.onNodeWithText("편집 취소").assertDoesNotExist()
        rule.onNodeWithTag("my_saved_targets").assertDoesNotExist()
        scroll("body_birth");rule.onNodeWithTag("body_birth").assertTextContains("1996-11-11")
    }

    @Test fun incompleteBodyShowsModalAndCannotReplaceSavedValues() {
        enter();scroll("body_height");rule.onNodeWithTag("body_height").performTextClearance()
        scroll("body_save");rule.onNodeWithTag("body_save").performClick()
        rule.onNodeWithText("신체 정보를 확인해주세요").assertExists()
        rule.onNodeWithText("아래 항목을 입력한 뒤 다시 저장해주세요.\n키").assertExists()
        assertEquals(body,runBlocking {f.settings.bodySettings.first()})
        rule.onNodeWithText("확인").performClick()
        rule.onNodeWithTag("destination_MY").assertIsDisplayed()
    }
}
