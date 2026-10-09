package de.oejendorferdamm.dammzeit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import de.oejendorferdamm.dammzeit.data.Einstellungen
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Laufrichtung
import kotlin.math.atan2
import kotlin.math.roundToLong

val HellerGrund = Color(0xFFECEEF1)
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
 * Das Zifferblatt. Bedienung wie beim echten Time Timer: an der Scheibe ziehen stellt die Zeit
 * ein ([onZiehen] während des Ziehens, [onLoslassen] am Ende), kurzes Antippen ruft [onTippen].
 */
@Composable
fun Zifferblatt(
    restMs: Long,
    dauerMs: Long,
    einstellungen: Einstellungen,
    modifier: Modifier = Modifier,
    mitZahlen: Boolean = einstellungen.zahlenAnzeigen,
    onZiehen: ((Long) -> Unit)? = null,
    onLoslassen: (() -> Unit)? = null,
    onTippen: (() -> Unit)? = null
) {
    val skala = einstellungen.skalaMinuten
    val ziehen by rememberUpdatedState(onZiehen)
    val loslassen by rememberUpdatedState(onLoslassen)
    val tippen by rememberUpdatedState(onTippen)
    val imUhrzeigersinn = einstellungen.laufrichtung == Laufrichtung.IM_UHRZEIGERSINN
    val bedienung = if (onZiehen == null && onTippen == null) Modifier else Modifier.pointerInput(skala, imUhrzeigersinn) {
        fun dauerFuer(p: Offset): Long {
            val dx = p.x - size.width / 2f
            val dy = p.y - size.height / 2f
            // Winkel ab 12 Uhr in Laufrichtung der Zahlen auf dem Zifferblatt.
            var grad = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
            grad = if (imUhrzeigersinn) (grad + 360.0) % 360.0 else (360.0 - grad) % 360.0
            val schritt = if (skala <= 10) 15_000L else if (skala <= 30) 30_000L else 60_000L
            val roh = grad / 360.0 * skala * 60_000.0
            return ((roh / schritt).roundToLong() * schritt).coerceIn(schritt, skala * 60_000L)
        }
        awaitEachGesture {
            val unten = awaitFirstDown()
            var gezogen = false
            while (true) {
                val ereignis = awaitPointerEvent()
                val aenderung = ereignis.changes.firstOrNull { it.id == unten.id } ?: break
                if (!gezogen && (aenderung.position - unten.position).getDistance() > viewConfiguration.touchSlop) {
                    gezogen = true
                }
                if (gezogen) {
                    ziehen?.invoke(dauerFuer(aenderung.position))
                    aenderung.consume()
                }
                if (!aenderung.pressed) break
            }
            if (gezogen) loslassen?.invoke() else tippen?.invoke()
        }
    }
    Canvas(modifier.then(bedienung).semantics { contentDescription = "Uhr" }) {
        drawIntoCanvas {
            ZifferblattZeichner.zeichne(
                it.nativeCanvas, size.width, size.height, restMs, dauerMs, skala,
                einstellungen.farbe, einstellungen.eigeneFarbe, einstellungen.hintergrund, mitZahlen,
                imUhrzeigersinn = imUhrzeigersinn
            )
        }
    }
}

/** Knöpfe geben beim Drücken sichtbar nach und federn zurück. */
@Composable
fun Modifier.drueckFeder(quelle: MutableInteractionSource): Modifier {
    val gedrueckt by quelle.collectIsPressedAsState()
    val faktor by animateFloatAsState(
        if (gedrueckt) 0.92f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "drueckFeder"
    )
    return graphicsLayer { scaleX = faktor; scaleY = faktor }
}

/** Was der Startknopf gerade anbietet. */
enum class StartZustand(val text: String, val symbol: Symbol) {
    START("Start", Symbol.START),
    PAUSE("Pause", Symbol.PAUSE),
    WEITER("Weiter", Symbol.START),
    STOPP("Stopp", Symbol.STOPP)
}

/**
 * Der große Startknopf unter der Uhr: Pille in der Farbe der Zeitscheibe (Farbe gleitet mit),
 * Symbol und Text wechseln mit einer weichen Überblendung.
 */
@Composable
fun StartKnopf(zustand: StartZustand, farbe: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val quelle = remember { MutableInteractionSource() }
    val hintergrund by animateColorAsState(farbe, tween(450), label = "startFarbe")
    Box(
        modifier = modifier
            .drueckFeder(quelle)
            .height(96.dp)
            .width(300.dp)
            .shadow(10.dp, RoundedCornerShape(50), ambientColor = hintergrund, spotColor = hintergrund)
            .clip(RoundedCornerShape(50))
            .background(hintergrund)
            .clickable(interactionSource = quelle, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = zustand.text },
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = zustand,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = 0.7f)) togetherWith
                    (fadeOut(tween(160)) + scaleOut(tween(200), targetScale = 0.7f))
            },
            label = "startInhalt"
        ) { z ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBild(z.symbol, Color.White, Modifier.size(38.dp))
                Spacer(Modifier.width(16.dp))
                Text(z.text, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
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
    val quelle = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .drueckFeder(quelle)
            .size(groesse)
            .shadow(if (farbe == Color.Transparent) 0.dp else 3.dp, CircleShape)
            .clip(CircleShape)
            .background(farbe)
            .clickable(interactionSource = quelle, indication = androidx.compose.material3.ripple(), enabled = aktiviert, role = Role.Button, onClick = onClick)
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
enum class Symbol { START, PAUSE, ZURUECK, PLUS, MINUS, ZURUECK_PFEIL, ZAHNRAD, STIFT, LAUTSPRECHER, STOPP, SCHLIESSEN, FAHNE, DISKETTE, POKAL, LOESCHEN }

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
            Symbol.SCHLIESSEN -> {
                drawLine(farbe, p(0.25f, 0.25f), p(0.75f, 0.75f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(farbe, p(0.75f, 0.25f), p(0.25f, 0.75f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
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
            Symbol.FAHNE -> {
                drawLine(farbe, p(0.28f, 0.15f), p(0.28f, 0.88f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.32f, 0.16f).x, p(0.32f, 0.16f).y); lineTo(p(0.82f, 0.16f).x, p(0.82f, 0.16f).y)
                    lineTo(p(0.7f, 0.34f).x, p(0.7f, 0.34f).y); lineTo(p(0.82f, 0.52f).x, p(0.82f, 0.52f).y); lineTo(p(0.32f, 0.52f).x, p(0.32f, 0.52f).y); close()
                }, farbe)
            }
            Symbol.DISKETTE -> {
                val st = androidx.compose.ui.graphics.drawscope.Stroke(linie * 0.85f, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.18f, 0.16f).x, p(0.18f, 0.16f).y); lineTo(p(0.7f, 0.16f).x, p(0.7f, 0.16f).y); lineTo(p(0.84f, 0.3f).x, p(0.84f, 0.3f).y)
                    lineTo(p(0.84f, 0.84f).x, p(0.84f, 0.84f).y); lineTo(p(0.18f, 0.84f).x, p(0.18f, 0.84f).y); close()
                }, farbe, style = st)
                drawRect(farbe, topLeft = p(0.3f, 0.16f), size = androidx.compose.ui.geometry.Size(s * 0.34f, s * 0.2f))
                drawRect(farbe, topLeft = p(0.3f, 0.56f), size = androidx.compose.ui.geometry.Size(s * 0.42f, s * 0.28f), style = st)
            }
            Symbol.POKAL -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.28f, 0.15f).x, p(0.28f, 0.15f).y); lineTo(p(0.72f, 0.15f).x, p(0.72f, 0.15f).y)
                    cubicTo(p(0.72f, 0.5f).x, p(0.72f, 0.5f).y, p(0.6f, 0.58f).x, p(0.6f, 0.58f).y, p(0.5f, 0.58f).x, p(0.5f, 0.58f).y)
                    cubicTo(p(0.4f, 0.58f).x, p(0.4f, 0.58f).y, p(0.28f, 0.5f).x, p(0.28f, 0.5f).y, p(0.28f, 0.15f).x, p(0.28f, 0.15f).y); close()
                }, farbe)
                val st = androidx.compose.ui.graphics.drawscope.Stroke(linie * 0.7f)
                drawArc(farbe, 90f, 180f, false, topLeft = p(0.14f, 0.2f), size = androidx.compose.ui.geometry.Size(s * 0.2f, s * 0.22f), style = st)
                drawArc(farbe, -90f, 180f, false, topLeft = p(0.66f, 0.2f), size = androidx.compose.ui.geometry.Size(s * 0.2f, s * 0.22f), style = st)
                drawRect(farbe, topLeft = p(0.45f, 0.56f), size = androidx.compose.ui.geometry.Size(s * 0.1f, s * 0.16f))
                drawRect(farbe, topLeft = p(0.3f, 0.72f), size = androidx.compose.ui.geometry.Size(s * 0.4f, s * 0.12f))
            }
            Symbol.LOESCHEN -> {
                drawLine(farbe, p(0.2f, 0.26f), p(0.8f, 0.26f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(p(0.27f, 0.3f).x, p(0.27f, 0.3f).y); lineTo(p(0.32f, 0.84f).x, p(0.32f, 0.84f).y)
                    lineTo(p(0.68f, 0.84f).x, p(0.68f, 0.84f).y); lineTo(p(0.73f, 0.3f).x, p(0.73f, 0.3f).y)
                }, farbe, style = androidx.compose.ui.graphics.drawscope.Stroke(linie * 0.85f))
                drawLine(farbe, p(0.4f, 0.14f), p(0.6f, 0.14f), linie, androidx.compose.ui.graphics.StrokeCap.Round)
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

/** Breiter Knopf mit Text. */
@Composable
fun GrosserKnopf(text: String, farbe: Color, textFarbe: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(farbe)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textFarbe, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Zwei Punkte zeigen, wie die Farbe am Anfang und kurz vor Schluss aussieht. */
@Composable
fun FarbVorschau(modus: FarbModus, eigeneFarbe: Int) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
        FarbPunkt(Color(ZifferblattZeichner.sektorFarbe(modus, eigeneFarbe, 30 * 60_000L, 30 * 60_000L)))
        FarbPunkt(Color(ZifferblattZeichner.sektorFarbe(modus, eigeneFarbe, 10_000L, 30 * 60_000L)))
    }
}
