package com.example.balancemeal_advanced.ui.navigation

import java.util.concurrent.atomic.AtomicBoolean

enum class AppDestination(val label: String) {
    HOME("홈"), RECORD("기록"), LIST("목록"), ANALYSIS("분석"), MY("마이")
}
enum class MySection { OVERVIEW, BODY_SETTINGS }

sealed interface NavigationRequest {
    data class Open(
        val destination: AppDestination,
        val mySection: MySection = MySection.OVERVIEW,
    ) : NavigationRequest
    data object Back : NavigationRequest
}

/** P07 may retain the decision callback until the user confirms or cancels. No dirty state here. */
fun interface NavigationPermission {
    fun check(request: NavigationRequest, decide: (Boolean) -> Unit)
}

val AllowNavigation = NavigationPermission { _, decide -> decide(true) }

/** Every app navigation request passes this boundary. Only the first decision is consumed. */
class NavigationRequests(
    private val permission: NavigationPermission,
    private val perform: (NavigationRequest) -> Unit,
) {
    fun request(request: NavigationRequest) {
        val answered = AtomicBoolean(false)
        permission.check(request) { allowed ->
            if (answered.compareAndSet(false, true) && allowed) perform(request)
        }
    }
}

