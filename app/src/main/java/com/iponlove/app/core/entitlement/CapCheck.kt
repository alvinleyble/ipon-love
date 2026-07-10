package com.iponlove.app.core.entitlement

/**
 * The outcome of a create-time count-cap check ([PremiumGate.checkCap]). [Allowed] whenever the
 * create may proceed — either enforcement is off (dormant), the user (or partner, for a
 * [Scope.SHARED] entity) has premium, or the free cap simply hasn't been reached. [Blocked] carries
 * both numbers the upsell copy needs ("You've reached the free limit of {freeLimit} … up to
 * {premiumMax}").
 */
sealed interface CapCheck {
    data object Allowed : CapCheck
    data class Blocked(val freeLimit: Int, val premiumMax: Int) : CapCheck
}
