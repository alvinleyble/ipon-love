package com.iponlove.app.feature.notes.domain.usecase

/**
 * Reduces a note's serialized HTML body to plain text. Used for the list preview snippet
 * and for the "is this note effectively empty?" check on save — both need the text the
 * reader sees, not the markup. Pure (no Android imports) so it stays JVM-unit-testable.
 *
 * Deliberately lightweight: the editor emits a small, well-formed tag set (`<p>`, `<br>`,
 * `<b>`, `<i>`, `<u>`, `<ul>/<ol>/<li>`), so stripping tags + decoding the handful of
 * entities it escapes is enough — no general HTML parser required.
 */
object NoteContentText {

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")

    fun plainText(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val withoutBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("(?i)</(p|li|div|h[1-6])>"), " ")
        val stripped = TAG.replace(withoutBreaks, "")
        val decoded = stripped
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return WHITESPACE.replace(decoded, " ").trim()
    }

    /** True when the title and body together carry no reader-visible text. */
    fun isBlank(title: String, html: String?): Boolean =
        title.isBlank() && plainText(html).isEmpty()
}
