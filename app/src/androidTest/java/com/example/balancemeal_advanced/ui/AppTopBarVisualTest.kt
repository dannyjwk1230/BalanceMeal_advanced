package com.example.balancemeal_advanced.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppTopBarVisualTest {
    @get:Rule val rule = createComposeRule()

    @Test fun compactBrandAndNarrowLargeTextKeepNavigationTargetsSeparate() {
        wakeP09Device()
        var width by mutableIntStateOf(367)
        var font by mutableFloatStateOf(1f)
        var profileClicks=0
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f,font)) {
                Box(Modifier.requiredWidth(width.dp).wrapContentHeight().testTag("header_frame")) {
                    BalanceMeal_advancedTheme {
                        Scaffold(topBar = { AppTopBar({profileClicks++}) }) {
                            Box(Modifier.padding(it))
                        }
                    }
                }
            }
        }
        for ((name,w,scale) in listOf(Triple("reference",367,1f),Triple("narrow",320,1f),Triple("large-text",280,2f))) {
            rule.runOnIdle {width=w;font=scale}
            rule.onNodeWithTag("top_logo",useUnmergedTree=true).assertIsDisplayed()
            rule.onNodeWithText("BalanceMeal").assertIsDisplayed()
            val brand=rule.onNodeWithTag("top_brand").fetchSemanticsNode().boundsInRoot
            val profile=rule.onNodeWithTag("top_profile").fetchSemanticsNode().boundsInRoot
            assertTrue("Brand and profile must not overlap",brand.right<=profile.left)
            assertTrue("Profile target must be at least 48dp: $profile",profile.width>=96f && profile.height>=96f)
            rule.onNodeWithTag("top_back").assertDoesNotExist()
            rule.onNodeWithTag("top_brand").assertHasNoClickAction()
            rule.onNodeWithTag("top_profile").performClick()
            capture(rule,name,"p09-topbar","top_bar")
        }
        rule.runOnIdle {assertEquals(3,profileClicks)}
    }
}
