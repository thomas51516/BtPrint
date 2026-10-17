package com.odootogo.btprint.print

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadsTest {

    private val bonjour = "Bonjour".toByteArray()

    @Test
    fun `decode base64 brut`() {
        assertArrayEquals(bonjour, Payloads.decodeBase64("Qm9uam91cg=="))
    }

    @Test
    fun `decode data URL`() {
        assertArrayEquals(bonjour, Payloads.decodeBase64("data:text/plain;base64,Qm9uam91cg=="))
    }

    @Test
    fun `tolere espaces et retours a la ligne`() {
        assertArrayEquals(bonjour, Payloads.decodeBase64("  Qm9u\nam91\r\ncg==  \n"))
    }

    @Test
    fun `rejette un contenu vide`() {
        assertThrows(IllegalArgumentException::class.java) { Payloads.decodeBase64("   ") }
        assertThrows(IllegalArgumentException::class.java) { Payloads.decodeBase64("data:image/png;base64,") }
    }

    @Test
    fun `rejette un base64 invalide`() {
        assertThrows(IllegalArgumentException::class.java) { Payloads.decodeBase64("%%%") }
    }

    @Test
    fun `lien base64 donne un travail brut`() {
        val job = Payloads.linkToJob("base64,G0A=") // ESC @
        assertTrue(job is PrintJob.Raw)
        assertArrayEquals(byteArrayOf(0x1B, 0x40), (job as PrintJob.Raw).bytes)
    }

    @Test
    fun `lien text donne du texte formate tel quel`() {
        val job = Payloads.linkToJob("text,[C]<b>Bonjour</b>\n")
        assertEquals(PrintJob.FormattedText("[C]<b>Bonjour</b>\n"), job)
    }

    @Test
    fun `lien sans prefixe est traite comme texte brut`() {
        val job = Payloads.linkToJob("Ligne 1\nLigne 2")
        assertEquals(PrintJob.FormattedText("[L]Ligne 1\n[L]Ligne 2\n"), job)
    }

    @Test
    fun `tolere la forme btprint avec deux barres`() {
        val job = Payloads.linkToJob("//text,[L]ok\n")
        assertEquals(PrintJob.FormattedText("[L]ok\n"), job)
    }

    @Test
    fun `lien vide est refuse`() {
        assertThrows(IllegalArgumentException::class.java) { Payloads.linkToJob("") }
        assertThrows(IllegalArgumentException::class.java) { Payloads.linkToJob("//") }
    }
}
