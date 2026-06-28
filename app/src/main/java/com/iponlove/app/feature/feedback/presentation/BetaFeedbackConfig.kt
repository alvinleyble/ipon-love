package com.iponlove.app.feature.feedback.presentation

import java.net.URLEncoder

/**
 * Placeholder config for the beta feedback Google Form.
 * Once Alvin creates the form, fill in FORM_BASE_URL (the "?embedded=true" share URL)
 * and the entry IDs for each prefill field (Settings → "Collect email" off → share link →
 * get prefill link from ⋮ menu → inspect URL params for the entry.XXXXXXXXXX keys).
 */
object BetaFeedbackConfig {
    const val FORM_BASE_URL: String =
        "https://docs.google.com/forms/d/e/1FAIpQLSfYerUWCmfcPc2om7fWTJm5fi0L_crF1DKbPMPwxZQ9VzsFhQ/viewform"

    // Map of context-field label → Google Form entry ID (e.g. "entry.1234567890").
    // Leave values blank until the form fields exist.
    val entryIds: Map<String, String> = mapOf(
        "tester" to "entry.710703401",
        "device" to "entry.761129799",
        "android" to "entry.575724410",
        "version" to "entry.64290927",
    )

    val isConfigured: Boolean get() = FORM_BASE_URL.isNotBlank()

    fun buildPrefillUrl(
        versionName: String,
        flavor: String,
        device: String,
        androidVersion: String,
        testerName: String,
    ): String {
        if (!isConfigured) return ""
        val params = buildList {
            entryIds["version"]?.takeIf { it.isNotBlank() }
                ?.let { add("$it=${encode("$versionName ($flavor)")}") }
            entryIds["device"]?.takeIf { it.isNotBlank() }
                ?.let { add("$it=${encode(device)}") }
            entryIds["android"]?.takeIf { it.isNotBlank() }
                ?.let { add("$it=${encode(androidVersion)}") }
            entryIds["tester"]?.takeIf { it.isNotBlank() }
                ?.let { add("$it=${encode(testerName)}") }
        }
        val query = (listOf("embedded=true") + params).joinToString("&")
        return "$FORM_BASE_URL?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
