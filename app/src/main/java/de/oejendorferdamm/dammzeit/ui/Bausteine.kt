package de.oejendorferdamm.dammzeit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.model.FarbModus
import kotlin.math.atan2
import kotlin.math.roundToLong

val Hintergrund = Color(0xFFECEEF1)
val Karte = Color(0xFFFFFFFF)
val Text1 = Color(0xFF1F2124)
val Text2 = Color(0xFF6B7078)
val Akzent = Color(0xFFE01E3C)
val Flaeche = Color(0xFFE1E4E8)

/**
 * Die aktuelle Uhrzeit als Compose-State. Solange [aktiv], wird sie jedes Bild neu gelesen –
 * dadurch läuft die Scheibe flüssig in Echtzeit statt in Sekundensprüngen.
 */
@Composable
fun rememberJetzt(aktiv: Boolean): State<Long> {
    val jetzt = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(aktiv) {
        jetzt.longValue = System.currentTimeMillis()
        while (aktiv) {
            withFrameMillis { }
            jetzt.longValue = System.currentTimeMillis()
        }
    }
    return jetzt
}

/**
 * Zifferblatt als Compose-Baustein. Mit [onDauer] lässt sich die Zeit wie beim echten Time Timer
 * durch Ziehen/Tippen auf dem Zifferblatt einstellen.
 */
@Composable
fun Zifferblatt(
    restMs: Long,
    dauerMs: Long,
    skalaMinuten: Int,
    farbe: FarbModus,
    modifier: Modifier = Modifier,
    mitZahlen: Boolean = true,
    onDauer: ((Long) -> Unit)? = null
) {
    val aktuell by rememberUpdatedState(onDauer)
    val bedienung = if (onDauer == null) Modifier else Modifier.pointerInput(skalaMinuten) {
        fun setze(p: Offset) {
            val dx = p.x - size.width / 2f
            val dy = p.y - size.height / 2f
            // Winkel ab 12 Uhr gegen den Uhrzeigersinn (wie die Zahlen auf dem Zifferblatt).
            var grad = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
            grad = (360.0 - grad) % 360.0
            val schritt = if (skalaMinuten <= 10) 15_000L else if (skalaMinuten <= 30) 30_000L else 60_000L
            val roh = grad / 360.0 * skalaMinuten * 60_000.0
            val gerundet = ((roh / schritt).roundToLong() * schritt).coerceIn(schritt, skalaMinuten * 60_000L)
            aktuell?.invoke(gerundet)
        }
        detectDragGestures(onDragStart = { setze(it) }) { change, _ ->
            change.consume()
            setze(change.position)
        }
    }.pointerInput(skalaMinuten) {
        detectTapGestures { p ->
            val dx = p.x - size.width / 2f
            val dy = p.y - size.height / 2f
            var grad = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
            grad = (360.0 - grad) % 360.0
            val schritt = if (skalaMinuten <= 10) 15_000L else if (skalaMinuten <= 30) 30_000L else 60_000L
            val roh = grad / 360.0 * skalaMinuten * 60_000.0
            aktuell?.invoke(((roh / schritt).roundToLong() * schritt).coerceIn(schritt, skalaMinuten * 60_000L))
        }
    }
    Canvas(modifier.then(bedienung).semantics { contentDescription = "Zifferblatt" }) {
        drawIntoCanvas {
            ZifferblattZeichner.zeichne(
                it.nativeCanvas, size.width, size.height, restMs, dauerMs, skalaMinuten, farbe, mitZahlen
            )
        }
    }
}

/** Runder Knopf mit Inhalt (Symbol oder Text). */
@Composable
fun RundKnopf(
    beschreibung: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    groesse: Dp = 64.dp,
    farbe: Color = Karte,
    aktiviert: Boolean = true,
    inhalt: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(groesse)
            .shadow(if (farbe == Color.Transparent) 0.dp else 3.dp, CircleShape)
            .clip(CircleShape)
            .background(farbe)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
        content = inhalt
    )
}

/** Auswahl-Chip (bewusst selbst gebaut – schlicht und auf jedem Android gleich). */
@Composable
fun Auswahl(
    text: String,
    gewaehlt: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    schrift: TextUnit = 15.sp,
    vorne: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (gewaehlt) Text1 else Flaeche)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (vorne != null) {
                vorne()
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            }
            Text(text, color = if (gewaehlt) Color.White else Text1, fontSize = schrift, fontWeight = FontWeight.Medium)
        }
    }
}

/** Kleiner Farbpunkt, z. B. für die Farbauswahl. */
@Composable
fun FarbPunkt(farbe: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(16.dp).clip(CircleShape).background(farbe))
}

/** Pfeil-/Symbolzeichnungen für Knöpfe, einfach und scharf in jeder Größe. */
enum class Symbol { START, PAUSE, ZURUECK, PLUS, MINUS, ZURUECK_PFEIL, ZAHNRAD, STIFT, LAUTSPRECHER, STOPP }

@Composable
fun SymbolBild(symbol: Symbol, farbe: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
        val linie = s * 0.1f
        when (symbol) {
            Symbol.START -> drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(p(0.3f, 0.18f).x, p(0.3f, 0.18f).y); lineTo(p(0.84f, 0.5f).x, p(0.84f, 0.5f).y); lineTo(p(0.3f, 0.82f).x, p(0.3f, 0.82f).y); close()
            }, farbe)
            Symbol.PAUSE -> {
                drawRect(farbe, topLeft = p(0.22f, 0.18f), size = androidx.compose.ui.geometry.Size(s * 0.2f, s * 0.64f))
                drawRect(farbe, topLeft = p(0.58f, 0.18f), size = androidx.compose.ui.geometry.Size(s * 0.2f, s * 0.64f))
            }
            Symbol.STOPP -> drawRect(farbe, topLeft = p(0.22f, 0.22f), size = androidx.compose.ui.geometry.Size(s * 0.56f, s * 0.56f))
            Symbol.PLUS -> {
                drawLine(farbe, p(0.5f, 0.2f), p(0.5f, 0.8f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(farbe, p(0.2f, 0.5f), p(0.8f, 0.5f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Symbol.MINUS -> drawLine(farbe, p(0.2f, 0.5f), p(0.8f, 0.5f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
            Symbol.ZURUECK -> {
                drawArc(
                    farbe, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                    topLeft = p(0.2f, 0.2f), size = androidx.compose.ui.geometry.Size(s * 0.6f, s * 0.6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(linie * 0.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.66f, 0.1f).x, p(0.66f, 0.1f).y); lineTo(p(0.8f, 0.28f).x, p(0.8f, 0.28f).y); lineTo(p(0.58f, 0.34f).x, p(0.58f, 0.34f).y); close()
                }, farbe)
            }
            Symbol.ZURUECK_PFEIL -> {
                drawLine(farbe, p(0.2f, 0.5f), p(0.8f, 0.5f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(farbe, p(0.2f, 0.5f), p(0.45f, 0.25f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(farbe, p(0.2f, 0.5f), p(0.45f, 0.75f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Symbol.ZAHNRAD -> {
                val m = p(0.5f, 0.5f)
                for (i in 0 until 8) {
                    val w = Math.toRadians(i * 45.0)
                    drawLine(
                        farbe, m + Offset((kotlin.math.cos(w) * s * 0.22f).toFloat(), (kotlin.math.sin(w) * s * 0.22f).toFloat()),
                        m + Offset((kotlin.math.cos(w) * s * 0.4f).toFloat(), (kotlin.math.sin(w) * s * 0.4f).toFloat()),
                        linie * 1.3f, androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                drawCircle(farbe, radius = s * 0.27f, center = m)
                drawCircle(Color.White, radius = s * 0.11f, center = m)
            }
            Symbol.STIFT -> {
                drawLine(farbe, p(0.25f, 0.75f), p(0.72f, 0.28f), linie * 1.6f, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(farbe, p(0.18f, 0.82f), p(0.25f, 0.75f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            Symbol.LAUTSPRECHER -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.15f, 0.38f).x, p(0.15f, 0.38f).y); lineTo(p(0.32f, 0.38f).x, p(0.32f, 0.38f).y)
                    lineTo(p(0.52f, 0.2f).x, p(0.52f, 0.2f).y); lineTo(p(0.52f, 0.8f).x, p(0.52f, 0.8f).y)
                    lineTo(p(0.32f, 0.62f).x, p(0.32f, 0.62f).y); lineTo(p(0.15f, 0.62f).x, p(0.15f, 0.62f).y); close()
                }, farbe)
                drawArc(
                    farbe, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = p(0.42f, 0.3f), size = androidx.compose.ui.geometry.Size(s * 0.4f, s * 0.4f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(linie * 0.7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}
