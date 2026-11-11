package com.example.balancemeal_advanced.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.ui.preview.*
import com.example.balancemeal_advanced.ui.components.CommonConfirmationDialog
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P09LayoutTest {
    @get:Rule val rule=createComposeRule()
    @Test(timeout=90000) fun exactWidthBoundariesAndFiveNavigationItemsKeepOrder() {
        wakeP09Device()
        rule.mainClock.autoAdvance=false
        var width by mutableIntStateOf(599)
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
            Box(Modifier.requiredSize(width.dp,700.dp).consumeWindowInsets(WindowInsets.systemBars)) {ReferenceHomePreview()}
        }}
        for(w in listOf(599,600,839,840)) {
            android.util.Log.i("P09Width","Request width $w")
            rule.runOnUiThread {width=w};rule.mainClock.advanceTimeBy(1000);rule.waitForIdle()
            android.util.Log.i("P09Width","Measured width $w")
            rule.onNodeWithTag(if(w<600)"navigation_bar" else "navigation_rail").assertExists()
            if(w>=840)rule.onNodeWithTag("destination_HOME_two_panes").assertExists() else rule.onNodeWithTag("destination_HOME_two_panes").assertDoesNotExist()
            val items=listOf("HOME","RECORD","LIST","ANALYSIS","MY").map {rule.onNodeWithTag("nav_$it").fetchSemanticsNode().boundsInRoot}
            val positions=items.map {if(w<600)it.left else it.top}
            assertEquals(positions.sorted(),positions);assertEquals(5,positions.distinct().size)
        }
    }
    @Test(timeout=120000) fun doubleFontLowHeightAllScreensAndScrollableConfirmation() {
        wakeP09Device()
        rule.mainClock.autoAdvance=true
        var screen by mutableStateOf("record")
        var dialog by mutableStateOf(false)
        var confirmed=false
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,2f)) {
            Box(Modifier.requiredSize(390.dp,480.dp).consumeWindowInsets(WindowInsets.systemBars).testTag("large_font_frame")) {
                when(screen){"home"->ReferenceHomePreview();"record"->ReferenceRecordPreview();"my"->ReferenceMyPreview();"list"->FilledMealListPreview();else->FilledAnalysisPreview()}
                if(dialog)BalanceMeal_advancedTheme {CommonConfirmationDialog("미저장 내용 확인","저장하지 않은 내용이 있습니다. 이동하면 작성 중인 내용이 삭제됩니다.","이동하기",{confirmed=true;dialog=false},{dialog=false},"계속 작성")}
            }
        }}
        for(s in listOf("record","my","home","list","analysis")) {
            android.util.Log.i("P09Font","Check $s")
            rule.runOnUiThread {screen=s};rule.mainClock.advanceTimeBy(1000);rule.waitForIdle()
            val tag="destination_"+s.uppercase()
            val end=when(s){"record"->hasTestTag("record_persistence");"my"->hasTestTag("body_save");"home"->hasTestTag("home_sub_comment");"list"->hasText("2025년 5월 14일 (수)");else->hasText("간식·음료 · 700 kcal")}
            rule.onNodeWithTag(tag).performScrollToNode(end);rule.onNode(end).assertIsDisplayed()
            android.util.Log.i("P09Font","Reached $s end control")
            if(s=="my") {
                rule.onNodeWithTag(tag).performScrollToNode(hasTestTag("body_height"));val height=rule.onNodeWithTag("body_height").fetchSemanticsNode().boundsInRoot
                val weight=rule.onNodeWithTag("body_weight").fetchSemanticsNode().boundsInRoot
                assertTrue("Large-font cm/kg fields must stack",weight.top>=height.bottom)
            }
        }
        rule.runOnUiThread {dialog=true};rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithTag("confirmation_confirm").performScrollTo().assertIsDisplayed().performClick();assertTrue(confirmed)
    }
}
