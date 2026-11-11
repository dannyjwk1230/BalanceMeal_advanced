package com.example.balancemeal_advanced.ui.preview

import androidx.compose.runtime.Composable
import com.example.balancemeal_advanced.core.model.*
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.ui.home.*
import com.example.balancemeal_advanced.ui.my.*
import com.example.balancemeal_advanced.ui.record.*
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import com.example.balancemeal_advanced.ui.navigation.AppDestination
import java.time.LocalDate

/** Fixed visual values only; never inserted into Food/Meal repositories or used by runtime routes. */
@Composable fun ReferenceMyPreview()=PreviewFrame(AppDestination.MY) {SavedMyPreview()}

@Composable fun ReferenceHomePreview()=PreviewFrame(AppDestination.HOME) {
    val amounts=NutrientAmounts(180.0,70.0,50.0,1450.0)
    HomeScreen(HomeUiState(LocalDate.of(2025,5,14),loading=false,total=amounts,nutrition=NutritionUiState.from(amounts),hasRecords=true,showAddMeal=true,
        targets=EnergyTargets.fromEnergy(2100.0),targetEnergyLabel="2100",mainComment="오늘의 칼로리가 목표보다 부족해요.",subComment="내일은 탄수화물 섭취에 신경 써보세요."),{},{},{})
}

@Composable fun ReferenceRecordPreview()=PreviewFrame(AppDestination.RECORD) {
    val nutrition=NutritionUiState.from(NutrientAmounts(55.995,5.04,.48,249.0))
    RecordScreen(RecordUiState(RecordKey(LocalDate.of(2025,5,14),MealType.LUNCH),loading=false,
        draft=RecordDraft(),rows=listOf(RecordRow("p09-rice","쌀밥","g","150",null,nutrition)),nutrition=nutrition),{},{},{_,_->},{},{},{},{},{},{},{},{})
}
