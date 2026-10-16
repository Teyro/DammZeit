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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
                drawIntoCanvas { c ->
                    val n = c.nativeCanvas
                    n.save()
                    n.rotate(drehung.value, size.width / 2f, size.height / 2f)
                    SanduhrZeichner.zeichne(n, size.width, size.height, anteil, farbe.toArgb(), laeuft && anteil > 0f, riesel, if (klingelnd) puls else 0f)
                    n.restore()
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
fun SandKnopf(z: SandZeit, gewaehlt: Boolean, onClick: () -> Unit) {
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

