package com.example.balancemeal_advanced.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWindowClass { COMPACT, MEDIUM, EXPANDED;
    companion object {fun fromWidth(width:Dp)=when {width<600.dp->COMPACT;width<840.dp->MEDIUM;else->EXPANDED}}
}
/** Width of the app window BEFORE the rail consumes space; previews can use their own constraints. */
val LocalAppWindowWidth=staticCompositionLocalOf<Dp?> {null}

/** Shared page scrolling keeps low-height/IME layouts reachable; only the card groups rearrange. */
@Composable
fun AdaptivePage(
    tag:String,modifier:Modifier=Modifier,secondaryInCompact:Boolean=true,
    header:@Composable ColumnScope.()->Unit={},
    primary:@Composable ColumnScope.()->Unit,
    secondary:@Composable ColumnScope.()->Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter) {
        val wide=AppWindowClass.fromWidth(LocalAppWindowWidth.current ?: maxWidth)==AppWindowClass.EXPANDED
        Column(Modifier.widthIn(max=if(wide)1200.dp else 640.dp).fillMaxSize().imePadding()
            .verticalScroll(rememberScrollState()).testTag(tag).padding(20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            header()
            if(wide) Row(Modifier.fillMaxWidth().testTag("${tag}_two_panes"),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).testTag("${tag}_primary"),verticalArrangement=Arrangement.spacedBy(24.dp),content=primary)
                Column(Modifier.weight(1f).testTag("${tag}_secondary"),verticalArrangement=Arrangement.spacedBy(24.dp),content=secondary)
            } else {
                Column(Modifier.fillMaxWidth().testTag("${tag}_primary"),verticalArrangement=Arrangement.spacedBy(24.dp),content=primary)
                if(secondaryInCompact)Column(Modifier.fillMaxWidth().testTag("${tag}_secondary"),verticalArrangement=Arrangement.spacedBy(24.dp),content=secondary)
            }
        }
    }
}
