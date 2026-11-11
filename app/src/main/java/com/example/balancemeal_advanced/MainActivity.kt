package com.example.balancemeal_advanced

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.balancemeal_advanced.ui.BalanceMealApp
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalanceMeal_advancedTheme { BalanceMealApp(onExit = { finish() }) }
        }
    }
}