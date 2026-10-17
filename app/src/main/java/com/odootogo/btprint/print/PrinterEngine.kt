package com.odootogo.btprint.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.dantsu.escposprinter.EscPosCharsetEncoding
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.odootogo.btprint.data.PrinterSettings
import kotlin.math.min

class PrintException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Envoie un [PrintJob] à l'imprimante Bluetooth sélectionnée.
 * Appel bloquant : à exécuter hors du thread principal.
 */
class PrinterEngine(private val context: Context) {

    private val settings = PrinterSettings(context)

    fun print(job: PrintJob) {
        when (job) {
            is PrintJob.FormattedText -> withPrinter { printer ->
                if (settings.autoCut) printer.printFormattedTextAndCut(job.text, FEED_MM)
                else printer.printFormattedText(job.text, FEED_MM)
            }

            is PrintJob.Raw -> printRaw(job.bytes)

            is PrintJob.Image -> withPrinter { printer ->
                printBitmap(printer, job.bitmap)
                finish(printer)
            }

            is PrintJob.ImageFile -> {
                val bitmap = ImageUtils.decode(context, job.uri)
                withPrinter { printer ->
                    printBitmap(printer, bitmap)
                    finish(printer)
                }
            }

            is PrintJob.PdfFile -> withPrinter { printer ->
                ImageUtils.renderPdfPages(context, job.uri, printer.printerWidthPx) { page ->
                    printBitmap(printer, ImageUtils.trimVertical(page))
                }
                finish(printer)
            }
        }
    }

    // --- Connexion ----------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // vérifié juste en dessous
    private fun openConnection(): BluetoothConnection {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw PrintException("Permission Bluetooth non accordée")
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw PrintException("Bluetooth non disponible sur cet appareil")
        if (!adapter.isEnabled) throw PrintException("Le Bluetooth est désactivé")

        val address = settings.printerAddress
            ?: throw PrintException("Aucune imprimante sélectionnée")
        val device = adapter.bondedDevices.firstOrNull { it.address == address }
            ?: throw PrintException("Imprimante non appairée ($address)")

        return BluetoothConnection(device)
    }

    private fun charsetEncoding(): EscPosCharsetEncoding {
        // settings.charset garantit une table disponible (repli cohérent table + code ESC/POS)
        val cs = settings.charset
        return EscPosCharsetEncoding(cs.javaName, cs.escPosCode)
    }

    /** Ouvre la connexion, exécute [block], puis ferme toujours la connexion. */
    private inline fun withPrinter(block: (EscPosPrinter) -> Unit) {
        val paper = settings.paperWidth
        val printer = try {
            // Le constructeur établit la connexion Bluetooth
            EscPosPrinter(openConnection(), PRINTER_DPI, paper.widthMm, paper.charsPerLine, charsetEncoding())
        } catch (e: PrintException) {
            throw e
        } catch (e: Exception) {
            throw PrintException("Connexion impossible : ${e.message ?: "imprimante éteinte ou hors de portée ?"}", e)
        }
        try {
            block(printer)
        } finally {
            printer.disconnectPrinter()
        }
    }

    // --- Impression ---------------------------------------------------------------------------

    private fun printRaw(bytes: ByteArray) {
        val connection = openConnection()
        try {
            connection.connect()
            connection.write(bytes)
            connection.send()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Imprime une image en bandes de [IMAGE_CHUNK_HEIGHT] pixels :
     * les imprimantes thermiques ont un petit buffer et décrochent sur les grandes images.
     */
    private fun printBitmap(printer: EscPosPrinter, source: Bitmap) {
        val bitmap = ImageUtils.prepareForPrinter(source, printer.printerWidthPx)
        var y = 0
        while (y < bitmap.height) {
            val h = min(IMAGE_CHUNK_HEIGHT, bitmap.height - y)
            val band = Bitmap.createBitmap(bitmap, 0, y, bitmap.width, h)
            val hex = PrinterTextParserImg.bitmapToHexadecimalString(printer, band)
            printer.printFormattedText("[C]<img>$hex</img>\n", 0f)
            y += h
        }
    }

    /** Avance le papier et coupe (si activé) après une image ou un PDF. */
    private fun finish(printer: EscPosPrinter) {
        if (settings.autoCut) printer.printFormattedTextAndCut("[L]\n", FEED_MM)
        else printer.printFormattedText("[L]\n", FEED_MM)
    }

    private companion object {
        const val PRINTER_DPI = 203          // valeur standard des imprimantes thermiques
        const val IMAGE_CHUNK_HEIGHT = 256
        const val FEED_MM = 15f
    }
}
