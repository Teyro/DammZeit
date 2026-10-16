package de.oejendorferdamm.dammzeit.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.laerm.Pegelmesser
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.ton.TonSpieler

private val GRUEN = Color(0xFF2EA043)
private val GELB = Color(0xFFF2B705)
private val ROT = Color(0xFFE01E3C)

private enum class Stufe { GRUEN, GELB, ROT }

/**
 * Lärmampel: misst die Lautstärke im Raum (nur den Pegel – nichts wird aufgenommen) und zeigt eine
 * große Ampel. Rot erst, wenn es eine Sekunde lang zu laut ist (ein Husten reicht nicht); zurück auf
 * Grün/Gelb erst nach 2 Sekunden Ruhe. Optional ein kurzer Ton bei Rot.
 */
@Composable
fun LaermAnsicht(schrift: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val einstellungen by Speicher.einstellungen.collectAsState()
    var erlaubt by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val anfrage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { erlaubt = it }
    LaunchedEffect(Unit) { if (!erlaubt) anfrage.launch(Manifest.permission.RECORD_AUDIO) }

    var pegel by remember { mutableFloatStateOf(0f) }
    var stufe by remember { mutableStateOf(Stufe.GRUEN) }
    var zuLaut by remember { mutableIntStateOf(0) }
    val spieler = remember { TonSpieler(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { spieler.stopp() } }

    // Bildschirm bleibt an, solange die Ampel zu sehen ist
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    if (erlaubt) LaunchedEffect(Unit) {
        var lautSeit = 0L
        var ruhigSeit = 0L
        var tonGespielt = 0L
        Pegelmesser.pegel().collect { p ->
            val jetzt = System.currentTimeMillis()
            // Den eigenen Warnton nicht mitmessen
            if (jetzt - tonGespielt < 1500) return@collect
            pegel = p
            val e = Speicher.einstellungen.value
            val ziel = when {
                p >= e.laermRot -> Stufe.ROT
                p >= e.laermGelb -> Stufe.GELB
                else -> Stufe.GRUEN
            }
            when {
                ziel == Stufe.ROT -> {
                    ruhigSeit = 0L
                    if (lautSeit == 0L) lautSeit = jetzt
                    if (stufe != Stufe.ROT && jetzt - lautSeit >= 1000) {
                        stufe = Stufe.ROT
                        zuLaut++
                        if (e.laermTon && jetzt - tonGespielt > 8000) {
                            tonGespielt = jetzt
                            spieler.spiele(Ton.GLOCKE, wiederholen = false, lautstaerke = e.lautstaerke)
                        }
                    }
                }
                else -> {
                    lautSeit = 0L
                    if (stufe == Stufe.ROT) {
                        if (ruhigSeit == 0L) ruhigSeit = jetzt
                        if (jetzt - ruhigSeit >= 2000) { stufe = ziel; ruhigSeit = 0L }
                    } else stufe = ziel
                }
            }
        }
    }

    Row(modifier.padding(horizontal = 50.dp, vertical = 40.dp).padding(top = 60.dp), verticalAlignment = Alignment.CenterVertically) {
        // --- Die Ampel ---
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Ampel(stufe, Modifier.fillMaxHeight(0.95f).width(320.dp))
        }
        Spacer(Modifier.width(40.dp))
        // --- Pegel und Einstellungen ---
        Column(Modifier.width(720.dp)) {
            if (!erlaubt) {
                Text("Die Lärmampel braucht das Mikrofon.", color = schrift, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("Es wird nichts aufgenommen oder gespeichert – nur gemessen, wie laut es gerade ist.", color = schrift.copy(alpha = 0.75f), fontSize = 24.sp)
                Spacer(Modifier.height(24.dp))
                GrosserKnopf("Mikrofon erlauben", Akzent, Color.White) { anfrage.launch(Manifest.permission.RECORD_AUDIO) }
                return@Column
            }
            Text(
                when (stufe) { Stufe.GRUEN -> "Schön leise"; Stufe.GELB -> "Etwas leiser, bitte"; Stufe.ROT -> "Zu laut!" },
                color = when (stufe) { Stufe.GRUEN -> GRUEN; Stufe.GELB -> GELB; Stufe.ROT -> ROT },
                fontSize = 64.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(20.dp))
            Pegelbalken(pegel, einstellungen.laermGelb, einstellungen.laermRot, Modifier.fillMaxWidth().height(56.dp))
            Spacer(Modifier.height(28.dp))
            Regler("Ab hier gelb", einstellungen.laermGelb, 10f..90f) { v -> Speicher.aendereEinstellungen { it.copy(laermGelb = v, laermRot = maxOf(it.laermRot, v + 5)) } }
            Regler("Ab hier rot", einstellungen.laermRot, 15f..98f) { v -> Speicher.aendereEinstellungen { it.copy(laermRot = v, laermGelb = minOf(it.laermGelb, v - 5)) } }
            Spacer(Modifier.height(10.dp))
            Schalter("Glocke, wenn es rot wird", einstellungen.laermTon) { an -> Speicher.aendereEinstellungen { it.copy(laermTon = an) } }
            Spacer(Modifier.height(14.dp))
            Text(
                (if (zuLaut > 0) "Seit dem Öffnen $zuLaut× zu laut. " else "") +
                    "Es wird nichts aufgenommen oder gespeichert – nur der Pegel gemessen.",
                color = schrift.copy(alpha = 0.7f), fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun Regler(name: String, wert: Int, bereich: ClosedFloatingPointRange<Float>, setze: (Int) -> Unit) {
    var lokal by remember(wert) { mutableFloatStateOf(wert.toFloat()) }
    Text("$name: ${lokal.toInt()}", color = Text2, fontSize = 22.sp)
    Slider(
        value = lokal, onValueChange = { lokal = it }, onValueChangeFinished = { setze(lokal.toInt()) },
        valueRange = bereich, colors = SliderDefaults.colors(thumbColor = Text1, activeTrackColor = Text1)
    )
}

/** Große Ampel: drei Lichter, das aktive leuchtet hell mit Schein, die anderen sind dunkel. */
@Composable
private fun Ampel(stufe: Stufe, modifier: Modifier) {
    val rot by animateColorAsState(if (stufe == Stufe.ROT) ROT else lerp(ROT, Color.Black, 0.75f), tween(300), label = "rot")
    val gelb by animateColorAsState(if (stufe == Stufe.GELB) GELB else lerp(GELB, Color.Black, 0.75f), tween(300), label = "gelb")
    val gruen by animateColorAsState(if (stufe == Stufe.GRUEN) GRUEN else lerp(GRUEN, Color.Black, 0.75f), tween(300), label = "gruen")
    Canvas(modifier.semantics { contentDescription = "Lärmampel: " + stufe.name.lowercase() }) {
        val b = minOf(size.width, size.height / 2.6f)
        val h = b * 2.6f
        val x = (size.width - b) / 2f
        val y = (size.height - h) / 2f
        // Gehäuse
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(x + b * 0.03f, y + b * 0.05f), Size(b, h), CornerRadius(b * 0.22f))
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF3A3F47), Color(0xFF22262C)), x, x + b), Offset(x, y), Size(b, h), CornerRadius(b * 0.22f))
        val r = b * 0.34f
        listOf(rot to (stufe == Stufe.ROT), gelb to (stufe == Stufe.GELB), gruen to (stufe == Stufe.GRUEN)).forEachIndexed { i, (farbe, an) ->
            val m = Offset(x + b / 2f, y + h * (i + 0.5f) / 3f)
            if (an) drawCircle(Brush.radialGradient(listOf(farbe.copy(alpha = 0.55f), Color.Transparent), m, r * 1.9f), r * 1.9f, m)
            drawCircle(Color.Black.copy(alpha = 0.5f), r * 1.08f, m)
            drawCircle(Brush.radialGradient(listOf(lerp(farbe, Color.White, if (an) 0.45f else 0.1f), farbe), Offset(m.x - r * 0.3f, m.y - r * 0.3f), r * 1.3f), r, m)
        }
    }
}

/** Pegel als Balken mit Markierungen für Gelb und Rot. */
@Composable
private fun Pegelbalken(pegel: Float, gelb: Int, rot: Int, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(Color(0x22000000))) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val farbe = when { pegel >= rot -> ROT; pegel >= gelb -> GELB; else -> GRUEN }
            drawRoundRect(farbe, Offset.Zero, Size(size.width * pegel / 100f, size.height), CornerRadius(size.height / 2f))
            for ((grenze, f) in listOf(gelb to GELB, rot to ROT)) {
                val gx = size.width * grenze / 100f
                drawLine(f, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 6f)
            }
        }
    }
}
