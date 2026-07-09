package com.iponlove.app.core.config

/**
 * The premium paywall remote-override config (D3 / ADR-0044), read locally from the Room
 * cache. Ships DORMANT — [enforcementEnabled] false means no gate ever locks.
 *
 * [capOverridesJson] is the raw `cap_overrides` jsonb as a string; parsing it into
 * `PlanLimits` is S2's job (this slice only persists + exposes it verbatim).
 */
data class AppConfig(
    val enforcementEnabled: Boolean,
    val capOverridesJson: String?,
) {
    companion object {
        /**
         * Fail-open cold-start default (G4 / ADR-0044 §6): a device that has not yet pulled
         * the `app_config` row treats enforcement as OFF and stays fully unlocked, self-healing
         * on the first foreground refresh. So a paying customer reinstalling offline is never
         * wrongly locked.
         */
        val DORMANT = AppConfig(enforcementEnabled = false, capOverridesJson = null)
    }
}
