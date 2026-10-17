package com.odootogo.btprint.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.odootogo.btprint.data.PaperWidth

/** Contenus de test : ticket texte (accents, alignement, codes) et image (tramage). */
object TestReceipts {

    fun text(paper: PaperWidth): String {
        val equals = "=".repeat(paper.charsPerLine)
        val dashes = "-".repeat(paper.charsPerLine)
        return buildString {
            append("[C]<b><font size='big'>ODOO TOGO</font></b>\n")
            append("[L]\n")
            append("[C]Test d'impression Bluetooth\n")
            append("[C]Papier ${paper.label}\n")
            append("[C]$equals\n")
            append("[L]<b>Café crème</b>[R]1 500 FCFA\n")
            append("[L]Thé à la menthe[R]1 000 FCFA\n")
            append("[L]Gâteau (x2)[R]2 000 FCFA\n")
            append("[C]$dashes\n")
            append("[L]<b>TOTAL</b>[R]<b>4 500 FCFA</b>\n")
            append("[L]\n")
            append("[L]Accents : é è ê à â ç ù ô î €\n")
            append("[L]\n")
            append("[C]<barcode type='128' height='10'>OT-0001</barcode>\n")
            append("[L]\n")
            append("[C]<qrcode size='20'>https://www.odoo.com</qrcode>\n")
        }
    }

    fun image(paper: PaperWidth): Bitmap {
        val w = paper.widthPx
        val h = w * 3 / 4
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Cercle en dégradé : permet de vérifier le tramage
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w / 2f, h * 0.42f, h * 0.32f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(w / 2f, h * 0.42f, h * 0.32f, gradient)

        val border = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.BLACK
        }
        canvas.drawRect(2f, 2f, w - 2f, h - 2f, border)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = w / 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Test image — ${paper.label}", w / 2f, h - h * 0.08f, text)
        return bitmap
    }
}
