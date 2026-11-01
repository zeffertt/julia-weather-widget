package uk.zeffertt.juliaweather

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser

/**
 * Draws Julia's panel exactly as on the HA floorplan overlay.
 * Coordinates are the overlay's own SVG units (panel spans x 32-370, y 12-195).
 */
object Render {
    const val W = 338f
    const val H = 183f

    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val aa = Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG

    private fun c(hex: String) = Color.parseColor(hex)
    private fun path(d: String): Path = PathParser.createPathFromPathData(d)

    fun bitmap(o: Out, maxWpx: Int, maxHpx: Int): Bitmap {
        val s = minOf(maxWpx / W, maxHpx / H).coerceAtLeast(0.5f)
        val bmp = Bitmap.createBitmap((W * s).toInt(), (H * s).toInt(), Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.scale(s, s)
        cv.translate(-32f, -12f)
        draw(cv, o)
        return bmp
    }

    fun draw(cv: Canvas, o: Out) {
        val panel = RectF(32f, 12f, 370f, 195f)
        val clip = Path().apply { addRoundRect(panel, 16f, 16f, Path.Direction.CW) }
        cv.save()
        cv.clipPath(clip)
        cv.drawRoundRect(panel, 16f, 16f, Paint(aa).apply { color = c("#fffafb") })
        blossom(cv)
        cv.restore()
        cv.drawRoundRect(RectF(32.5f, 12.5f, 369.5f, 194.5f), 15.5f, 15.5f,
            Paint(aa).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = c("#f1d3dc") })

        icon(cv, o.icon)
        text(cv, "Julia", 76f, 38f, 14f, "#c25a7c", medium)

        // Header lines (HA state-labels, vertically centred)
        textMid(cv, o.day, 354f, 34f, 11f, "#a8909a", regular, Paint.Align.RIGHT)
        textMid(cv, o.temp, 48f, 51f, 13f, "#4a3a40", regular, Paint.Align.LEFT)
        textMid(cv, o.summary, 354f, 51f, 12f, "#4a3a40", regular, Paint.Align.RIGHT)

        // Key-time boxes
        box(cv, 48f, 97f, "Before 9:45", o.m, o.mt)
        box(cv, 152f, 201f, "Lunch", o.l, o.lt)
        box(cv, 256f, 305f, "After 18:00", o.e, o.et)

        // Verdict banner + rain line
        val (vf, vt, vtxt) = when (o.verdict) {
            "raincoat" -> Triple("#e6ebfb", "#3d5fa8", "Wear the long raincoat")
            "umbrella" -> Triple("#fdf0dc", "#a0661a", "Take an umbrella")
            else -> Triple("#e6f5ee", "#2f7a55", "No rain at your times")
        }
        cv.drawRoundRect(RectF(48f, 92f, 354f, 119f), 10f, 10f, Paint(aa).apply { color = c(vf) })
        text(cv, vtxt, 201f, 103.5f, 12f, vt, medium, Paint.Align.CENTER)
        textMid(cv, o.rain, 201f, 112.5f, 11f, "#7a6870", regular, Paint.Align.CENTER)

        // Outfit
        text(cv, "Bottom", 48f, 136.5f, 10f, "#c27a93", regular)
        text(cv, "Top", 48f, 163.5f, 10f, "#c27a93", regular)
        text(cv, "Extras", 48f, 182.5f, 10f, "#c27a93", regular)
        wrapped(cv, o.bottom, 96f, 133f)
        wrapped(cv, o.top, 96f, 160f)
        wrapped(cv, o.extras, 96f, 179f)
    }

    private fun box(cv: Canvas, x: Float, cx: Float, label: String, state: String, txt: String) {
        val (fill, lc) = when (state) {
            "Showers" -> "#e6effb" to "#5b7fb8"
            "Rain" -> "#d6e4f8" to "#4a72ad"
            else -> "#fdf0f3" to "#a8909a"
        }
        cv.drawRoundRect(RectF(x, 60f, x + 98f, 88f), 10f, 10f, Paint(aa).apply { color = c(fill) })
        text(cv, label, cx, 70.5f, 9f, lc, regular, Paint.Align.CENTER)
        textMid(cv, txt, cx, 80.5f, 12f, "#4a3a40", medium, Paint.Align.CENTER)
    }

    private fun paint(size: Float, hex: String, tf: Typeface, align: Paint.Align) = Paint(aa).apply {
        textSize = size; color = c(hex); typeface = tf; textAlign = align
    }

    private fun text(cv: Canvas, s: String, x: Float, baseline: Float, size: Float, hex: String, tf: Typeface,
                     align: Paint.Align = Paint.Align.LEFT) {
        cv.drawText(s, x, baseline, paint(size, hex, tf, align))
    }

    private fun textMid(cv: Canvas, s: String, x: Float, cy: Float, size: Float, hex: String, tf: Typeface,
                        align: Paint.Align) {
        val p = paint(size, hex, tf, align)
        val fm = p.fontMetrics
        cv.drawText(s, x, cy - (fm.ascent + fm.descent) / 2, p)
    }

    /** 12-unit text in a 258-wide column, 14-unit lines, centred on cy (as HA's label). */
    private fun wrapped(cv: Canvas, s: String, x: Float, cy: Float) {
        val p = paint(12f, "#4a3a40", regular, Paint.Align.LEFT)
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in s.split(" ")) {
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (p.measureText(t) <= 258f || cur.isEmpty()) cur = t else { lines += cur; cur = w }
        }
        if (cur.isNotEmpty()) lines += cur
        val fm = p.fontMetrics
        val top = cy - lines.size * 14f / 2
        lines.forEachIndexed { i, l -> cv.drawText(l, x, top + i * 14f + 7f - (fm.ascent + fm.descent) / 2, p) }
    }

    private fun flower(cv: Canvas, x: Float, y: Float, rot: Float, alpha: Float) {
        cv.save()
        cv.translate(x, y); cv.rotate(rot)
        val n = cv.saveLayerAlpha(-12f, -12f, 12f, 12f, (alpha * 255).toInt())
        val petal = Paint(aa).apply { color = c("#f7c6d4") }
        for ((px, py) in listOf(0f to -5f, 4.8f to -1.5f, 3f to 4f, -3f to 4f, -4.8f to -1.5f)) cv.drawCircle(px, py, 4.2f, petal)
        cv.drawCircle(0f, 0f, 2f, Paint(aa).apply { color = c("#e88aa6") })
        cv.restoreToCount(n)
        cv.restore()
    }

    private fun stem(cv: Canvas, d: String, width: Float, alpha: Float) {
        cv.drawPath(path(d), Paint(aa).apply {
            style = Paint.Style.STROKE; strokeWidth = width; color = c("#c9a28f"); this.alpha = (alpha * 255).toInt()
        })
    }

    private fun blossom(cv: Canvas) {
        stem(cv, "M372 22 C352 18 330 12 306 6", 1.6f, 0.7f)
        stem(cv, "M334 13 c-3 -4 -1 -8 3 -9", 1.2f, 0.7f)
        flower(cv, 362f, 16f, 0f, 0.85f)
        flower(cv, 340f, 10f, 20f, 0.7f)
        flower(cv, 318f, 6f, 0f, 0.55f)
        cv.drawCircle(351f, 23f, 2.5f, Paint(aa).apply { color = c("#f7c6d4"); alpha = (0.8f * 255).toInt() })
        stem(cv, "M370 168 C356 176 344 186 338 195", 1.6f, 0.6f)
        flower(cv, 358f, 176f, 0f, 0.6f)
        flower(cv, 343f, 188f, 30f, 0.45f)
    }

    private fun fill(cv: Canvas, d: String, hex: String) = cv.drawPath(path(d), Paint(aa).apply { color = c(hex) })

    private fun lines(cv: Canvas, hex: String, w: Float, vararg l: Float) {
        val p = Paint(aa).apply { color = c(hex); strokeWidth = w; strokeCap = Paint.Cap.ROUND }
        var i = 0
        while (i < l.size) { cv.drawLine(l[i], l[i + 1], l[i + 2], l[i + 3], p); i += 4 }
    }

    private fun icon(cv: Canvas, name: String) {
        cv.save()
        cv.translate(47f, 22f)
        when (name) {
            "sunny" -> {
                cv.drawCircle(12f, 11f, 5.5f, Paint(aa).apply { color = c("#ffcf6b") })
                lines(cv, "#ffcf6b", 1.6f, 12f, 1f, 12f, 3f, 12f, 19f, 12f, 21f, 2f, 11f, 4f, 11f, 20f, 11f, 22f, 11f,
                    5f, 4f, 6.4f, 5.4f, 19f, 4f, 17.6f, 5.4f)
            }
            "spells" -> {
                cv.drawCircle(12f, 10f, 5f, Paint(aa).apply { color = c("#ffcf6b") })
                lines(cv, "#ffcf6b", 1.6f, 12f, 1f, 12f, 3f, 3f, 10f, 5f, 10f, 5.6f, 3.6f, 7f, 5f, 18.4f, 3.6f, 17f, 5f)
                fill(cv, "M8 20a5 5 0 0 1 1-9.8 6 6 0 0 1 11 2.2A4 4 0 0 1 20 20z", "#dfe8f5")
            }
            "rain" -> {
                fill(cv, "M5 14a5 5 0 0 1 1-9.8 6 6 0 0 1 11 2.2A4 4 0 0 1 17 14z", "#c9d3e3")
                lines(cv, "#7fa6dd", 1.8f, 8f, 17f, 7f, 20f, 12f, 17f, 11f, 20f, 16f, 17f, 15f, 20f)
            }
            "storm" -> {
                fill(cv, "M5 14a5 5 0 0 1 1-9.8 6 6 0 0 1 11 2.2A4 4 0 0 1 17 14z", "#b8c2d4")
                cv.drawPath(path("M12 14l-3 4h3l-2 4"), Paint(aa).apply {
                    style = Paint.Style.STROKE; strokeWidth = 1.8f; color = c("#f2b84b")
                    strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                })
            }
            "snow" -> {
                fill(cv, "M5 14a5 5 0 0 1 1-9.8 6 6 0 0 1 11 2.2A4 4 0 0 1 17 14z", "#c9d3e3")
                val p = Paint(aa).apply { color = c("#9fbde6") }
                cv.drawCircle(8f, 18f, 1.4f, p); cv.drawCircle(12f, 20f, 1.4f, p); cv.drawCircle(16f, 18f, 1.4f, p)
            }
            "fog" -> lines(cv, "#b8c2d4", 2f, 4f, 7f, 20f, 7f, 2f, 12f, 18f, 12f, 6f, 17f, 22f, 17f)
            "night" -> fill(cv, "M15 3a8 8 0 1 0 6 12A7 7 0 0 1 15 3z", "#c7bfe6")
            else -> fill(cv, "M5 18a5 5 0 0 1 1-9.8 6 6 0 0 1 11 2.2A4 4 0 0 1 17 18z", "#c9d3e3")
        }
        cv.restore()
    }
}
