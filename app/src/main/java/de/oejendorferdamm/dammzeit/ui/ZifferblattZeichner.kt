package de.oejendorferdamm.dammzeit.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Hintergrund
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Zeichnet das Zifferblatt im Stil des Time Timers: helle Scheibe, 60 Striche rundherum, die
 * Zahlen gegen den Uhrzeigersinn (0 oben, dann 5, 10 … nach links) und eine farbige Scheibe,
 * die in Echtzeit zur 0 hin schrumpft.
 *
 * Mit normalem android.graphics gezeichnet, damit App (Compose) und Widgets (Bild) exakt
 * dasselbe Zifferblatt verwenden.
 */
object ZifferblattZeichner {

    private val scheibe = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scheibenRand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sektor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.BUTT }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.rgb(0x22, 0x22, 0x22)
    }
    private val knopf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x1E, 0x1E) }
    private val nullLinie = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB8, 0xBB, 0xC0) }
    private val pfad = Path()
    private val rechteck = RectF()

    /** Farben eines Erscheinungsbilds (Hintergrund, Scheibe, Striche, Schrift). */
    private class Stil(val flaeche: Int, val scheibeInnen: Int, val scheibeMitte: Int, val scheibeRand: Int, val strichLang: Int, val strichKurz: Int, val schrift: Int, val knopf: Int)

    private fun stil(h: Hintergrund): Stil = when (h) {
        Hintergrund.HELL -> Stil(Color.rgb(0xEC, 0xEE, 0xF1), Color.rgb(0xF1, 0xF2, 0xF5), Color.rgb(0xE6, 0xE8, 0xEC), Color.rgb(0xD4, 0xD7, 0xDD), Color.rgb(0x22, 0x22, 0x22), Color.rgb(0x8E, 0x92, 0x99), Color.rgb(0x22, 0x22, 0x22), Color.rgb(0x1E, 0x1E, 0x1E))
        Hintergrund.GRAU -> Stil(Color.rgb(0xC9, 0xCD, 0xD3), Color.rgb(0xEE, 0xEF, 0xF2), Color.rgb(0xE2, 0xE4, 0xE8), Color.rgb(0xCB, 0xCE, 0xD4), Color.rgb(0x1C, 0x1C, 0x1C), Color.rgb(0x6E, 0x72, 0x79), Color.rgb(0x1C, 0x1C, 0x1C), Color.rgb(0x1E, 0x1E, 0x1E))
        Hintergrund.DUNKEL -> Stil(Color.rgb(0x26, 0x29, 0x2E), Color.rgb(0x3E, 0x42, 0x49), Color.rgb(0x36, 0x3A, 0x40), Color.rgb(0x2C, 0x2F, 0x35), Color.rgb(0xF0, 0xF0, 0xF0), Color.rgb(0x9A, 0x9E, 0xA5), Color.rgb(0xF0, 0xF0, 0xF0), Color.rgb(0xF0, 0xF0, 0xF0))
        Hintergrund.SCHWARZ -> Stil(Color.BLACK, Color.rgb(0x22, 0x22, 0x22), Color.rgb(0x1A, 0x1A, 0x1A), Color.rgb(0x10, 0x10, 0x10), Color.WHITE, Color.rgb(0x88, 0x88, 0x88), Color.WHITE, Color.WHITE)
    }

    /** Hintergrundfarbe der App zum gewählten Erscheinungsbild. */
    fun flaechenFarbe(h: Hintergrund): Int = stil(h).flaeche

    /** Schriftfarbe (für Titel/Restzeit) zum Erscheinungsbild. */
    fun schriftFarbe(h: Hintergrund): Int = stil(h).schrift

    /**
     * @param restMs verbleibende Zeit
     * @param dauerMs Gesamtdauer (für Farbverläufe)
     * @param skalaMinuten Minuten für eine volle Umdrehung
     * @param mitZahlen Zahlen außen zeichnen (bei sehr kleinen Widgets weglassen)
     */
    @Synchronized
    fun zeichne(
        canvas: Canvas,
        breite: Float,
        hoehe: Float,
        restMs: Long,
        dauerMs: Long,
        skalaMinuten: Int,
        farbModus: FarbModus,
        eigeneFarbe: Int,
        hintergrund: Hintergrund,
        mitZahlen: Boolean = true
    ) {
        val s = stil(hintergrund)
        val seite = min(breite, hoehe)
        if (seite <= 0f) return
        val cx = breite / 2f
        val cy = hoehe / 2f
        val radius = seite * (if (mitZahlen) 0.31f else 0.40f)

        // Scheibe mit leichtem Verlauf: innen heller, zum Rand etwas dunkler (wie im Original).
        scheibe.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(s.scheibeInnen, s.scheibeMitte, s.scheibeRand),
            floatArrayOf(0f, 0.86f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, scheibe)

        // Farbige Restzeit: von 0 (oben) gegen den Uhrzeigersinn.
        val skalaMs = skalaMinuten * 60_000f
        val anteil = (restMs / skalaMs).coerceIn(0f, 1f)
        if (anteil > 0f) {
            sektor.color = sektorFarbe(farbModus, eigeneFarbe, restMs, dauerMs)
            rechteck.set(cx - radius, cy - radius, cx + radius, cy + radius)
            pfad.reset()
            pfad.moveTo(cx, cy)
            if (anteil >= 0.9999f) {
                pfad.addCircle(cx, cy, radius, Path.Direction.CW)
            } else {
                pfad.arcTo(rechteck, -90f, -360f * anteil, false)
                pfad.close()
            }
            canvas.drawPath(pfad, sektor)
        }

        scheibenRand.strokeWidth = seite * 0.004f
        scheibenRand.color = Color.argb(40, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, scheibenRand)

        // 60 Striche: alle 5 lang und kräftig, dazwischen kurz und grau.
        for (i in 0 until 60) {
            val lang = i % 5 == 0
            val winkel = Math.toRadians((-90.0 - i * 6.0))
            val innen = radius + seite * 0.018f
            val aussen = innen + seite * (if (lang) 0.045f else 0.025f)
            strich.strokeWidth = seite * (if (lang) 0.0075f else 0.004f)
            strich.color = if (lang) s.strichLang else s.strichKurz
            canvas.drawLine(
                cx + cos(winkel).toFloat() * innen, cy + sin(winkel).toFloat() * innen,
                cx + cos(winkel).toFloat() * aussen, cy + sin(winkel).toFloat() * aussen,
                strich
            )
        }

        if (mitZahlen) {
            val schritt = beschriftungsSchritt(skalaMinuten)
            val anzahl = skalaMinuten / schritt
            schrift.color = s.schrift
            schrift.textSize = seite * 0.068f
            val textRadius = radius + seite * 0.118f
            val mitteText = (schrift.descent() + schrift.ascent()) / 2f
            for (k in 0 until anzahl) {
                val winkel = Math.toRadians(-90.0 - k * 360.0 / anzahl)
                val x = cx + cos(winkel).toFloat() * textRadius
                val y = cy + sin(winkel).toFloat() * textRadius - mitteText
                canvas.drawText("${k * schritt}", x, y, schrift)
            }
        }

        // Ist die Zeit um, zeigt eine feine Linie auf die 0 (wie im Original).
        if (anteil <= 0f) {
            nullLinie.strokeWidth = seite * 0.004f
            canvas.drawLine(cx, cy, cx, cy - radius, nullLinie)
        }

        // Mittelknopf mit kleinem Zeiger zur Kante der Restzeit.
        val zeigerWinkel = Math.toRadians(-90.0 - 360.0 * anteil)
        knopf.color = s.knopf
        strich.color = knopf.color
        strich.strokeWidth = seite * 0.008f
        val zeigerLaenge = seite * 0.085f
        canvas.drawLine(
            cx, cy,
            cx + cos(zeigerWinkel).toFloat() * zeigerLaenge, cy + sin(zeigerWinkel).toFloat() * zeigerLaenge,
            strich
        )
        canvas.drawCircle(cx, cy, seite * 0.042f, knopf)
    }

    /** Beschriftung alle 5 Minuten bei 60, alle 10 bei 120, jede Minute bei kleinen Skalen. */
    private fun beschriftungsSchritt(skala: Int): Int = when {
        skala <= 10 -> 1
        skala <= 20 -> 2
        skala <= 60 -> 5
        else -> 10
    }

    private val ROT = Color.rgb(0xE0, 0x1E, 0x3C)
    private val DUNKELROT = Color.rgb(0x7A, 0x00, 0x14)
    private val GELB = Color.rgb(0xF2, 0xB7, 0x05)
    private val GRUEN = Color.rgb(0x2E, 0xA0, 0x43)

    /** Farbe der Restzeit je nach Modus – Verläufe hängen von der verbleibenden Zeit ab. */
    fun sektorFarbe(modus: FarbModus, eigeneFarbe: Int, restMs: Long, dauerMs: Long): Int = when (modus) {
        FarbModus.ROT -> ROT
        FarbModus.EIGENE -> eigeneFarbe
        // In den letzten 5 Minuten (bei kurzen Timern im letzten Drittel) langsam dunkelrot.
        FarbModus.VERLAUF -> {
            val fenster = minOf(5 * 60_000L, maxOf(dauerMs / 3, 1L))
            val t = (1f - restMs.toFloat() / fenster).coerceIn(0f, 1f)
            mische(ROT, DUNKELROT, t)
        }
        // Über die ganze Dauer: grün → gelb → rot.
        FarbModus.AMPEL -> {
            val anteil = if (dauerMs > 0) (restMs.toFloat() / dauerMs).coerceIn(0f, 1f) else 0f
            if (anteil > 0.5f) mische(GELB, GRUEN, (anteil - 0.5f) * 2f) else mische(ROT, GELB, anteil * 2f)
        }
    }

    private fun mische(a: Int, b: Int, t: Float): Int {
        fun kanal(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            kanal(Color.red(a), Color.red(b)),
            kanal(Color.green(a), Color.green(b)),
            kanal(Color.blue(a), Color.blue(b))
        )
    }
}
