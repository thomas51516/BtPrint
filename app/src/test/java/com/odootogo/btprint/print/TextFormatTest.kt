package com.odootogo.btprint.print

import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatTest {

    @Test
    fun `prefixe chaque ligne et termine par un retour`() {
        assertEquals("[L]a\n[L]b\n", TextFormat.plain("a\nb"))
    }

    @Test
    fun `normalise les fins de ligne Windows`() {
        assertEquals("[L]a\n[L]b\n", TextFormat.plain("a\r\nb"))
    }

    @Test
    fun `conserve les lignes vides`() {
        assertEquals("[L]a\n[L]\n[L]b\n", TextFormat.plain("a\n\nb"))
    }

    @Test
    fun `texte vide donne une ligne vide`() {
        assertEquals("[L]\n", TextFormat.plain(""))
    }
}
