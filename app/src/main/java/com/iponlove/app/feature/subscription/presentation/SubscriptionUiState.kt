package com.iponlove.app.feature.subscription.presentation

/**
 * Paywall screen state (S5). [isPremium] reflects the signed-in user's *own* cached entitlement
 * (§10.3 "already owned" — a partner's premium unlocks shared surfaces but the buyer still gets
 * their own individual perks, so the buy CTA stays available until *this* user owns it).
 * [message] is a one-shot result banner (purchase/restore outcome), cleared once shown.
 */
data class SubscriptionUiState(
    val loading: Boolean = true,
    val isPremium: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val restoreInProgress: Boolean = false,
    val message: String? = null,
)
