package de.oejendorferdamm.dammzeit.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
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
    private val strich = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val schrift = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        color = Color.rgb(0x22, 0x22, 0x22)
    }
    private val knopf = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x1E, 0x1E, 0x1E) }
    private val nullLinie = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xB8, 0xBB, 0xC0) }
    private val schatten = Paint(Paint.ANTI_ALIAS_FLAG)
    private val luenette = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glanz = Paint(Paint.ANTI_ALIAS_FLAG)
    private val kante = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val knopfGlanz = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pfad = Path()
    private val rechteck = RectF()

    // Verläufe nur neu anlegen, wenn sich Größe/Farben ändern – nicht in jedem Bild (flüssiger).
    private var cacheSchluessel = ""
    private var cacheSektorFarbe = 0
    private var cacheSektorOrt = ""

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
        val dunkel = hintergrund == Hintergrund.DUNKEL || hintergrund == Hintergrund.SCHWARZ

        val schluessel = "$breite/$hoehe/$radius/${hintergrund.name}"
        if (schluessel != cacheSchluessel) {
            cacheSchluessel = schluessel
            // Weicher Schatten unter der Scheibe – als Verlauf statt setShadowLayer, das auf
            // älteren Android-Versionen mit Hardwarebeschleunigung nicht gezeichnet wird.
            val schattenAlpha = if (dunkel) 110 else 60
            schatten.shader = RadialGradient(
                cx, cy + seite * 0.012f, radius * 1.12f,
                intArrayOf(Color.argb(schattenAlpha, 0, 0, 0), Color.argb(schattenAlpha, 0, 0, 0), Color.argb(0, 0, 0, 0)),
                floatArrayOf(0f, 0.86f, 1f), Shader.TileMode.CLAMP
            )
            // Scheibe mit leichtem Verlauf: innen heller, zum Rand etwas dunkler (wie im Original).
            scheibe.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(s.scheibeInnen, s.scheibeMitte, s.scheibeRand),
                floatArrayOf(0f, 0.86f, 1f), Shader.TileMode.CLAMP
            )
            // Glas-Schimmer auf der oberen Hälfte.
            glanz.shader = LinearGradient(
                cx, cy - radius, cx, cy + radius * 0.1f,
                Color.argb(if (dunkel) 26 else 70, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
            )
            // Mittelknopf mit Lichtreflex oben links.
            knopfGlanz.shader = RadialGradient(
                cx - seite * 0.014f, cy - seite * 0.016f, seite * 0.05f,
                intArrayOf(Color.argb(if (dunkel) 90 else 120, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null, Shader.TileMode.CLAMP
            )
        }

        canvas.drawCircle(cx, cy + seite * 0.012f, radius * 1.12f, schatten)
        canvas.drawCircle(cx, cy, radius, scheibe)

        // Farbige Restzeit: von 0 (oben) gegen den Uhrzeigersinn.
        val skalaMs = skalaMinuten * 60_000f
        val anteil = (restMs / skalaMs).coerceIn(0f, 1f)
        if (anteil > 0f) {
            val farbe = sektorFarbe(farbModus, eigeneFarbe, restMs, dauerMs)
            // Zur Mitte hin etwas heller: gibt der Scheibe Tiefe statt einer flachen Fläche.
            if (farbe != cacheSektorFarbe || schluessel != cacheSektorOrt) {
                cacheSektorFarbe = farbe
                cacheSektorOrt = schluessel
                sektor.shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(mische(farbe, Color.WHITE, 0.22f), farbe, mische(farbe, Color.BLACK, 0.12f)),
                    floatArrayOf(0f, 0.8f, 1f), Shader.TileMode.CLAMP
                )
            }
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
            // Feine, dunklere Kante an der wandernden Grenze – wirkt wie ein echtes Blatt.
            if (anteil < 0.9999f) {
                val w = Math.toRadians(-90.0 - 360.0 * anteil)
                kante.color = mische(farbe, Color.BLACK, 0.35f)
                kante.strokeWidth = seite * 0.005f
                canvas.drawLine(cx, cy, cx + cos(w).toFloat() * radius, cy + sin(w).toFloat() * radius, kante)
            }
        }

        // Glas-Schimmer über Scheibe und Restzeit.
        canvas.drawCircle(cx, cy, radius, glanz)

        scheibenRand.strokeWidth = seite * 0.004f
        scheibenRand.color = Color.argb(40, 0, 0, 0)
        canvas.drawCircle(cx, cy, radius, scheibenRand)
        // Dezente Lünette außen um die Scheibe.
        luenette.strokeWidth = seite * 0.006f
        luenette.color = if (dunkel) Color.argb(60, 255, 255, 255) else Color.argb(160, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius + seite * 0.006f, luenette)

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
        canvas.drawCircle(cx, cy, seite * 0.042f, knopfGlanz)
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

    fun mische(a: Int, b: Int, t: Float): Int {
        fun kanal(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(
            kanal(Color.red(a), Color.red(b)),
            kanal(Color.green(a), Color.green(b)),
            kanal(Color.blue(a), Color.blue(b))
        )
    }
}
