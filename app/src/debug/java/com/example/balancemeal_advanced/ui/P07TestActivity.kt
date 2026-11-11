package com.example.balancemeal_advanced.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.balancemeal_advanced.data.repository.MyDependencies
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme

/** Debug-only host for real-storage UI tests, including Activity recreation. */
object P07TestHost {
    var dependencies:MyDependencies?=null
    var registry:ActivityResultRegistry?=null
    var fontScale:Float?=null
}
class P07TestActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val dependencies=P07TestHost.dependencies ?: run {finish();return}
        val registry=P07TestHost.registry ?: activityResultRegistry
        val owner=object:ActivityResultRegistryOwner {override val activityResultRegistry=registry}
        setContent {CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner,
            LocalDensity provides Density(LocalDensity.current.density,P07TestHost.fontScale ?: LocalDensity.current.fontScale)) {
            BalanceMeal_advancedTheme {BalanceMealApp(dependenciesOverride=dependencies,onExit={finish()})}
        }}
    }
}
