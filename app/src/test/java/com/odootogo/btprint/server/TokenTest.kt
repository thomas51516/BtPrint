package com.odootogo.btprint.server

import com.odootogo.btprint.data.PrinterSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenTest {

    @Test
    fun `jeton identique accepte, espaces autour toleres`() {
        assertTrue(LocalPrintServer.tokenMatches("abc123", "abc123"))
        assertTrue(LocalPrintServer.tokenMatches("  abc123\n", "abc123"))
    }

    @Test
    fun `jeton different ou vide refuse`() {
        assertFalse(LocalPrintServer.tokenMatches("abc124", "abc123"))
        assertFalse(LocalPrintServer.tokenMatches("abc12", "abc123"))
        assertFalse(LocalPrintServer.tokenMatches("", "abc123"))
    }

    @Test
    fun `jeton genere a la bonne forme et change a chaque appel`() {
        val a = PrinterSettings.newToken()
        val b = PrinterSettings.newToken()
        assertEquals(24, a.length)
        assertTrue(a.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789" })
        assertNotEquals(a, b)
    }
}
