package com.odootogo.btprint.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import kotlin.math.max
import kotlin.math.min

object ImageUtils {

    /** Décode une image (content://, file://) en bitmap logiciel lisible pixel par pixel. */
    fun decode(context: Context, uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // Android 8 / 8.1 : BitmapFactory produit déjà un bitmap logiciel
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw PrintException("Image illisible")
        }

    /** Redimensionne à la largeur de la tête d'impression, sur fond blanc (gère la transparence). */
    fun fitWidth(src: Bitmap, targetWidth: Int): Bitmap {
        val height = max(1, (src.height.toLong() * targetWidth / src.width).toInt())
        val out = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(src, null, Rect(0, 0, targetWidth, height), paint)
        return out
    }

    /**
     * Tramage Floyd–Steinberg : convertit en noir et blanc pur en gardant
     * l'illusion des niveaux de gris (logos, photos, dégradés).
     */
    fun dither(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val lum = FloatArray(w * h) { i ->
            val c = pixels[i]
            0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = lum[i]
                val new = if (old < 128f) 0f else 255f
                val err = old - new
                pixels[i] = if (new == 0f) Color.BLACK else Color.WHITE

                if (x + 1 < w) lum[i + 1] += err * 7f / 16f
                if (y + 1 < h) {
                    if (x > 0) lum[i + w - 1] += err * 3f / 16f
                    lum[i + w] += err * 5f / 16f
                    if (x + 1 < w) lum[i + w + 1] += err * 1f / 16f
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun prepareForPrinter(src: Bitmap, widthPx: Int): Bitmap = dither(fitWidth(src, widthPx))

    /**
     * Retire les bandes blanches en haut et en bas (utile pour les PDF A4
     * qui ne contiennent qu'un petit ticket).
     */
    fun trimVertical(src: Bitmap, margin: Int = 8): Bitmap {
        val w = src.width
        val h = src.height
        val row = IntArray(w)

        fun rowHasInk(y: Int): Boolean {
            src.getPixels(row, 0, w, 0, y, w, 1)
            return row.any { c ->
                Color.alpha(c) > 0 && (Color.red(c) + Color.green(c) + Color.blue(c)) / 3 < 200
            }
        }

        var top = 0
        while (top < h && !rowHasInk(top)) top++
        if (top == h) return Bitmap.createBitmap(src, 0, 0, w, min(h, 1))

        var bottom = h - 1
        while (bottom > top && !rowHasInk(bottom)) bottom--

        val start = max(0, top - margin)
        val end = min(h, bottom + 1 + margin)
        return Bitmap.createBitmap(src, 0, start, w, end - start)
    }

    /** Rend chaque page du PDF à la largeur de l'imprimante et la passe à [onPage]. */
    fun renderPdfPages(context: Context, uri: Uri, widthPx: Int, onPage: (Bitmap) -> Unit) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw PrintException("PDF illisible")
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val height = max(1, (page.height.toLong() * widthPx / page.width).toInt())
                        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        onPage(bitmap)
                    }
                }
            }
        }
    }
}
