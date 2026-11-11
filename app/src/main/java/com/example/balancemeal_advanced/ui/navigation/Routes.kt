package com.example.balancemeal_advanced.ui.navigation

import kotlinx.serialization.Serializable

object Routes {
    @Serializable data object Home
    @Serializable data object Record
    @Serializable data object MealList
    @Serializable data object Analysis
    // Only a section name and a one-shot entry token are passed, never a profile/meal object.
    @Serializable data class My(val section: String = MySection.OVERVIEW.name, val entry: String = "")
}

