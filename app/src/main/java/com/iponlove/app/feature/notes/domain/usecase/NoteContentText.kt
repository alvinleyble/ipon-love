package com.iponlove.app.feature.notes.domain.usecase

/**
 * Reduces a note's serialized HTML body to plain text. Used for the list preview snippet
 * and for the "is this note effectively empty?" check on save — both need the text the
 * reader sees, not the markup. Pure (no Android imports) so it stays JVM-unit-testable.
 *
 * The rich editor serializes aggressively: besides the block/inline tags, it escapes ordinary
 * punctuation as HTML5 named entities (`&comma;`, `&lpar;`, `&period;`, …). So decoding must
 * cover the numeric/hex character references AND that punctuation-heavy named set, otherwise the
 * raw `&…;` codes leak into the preview.
 */
object NoteContentText {

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val NUMERIC_REF = Regex("&#(\\d+);")
    private val HEX_REF = Regex("&#[xX]([0-9a-fA-F]+);")

    /**
     * Named entities the editor emits, mapped to the reader-visible glyph. Punctuation is mapped
     * to plain ASCII (readable and collapse-friendly); `&amp;` is intentionally omitted here and
     * applied last so a literal, double-escaped "&amp;" can't cascade into another entity.
     */
    private val NAMED_ENTITIES: List<Pair<String, String>> = listOf(
        "&nbsp;" to " ",
        "&quot;" to "\"", "&apos;" to "'",
        "&lt;" to "<", "&gt;" to ">",
        "&excl;" to "!", "&num;" to "#", "&dollar;" to "$", "&percnt;" to "%",
        "&lpar;" to "(", "&rpar;" to ")", "&ast;" to "*", "&plus;" to "+",
        "&comma;" to ",", "&period;" to ".", "&sol;" to "/",
        "&colon;" to ":", "&semi;" to ";", "&equals;" to "=", "&quest;" to "?",
        "&commat;" to "@", "&lbrack;" to "[", "&bsol;" to "\\", "&rbrack;" to "]",
        "&lowbar;" to "_", "&grave;" to "`",
        "&lbrace;" to "{", "&vert;" to "|", "&verbar;" to "|", "&rbrace;" to "}",
        "&hyphen;" to "-", "&minus;" to "-", "&Hat;" to "^",
        "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
        "&lsquo;" to "‘", "&rsquo;" to "’",
        "&ldquo;" to "“", "&rdquo;" to "”",
        "&copy;" to "©", "&reg;" to "®", "&trade;" to "™", "&deg;" to "°",
    )

    fun plainText(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val withoutBreaks = html
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("(?i)</(p|li|div|h[1-6])>"), " ")
        val stripped = TAG.replace(withoutBreaks, "")
        val decoded = decodeEntities(stripped)
        return WHITESPACE.replace(decoded, " ").trim()
    }

    /** True when the title and body together carry no reader-visible text. */
    fun isBlank(title: String, html: String?): Boolean =
        title.isBlank() && plainText(html).isEmpty()

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        var result = NUMERIC_REF.replace(text) { m ->
            m.groupValues[1].toIntOrNull()?.let(::codePointToString) ?: m.value
        }
        result = HEX_REF.replace(result) { m ->
            m.groupValues[1].toIntOrNull(16)?.let(::codePointToString) ?: m.value
        }
        for ((name, value) in NAMED_ENTITIES) result = result.replace(name, value)
        return result.replace("&amp;", "&")
    }

    private fun codePointToString(cp: Int): String =
        if (cp in 0..0x10FFFF) String(Character.toChars(cp)) else ""
}
