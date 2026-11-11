package com.example.balancemeal_advanced.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.balancemeal_advanced.ui.my.*
import com.example.balancemeal_advanced.ui.theme.BalanceMeal_advancedTheme

@Preview(widthDp=390,heightDp=1600,showBackground=true)
@Composable
fun SavedMyPreview() {
    BalanceMeal_advancedTheme {
        MyScreen(MyUiState(loaded=true,nicknameDraft="First Account",draft=BodyDraft("1996-11-10",com.example.balancemeal_advanced.core.model.Sex.MALE,"175","70",com.example.balancemeal_advanced.core.model.ActivityLevel.LOW_ACTIVE),target=TargetDisplay("2660","332.6 ~ 432.3 g","66.5 ~ 133.0 g","44.3 ~ 88.7 g")),ProfileUiState(loading=false),{},{},{},{},{},{},{},{},false,"")
    }
}
