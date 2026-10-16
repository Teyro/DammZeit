package de.oejendorferdamm.dammzeit.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.data.Favorit
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.SKALEN
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import de.oejendorferdamm.dammzeit.stoppuhr.Ansicht
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung
import java.util.Calendar

// ---------------------------------------------------------------- Gemeinsam

/** Zeit einstellen und starten; passt die Skala an, wenn die Zeit nicht aufs Zifferblatt passt. */
fun zeitStarten(context: Context, dauerMs: Long) {
    val minuten = ((dauerMs + 59_999) / 60_000).toInt()
    val skala = Speicher.einstellungen.value.skalaMinuten
    if (minuten > skala) {
        val neu = SKALEN.firstOrNull { it >= minuten } ?: SKALEN.last()
        Speicher.aendereEinstellungen { it.copy(skalaMinuten = neu) }
    }
    TimerSteuerung.setzeDauer(context, dauerMs, starten = true)
}

// ---------------------------------------------------------------- Favoriten

/** Farbe, in der ein Favorit gezeigt wird. */
fun favoritFarbe(f: Favorit): Color = if (f.sanduhr) sandFarbe(f.dauerMs) else Color(
    ZifferblattZeichner.sektorFarbe(f.farbe, f.eigeneFarbe, f.dauerMs, f.dauerMs)
)

/**
 * Favoriten: gespeicherte Zeiten mit Namen, Farbe und Ansicht (Uhr oder Sanduhr) – ein Tipp
 * stellt alles ein und startet. Unten lässt sich die aktuelle Uhr als neuer Favorit speichern.
 */
@Composable
fun FavoritenDialog(onSchliessen: () -> Unit) {
    val context = LocalContext.current
    val favoriten by Speicher.favoriten.collectAsState()
    val timer by Speicher.timer.collectAsState()
    val einstellungen by Speicher.einstellungen.collectAsState()
    val sanduhr by Ansicht.sanduhr.collectAsState()
    var name by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onSchliessen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.82f).shadow(16.dp, RoundedCornerShape(36.dp)).clip(RoundedCornerShape(36.dp))
                .background(HellerGrund).clickable(remember { MutableInteractionSource() }, null) {}.padding(40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("★  Favoriten", color = Text1, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RundKnopf("Schließen", onSchliessen, groesse = 72.dp, farbe = Flaeche) { SymbolBild(Symbol.SCHLIESSEN, Text1, Modifier.size(30.dp)) }
            }
            Spacer(Modifier.height(20.dp))
            if (favoriten.isEmpty()) {
                Text("Noch keine Favoriten. Unten die aktuelle Uhr speichern – z. B. „Lesezeit“ oder „Aufräumen“.", color = Text2, fontSize = 24.sp)
            }
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                favoriten.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Karte)
                            .clickable(role = Role.Button) {
                                Speicher.aendereEinstellungen { it.copy(farbe = f.farbe, eigeneFarbe = f.eigeneFarbe) }
                                Ansicht.stoppuhr.value = false
                                Ansicht.laerm.value = false
                                Ansicht.sanduhr.value = f.sanduhr
                                zeitStarten(context, f.dauerMs)
                                onSchliessen()
                            }
                            .semantics { contentDescription = "Favorit ${f.name}" }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(56.dp).clip(CircleShape).background(favoritFarbe(f)), contentAlignment = Alignment.Center) {
                            Text(if (f.sanduhr) "⏳" else "◔", color = Color.White, fontSize = 26.sp)
                        }
                        Spacer(Modifier.width(20.dp))
                        Text(f.name, color = Text1, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(formatiereDauer(f.dauerMs), color = Text2, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(16.dp))
                        RundKnopf("${f.name} löschen", { Speicher.speichereFavoriten(favoriten - f) }, groesse = 60.dp, farbe = Flaeche) {
                            SymbolBild(Symbol.LOESCHEN, Text2, Modifier.size(28.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Aktuelle Uhr speichern: ${formatiereDauer(timer.dauerMs)} · ${if (sanduhr) "Sanduhr" else einstellungen.farbe.bezeichnung}",
                color = Text2, fontSize = 22.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { name = it.take(40) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Name, z. B. Lesezeit", fontSize = 20.sp) }, textStyle = TextStyle(fontSize = 28.sp)
                )
                Spacer(Modifier.width(18.dp))
                GrosserKnopf("Speichern", Akzent, Color.White, Modifier.width(240.dp).height(84.dp)) {
                    val n = name.trim()
                    if (n.isNotEmpty()) {
                        val neu = Favorit(n, timer.dauerMs, einstellungen.farbe, einstellungen.eigeneFarbe, sanduhr)
                        Speicher.speichereFavoriten(favoriten.filter { it.name != n } + neu)
                        name = ""
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Bis zur Pause

/** Nächste Pausen-/Stundenend-Zeit heute (ms seit 1970) mit ihrem Text – oder null. */
fun naechstePause(zeiten: List<String>, jetzt: Long = System.currentTimeMillis()): Pair<Long, String>? {
    val kal = Calendar.getInstance()
    return zeiten.mapNotNull { z ->
        val (h, m) = z.split(':').map { it.toInt() }
        kal.timeInMillis = jetzt
        kal.set(Calendar.HOUR_OF_DAY, h); kal.set(Calendar.MINUTE, m); kal.set(Calendar.SECOND, 0); kal.set(Calendar.MILLISECOND, 0)
        // mindestens eine Minute Vorlauf, sonst lohnt es nicht
        kal.timeInMillis.takeIf { it - jetzt >= 60_000L }?.let { it to z }
    }.minByOrNull { it.first }
}

/** Knopf „Bis zur Pause“: stellt die Uhr auf die Zeit bis zum nächsten Klingeln und startet sie. */
@Composable
fun BisZurPauseKnopf(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val einstellungen by Speicher.einstellungen.collectAsState()
    val jetzt by rememberMinute()
    val naechste = naechstePause(einstellungen.pausenZeiten, jetzt) ?: return
    Box(
        modifier.shadow(3.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Karte)
            .clickable(role = Role.Button) { naechstePause(Speicher.einstellungen.value.pausenZeiten)?.let { (ende, _) -> zeitStarten(context, ende - System.currentTimeMillis()) } }
            .semantics { contentDescription = "Bis zur Pause um ${naechste.second}" }
            .padding(horizontal = 30.dp, vertical = 16.dp)
    ) {
        Text("⏰  Bis zur Pause · ${naechste.second}", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Uhrzeit, die sich einmal pro Minute aktualisiert (für Knöpfe, die von der Uhrzeit abhängen). */
@Composable
private fun rememberMinute(): androidx.compose.runtime.State<Long> {
    val jetzt = remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            jetzt.longValue = System.currentTimeMillis()
        }
    }
    return jetzt
}

// ---------------------------------------------------------------- Große Ziffern

/**
 * Die Restzeit als riesige Ziffern über die ganze Fläche – von hinten im Raum lesbar. Darunter ein
 * Balken mit der verbleibenden Zeit. Antippen startet/pausiert wie die Uhr.
 */
@Composable
fun ZiffernAnzeige(restMs: Long, dauerMs: Long, farbe: Color, schrift: Color, klingelnd: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(
        modifier.clickable(remember { MutableInteractionSource() }, null) { TimerSteuerung.antippen(context) }
            .semantics { contentDescription = "Restzeit ${formatiereDauer(restMs)}, antippen zum Starten oder Anhalten" },
        contentAlignment = Alignment.Center
    ) {
        val text = if (klingelnd) "0:00" else formatiereDauer(restMs)
        // Schrift so groß, dass der Text die Breite fast füllt (Ziffern ~0,6 em breit)
        val breiteDp = maxWidth.value * 0.9f
        val hoeheDp = maxHeight.value * 0.62f
        val groesse = minOf(breiteDp / (text.length * 0.6f), hoeheDp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text, color = if (klingelnd) Akzent else farbe, fontSize = groesse.sp, fontWeight = FontWeight.Black,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
            val anteil = if (dauerMs > 0) (restMs.toFloat() / dauerMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.width((breiteDp * 0.9f).dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(schrift.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxWidth(anteil).height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (klingelnd) Akzent else farbe))
            }
            if (klingelnd) {
                Spacer(Modifier.height(20.dp))
                Text("Zeit ist um!", color = Akzent, fontSize = 64.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
