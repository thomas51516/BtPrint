package com.odootogo.btprint.service

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import com.odootogo.btprint.data.PaperWidth
import com.odootogo.btprint.data.PrinterSettings
import com.odootogo.btprint.print.PrintQueue
import com.odootogo.btprint.print.PrinterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import com.odootogo.btprint.print.PrintJob as BtJob

/**
 * Service d'impression **système** : fait apparaître BT Print dans la fenêtre « Imprimer »
 * d'Android (Chrome, lecteurs PDF, Odoo dans le navigateur…), comme RawBT.
 *
 * L'utilisateur doit l'activer une fois dans *Paramètres → Impression*. Le système nous
 * remet ensuite chaque document sous forme de PDF, déjà mis en page à la largeur du ticket,
 * que l'on imprime via la chaîne PDF habituelle (rendu, rognage des blancs, tramage).
 */
class SystemPrintService : PrintService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var engine: PrinterEngine

    override fun onCreate() {
        super.onCreate()
        engine = PrinterEngine(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession = Session()

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        // Une fois l'envoi Bluetooth commencé, on ne peut plus l'interrompre proprement.
        if (printJob.isQueued) printJob.cancel()
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        val label = printJob.info.label.ifBlank { "Document" }
        if (!printJob.start()) return

        // Le descripteur n'est valable que pendant ce rappel : copie immédiate dans le cache.
        val file = runCatching { copyToCache(printJob) }.getOrElse { e ->
            Log.e(TAG, "Lecture du document impossible", e)
            printJob.fail("Document illisible : ${e.message}")
            return
        }

        PrintQueue.setStatus("Impression système : $label…")
        scope.launch {
            val job = BtJob.PdfFile(Uri.fromFile(file))
            val error = try {
                engine.print(job)
                null
            } catch (e: Throwable) {
                Log.e(TAG, "Échec d'impression système", e)
                e.message ?: e.javaClass.simpleName
            } finally {
                job.cleanup()
            }
            PrintQueue.setStatus(if (error == null) "Imprimé : $label" else "Échec ($label) : $error")
            mainHandler.post {
                if (error == null) printJob.complete() else printJob.fail(error)
            }
        }
    }

    private fun copyToCache(printJob: PrintJob): File {
        val pfd = printJob.document.data ?: throw IllegalStateException("Document vide")
        val file = File(cacheDir, "sysprint_${System.nanoTime()}.pdf")
        pfd.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        return file
    }

    /**
     * Une seule « imprimante » système : celle sélectionnée dans BT Print.
     * Ses formats de papier reflètent la largeur configurée (58 / 80 mm).
     */
    private inner class Session : PrinterDiscoverySession() {

        override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
            val settings = PrinterSettings(this@SystemPrintService)
            val address = settings.printerAddress ?: return
            val name = settings.printerName ?: address
            val id = generatePrinterId(address)
            addPrinters(listOf(printerInfo(id, name, settings.paperWidth)))
        }

        override fun onStopPrinterDiscovery() {}
        override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {}
        override fun onStartPrinterStateTracking(printerId: PrinterId) {}
        override fun onStopPrinterStateTracking(printerId: PrinterId) {}
        override fun onDestroy() {}

        private fun printerInfo(id: PrinterId, name: String, paper: PaperWidth): PrinterInfo {
            val caps = PrinterCapabilitiesInfo.Builder(id)
                .addResolution(PrintAttributes.Resolution("203dpi", "203 dpi", DPI, DPI), true)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            // Format 1:1 (largeur utile réelle) puis des formats « réduits » : la page est
            // déclarée plus large (les reçus web, Odoo compris, font souvent ~80 mm) ; l'app
            // rogne ensuite les marges blanches et réduit à la largeur de tête à l'impression.
            val layouts = buildList {
                add(paper.widthMm to "")
                REDUCED_LAYOUTS_MM.filter { it > paper.widthMm }
                    .forEach { wide -> add(wide.toFloat() to " (réduit depuis $wide mm)") }
            }
            layouts.forEach { (layoutWidthMm, suffix) ->
                TICKET_LENGTHS_MM.forEach { lengthMm ->
                    val isDefault = suffix.isEmpty() && lengthMm == DEFAULT_LENGTH_MM
                    caps.addMediaSize(mediaSize(paper, layoutWidthMm, lengthMm, suffix), isDefault)
                }
            }
            return PrinterInfo.Builder(id, name, PrinterInfo.STATUS_IDLE)
                .setDescription("Bluetooth ESC/POS · ${paper.label}")
                .setCapabilities(caps.build())
                .build()
        }

        private fun mediaSize(paper: PaperWidth, layoutWidthMm: Float, lengthMm: Int, suffix: String) =
            PrintAttributes.MediaSize(
                "btprint_${paper.name}_${layoutWidthMm.toInt()}_$lengthMm",
                "Ticket ${paper.label}$suffix × $lengthMm mm",
                mmToMils(layoutWidthMm),
                mmToMils(lengthMm.toFloat())
            )

        private fun mmToMils(mm: Float): Int = (mm * 1000f / 25.4f).toInt()
    }

    private companion object {
        const val TAG = "SystemPrintService"
        const val DPI = 203
        val TICKET_LENGTHS_MM = listOf(100, 200, 300)
        val REDUCED_LAYOUTS_MM = listOf(80, 100)
        const val DEFAULT_LENGTH_MM = 200
    }
}
