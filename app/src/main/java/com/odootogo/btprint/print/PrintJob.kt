package com.odootogo.btprint.print

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/** Un travail d'impression placé dans la file. */
sealed interface PrintJob {
    val label: String

    /** Supprime les fichiers temporaires éventuels une fois le travail terminé. */
    fun cleanup() {}

    /** Texte au format DantSu : [L] [C] [R], <b>, <font size='big'>, <qrcode>, <barcode>... */
    data class FormattedText(val text: String) : PrintJob {
        override val label = "Texte"
    }

    /** Octets ESC/POS envoyés tels quels à l'imprimante. */
    class Raw(val bytes: ByteArray) : PrintJob {
        override val label = "ESC/POS brut"
    }

    /** Image déjà en mémoire. */
    class Image(val bitmap: Bitmap) : PrintJob {
        override val label = "Image"
    }

    /** Image stockée dans un fichier (PNG, JPEG, WebP...). */
    data class ImageFile(val uri: Uri) : PrintJob {
        override val label = "Image"
        override fun cleanup() = deleteIfTemp(uri)
    }

    /** Document PDF : chaque page est rendue en image puis imprimée. */
    data class PdfFile(val uri: Uri) : PrintJob {
        override val label = "PDF"
        override fun cleanup() = deleteIfTemp(uri)
    }
}

private fun deleteIfTemp(uri: Uri) {
    if (uri.scheme == "file") uri.path?.let { File(it).delete() }
}

/** Convertit du texte brut en texte formaté DantSu (une ligne alignée à gauche par ligne). */
object TextFormat {
    fun plain(text: String): String =
        text.replace("\r\n", "\n")
            .lines()
            .joinToString("\n", postfix = "\n") { "[L]$it" }
}
