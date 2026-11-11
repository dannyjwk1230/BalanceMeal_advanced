package com.example.balancemeal_advanced.ui.home

import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.nutrition.EnergyTargets
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import java.time.LocalDate

data class HomeUiState(
    val date:LocalDate,
    val loading:Boolean=true,
    val error:String?=null,
    val total:NutrientAmounts?=null,
    val nutrition:NutritionUiState=NutritionUiState.Initial,
    val hasRecords:Boolean=false,
    val showAddMeal:Boolean=false,
    val needsBody:Boolean=false,
    val targets:EnergyTargets?=null,
    val targetEnergyLabel:String?=null,
    val mainComment:String="",
    val subComment:String="",
    val calculationDisabled:Boolean=false,
    val activatingBody:Boolean=false,
    val activationError:String?=null,
)
