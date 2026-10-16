package de.oejendorferdamm.dammzeit.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zeichnet die Sanduhr mit normalem android.graphics – damit App (Compose) und Widget (Bild)
 * exakt dieselbe Sanduhr zeigen. Holzkappen mit Stäben, Glas mit Glanz, oben der restliche Sand
 * (sinkt), unten ein wachsender Haufen, dazwischen ein rieselnder Strahl.
 */
object SanduhrZeichner {
    private val malen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val rechteck = RectF()

    /** Feste Sandkörner (immer gleich, damit nichts flimmert). */
    private val KOERNER = Random(7).let { r -> List(260) { floatArrayOf(r.nextFloat(), r.nextFloat()) } }

    private const val STUFEN = 120

    /**
     * @param anteil verbleibender Sand oben (0–1)
     * @param farbe Sandfarbe (ARGB)
     * @param rieselt Strahl zeigen (Uhr läuft)
     * @param riesel Phase der fallenden Körner (0–1, für die Bewegung)
     * @param leuchten 0–1: Leuchten hinter der Sanduhr (Zeit ist um)
     */
    @Synchronized
    fun zeichne(c: Canvas, breiteGesamt: Float, hoeheGesamt: Float, anteil: Float, farbe: Int, rieselt: Boolean, riesel: Float, leuchten: Float) {
        val hoehe = minOf(hoeheGesamt * 0.96f, breiteGesamt * 1.45f)
        if (hoehe <= 0f) return
        val breite = hoehe * 0.62f
        val cx = breiteGesamt / 2f
        val cy = hoeheGesamt / 2f
        val oben = cy - hoehe / 2f
        val unten = cy + hoehe / 2f
        val kappe = hoehe * 0.065f
        val glasOben = oben + kappe
        val glasUnten = unten - kappe
        val halbGlas = (glasUnten - glasOben) / 2f
        val bauch = breite * 0.43f
        val hals = breite * 0.045f
        fun halbBreite(u: Float): Float = hals + (bauch - hals) * (1f - u.pow(2.4f)).coerceAtLeast(0f).pow(0.7f)

        malen.shader = null
        malen.style = Paint.Style.FILL

        // Leuchten beim Klingeln
        if (leuchten > 0f) {
            malen.shader = RadialGradient(cx, cy, hoehe * 0.6f, intArrayOf(Color.argb((90 * (0.5f + 0.5f * leuchten)).toInt(), 0xE0, 0x1E, 0x3C), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, hoehe * 0.6f, malen)
            malen.shader = null
        }

        // Glasform (oben und unten gespiegelt)
        val n = 48
        val glas = Path().apply {
            moveTo(cx - halbBreite(0f), glasOben)
            for (i in 1..n) { val u = i / n.toFloat(); lineTo(cx - halbBreite(u), glasOben + u * halbGlas) }
            for (i in n - 1 downTo 0) { val u = i / n.toFloat(); lineTo(cx - halbBreite(u), glasUnten - u * halbGlas) }
            lineTo(cx + halbBreite(0f), glasUnten)
            for (i in 1..n) { val u = i / n.toFloat(); lineTo(cx + halbBreite(u), glasUnten - u * halbGlas) }
            for (i in n - 1 downTo 0) { val u = i / n.toFloat(); lineTo(cx + halbBreite(u), glasOben + u * halbGlas) }
            close()
        }

        // Schatten unter der Sanduhr
        malen.color = Color.argb(41, 0, 0, 0)
        rechteck.set(cx - breite * 0.55f, unten - hoehe * 0.012f, cx + breite * 0.55f, unten + hoehe * 0.038f)
        c.drawOval(rechteck, malen)

        // Glas innen leicht getönt
        malen.shader = LinearGradient(cx - bauch, 0f, cx + bauch, 0f, intArrayOf(Color.argb(140, 255, 255, 255), Color.argb(64, 255, 255, 255), Color.argb(128, 255, 255, 255)), null, Shader.TileMode.CLAMP)
        c.drawPath(glas, malen)
        malen.shader = null

        // Sandmenge pro Höhe: oben vom Hals aufwärts, unten vom Boden aufwärts
        val flaechen = FloatArray(STUFEN + 1)
        for (i in 1..STUFEN) flaechen[i] = flaechen[i - 1] + halbBreite(1f - i / STUFEN.toFloat())
        val gesamt = flaechen[STUFEN]
        val flaechenUnten = FloatArray(STUFEN + 1)
        for (i in 1..STUFEN) flaechenUnten[i] = flaechenUnten[i - 1] + halbBreite((i - 1) / STUFEN.toFloat())
        fun hoeheOben(menge: Float) = flaechen.indexOfFirst { it >= menge * gesamt }.let { if (it < 0) STUFEN else it } / STUFEN.toFloat()
        fun hoeheUnten(menge: Float) = flaechenUnten.indexOfFirst { it >= menge * gesamt }.let { if (it < 0) STUFEN else it } / STUFEN.toFloat()

        val sandHell = mische(farbe, Color.WHITE, 0.25f)
        val sandDunkel = mische(farbe, Color.BLACK, 0.22f)

        c.save()
        c.clipPath(glas)
        // --- oben: Rest
        if (anteil > 0f) {
            val yOberflaeche = glasOben + (1f - hoeheOben(anteil)) * halbGlas
            val mulde = if (rieselt) halbGlas * 0.05f * anteil.coerceAtMost(0.6f) else 0f
            val sand = Path().apply {
                moveTo(cx - bauch * 1.2f, yOberflaeche)
                quadTo(cx, yOberflaeche + 2 * mulde, cx + bauch * 1.2f, yOberflaeche)
                lineTo(cx + bauch * 1.2f, cy)
                lineTo(cx - bauch * 1.2f, cy)
                close()
            }
            sandMalen(c, sand, yOberflaeche, cy, farbe, sandHell, sandDunkel, cx - bauch, bauch * 2)
        }
        // --- unten: Haufen
        val rest = 1f - anteil
        if (rest > 0f) {
            val uSpiegel = hoeheUnten(rest)
            val yEbene = glasUnten - uSpiegel * halbGlas
            val kegel = halbGlas * 0.16f * (0.4f + 0.6f * rest).coerceAtMost(1f) * (1f - uSpiegel * 0.6f)
            val haufen = Path().apply {
                moveTo(cx - bauch * 1.2f, yEbene + kegel * 0.3f)
                cubicTo(cx - bauch * 0.5f, yEbene + kegel * 0.2f, cx - bauch * 0.15f, yEbene - kegel, cx, yEbene - kegel)
                cubicTo(cx + bauch * 0.15f, yEbene - kegel, cx + bauch * 0.5f, yEbene + kegel * 0.2f, cx + bauch * 1.2f, yEbene + kegel * 0.3f)
                lineTo(cx + bauch * 1.2f, glasUnten + 2f)
                lineTo(cx - bauch * 1.2f, glasUnten + 2f)
                close()
            }
            sandMalen(c, haufen, yEbene - kegel, glasUnten, farbe, sandHell, sandDunkel, cx - bauch, bauch * 2)
        }
        // --- Strahl
        if (rieselt && anteil > 0f) {
            val yEnde = glasUnten - hoeheUnten(rest) * halbGlas - halbGlas * 0.1f
            linie.shader = null
            linie.color = farbe
            linie.strokeWidth = hals * 0.7f
            c.drawLine(cx, cy - halbGlas * 0.02f, cx, yEnde, linie)
            malen.color = sandHell
            val laenge = yEnde - cy
            for (k in 0 until 6) {
                val p = ((k / 6f) + riesel) % 1f
                c.drawCircle(cx + sin((p * 7 + k) * PI).toFloat() * hals * 0.3f, cy + p * laenge, hals * 0.45f, malen)
            }
        }
        c.restore()

        // Glasrand und Glanzlichter
        linie.shader = null
        linie.color = 0x553C4450
        linie.strokeWidth = breite * 0.012f
        c.drawPath(glas, linie)
        val glanz = Path().apply {
            moveTo(cx - halbBreite(0.12f) * 0.72f, glasOben + 0.12f * halbGlas)
            for (i in 13..70) { val u = i / 100f; lineTo(cx - halbBreite(u) * 0.72f, glasOben + u * halbGlas) }
        }
        linie.color = Color.argb(191, 255, 255, 255)
        linie.strokeWidth = breite * 0.03f
        c.drawPath(glanz, linie)
        val glanz2 = Path().apply {
            moveTo(cx - halbBreite(0.75f) * 0.72f, glasUnten - 0.75f * halbGlas)
            for (i in 74 downTo 25) { val u = i / 100f; lineTo(cx - halbBreite(u) * 0.72f, glasUnten - u * halbGlas) }
        }
        linie.color = Color.argb(140, 255, 255, 255)
        linie.strokeWidth = breite * 0.022f
        c.drawPath(glanz2, linie)

        // Stäbe und Holzkappen
        val holzHell = Color.rgb(0x9A, 0x6A, 0x43)
        val holz = Color.rgb(0x6E, 0x45, 0x27)
        val holzDunkel = Color.rgb(0x4A, 0x2D, 0x18)
        val stab = breite * 0.035f
        for (x in floatArrayOf(cx - breite * 0.47f, cx + breite * 0.47f)) {
            malen.shader = LinearGradient(x - stab / 2, 0f, x + stab / 2, 0f, intArrayOf(holzHell, holz, holzDunkel), null, Shader.TileMode.CLAMP)
            rechteck.set(x - stab / 2, glasOben - 2f, x + stab / 2, glasUnten + 2f)
            c.drawRoundRect(rechteck, stab / 2, stab / 2, malen)
        }
        for (y in floatArrayOf(oben, unten - kappe)) {
            malen.shader = LinearGradient(0f, y, 0f, y + kappe, intArrayOf(holzHell, holz, holzDunkel), null, Shader.TileMode.CLAMP)
            rechteck.set(cx - breite / 2f, y, cx + breite / 2f, y + kappe)
            c.drawRoundRect(rechteck, kappe * 0.35f, kappe * 0.35f, malen)
            malen.shader = null
            malen.color = Color.argb(46, 255, 255, 255)
            rechteck.set(cx - breite / 2f + kappe * 0.3f, y + kappe * 0.12f, cx + breite / 2f - kappe * 0.3f, y + kappe * 0.3f)
            c.drawRoundRect(rechteck, kappe * 0.1f, kappe * 0.1f, malen)
        }
        malen.shader = null
    }

    private fun sandMalen(c: Canvas, form: Path, von: Float, bis: Float, farbe: Int, hell: Int, dunkel: Int, x: Float, b: Float) {
        malen.shader = LinearGradient(0f, von, 0f, maxOf(bis, von + 1f), intArrayOf(hell, farbe, dunkel), null, Shader.TileMode.CLAMP)
        c.drawPath(form, malen)
        malen.shader = null
        // feine Körnung
        val h = bis - von
        if (h <= 1f) return
        c.save()
        c.clipPath(form)
        malen.color = Color.argb(89, Color.red(dunkel), Color.green(dunkel), Color.blue(dunkel))
        val r = b * 0.0055f
        for (k in KOERNER) c.drawCircle(x + k[0] * b, von + k[1] * h, r, malen)
        c.restore()
    }

    private fun mische(a: Int, b: Int, t: Float): Int {
        fun kanal(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(kanal(Color.red(a), Color.red(b)), kanal(Color.green(a), Color.green(b)), kanal(Color.blue(a), Color.blue(b)))
    }
}
