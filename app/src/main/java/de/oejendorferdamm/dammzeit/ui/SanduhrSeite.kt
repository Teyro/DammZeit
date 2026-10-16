package de.oejendorferdamm.dammzeit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.SKALEN
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sanduhren wie im Klassenzimmer: jede Zeit hat ihre eigene Sandfarbe (1 Minute rot, 3 Minuten
 * gelb, 5 Minuten grün …). Die Sanduhr zeigt denselben Timer wie das Zifferblatt – Ton, Widget
 * und „Zeit ist um“ funktionieren also genauso. Eine Zeit antippen dreht die Sanduhr um und startet.
 */
data class SandZeit(val minuten: Int, val farbe: Color, val name: String)

val SANDUHREN = listOf(
    SandZeit(1, Color(0xFFE01E3C), "Rot"),
    SandZeit(2, Color(0xFFF06A1D), "Orange"),
    SandZeit(3, Color(0xFFF2B705), "Gelb"),
    SandZeit(5, Color(0xFF2EA043), "Grün"),
    SandZeit(10, Color(0xFF1E6FD9), "Blau"),
    SandZeit(15, Color(0xFF7B3FE4), "Lila"),
    SandZeit(20, Color(0xFFE0408F), "Pink"),
    SandZeit(30, Color(0xFF0B9A8D), "Türkis")
)

/** Sandfarbe zu einer Dauer: die der passenden Sanduhr, sonst ein warmes Sandgelb. */
fun sandFarbe(dauerMs: Long): Color =
    SANDUHREN.firstOrNull { it.minuten * 60_000L == dauerMs }?.farbe ?: Color(0xFFE2B86B)

@Composable
fun SanduhrAnsicht(schrift: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val timer by Speicher.timer.collectAsState()
    val einstellungen by Speicher.einstellungen.collectAsState()
    val klingelt by KlingelDienst.klingelt.collectAsState()
    val jetzt by rememberJetzt(aktiv = timer.laeuft)
    val abgelaufen = timer.abgelaufen(jetzt)
    val laeuft = timer.laeuft && !abgelaufen
    val rest = timer.restMs(jetzt)
    val anteil = if (timer.dauerMs > 0) (rest.toFloat() / timer.dauerMs).coerceIn(0f, 1f) else 0f
    val klingelnd = klingelt != null || abgelaufen
    val farbe = sandFarbe(timer.dauerMs)

    // Umdrehen beim Wählen einer Zeit
    val drehung = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun waehle(z: SandZeit) {
        if (z.minuten > einstellungen.skalaMinuten) {
            val skala = SKALEN.firstOrNull { it >= z.minuten } ?: SKALEN.last()
            Speicher.aendereEinstellungen { it.copy(skalaMinuten = skala) }
        }
        TimerSteuerung.setzeDauer(context, z.minuten * 60_000L, starten = true)
        scope.launch {
            drehung.snapTo(-180f)
            drehung.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    // Rieselnder Sand und Leuchten beim Klingeln
    val t = rememberInfiniteTransition(label = "sand")
    val riesel by t.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing)), label = "riesel")
    val puls by t.animateFloat(0f, 1f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "puls")

    val startZustand = when {
        klingelnd -> StartZustand.STOPP
        laeuft -> StartZustand.PAUSE
        timer.pausiert -> StartZustand.WEITER
        else -> StartZustand.START
    }

    Row(modifier.padding(horizontal = 40.dp, vertical = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        // --- Die Sanduhr ---
        Box(
            Modifier.weight(1f).fillMaxHeight()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { TimerSteuerung.antippen(context) }
                .semantics { contentDescription = "Sanduhr, antippen zum Starten oder Anhalten" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(top = 70.dp)) {
                rotate(drehung.value) {
                    sanduhr(anteil, farbe, laeuft && anteil > 0f, riesel, if (klingelnd) puls else 0f)
                }
            }
        }
        Spacer(Modifier.width(40.dp))
        // --- Zeit, Auswahl, Knöpfe ---
        Column(Modifier.width(620.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (klingelnd) "Zeit ist um!" else formatiereDauer(rest),
                color = if (klingelnd) Akzent else schrift,
                fontSize = if (klingelnd) 72.sp else 96.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(28.dp))
            SANDUHREN.chunked(4).forEach { reihe ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    reihe.forEach { z -> SandKnopf(z, gewaehlt = timer.dauerMs == z.minuten * 60_000L) { waehle(z) } }
                }
                Spacer(Modifier.height(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RundKnopf("Zurücksetzen", { TimerSteuerung.zuruecksetzen(context) }, groesse = 84.dp) {
                    SymbolBild(Symbol.ZURUECK, Text1, Modifier.size(40.dp))
                }
                Spacer(Modifier.width(30.dp))
                StartKnopf(startZustand, if (klingelnd) Akzent else farbe, onClick = { TimerSteuerung.antippen(context) })
            }
        }
    }
}

/** Runder, farbiger Knopf mit der Minutenzahl – sieht aus wie Sand im Glas. */
@Composable
private fun SandKnopf(z: SandZeit, gewaehlt: Boolean, onClick: () -> Unit) {
    val quelle = remember { MutableInteractionSource() }
    Box(
        Modifier
            .drueckFeder(quelle)
            .size(126.dp)
            .shadow(if (gewaehlt) 12.dp else 5.dp, CircleShape)
            .background(Color.White, CircleShape)
            .padding(if (gewaehlt) 0.dp else 6.dp)
            .background(Brush.verticalGradient(listOf(lerp(z.farbe, Color.White, 0.25f), z.farbe, lerp(z.farbe, Color.Black, 0.18f))), CircleShape)
            .clickable(interactionSource = quelle, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "${z.minuten} Minuten, Sand ${z.name}" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${z.minuten}", color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Black)
            Text("Min", color = Color.White.copy(alpha = 0.9f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- Zeichnen

/** Feste Sandkörner (immer gleich, damit nichts flimmert). */
private val KOERNER = Random(7).let { r -> List(260) { Offset(r.nextFloat(), r.nextFloat()) } }

/**
 * Zeichnet die Sanduhr: Holzkappen mit Stäben, Glas mit Glanz, oben der restliche Sand (sinkt),
 * unten ein wachsender Haufen, dazwischen ein rieselnder Strahl.
 */
private fun DrawScope.sanduhr(anteil: Float, farbe: Color, rieselt: Boolean, riesel: Float, leuchten: Float) {
    val hoehe = minOf(size.height * 0.96f, size.width * 1.45f)
    val breite = hoehe * 0.62f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val oben = cy - hoehe / 2f
    val unten = cy + hoehe / 2f
    val kappe = hoehe * 0.065f
    val glasOben = oben + kappe
    val glasUnten = unten - kappe
    val halbGlas = (glasUnten - glasOben) / 2f
    val bauch = breite * 0.43f
    val hals = breite * 0.045f

    // Halbe Breite des Glases bei u = 0 (Kappe) … 1 (Hals)
    fun halbBreite(u: Float): Float = hals + (bauch - hals) * (1f - u.pow(2.4f)).coerceAtLeast(0f).pow(0.7f)

    // Leuchten beim Klingeln
    if (leuchten > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Akzent.copy(alpha = 0.35f * (0.5f + 0.5f * leuchten)), Color.Transparent), Offset(cx, cy), hoehe * 0.6f),
            hoehe * 0.6f, Offset(cx, cy)
        )
    }

    // Glasform als Pfad (oben und unten gespiegelt)
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
    drawOval(Color.Black.copy(alpha = 0.16f), Offset(cx - breite * 0.55f, unten - hoehe * 0.012f), Size(breite * 1.1f, hoehe * 0.05f))

    // Glas innen leicht getönt
    drawPath(glas, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.5f)), cx - bauch, cx + bauch))

    // Sandmenge pro Höhe: Fläche ~ Breite. Oben sammelt sich der Rest unten am Hals.
    val stufen = 120
    val flaechen = FloatArray(stufen + 1) // kumuliert vom Hals (u = 1) nach oben
    for (i in 1..stufen) {
        val u = 1f - i / stufen.toFloat()
        flaechen[i] = flaechen[i - 1] + halbBreite(u)
    }
    val gesamt = flaechen[stufen]
    fun stufeFuer(menge: Float): Int = flaechen.indexOfFirst { it >= menge * gesamt }.coerceAtLeast(0)

    val sandHell = lerp(farbe, Color.White, 0.25f)
    val sandDunkel = lerp(farbe, Color.Black, 0.22f)

    clipPath(glas) {
        // --- oben: Rest
        if (anteil > 0f) {
            val s = stufeFuer(anteil)
            val uOberflaeche = 1f - s / stufen.toFloat()
            val yOberflaeche = glasOben + uOberflaeche * halbGlas
            val mulde = if (rieselt) halbGlas * 0.05f * anteil.coerceAtMost(0.6f) else 0f
            val sand = Path().apply {
                moveTo(cx - bauch * 1.2f, yOberflaeche)
                quadraticBezierTo(cx, yOberflaeche + 2 * mulde, cx + bauch * 1.2f, yOberflaeche)
                lineTo(cx + bauch * 1.2f, cy)
                lineTo(cx - bauch * 1.2f, cy)
                close()
            }
            drawPath(sand, Brush.verticalGradient(listOf(sandHell, farbe, sandDunkel), yOberflaeche, cy))
            koerner(sand, sandDunkel, cx - bauch, yOberflaeche, bauch * 2, cy - yOberflaeche)
        }
        // --- unten: Haufen
        val rest = 1f - anteil
        if (rest > 0f) {
            val s = stufeFuer(rest)
            val uSpiegel = 1f - s / stufen.toFloat()
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
            drawPath(haufen, Brush.verticalGradient(listOf(sandHell, farbe, sandDunkel), yEbene - kegel, glasUnten))
            koerner(haufen, sandDunkel, cx - bauch, yEbene - kegel, bauch * 2, glasUnten - yEbene + kegel)
        }
        // --- Strahl
        if (rieselt) {
            val yEnde = glasUnten - (1f - stufeFuer(1f - anteil) / stufen.toFloat()) * halbGlas - halbGlas * 0.1f
            drawLine(farbe, Offset(cx, cy - halbGlas * 0.02f), Offset(cx, yEnde), strokeWidth = hals * 0.7f)
            // fallende Körner im Strahl
            val laenge = yEnde - cy
            for (k in 0 until 6) {
                val p = ((k / 6f) + riesel) % 1f
                drawCircle(sandHell, hals * 0.45f, Offset(cx + sin((p * 7 + k) * PI).toFloat() * hals * 0.3f, cy + p * laenge))
            }
        }
    }

    // Glasrand und Glanzlicht
    drawPath(glas, Color(0x553C4450), style = Stroke(width = breite * 0.012f))
    val glanz = Path().apply {
        moveTo(cx - halbBreite(0.12f) * 0.72f, glasOben + 0.12f * halbGlas)
        for (i in 13..70) { val u = i / 100f; lineTo(cx - halbBreite(u) * 0.72f, glasOben + u * halbGlas) }
    }
    drawPath(glanz, Color.White.copy(alpha = 0.75f), style = Stroke(width = breite * 0.03f, cap = StrokeCap.Round))
    val glanz2 = Path().apply {
        moveTo(cx - halbBreite(0.75f) * 0.72f, glasUnten - 0.75f * halbGlas)
        for (i in 74 downTo 25) { val u = i / 100f; lineTo(cx - halbBreite(u) * 0.72f, glasUnten - u * halbGlas) }
    }
    drawPath(glanz2, Color.White.copy(alpha = 0.55f), style = Stroke(width = breite * 0.022f, cap = StrokeCap.Round))

    // Stäbe und Holzkappen
    val holzHell = Color(0xFF9A6A43)
    val holz = Color(0xFF6E4527)
    val holzDunkel = Color(0xFF4A2D18)
    val stab = breite * 0.035f
    for (x in listOf(cx - breite * 0.47f, cx + breite * 0.47f)) {
        drawRoundRect(Brush.horizontalGradient(listOf(holzHell, holz, holzDunkel), x - stab / 2, x + stab / 2), Offset(x - stab / 2, glasOben - 2f), Size(stab, glasUnten - glasOben + 4f), CornerRadius(stab / 2))
    }
    for (y in listOf(oben, unten - kappe)) {
        drawRoundRect(Brush.verticalGradient(listOf(holzHell, holz, holzDunkel), y, y + kappe), Offset(cx - breite / 2f, y), Size(breite, kappe), CornerRadius(kappe * 0.35f))
        drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(cx - breite / 2f + kappe * 0.3f, y + kappe * 0.12f), Size(breite - kappe * 0.6f, kappe * 0.18f), CornerRadius(kappe * 0.1f))
    }
}

/** Feine Körnung im Sand. */
private fun DrawScope.koerner(form: Path, farbe: Color, x: Float, y: Float, b: Float, h: Float) {
    if (h <= 1f) return
    clipPath(form) {
        val r = b * 0.0055f
        KOERNER.forEach { k -> drawCircle(farbe.copy(alpha = 0.35f), r, Offset(x + k.x * b, y + k.y * h)) }
    }
}
