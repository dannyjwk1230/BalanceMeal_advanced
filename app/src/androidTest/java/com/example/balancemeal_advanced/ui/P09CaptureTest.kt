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
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal_advanced.ui.preview.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream

internal fun wakeP09Device() {
    val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
    for(command in listOf("input keyevent KEYCODE_WAKEUP","wm dismiss-keyguard")) {
        automation.executeShellCommand(command).use {fd->FileInputStream(fd.fileDescriptor).use {it.readBytes()}}
    }
}

@RunWith(AndroidJUnit4::class)
class P09CaptureTest {
    @get:Rule val rule=createComposeRule()
    @Test fun sameFixturesAcrossWindowClassesAndInitialShells() {
        wakeP09Device()
        val phase=InstrumentationRegistry.getArguments().getString("capturePhase") ?: "after"
        var width by mutableIntStateOf(390)
        var screen by mutableStateOf("home")
        val heights=mapOf("home" to 1494,"record" to 1890,"list" to 1417,"analysis" to 1729,"my" to 1467,"list-initial" to 844,"analysis-initial" to 844)
        rule.setContent {CompositionLocalProvider(LocalDensity provides Density(1f,1f)) {
            Box(Modifier.requiredSize(width.dp,checkNotNull(heights[screen]).dp).consumeWindowInsets(WindowInsets.systemBars).testTag("p09_frame")) {
                when(screen) {
                    "home"->ReferenceHomePreview();"record"->ReferenceRecordPreview();"list"->FilledMealListPreview()
                    "analysis"->FilledAnalysisPreview();"my"->ReferenceMyPreview();"list-initial"->InitialMealListPreview();else->InitialAnalysisPreview()
                }
            }
        }}
        val widths=if(phase=="before")listOf(390) else listOf(390,700,1000)
        val rows=mutableListOf<String>()
        for(w in widths)for(s in heights.keys) {
            rule.runOnIdle {width=w;screen=s};rule.waitForIdle()
            if(phase!="before") {
                rule.onNodeWithTag(if(w<600)"navigation_bar" else "navigation_rail").assertExists()
                val destination=when(s){"list","list-initial"->"LIST";"analysis","analysis-initial"->"ANALYSIS";else->s.uppercase()}
                if(w>=840)rule.onNodeWithTag("destination_${destination}_two_panes").assertExists()
                else rule.onNodeWithTag("destination_${destination}_two_panes").assertDoesNotExist()
            }
            capture(rule,"$s-$w","p09-$phase","p09_frame")
            val node=rule.onNodeWithTag("p09_frame").fetchSemanticsNode()
            rows+="{\"screen\":\"$s\",\"widthDp\":$w,\"heightDp\":${heights[s]},\"widthPx\":${node.size.width},\"heightPx\":${node.size.height},\"density\":1,\"fontScale\":1,\"systemBarsIncluded\":false}"
        }
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"p09-$phase")
        File(dir,"capture-conditions.json").writeText(rows.joinToString(",\n","[\n","\n]"))
    }
}
