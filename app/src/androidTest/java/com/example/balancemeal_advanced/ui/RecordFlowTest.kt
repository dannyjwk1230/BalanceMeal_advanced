package com.example.balancemeal_advanced.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.core.model.MealType
import com.example.balancemeal_advanced.data.image.CameraTicket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordFlowTest {
    private lateinit var f:P07Fixture
    private lateinit var source:CameraTicket
    private lateinit var registry:ControlledResults
    private val rule=createAndroidComposeRule<P07TestActivity>()
    private val fixtureRule=object:ExternalResource() {
        override fun before(){f=P07Fixture();source=runBlocking{f.imageUri()};registry=ControlledResults();P07TestHost.dependencies=f;P07TestHost.registry=registry}
        override fun after(){P07TestHost.dependencies=null;P07TestHost.registry=null;runBlocking{f.camera.discard(source)};f.close()}
    }
    @get:Rule val chain:TestRule=RuleChain.outerRule(fixtureRule).around(rule)
    private enum class Outcome {SUCCESS,CANCEL,DENIED}
    private inner class ControlledResults:ActivityResultRegistry() {
        var gallery=Outcome.SUCCESS;var camera=Outcome.SUCCESS
        var galleryCalls=0;var cameraCalls=0
        var lastCamera:Uri?=null
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            val isCamera=contract is ActivityResultContracts.TakePicture
            if(isCamera)cameraCalls++ else galleryCalls++
            val outcome=if(isCamera)camera else gallery
            if(outcome==Outcome.DENIED)throw SecurityException("Injected platform launch denial")
            if(isCamera) {
                lastCamera=input as Uri
                if(outcome==Outcome.SUCCESS)f.writeImage(checkNotNull(lastCamera))
                dispatchResult(requestCode,if(outcome==Outcome.SUCCESS)Activity.RESULT_OK else Activity.RESULT_CANCELED,null)
            } else dispatchResult(requestCode,if(outcome==Outcome.SUCCESS)Activity.RESULT_OK else Activity.RESULT_CANCELED,if(outcome==Outcome.SUCCESS)Intent().setData(Uri.parse(source.uri)) else null)
        }
    }
    private fun waitFor(condition:()->Boolean)=rule.waitUntil(10000,condition)
    private fun scroll(tag:String){rule.onNodeWithTag("destination_RECORD").performScrollToNode(hasTestTag(tag))}
    private fun enter(){rule.onNodeWithTag("nav_RECORD").performClick();waitFor{rule.onAllNodes(hasTestTag("record_add_food") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}}
    private fun changeAmount() {
        val id=checkNotNull(stored()).items.single().id
        scroll("amount_$id");rule.onNodeWithTag("amount_$id").performTextReplacement("175")
    }
    private fun chooseDay(day:String){
        scroll("record_date");rule.onNodeWithTag("record_date").performClick()
        rule.onNodeWithTag("date_wheel_day").performSemanticsAction(SemanticsActions.SetProgress) { it(day.toFloat()) }
        rule.waitForIdle()
        rule.onNodeWithTag("date_picker_confirm").performClick()
        rule.waitForIdle()
    }
    private fun add(){
        scroll("record_add_food");rule.onNodeWithTag("record_add_food").performClick()
        rule.onNodeWithTag("food_search").performTextInput("쌀밥")
        waitFor{rule.onAllNodes(hasStateDescription("1개 검색 결과")).fetchSemanticsNodes().isNotEmpty()}
        val result=hasText("쌀밥") and !hasSetTextAction() and hasAnyAncestor(hasTestTag("food_picker_list"))
        rule.onNodeWithTag("food_picker_list").performScrollToNode(result);rule.onNode(result).performClick()
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_amount"));rule.onNodeWithTag("food_add_amount").performTextInput("150")
        rule.onNodeWithTag("food_picker_list").performScrollToNode(hasTestTag("food_add_confirm"));rule.onNodeWithTag("food_add_confirm").performClick()
        androidx.test.espresso.Espresso.pressBack()
        rule.waitForIdle()
        if (rule.onAllNodesWithTag("food_picker_list").fetchSemanticsNodes().isNotEmpty()) {
            androidx.test.espresso.Espresso.pressBack()
            rule.waitForIdle()
        }
    }
    private fun save(){scroll("record_persistence");rule.onNodeWithTag("record_persistence").performClick();rule.onNodeWithTag("confirmation_confirm").performClick();waitFor{rule.onAllNodesWithTag("record_success").fetchSemanticsNodes().isNotEmpty()}}
    private fun warning(){rule.onNodeWithText("저장하지 않은 내용이 있습니다. 이동하면 작성 중인 내용이 삭제됩니다.").assertExists()}
    private fun stored()=runBlocking{f.meals.getMeal(f.time.now().toLocalDate(),MealType.BREAKFAST)}

    @Test fun saveCancelUpdateRecreateDeleteUsesRealStorageAndConfirmation(){
        enter();add()
        scroll("record_persistence");rule.onNodeWithTag("record_persistence").performClick();assertNull(stored());rule.onNodeWithTag("confirmation_cancel").performClick();assertNull(stored())
        save();val original=checkNotNull(stored());val id=original.items.single().id
        rule.onNodeWithTag("nav_HOME").performClick()
        waitFor{rule.onAllNodes(hasTestTag("home_energy") and hasText("249 kcal")).fetchSemanticsNodes().isNotEmpty()}
        enter();scroll("amount_$id");rule.onNodeWithTag("amount_$id").assertTextContains("150.0")
        scroll("amount_$id");rule.onNodeWithTag("amount_$id").performTextReplacement("175.5")
        assertEquals(150.0,stored()!!.items.single().amount,0.0);save();assertEquals(original.id,stored()!!.id)
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        scroll("amount_$id");rule.onNodeWithTag("amount_$id").assertTextContains("175.5")
        scroll("remove_$id");rule.onNodeWithTag("remove_$id").performClick();assertEquals(1,stored()!!.items.size)
        scroll("record_persistence");rule.onNodeWithTag("record_persistence").assertTextContains("삭제").performClick()
        rule.onNodeWithText("2026-11-11 조식 식단을 삭제할까요?").assertExists();rule.onNodeWithTag("confirmation_cancel").performClick();assertNotNull(stored())
        scroll("record_persistence");rule.onNodeWithTag("record_persistence").performClick();rule.onNodeWithTag("confirmation_confirm").performClick()
        waitFor{stored()==null};assertEquals(0,runBlocking{f.database.meals().itemCount()})
    }
    @Test fun amountWarningsCoverTabsProfileSystemBackDateAndMeal(){
        enter();add();save();val baseline=checkNotNull(stored())
        val actions=listOf<()->Unit>(
            {rule.onNodeWithTag("nav_HOME").performClick()}, {rule.onNodeWithTag("nav_LIST").performClick()},
            {rule.onNodeWithTag("nav_ANALYSIS").performClick()}, {rule.onNodeWithTag("nav_MY").performClick()},
            {rule.onNodeWithTag("top_profile").performClick()},
            {rule.runOnUiThread{rule.activity.onBackPressedDispatcher.onBackPressed()}}
        )
        for(action in actions){
            changeAmount();action();warning();rule.onNodeWithTag("confirmation_cancel").performClick();rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
            action();warning();rule.onNodeWithTag("confirmation_confirm").performClick();rule.waitForIdle();assertEquals(baseline,stored());enter()
        }
        changeAmount();rule.onNodeWithTag("nav_HOME").performClick();warning()
        rule.activityRule.scenario.recreate();rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        rule.onNodeWithText("저장하지 않은 내용이 있습니다. 이동하면 작성 중인 내용이 삭제됩니다.").assertDoesNotExist()
        val row=baseline.items.single().id;scroll("amount_$row");rule.onNodeWithTag("amount_$row").assertTextContains("175")
        scroll("record_meal_LUNCH");rule.onNodeWithTag("record_meal_LUNCH").performClick();warning();rule.onNodeWithTag("confirmation_cancel").performClick();rule.onNodeWithTag("record_meal_BREAKFAST").assertIsSelected()
        rule.onNodeWithTag("record_meal_LUNCH").performClick();rule.onNodeWithTag("confirmation_confirm").performClick();waitFor{rule.onAllNodes(hasTestTag("record_meal_LUNCH") and isSelected()).fetchSemanticsNodes().isNotEmpty()}
        scroll("record_meal_BREAKFAST");rule.onNodeWithTag("record_meal_BREAKFAST").performClick();waitFor{rule.onAllNodes(hasTestTag("record_add_food") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        changeAmount();chooseDay("10");warning();rule.onNodeWithTag("confirmation_cancel").performClick();rule.onNodeWithTag("record_date").assertTextContains("2026-11-11")
        chooseDay("10");rule.onNodeWithTag("confirmation_confirm").performClick();waitFor{rule.onAllNodes(hasText("2026-11-10")).fetchSemanticsNodes().isNotEmpty()};assertEquals(baseline,stored())
    }
    @Test fun galleryCameraSuccessCancelAndDenialPreserveOrReplaceDraftAndPersistPhoto(){
        enter();scroll("record_gallery");rule.onNodeWithTag("record_gallery").performClick()
        waitFor{rule.onAllNodesWithTag("record_photo").fetchSemanticsNodes().isNotEmpty()}
        val galleryFile=runBlocking{f.images.ownedReferences().single()}
        scroll("record_persistence");rule.onNodeWithTag("record_persistence").assertIsNotEnabled();assertNull(stored())
        registry.gallery=Outcome.CANCEL;scroll("record_gallery");rule.onNodeWithTag("record_gallery").performClick();rule.waitForIdle();rule.onNodeWithTag("record_photo").assertExists()
        waitFor{rule.onAllNodes(hasTestTag("record_gallery") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        registry.gallery=Outcome.DENIED;rule.onNodeWithTag("record_gallery").performClick();waitFor{rule.onAllNodesWithText("사진 선택·촬영을 완료하지 못했습니다. 기존 초안과 사진을 유지합니다.").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithTag("record_photo").assertExists()
        waitFor{rule.onAllNodes(hasTestTag("record_camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        registry.camera=Outcome.CANCEL;scroll("record_camera");rule.onNodeWithTag("record_camera").performClick();rule.waitForIdle();rule.onNodeWithTag("record_photo").assertExists()
        waitFor{rule.onAllNodes(hasTestTag("record_camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        registry.camera=Outcome.DENIED;rule.onNodeWithTag("record_camera").performClick();rule.waitForIdle();rule.onNodeWithTag("record_photo").assertExists()
        waitFor{rule.onAllNodes(hasTestTag("record_camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        registry.camera=Outcome.SUCCESS;rule.onNodeWithTag("record_camera").performClick();waitFor{rule.onAllNodes(hasTestTag("record_camera") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        add();save();val reference=checkNotNull(stored()!!.photoReference)
        assertEquals(3,registry.galleryCalls);assertEquals(3,registry.cameraCalls);assertFalse(java.io.File(galleryFile).exists());assertNotEquals(galleryFile,reference)
        assertTrue(java.io.File(reference).exists());rule.activityRule.scenario.recreate();scroll("record_gallery");waitFor{rule.onAllNodesWithTag("record_photo").fetchSemanticsNodes().isNotEmpty()}
        val bitmap=runBlocking{f.images.load(reference)};assertEquals(12,bitmap.width);bitmap.recycle()
        capture(rule,"record-photo-saved","p07-captures")
    }
}
