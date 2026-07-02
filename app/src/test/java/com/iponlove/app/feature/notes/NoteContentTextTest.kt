package com.iponlove.app.feature.notes

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.notes.domain.usecase.NoteContentText
import org.junit.Test

class NoteContentTextTest {

    @Test
    fun plainText_stripsTags_keepsVisibleText() {
        val html = "<p><b>Buy</b> milk &amp; <i>eggs</i></p>"
        assertThat(NoteContentText.plainText(html)).isEqualTo("Buy milk & eggs")
    }

    @Test
    fun plainText_insertsSpaceAtBlockAndLineBreaks() {
        val html = "<p>line one</p><p>line two</p>"
        assertThat(NoteContentText.plainText(html)).isEqualTo("line one line two")

        val withBreak = "first<br>second"
        assertThat(NoteContentText.plainText(withBreak)).isEqualTo("first second")
    }

    @Test
    fun plainText_listItemsBecomeSpaceSeparated() {
        val html = "<ul><li>one</li><li>two</li></ul>"
        assertThat(NoteContentText.plainText(html)).isEqualTo("one two")
    }

    @Test
    fun plainText_decodesPunctuationNamedEntities() {
        // The rich editor escapes ordinary punctuation as HTML5 named entities; the preview must
        // decode them instead of leaking the raw &comma;/&lpar; codes.
        val html = "<p>Buy eggs&comma; milk &lpar;2L&rpar; &amp; bread&period;</p>"
        assertThat(NoteContentText.plainText(html)).isEqualTo("Buy eggs, milk (2L) & bread.")
    }

    @Test
    fun plainText_decodesNumericAndHexRefs() {
        assertThat(NoteContentText.plainText("caf&#233; &#x2014; open")).isEqualTo("café — open")
    }

    @Test
    fun plainText_leavesUnknownEntityUntouched() {
        // An unrecognised code is left as-is rather than mangled.
        assertThat(NoteContentText.plainText("a &bogus; b")).isEqualTo("a &bogus; b")
    }

    @Test
    fun plainText_emptyOrTagOnly_isEmpty() {
        assertThat(NoteContentText.plainText(null)).isEmpty()
        assertThat(NoteContentText.plainText("")).isEmpty()
        assertThat(NoteContentText.plainText("<p></p>")).isEmpty()
    }

    @Test
    fun isBlank_trueWhenTitleAndBodyHaveNoVisibleText() {
        assertThat(NoteContentText.isBlank("   ", "<p></p>")).isTrue()
        assertThat(NoteContentText.isBlank("", null)).isTrue()
    }

    @Test
    fun isBlank_falseWhenEitherTitleOrBodyHasText() {
        assertThat(NoteContentText.isBlank("Title", "<p></p>")).isFalse()
        assertThat(NoteContentText.isBlank("  ", "<p>body</p>")).isFalse()
    }
}
