package com.odootogo.btprint.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.odootogo.btprint.print.PrintJob
import com.odootogo.btprint.print.PrintQueue
import com.odootogo.btprint.print.TextFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activité sans interface : reçoit un partage (PDF, image, texte), un "Ouvrir avec" PDF
 * ou un lien btprint:..., met le travail en file puis se ferme.
 *
 * Liens pris en charge :
 *   btprint:base64,<octets ESC/POS en base64>
 *   btprint:text,<texte formaté DantSu, encodé en URL>
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            toast("Ouvrez BT Print pour autoriser le Bluetooth")
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        val incoming = intent
        lifecycleScope.launch {
            // Copie immédiate des fichiers : l'accès temporaire à l'URI expire avec l'activité
            val result = runCatching { withContext(Dispatchers.IO) { buildJob(incoming) } }
            result
                .onSuccess { job ->
                    PrintQueue.submit(applicationContext, job)
                    toast("Envoyé à l'imprimante")
                }
                .onFailure { toast("Impression impossible : ${it.message}") }
            finish()
        }
    }

    private fun buildJob(intent: Intent): PrintJob = when (intent.action) {
        Intent.ACTION_SEND -> fromShare(intent)
        Intent.ACTION_VIEW -> fromView(intent)
        else -> throw IllegalArgumentException("Action non prise en charge")
    }

    private fun fromShare(intent: Intent): PrintJob {
        val type = intent.type.orEmpty()
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        return when {
            stream == null && type.startsWith("text/") -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: throw IllegalArgumentException("Texte vide")
                PrintJob.FormattedText(TextFormat.plain(text))
            }
            stream == null -> throw IllegalArgumentException("Aucun fichier reçu")
            type == "application/pdf" -> PrintJob.PdfFile(copyToCache(stream, "pdf"))
            type.startsWith("image/") -> PrintJob.ImageFile(copyToCache(stream, "img"))
            type.startsWith("text/") -> PrintJob.FormattedText(TextFormat.plain(readText(stream)))
            else -> throw IllegalArgumentException("Type non pris en charge : $type")
        }
    }

    private fun fromView(intent: Intent): PrintJob {
        val data = intent.data ?: throw IllegalArgumentException("Aucune donnée")
        if (data.scheme == "btprint") return fromScheme(data)

        val type = intent.type ?: contentResolver.getType(data)
        if (type == "application/pdf") return PrintJob.PdfFile(copyToCache(data, "pdf"))
        throw IllegalArgumentException("Type non pris en charge : $type")
    }

    private fun fromScheme(data: Uri): PrintJob {
        // getSchemeSpecificPart() décode déjà les %XX ; on tolère btprint://...
        val content = data.schemeSpecificPart.orEmpty().removePrefix("//")
        return when {
            content.startsWith("base64,") ->
                PrintJob.Raw(Base64.decode(content.removePrefix("base64,"), Base64.DEFAULT))
            content.startsWith("text,") ->
                PrintJob.FormattedText(content.removePrefix("text,"))
            content.isNotBlank() -> PrintJob.FormattedText(TextFormat.plain(content))
            else -> throw IllegalArgumentException("Lien vide")
        }
    }

    private fun copyToCache(uri: Uri, extension: String): Uri {
        val file = File(cacheDir, "job_${System.nanoTime()}.$extension")
        val input = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Fichier illisible")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return Uri.fromFile(file)
    }

    private fun readText(uri: Uri): String =
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalArgumentException("Fichier illisible")

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
