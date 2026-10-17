package com.odootogo.btprint.data

import android.content.Context
import androidx.core.content.edit

/** Largeur de papier : largeur imprimable (mm) et nombre de caractères par ligne (police A). */
enum class PaperWidth(val label: String, val widthMm: Float, val charsPerLine: Int, val widthPx: Int) {
    MM58("58 mm", 48f, 32, 384),
    MM80("80 mm", 72f, 48, 576)
}

/**
 * Table de caractères envoyée à l'imprimante (commande ESC t n).
 * Le code dépend du modèle : si les accents sortent mal, essayer une autre entrée.
 */
enum class PrinterCharset(val label: String, val javaName: String, val escPosCode: Int) {
    WPC1252("Windows-1252 (WPC1252)", "windows-1252", 16),
    PC858("PC858 (accents + €)", "IBM00858", 19),
    PC850("PC850 (multilingue)", "IBM850", 2)
}

class PrinterSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("printer_settings", Context.MODE_PRIVATE)

    var printerAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit { putString(KEY_ADDRESS, value) }

    var printerName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    var paperWidth: PaperWidth
        get() = enumOrDefault(prefs.getString(KEY_PAPER, null), PaperWidth.MM58)
        set(value) = prefs.edit { putString(KEY_PAPER, value.name) }

    var charset: PrinterCharset
        get() = enumOrDefault(prefs.getString(KEY_CHARSET, null), PrinterCharset.WPC1252)
        set(value) = prefs.edit { putString(KEY_CHARSET, value.name) }

    var autoCut: Boolean
        get() = prefs.getBoolean(KEY_CUT, true)
        set(value) = prefs.edit { putBoolean(KEY_CUT, value) }

    var serverEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVER, true)
        set(value) = prefs.edit { putBoolean(KEY_SERVER, value) }

    var serverPort: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) = prefs.edit { putInt(KEY_PORT, value) }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    companion object {
        const val DEFAULT_PORT = 8085
        private const val KEY_ADDRESS = "address"
        private const val KEY_NAME = "name"
        private const val KEY_PAPER = "paper"
        private const val KEY_CHARSET = "charset"
        private const val KEY_CUT = "auto_cut"
        private const val KEY_SERVER = "server_enabled"
        private const val KEY_PORT = "server_port"
    }
}
