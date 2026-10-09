package de.oejendorferdamm.dammzeit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.stoppuhr.Bestzeit
import de.oejendorferdamm.dammzeit.stoppuhr.StoppuhrSpeicher
import de.oejendorferdamm.dammzeit.stoppuhr.StoppuhrSteuerung
import de.oejendorferdamm.dammzeit.stoppuhr.formatStoppzeit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Gold = Color(0xFFF2B705)
val Silber = Color(0xFFB8BEC6)
val Bronze = Color(0xFFCD7F42)
val Gruen = Color(0xFF2EA043)

private val ZIFFERN = TextStyle(fontFeatureSettings = "tnum")

/** Stoppuhr in der App: große Zeit, Start/Stopp, Runden, Speichern, Bestenliste. */
@Composable
fun StoppuhrAnsicht(schrift: Color, modifier: Modifier = Modifier, onSpeichern: () -> Unit, onBestenliste: () -> Unit) {
    val context = LocalContext.current
    val uhr by StoppuhrSpeicher.uhr.collectAsState()
    val jetzt by rememberJetzt(aktiv = uhr.laeuft)
    val zeit = uhr.zeitMs(jetzt)
    Row(modifier) {
        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Stoppuhr", color = schrift.copy(alpha = 0.7f), fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                formatStoppzeit(zeit, hundertstel = true),
                color = if (uhr.frisch) schrift.copy(alpha = 0.35f) else schrift,
                fontSize = 230.sp, fontWeight = FontWeight.Bold, style = ZIFFERN, maxLines = 1
            )
            val runde = if (uhr.runden.isNotEmpty() && uhr.laeuft) zeit - uhr.runden.last() else null
            Text(
                if (runde != null) "Runde ${uhr.runden.size + 1}:  ${formatStoppzeit(runde)}" else if (uhr.frisch) "Start drücken – los geht's!" else if (!uhr.laeuft) "Gestoppt" else " ",
                color = schrift.copy(alpha = 0.7f), fontSize = 40.sp, style = ZIFFERN
            )
            Spacer(Modifier.height(40.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                RundKnopf("Zurücksetzen", { StoppuhrSteuerung.zuruecksetzen(context) }, groesse = 100.dp, aktiviert = !uhr.frisch) {
                    SymbolBild(Symbol.ZURUECK, if (uhr.frisch) Text2.copy(alpha = 0.4f) else Text1, Modifier.size(46.dp))
                }
                StartStoppKnopf(uhr.laeuft) { StoppuhrSteuerung.startStopp(context) }
                RundKnopf("Runde", { StoppuhrSteuerung.runde(context) }, groesse = 100.dp, aktiviert = uhr.laeuft) {
                    SymbolBild(Symbol.FAHNE, if (uhr.laeuft) Text1 else Text2.copy(alpha = 0.4f), Modifier.size(46.dp))
                }
                RundKnopf("Zeit speichern", onSpeichern, groesse = 100.dp, aktiviert = !uhr.laeuft && !uhr.frisch) {
                    SymbolBild(Symbol.DISKETTE, if (!uhr.laeuft && !uhr.frisch) Text1 else Text2.copy(alpha = 0.4f), Modifier.size(46.dp))
                }
                RundKnopf("Bestenliste", onBestenliste, groesse = 100.dp) {
                    SymbolBild(Symbol.POKAL, Gold, Modifier.size(50.dp))
                }
            }
        }
        // Immer Platz für die Runden lassen, damit die Knöpfe beim ersten Rundendruck nicht wegspringen
        RundenListe(uhr.runden, Modifier.width(560.dp).fillMaxHeight().padding(top = 140.dp, bottom = 40.dp, end = 40.dp))
    }
}

/** Großer Start/Stopp-Knopf: grün zum Starten, rot zum Anhalten, mit Feder beim Drücken. */
@Composable
private fun StartStoppKnopf(laeuft: Boolean, onClick: () -> Unit) {
    val quelle = remember { MutableInteractionSource() }
    val farbe = if (laeuft) Color(0xFFE01E3C) else Gruen
    Row(
        Modifier
            .drueckFeder(quelle)
            .height(128.dp)
            .widthIn(min = 340.dp)
            .shadow(10.dp, RoundedCornerShape(64.dp), ambientColor = farbe, spotColor = farbe)
            .clip(RoundedCornerShape(64.dp))
            .background(Brush.verticalGradient(listOf(lerpFarbe(farbe, Color.White, 0.18f), farbe)))
            .clickable(quelle, null, onClick = onClick)
            .padding(horizontal = 50.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        SymbolBild(if (laeuft) Symbol.STOPP else Symbol.START, Color.White, Modifier.size(52.dp))
        Spacer(Modifier.width(20.dp))
        Text(if (laeuft) "Stopp" else "Start", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold)
    }
}

private fun lerpFarbe(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** Runden: neueste oben; schnellste grün, langsamste rot. */
@Composable
private fun RundenListe(runden: List<Long>, modifier: Modifier) {
    // in Hundertsteln vergleichen, wie angezeigt: gleich aussehende Runden nicht grün und rot färben
    val dauern = runden.mapIndexed { i, t -> (t - (runden.getOrNull(i - 1) ?: 0L)) / 10 * 10 }
    val beste = if (dauern.size > 1 && dauern.min() != dauern.max()) dauern.min() else -1L
    val schlechteste = if (beste >= 0) dauern.max() else -1L
    Column(modifier.shadow(3.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Karte).padding(26.dp)) {
        Text("Runden", color = Text1, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (runden.isEmpty()) Text("Mit der Fahne hältst du Runden fest.", color = Text2, fontSize = 24.sp)
        LazyColumn {
            itemsIndexed(dauern.reversed()) { j, d ->
                val nr = dauern.size - j
                val farbe = when (d) { beste -> Gruen; schlechteste -> Color(0xFFE01E3C); else -> Text1 }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$nr", color = Text2, fontSize = 26.sp, modifier = Modifier.width(60.dp), style = ZIFFERN)
                    Text(formatStoppzeit(d, true), color = farbe, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), style = ZIFFERN)
                    Text(formatStoppzeit(runden[nr - 1]), color = Text2, fontSize = 24.sp, style = ZIFFERN)
                }
            }
        }
    }
}

/** Zeit speichern: Name und Challenge. [onGespeichert] bekommt den Eintrag und seinen Platz. */
@Composable
fun SpeichernDialog(onAbbrechen: () -> Unit, onGespeichert: (Bestzeit, Int) -> Unit) {
    val context = LocalContext.current
    val uhr by StoppuhrSpeicher.uhr.collectAsState()
    val challenges = remember { StoppuhrSpeicher.challenges() }
    var name by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf(challenges.firstOrNull() ?: "") }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null, onClick = onAbbrechen), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 1000.dp).fillMaxWidth(0.8f).shadow(16.dp, RoundedCornerShape(36.dp)).clip(RoundedCornerShape(36.dp))
                .background(HellerGrund).clickable(remember { MutableInteractionSource() }, null) {}.padding(40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBild(Symbol.DISKETTE, Text1, Modifier.size(46.dp))
                Spacer(Modifier.width(18.dp))
                Text("Zeit speichern", color = Text1, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(formatStoppzeit(uhr.zeitMs(System.currentTimeMillis()), true), color = Text1, fontSize = 52.sp, fontWeight = FontWeight.Bold, style = ZIFFERN)
            }
            Spacer(Modifier.height(26.dp))
            OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name", fontSize = 20.sp) }, textStyle = TextStyle(fontSize = 30.sp))
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(challenge, { challenge = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Challenge (z. B. „Hampelmann 20×“)", fontSize = 20.sp) }, textStyle = TextStyle(fontSize = 30.sp))
            if (challenges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    challenges.take(10).forEach { c -> Auswahl(c, c == challenge, { challenge = c }, schrift = 22.sp) }
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                GrosserKnopf("Abbrechen", Karte, Text1, Modifier.weight(1f).height(90.dp), onClick = onAbbrechen)
                GrosserKnopf("Speichern", Gruen, Color.White, Modifier.weight(1f).height(90.dp)) {
                    StoppuhrSteuerung.speichern(context, name, challenge)?.let { (b, platz) -> onGespeichert(b, platz) }
                }
            }
        }
    }
}

/** Bestenliste einer Challenge: Siegertreppchen für die ersten drei (mit Krone), darunter der Rest. */
@Composable
fun Bestenliste(startChallenge: String?, neuId: String?, onSchliessen: () -> Unit) {
    val liste by StoppuhrSpeicher.liste.collectAsState()
    val challenges = remember(liste) { StoppuhrSpeicher.challenges() }
    var challenge by remember { mutableStateOf(startChallenge ?: challenges.firstOrNull() ?: "") }
    val rang = remember(liste, challenge) { StoppuhrSpeicher.rangliste(challenge) }
    var loeschen by remember { mutableStateOf<Bestzeit?>(null) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1C3D5A), Color(0xFF10263A))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 50.dp, vertical = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SymbolBild(Symbol.POKAL, Gold, Modifier.size(60.dp))
                Spacer(Modifier.width(20.dp))
                Text("Bestenliste", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RundKnopf("Schließen", onSchliessen, groesse = 84.dp) { SymbolBild(Symbol.SCHLIESSEN, Text1, Modifier.size(38.dp)) }
            }
            Spacer(Modifier.height(16.dp))
            if (challenges.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Zeiten.\nStoppuhr starten, stoppen und mit der Diskette speichern.", color = Color.White.copy(alpha = 0.8f), fontSize = 38.sp, textAlign = TextAlign.Center)
                }
                return@Column
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                challenges.forEach { c -> Auswahl(c.ifBlank { "Ohne Challenge" }, c == challenge, { challenge = c }, schrift = 24.sp) }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxSize()) {
                Siegertreppchen(rang.take(3), neuId, Modifier.weight(1.25f).fillMaxHeight()) { loeschen = it }
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (rang.size > 3) {
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(rang.drop(3)) { i, b -> ListenZeile(i + 4, b, b.id == neuId) { loeschen = b } }
                        }
                    } else {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(if (rang.size < 3) "Noch Plätze frei auf dem Treppchen!" else "Wer schafft es aufs Treppchen?", color = Color.White.copy(alpha = 0.75f), fontSize = 32.sp, textAlign = TextAlign.Center)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (rang.isNotEmpty()) Text("Einen Eintrag antippen, um ihn zu löschen.", color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
                }
            }
        }
        loeschen?.let { b ->
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable { loeschen = null }, contentAlignment = Alignment.Center) {
                Column(Modifier.width(820.dp).clip(RoundedCornerShape(32.dp)).background(HellerGrund).clickable(remember { MutableInteractionSource() }, null) {}.padding(36.dp)) {
                    Text("„${b.name}“ (${formatStoppzeit(b.ms, true)}) aus der Bestenliste löschen?", color = Text1, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(26.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        GrosserKnopf("Behalten", Karte, Text1, Modifier.weight(1f).height(84.dp)) { loeschen = null }
                        GrosserKnopf("Löschen", Color(0xFFE01E3C), Color.White, Modifier.weight(1f).height(84.dp)) {
                            StoppuhrSpeicher.loeschen(b.id)
                            loeschen = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListenZeile(platz: Int, b: Bestzeit, neu: Boolean, onLoeschen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (neu) Gold.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onLoeschen).padding(horizontal = 26.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$platz.", color = Color.White.copy(alpha = 0.7f), fontSize = 32.sp, modifier = Modifier.width(80.dp))
        Text(b.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatStoppzeit(b.ms, true), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, style = ZIFFERN)
        Spacer(Modifier.width(18.dp))
        Box(Modifier.size(56.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            SymbolBild(Symbol.LOESCHEN, Color.White.copy(alpha = 0.6f), Modifier.size(32.dp))
        }
    }
}

/** Siegertreppchen: 2 – 1 – 3, Gold mit Krone; der neue Eintrag hüpft kurz. */
@Composable
private fun Siegertreppchen(top: List<Bestzeit>, neuId: String?, modifier: Modifier, onAntippen: (Bestzeit) -> Unit) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        val reihenfolge = listOf(1, 0, 2)
        for (i in reihenfolge) {
            val b = top.getOrNull(i)
            val farbe = when (i) { 0 -> Gold; 1 -> Silber; else -> Bronze }
            val hoehe = when (i) { 0 -> 0.62f; 1 -> 0.46f; else -> 0.34f }
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable(enabled = b != null) { b?.let(onAntippen) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
            ) {
                val huepfer = remember { Animatable(1f) }
                LaunchedEffect(b?.id == neuId && b != null) {
                    if (b != null && b.id == neuId) {
                        huepfer.snapTo(0.6f)
                        huepfer.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 260f))
                    }
                }
                if (b != null) {
                    if (i == 0) Krone(Modifier.size(width = 130.dp, height = 90.dp).scale(huepfer.value))
                    Text(b.name, color = Color.White, fontSize = if (i == 0) 44.sp else 36.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.scale(huepfer.value))
                    Text(formatStoppzeit(b.ms, true), color = farbe, fontSize = if (i == 0) 42.sp else 34.sp, fontWeight = FontWeight.Bold, style = ZIFFERN)
                    Spacer(Modifier.height(14.dp))
                } else {
                    Text("frei", color = Color.White.copy(alpha = 0.35f), fontSize = 30.sp)
                    Spacer(Modifier.height(14.dp))
                }
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(hoehe)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                        .background(Brush.verticalGradient(listOf(lerpFarbe(farbe, Color.White, 0.25f), farbe, lerpFarbe(farbe, Color.Black, 0.25f)))),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text("${i + 1}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** Goldene Krone mit Edelsteinen. */
@Composable
fun Krone(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val form = Path().apply {
            moveTo(w * 0.08f, h * 0.88f)
            lineTo(w * 0.04f, h * 0.28f)
            lineTo(w * 0.3f, h * 0.55f)
            lineTo(w * 0.5f, h * 0.08f)
            lineTo(w * 0.7f, h * 0.55f)
            lineTo(w * 0.96f, h * 0.28f)
            lineTo(w * 0.92f, h * 0.88f)
            close()
        }
        drawPath(form, Brush.verticalGradient(listOf(Color(0xFFFFE27A), Gold, Color(0xFFC98E00))))
        drawPath(form, Color(0xFF8A5A00), style = Stroke(w * 0.025f))
        drawRect(Color(0xFFC98E00), topLeft = Offset(w * 0.08f, h * 0.78f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.12f))
        for ((x, y) in listOf(0.04f to 0.24f, 0.5f to 0.06f, 0.96f to 0.24f)) drawCircle(Color(0xFFE01E3C), w * 0.055f, Offset(w * x, h * y))
        drawCircle(Color(0xFF1E6FD9), w * 0.05f, Offset(w * 0.5f, h * 0.66f))
    }
}
