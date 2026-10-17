package com.odootogo.btprint.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterCharsetTest {

    @Test
    fun `la table par defaut est toujours disponible`() {
        assertTrue(PrinterCharset.DEFAULT.isAvailable)
        assertTrue(PrinterCharset.DEFAULT in PrinterCharset.available)
    }

    @Test
    fun `les codes ESC t correspondent a la norme Epson`() {
        assertEquals(16, PrinterCharset.WPC1252.escPosCode)
        assertEquals(19, PrinterCharset.PC858.escPosCode)
        assertEquals(2, PrinterCharset.PC850.escPosCode)
    }
}
