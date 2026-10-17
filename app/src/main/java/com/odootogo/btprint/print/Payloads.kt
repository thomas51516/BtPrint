package com.odootogo.btprint.print

import java.util.Base64

/** Décodage des corps de requête et des liens : logique pure, testable sur la JVM. */
object Payloads {

    /**
     * Décode du base64 brut ou une data URL (`data:image/png;base64,....`).
     * Tolère les retours à la ligne et les espaces, mais refuse tout autre caractère
     * (le décodeur MIME de Java les ignorerait en silence et renverrait des octets tronqués).
     */
    fun decodeBase64(text: String): ByteArray {
        val trimmed = text.trim()
        val payload = (if (trimmed.contains("base64,")) trimmed.substringAfter("base64,") else trimmed)
            .filterNot { it.isWhitespace() }
        require(payload.isNotEmpty()) { "Contenu base64 vide" }
        return try {
            Base64.getDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Base64 invalide", e)
        }
    }

    /**
     * Convertit la partie après `btprint:` en travail d'impression.
     *   base64,<octets ESC/POS en base64>   → [PrintJob.Raw]
     *   text,<texte formaté DantSu>         → [PrintJob.FormattedText]
     *   <autre texte>                       → texte brut, une ligne par ligne
     * Le `//` d'un éventuel `btprint://` est ignoré.
     */
    fun linkToJob(schemeSpecificPart: String): PrintJob {
        val content = schemeSpecificPart.removePrefix("//")
        return when {
            content.startsWith("base64,") -> PrintJob.Raw(decodeBase64(content.removePrefix("base64,")))
            content.startsWith("text,") -> PrintJob.FormattedText(content.removePrefix("text,"))
            content.isNotBlank() -> PrintJob.FormattedText(TextFormat.plain(content))
            else -> throw IllegalArgumentException("Lien vide")
        }
    }
}
