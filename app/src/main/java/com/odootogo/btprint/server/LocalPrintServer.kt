package com.odootogo.btprint.server

import android.content.Context
import android.net.Uri
import com.odootogo.btprint.data.PrinterSettings
import com.odootogo.btprint.print.Payloads
import com.odootogo.btprint.print.PrintJob
import com.odootogo.btprint.print.PrintQueue
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest

/**
 * Serveur HTTP sur 127.0.0.1 (jamais exposé au réseau) pour imprimer depuis
 * un navigateur ou une PWA (Odoo POS, etc.).
 *
 *   GET  /status        état de l'app et de l'imprimante
 *   POST /print/text    texte formaté DantSu (UTF-8)
 *   POST /print/raw     octets ESC/POS en base64
 *   POST /print/image   image (PNG/JPEG) en base64 ou data URL
 *   POST /print/pdf     PDF en base64 ou data URL
 *
 * Les routes POST exigent le jeton de l'app (réglages), transmis dans l'en-tête
 * `X-Print-Token` ou le paramètre `?token=`. Sans lui, n'importe quelle page ou
 * application du téléphone pourrait imprimer (et ouvrir un tiroir-caisse).
 */
class LocalPrintServer(
    private val context: Context,
    port: Int
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, ""))
        }
        val response = try {
            route(session)
        } catch (e: Exception) {
            json(Response.Status.BAD_REQUEST, ok = false, message = e.message ?: "Requête invalide")
        }
        return cors(response)
    }

    private fun route(session: IHTTPSession): Response {
        val path = session.uri.trimEnd('/').ifEmpty { "/" }

        if (session.method == Method.GET && (path == "/" || path == "/status")) return status()
        if (session.method != Method.POST) {
            return json(Response.Status.METHOD_NOT_ALLOWED, ok = false, message = "Méthode non autorisée")
        }
        if (!isAuthorized(session)) {
            return json(Response.Status.UNAUTHORIZED, ok = false, message = "Jeton manquant ou invalide (en-tête X-Print-Token)")
        }

        val body = readBody(session)
        val job = when (path) {
            "/print/text" -> PrintJob.FormattedText(String(body, Charsets.UTF_8))
            "/print/raw" -> PrintJob.Raw(decodeBase64(body))
            "/print/image" -> PrintJob.ImageFile(saveTemp(decodeBase64(body), "img"))
            "/print/pdf" -> PrintJob.PdfFile(saveTemp(decodeBase64(body), "pdf"))
            else -> return json(Response.Status.NOT_FOUND, ok = false, message = "Route inconnue : $path")
        }

        PrintQueue.submit(context, job)
        return json(Response.Status.ACCEPTED, ok = true, message = "Travail ajouté à la file")
    }

    private fun status(): Response {
        val settings = PrinterSettings(context)
        val payload = JSONObject()
            .put("ok", true)
            .put("app", "BT Print")
            .put("printer", settings.printerName ?: JSONObject.NULL)
            .put("address", settings.printerAddress ?: JSONObject.NULL)
            .put("paper", settings.paperWidth.label)
            .put("widthPx", settings.paperWidth.widthPx)
            .put("tokenRequired", settings.tokenRequired)
            .put("status", PrintQueue.status.value)
        return newFixedLengthResponse(Response.Status.OK, "application/json", payload.toString())
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val settings = PrinterSettings(context)
        if (!settings.tokenRequired) return true
        val provided = session.headers[TOKEN_HEADER.lowercase()]
            ?: session.parameters["token"]?.firstOrNull()
            ?: return false
        return tokenMatches(provided, settings.serverToken)
    }

    private fun readBody(session: IHTTPSession): ByteArray {
        val length = session.headers["content-length"]?.toLongOrNull()
            ?: throw IllegalArgumentException("En-tête Content-Length manquant")
        require(length in 1..MAX_BODY_BYTES) { "Taille du corps invalide ($length octets)" }
        val buffer = ByteArray(length.toInt())
        DataInputStream(session.inputStream).readFully(buffer)
        return buffer
    }

    private fun decodeBase64(body: ByteArray): ByteArray =
        Payloads.decodeBase64(String(body, Charsets.US_ASCII))

    private fun saveTemp(bytes: ByteArray, extension: String): Uri {
        val file = File(context.cacheDir, "job_${System.nanoTime()}.$extension")
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun json(status: Response.Status, ok: Boolean, message: String): Response =
        newFixedLengthResponse(
            status, "application/json",
            JSONObject().put("ok", ok).put("message", message).toString()
        )

    /** CORS + Private Network Access : autorise les appels depuis une page HTTPS (ex. Odoo). */
    private fun cors(response: Response): Response = response.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Content-Type, $TOKEN_HEADER")
        addHeader("Access-Control-Allow-Private-Network", "true")
        addHeader("Access-Control-Max-Age", "600")
    }

    companion object {
        const val TOKEN_HEADER = "X-Print-Token"
        private const val MAX_BODY_BYTES = 20L * 1024 * 1024 // 20 Mo

        /** Comparaison en temps constant (évite de deviner le jeton octet par octet). */
        fun tokenMatches(provided: String, expected: String): Boolean =
            MessageDigest.isEqual(provided.trim().toByteArray(), expected.toByteArray())
    }
}
