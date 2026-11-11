package com.example.balancemeal_advanced.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.app.UiAutomation
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.data.image.CameraTicket
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.time.*

@RunWith(AndroidJUnit4::class)
class P09RuntimeAdaptiveTest {
    private lateinit var f:P07Fixture
    private lateinit var source:CameraTicket
    private lateinit var saved:MealRecord
    private val rule=createAndroidComposeRule<P07TestActivity>()
    private val automation get()=InstrumentationRegistry.getInstrumentation().uiAutomation
    private var originalOverride:String?=null
    private var originalRotation=0
    private var deviceDensity=1f
    private var originalHardwareIme="null"
    private fun shell(command:String):String=automation.executeShellCommand(command).use {fd->FileInputStream(fd.fileDescriptor).bufferedReader().use {it.readText()}}
    private val fixture=object:ExternalResource() {
        override fun before(){
            shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard")
            f=P07Fixture();source=runBlocking{f.imageUri()}
            deviceDensity=f.context.resources.displayMetrics.density
            originalOverride=Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
            originalHardwareIme=shell("settings get secure show_ime_with_hard_keyboard").trim()
            shell("settings put secure show_ime_with_hard_keyboard 1")
            originalRotation=InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.orientation.let {if(it==Configuration.ORIENTATION_LANDSCAPE)1 else 0}
            runBlocking {
                val rice=f.foods.getAll().single {it.name=="쌀밥"}
                saved=f.meals.createMeal(f.time.now().toLocalDate(),MealType.BREAKFAST,listOf(MealItem(rice.code,100.0)))
                f.settings.saveBodySettings(BodySettings(LocalDate.of(1996,11,11),Sex.MALE,175.0,70.0,ActivityLevel.LOW_ACTIVE))
            }
            P07TestHost.dependencies=f;P07TestHost.fontScale=1.6f
            P07TestHost.registry=object:ActivityResultRegistry() {
                override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
                    dispatchResult(requestCode,Activity.RESULT_OK,Intent().setData(android.net.Uri.parse(source.uri)))
                }
            }
        }
        override fun after(){
            shell("wm size "+(originalOverride ?: "reset"));automation.setRotation(originalRotation);automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            shell(if(originalHardwareIme=="null")"settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $originalHardwareIme")
            P07TestHost.dependencies=null;P07TestHost.registry=null;P07TestHost.fontScale=null
            runBlocking {f.camera.discard(source)};f.close()
        }
    }
    @get:Rule val chain:TestRule=RuleChain.outerRule(fixture).around(rule)
    private fun waitFor(test:()->Boolean)=rule.waitUntil(15000,test)
    private fun scroll(destination:String,tag:String){rule.onNodeWithTag("destination_$destination").performScrollToNode(hasTestTag(tag))}
    private fun foreground() {
        automation.adoptShellPermissionIdentity(android.Manifest.permission.REORDER_TASKS)
        try {rule.runOnUiThread {
            (rule.activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).moveTaskToFront(rule.activity.taskId,0)
        }} finally {automation.dropShellPermissionIdentity()}
        // Semantics clicks bypass WindowManager; use native input to activate the visible window.
        val width=rule.activity.resources.displayMetrics.widthPixels
        shell("input tap ${width/2} ${(64*deviceDensity).toInt()}")
        waitFor {rule.activity.hasWindowFocus()}
    }
    private fun resize(widthPixels:Int,expectExpanded:Boolean,destination:String) {
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        shell("wm size ${widthPixels}x2100")
        val expectedDp=widthPixels/deviceDensity
        waitFor {kotlin.math.abs(rule.activity.resources.configuration.screenWidthDp-expectedDp)<3}
        waitFor {rule.onAllNodesWithTag("destination_${destination}_two_panes").fetchSemanticsNodes().isNotEmpty()==expectExpanded}
        rule.onNodeWithTag("nav_$destination").assertIsSelected()
        rule.onNodeWithTag("confirmation_dialog").assertDoesNotExist()
    }
    @Test fun recordDateFoodPhotoAndDirtyDraftSurviveRotationAndWidthChanges() {
        rule.onNodeWithTag("nav_RECORD").performClick()
        waitFor{rule.onAllNodes(hasTestTag("record_add_food") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        val row=saved.items.single().id
        scroll("RECORD","amount_$row");rule.onNodeWithTag("amount_$row").performTextReplacement("150.5")
        scroll("RECORD","record_gallery");rule.onNodeWithTag("record_gallery").performClick()
        waitFor{rule.onAllNodesWithTag("record_photo").fetchSemanticsNodes().isNotEmpty()}
        val references=runBlocking{f.images.ownedReferences()}
        assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
        waitFor{rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        rule.onNodeWithTag("nav_RECORD").assertIsSelected();rule.onNodeWithTag("confirmation_dialog").assertDoesNotExist()
        for((width,expanded) in listOf(2363 to true,1575 to false,1080 to false)) {
            resize(width,expanded,"RECORD")
            scroll("RECORD","record_date");rule.onNodeWithTag("record_date").assertTextEquals(saved.date.toString())
            scroll("RECORD","amount_$row");rule.onNodeWithTag("amount_$row").assertTextContains("150.5")
            scroll("RECORD","record_gallery");rule.onNodeWithTag("record_photo").assertExists()
            assertEquals(references,runBlocking{f.images.ownedReferences()})
        }
        assertEquals(saved,runBlocking{f.meals.getMeal(saved.id)})
        rule.onNodeWithTag("nav_HOME").performClick();rule.onNodeWithTag("confirmation_dialog").assertExists()
        rule.onNodeWithTag("confirmation_cancel").performClick();rule.onNodeWithTag("nav_RECORD").assertIsSelected()
    }
    @Test fun myEditsStaySeparateFromSavedTargetsAcrossRotationAndWidths() {
        rule.onNodeWithTag("nav_MY").performClick()
        waitFor{rule.onAllNodes(hasTestTag("nickname_input") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        scroll("MY","nickname_input");rule.onNodeWithTag("nickname_input").performTextReplacement("Draft nickname")
        scroll("MY","body_weight");rule.onNodeWithTag("body_weight").performTextReplacement("80.25")
        assertTrue(automation.setRotation(UiAutomation.ROTATION_FREEZE_90))
        waitFor{rule.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        for((width,expanded) in listOf(2363 to true,1575 to false,1080 to false)) {
            resize(width,expanded,"MY")
            scroll("MY","body_weight");rule.onNodeWithTag("body_weight").assertTextContains("80.25")
            scroll("MY","nickname_input");rule.onNodeWithTag("nickname_input").assertTextContains("Draft nickname")
            rule.onNodeWithTag("my_saved_targets").assertDoesNotExist()
            assertEquals(70.0,runBlocking {f.settings.bodySettings.first()}!!.weightKg,0.0)
            assertEquals("First Account",runBlocking{f.settings.profile.first()}.nickname)
        }
    }
    @Test fun enlargedTextWithRealKeyboardKeepsFieldsAndSaveReachable() {
        foreground()
        rule.onNodeWithTag("nav_MY").performClick()
        waitFor{rule.onAllNodes(hasTestTag("body_weight") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        scroll("MY","body_weight");rule.onNodeWithTag("body_weight").performClick().performTextReplacement("80.25")
        rule.runOnUiThread {WindowCompat.getInsetsController(rule.activity.window,rule.activity.window.decorView).show(WindowInsetsCompat.Type.ime())}
        rule.onNodeWithTag("body_weight").assertIsFocused()
        try {waitFor{ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}}
        catch(e:Throwable) {
            val report=shell("dumpsys input_method")
            java.io.File(f.context.getExternalFilesDir(null),"p09-ime-diagnostics.txt").writeText(report)
            android.util.Log.i("P09Ime","focus=${rule.activity.currentFocus} windowFocus=${rule.activity.hasWindowFocus()}")
            capture(rule,"ime-not-shown-diagnostic","p09-runtime")
            throw e
        }
        rule.onNodeWithTag("body_weight").assertIsDisplayed()
        rule.onNodeWithTag("body_weight").assertIsFocused()
        scroll("MY","body_save");rule.onNodeWithTag("body_save").assertIsDisplayed().assertIsEnabled()
        capture(rule,"my-large-font-keyboard","p09-runtime")
        rule.onNodeWithTag("body_save").performClick()
        rule.onNodeWithTag("confirmation_confirm").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("confirmation_cancel").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(70.0,runBlocking {f.settings.bodySettings.first()}!!.weightKg,0.0)
    }
    @Test fun actualRuntimeListAndAnalysisRemainInitialWithSavedMeals() {
        P07TestHost.fontScale=1f
        rule.activityRule.scenario.recreate()
        resize(1024,false,"HOME")
        rule.onNodeWithTag("nav_LIST").performClick()
        rule.onNodeWithTag("destination_LIST").assertIsDisplayed()
        rule.onNodeWithText("기간을 선택해 식단을 확인해보세요.").assertExists()
        rule.onNodeWithText("월 (30일)").performClick()
        rule.onNodeWithText("주 (7일)").assertIsSelected()
        rule.onNodeWithContentDescription("기간 달력 열기").performClick()
        rule.onNodeWithTag("confirmation_dialog").assertDoesNotExist()
        capture(rule,"list-runtime-initial","p09-runtime")
        rule.onNodeWithTag("nav_ANALYSIS").performClick()
        rule.onNodeWithTag("destination_ANALYSIS").assertIsDisplayed()
        rule.onNodeWithText("월 (30일)").performClick()
        rule.onNodeWithText("주 (7일)").assertIsSelected()
        rule.onAllNodesWithText("—\u00a0kcal").assertCountEquals(2)
        capture(rule,"analysis-runtime-initial","p09-runtime")
        assertEquals(saved,runBlocking{f.meals.getMeal(saved.id)})
    }
}
