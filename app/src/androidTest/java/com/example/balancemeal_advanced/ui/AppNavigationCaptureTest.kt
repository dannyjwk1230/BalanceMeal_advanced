package com.example.balancemeal_advanced.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.MainActivity
import com.example.balancemeal_advanced.ui.navigation.NavigationPermission
import com.example.balancemeal_advanced.ui.navigation.NavigationRequest
import com.example.balancemeal_advanced.ui.preview.FilledAnalysisPreview
import com.example.balancemeal_advanced.ui.preview.FilledMealListPreview
import com.example.balancemeal_advanced.ui.preview.SavedMyPreview
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun capture(rule: ComposeTestRule, name: String, directoryName: String = "p04-captures", nodeTag: String? = null) {
    val directory = File(
        checkNotNull(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)),
        directoryName,
    )
    check(directory.exists() || directory.mkdirs())
    val target = File(directory, name + ".png")
    rule.waitForIdle()
    val node=if(nodeTag==null)rule.onRoot() else rule.onNodeWithTag(nodeTag)
    val bitmap=node.captureToImage().asAndroidBitmap()
    target.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    assertTrue(target.length() > 0)
}

@RunWith(AndroidJUnit4::class)
class AppNavigationCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun fiveDestinationsProfileAndBodyEntryAndRuntimeCaptures() {
        for (destination in listOf("HOME", "RECORD", "LIST", "ANALYSIS", "MY")) {
            rule.onNodeWithTag("nav_" + destination).performClick().assertIsSelected()
            rule.onNodeWithTag("destination_" + destination).assertIsDisplayed()
            if (destination == "LIST") capture(rule, "list-runtime-initial")
            if (destination == "ANALYSIS") {
                rule.onAllNodesWithText("5,800").assertCountEquals(0)
                capture(rule, "analysis-runtime-initial")
            }
        }
        rule.onNodeWithTag("top_profile").performClick()
        rule.onNodeWithTag("destination_MY").assertIsDisplayed()
        rule.onNodeWithTag("nav_MY").assertIsSelected()
        rule.onNodeWithTag("nav_HOME").performClick()
        rule.onNodeWithTag("body_settings_cta").performScrollTo().performClick()
        rule.onNodeWithTag("destination_MY").assertIsDisplayed()
        rule.onNodeWithTag("my_body_settings").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class FilledPreviewCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test fun filledMealListPreviewCapture() {
        rule.setContent { FilledMealListPreview() }
        rule.onNodeWithTag("destination_LIST").assertIsDisplayed()
        capture(rule, "list-preview-filled")
    }

    @Test fun filledAnalysisPreviewCapture() {
        rule.setContent { FilledAnalysisPreview() }
        rule.onNodeWithTag("destination_ANALYSIS").assertIsDisplayed()
        capture(rule, "analysis-preview-filled")
    }

    @Test fun savedMyPreviewCapture() {
        rule.setContent { SavedMyPreview() }
        rule.onNodeWithTag("destination_MY").assertIsDisplayed()
        capture(rule, "my-preview-saved")
    }
}

@RunWith(AndroidJUnit4::class)
class MyRuntimeCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun myRuntimeCapture() {
        rule.onNodeWithTag("nav_MY").performClick().assertIsSelected()
        rule.onNodeWithTag("destination_MY").assertIsDisplayed()
        capture(rule,"my-runtime")
    }
}

/** Real dispatcher/NavHost integration: a pure NavigationRequests test cannot detect bypasses. */
@RunWith(AndroidJUnit4::class)
class NavigationPermissionIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun systemBackWaitsForPermissionAndHonorsDenialAndRootExit() {
        var pendingBack: ((Boolean) -> Unit)? = null
        var backRequests = 0
        var exits = 0
        val permission = NavigationPermission { request, decide ->
            if (request == NavigationRequest.Back) {
                backRequests++
                pendingBack = decide
            } else decide(true)
        }
        rule.setContent {
            BalanceMeal_advancedTheme {
                BalanceMealApp(navigationPermission = permission, onExit = { exits++ })
            }
        }
        rule.onNodeWithTag("nav_RECORD").performClick().assertIsSelected()
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, backRequests)
            checkNotNull(pendingBack)(false)
            pendingBack = null
        }
        rule.onNodeWithTag("nav_RECORD").assertIsSelected()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("destination_RECORD").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(2, backRequests)
            checkNotNull(pendingBack)(true)
            pendingBack = null
        }
        rule.onNodeWithTag("destination_HOME").assertIsDisplayed()
        rule.onNodeWithTag("nav_HOME").assertIsSelected()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle {
            assertEquals(3, backRequests)
            assertEquals(0, exits)
            checkNotNull(pendingBack)(true)
            assertEquals(1, exits)
        }
    }
}

