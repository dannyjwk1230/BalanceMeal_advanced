package com.example.balancemeal_advanced.ui

import com.example.balancemeal_advanced.core.model.NutrientAmounts
import com.example.balancemeal_advanced.core.nutrition.MacronutrientDistribution
import com.example.balancemeal_advanced.ui.navigation.*
import com.example.balancemeal_advanced.ui.presentation.NutritionUiState
import org.junit.Assert.*
import org.junit.Test

class CommonUiLogicTest {
    @Test fun chartUsesGramMass50503020AndIgnoresKcal() {
        val ratios = MacronutrientDistribution.from(NutrientAmounts(50.0, 30.0, 20.0, 999.0))
        assertEquals(.5, ratios.carbohydrate, 1e-12)
        assertEquals(.3, ratios.protein, 1e-12)
        assertEquals(.2, ratios.fat, 1e-12)
        val ui = NutritionUiState.from(NutrientAmounts(50.0, 30.0, 20.0, 999.0))
        assertEquals(listOf("50.0", "30.0", "20.0"), ui.nutrients.map { it.grams })
        assertEquals(listOf("50.0", "30.0", "20.0"), ui.nutrients.map { it.percent })
        assertEquals("999", ui.energyKcal)
    }
    @Test fun zeroMassIsEmptyEvenWithNonzeroSourceKcal() {
        val ui = NutritionUiState.from(NutrientAmounts(0.0, 0.0, 0.0, 3.0))
        assertTrue(ui.isEmpty)
        assertEquals("3", ui.energyKcal)
        assertTrue(ui.nutrients.all { it.fraction == 0f && it.percent == "0.0" })
    }
    @Test fun veryLargeFiniteMassStillHasFiniteRatios() {
        val ratios = MacronutrientDistribution.from(NutrientAmounts(Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.0))
        assertEquals(.5, ratios.carbohydrate, 1e-12)
        assertEquals(.5, ratios.protein, 1e-12)
    }
    @Test fun unknownInitialStateDoesNotClaimQueriedZeroes() {
        assertFalse(NutritionUiState.Initial.isKnown)
        assertEquals("—", NutritionUiState.Initial.energyKcal)
        assertTrue(NutritionUiState.Initial.nutrients.all { it.grams == "—" })
    }
    @Test fun fiveDestinationsHaveRequiredOrderAndOneMyDestination() {
        assertEquals(listOf("홈", "기록", "목록", "분석", "마이"), AppDestination.entries.map { it.label })
        assertEquals(AppDestination.MY, NavigationRequest.Open(AppDestination.MY, MySection.BODY_SETTINGS).destination)
    }
    @Test fun allowedRequestsNavigateAndDeniedRequestsDoNot() {
        val executed = mutableListOf<NavigationRequest>()
        NavigationRequests(AllowNavigation, executed::add).request(NavigationRequest.Open(AppDestination.MY))
        NavigationRequests(NavigationPermission { _, decide -> decide(false) }, executed::add).request(NavigationRequest.Back)
        assertEquals(listOf(NavigationRequest.Open(AppDestination.MY)), executed)
    }
    @Test fun pendingPermissionDoesNotNavigateUntilConfirmedAndConsumesDecisionOnce() {
        var decide: ((Boolean) -> Unit)? = null
        val executed = mutableListOf<NavigationRequest>()
        val requests = NavigationRequests(NavigationPermission { _, answer -> decide = answer }, executed::add)
        requests.request(NavigationRequest.Back)
        assertTrue(executed.isEmpty())
        decide!!(true)
        decide!!(true)
        assertEquals(listOf(NavigationRequest.Back), executed)
    }
    @Test fun bodyEntryKeepsOnlyDestinationAndSectionAcrossTheGuard() {
        val request = NavigationRequest.Open(AppDestination.MY, MySection.BODY_SETTINGS)
        var received: NavigationRequest? = null
        NavigationRequests(AllowNavigation) { received = it }.request(request)
        assertEquals(request, received)
    }
}

