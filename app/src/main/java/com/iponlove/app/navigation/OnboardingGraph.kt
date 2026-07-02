package com.iponlove.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iponlove.app.feature.onboarding.presentation.OnboardingPairScreen
import com.iponlove.app.feature.onboarding.presentation.OnboardingTemplatesScreen
import com.iponlove.app.feature.onboarding.presentation.OnboardingWelcomeScreen

private const val WELCOME_ROUTE = "onboarding_welcome"
private const val PAIR_ROUTE = "onboarding_pair"
private const val TEMPLATES_ROUTE = "onboarding_templates"

/**
 * First-run graph (ADR-0024): Welcome → pair-or-solo → starter-template picker → [onComplete].
 * Mounted by [com.iponlove.app.MainActivity] in place of [IponApp] while the new-user gate is
 * open; it has its own small back stack and is never reachable once onboarding finishes (the
 * caller swaps it out for [IponApp] rather than popping back into this graph).
 */
@Composable
fun OnboardingGraph(onComplete: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = WELCOME_ROUTE) {
        composable(WELCOME_ROUTE) {
            OnboardingWelcomeScreen(onContinue = { navController.navigate(PAIR_ROUTE) })
        }
        composable(PAIR_ROUTE) {
            OnboardingPairScreen(onContinue = { navController.navigate(TEMPLATES_ROUTE) })
        }
        composable(TEMPLATES_ROUTE) {
            OnboardingTemplatesScreen(onFinish = onComplete)
        }
    }
}
