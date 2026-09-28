package de.oejendorferdamm.dammzeit.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.data.Einstellungen
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.update.ApkPruefung
import de.oejendorferdamm.dammzeit.update.UpdateClient
import de.oejendorferdamm.dammzeit.update.UpdateInfo
import de.oejendorferdamm.dammzeit.update.installationsIntentFuer
import de.oejendorferdamm.dammzeit.update.kannUnbekannteQuellenInstallieren
import de.oejendorferdamm.dammzeit.update.oeffneUnbekannteQuellenEinstellungen
import de.oejendorferdamm.dammzeit.update.pruefeUpdateApk
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer
import de.oejendorferdamm.dammzeit.widget.WidgetGross
import de.oejendorferdamm.dammzeit.widget.WidgetKlein
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EinstellungenSeite(
    einstellungen: Einstellungen,
    version: String,
    updateInfo: UpdateInfo?,
    pruefungLaeuft: Boolean,
    bereitsAktuell: Boolean,
    onJetztPruefen: () -> Unit,
    onZurueck: () -> Unit
) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Hintergrund).systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RundKnopf("Zurück", onZurueck, groesse = 52.dp) { SymbolBild(Symbol.ZURUECK_PFEIL, Text1, Modifier.size(26.dp)) }
                Spacer(Modifier.size(14.dp))
                Text("Einstellungen", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))

            UpdateKarte(version, updateInfo, pruefungLaeuft, bereitsAktuell, einstellungen.autoUpdate, onJetztPruefen)
            Spacer(Modifier.height(16.dp))

            Abschnittskarte("Signalton") {
                Text("Wie lange der Ton am Ende klingt", color = Text2, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 30, 60, 120).forEach { s ->
                        Auswahl(if (s < 60) "$s Sekunden" else "${s / 60} Minute${if (s > 60) "n" else ""}", einstellungen.klingelSekunden == s, {
                            Speicher.aendereEinstellungen { it.copy(klingelSekunden = s) }
                        })
                    }
                }
                Schalter("Vibrieren", "Zusätzlich zum Ton (bei Geräten mit Vibration)", einstellungen.vibration) { an ->
                    Speicher.aendereEinstellungen { it.copy(vibration = an) }
                }
            }
            Spacer(Modifier.height(16.dp))

            Abschnittskarte("Anzeige") {
                Text("Farbe für neue Timer", color = Text2, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarbModus.entries.forEach { modus ->
                        Auswahl(modus.bezeichnung, einstellungen.standardFarbe == modus, {
                            Speicher.aendereEinstellungen { it.copy(standardFarbe = modus) }
                        }) { FarbVorschau(modus) }
                    }
                }
                Schalter("Restzeit als Zahl anzeigen", "Unter dem Zifferblatt, z. B. 12:34", einstellungen.zahlAnzeigen) { an ->
                    Speicher.aendereEinstellungen { it.copy(zahlAnzeigen = an) }
                }
                Schalter("Bildschirm anlassen", "Solange ein Timer groß angezeigt wird und läuft", einstellungen.bildschirmAn) { an ->
                    Speicher.aendereEinstellungen { it.copy(bildschirmAn = an) }
                }
            }
            Spacer(Modifier.height(16.dp))

            Abschnittskarte("Widgets") {
                Text(
                    "Das Widget zeigt den zuletzt gestarteten oder geöffneten Timer. Du kannst es auch " +
                        "über den Startbildschirm hinzufügen (lange drücken → Widgets → DammZeit).",
                    color = Text2, fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                WidgetVorschau()
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GrosserKnopf("Widget 2 × 2", Flaeche, Text1, Modifier.weight(1f)) { widgetAnheften(context, gross = false) }
                    GrosserKnopf("Widget 4 × 4", Flaeche, Text1, Modifier.weight(1f)) { widgetAnheften(context, gross = true) }
                }
            }
            Spacer(Modifier.height(16.dp))

            Abschnittskarte("Über DammZeit") {
                Text(
                    "DammZeit ist freie Software unter der GNU General Public License, Version 3. " +
                        "Du darfst sie nutzen, weitergeben und verändern.",
                    color = Text2, fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                GrosserKnopf("Quellcode auf GitHub", Flaeche, Text1, Modifier.fillMaxWidth()) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Teyro/DammZeit")))
                    } catch (_: Exception) {
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Zeigt beide Widgets genau so, wie sie auf dem Startbildschirm aussehen – mit demselben Code.
 * Wird alle paar Sekunden aufgefrischt, damit man den Ablauf sieht.
 */
@Composable
private fun WidgetVorschau() {
    val context = LocalContext.current
    val timer by Speicher.timer.collectAsState()
    var takt by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            takt++
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        for (gross in listOf(false, true)) {
            AndroidView(
                factory = { FrameLayout(it) },
                modifier = Modifier.size(if (gross) 190.dp else 120.dp).clip(RoundedCornerShape(20.dp)),
                update = { rahmen ->
                    // takt und timer werden gelesen, damit die Vorschau bei Änderungen neu gebaut wird.
                    rahmen.tag = takt to timer
                    val ansicht = WidgetAktualisierer.baue(context, Speicher.widgetTimer(), System.currentTimeMillis(), gross)
                        .apply(context, rahmen)
                    rahmen.removeAllViews()
                    rahmen.addView(ansicht)
                }
            )
        }
    }
}

private fun widgetAnheften(context: Context, gross: Boolean) {
    val manager = AppWidgetManager.getInstance(context)
    val ziel = ComponentName(context, if (gross) WidgetGross::class.java else WidgetKlein::class.java)
    if (manager != null && manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ziel, null, null)
    } else {
        Toast.makeText(context, "Bitte über den Startbildschirm hinzufügen: lange drücken → Widgets", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Abschnittskarte(titel: String, inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Karte).padding(20.dp)
    ) {
        Text(titel, color = Text1, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        inhalt()
    }
}

@Composable
private fun Schalter(titel: String, text: String, an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titel, color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text, color = Text2, fontSize = 13.sp)
        }
        Switch(
            checked = an, onCheckedChange = onAendern,
            colors = SwitchDefaults.colors(checkedTrackColor = Akzent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun UpdateKarte(
    version: String,
    info: UpdateInfo?,
    pruefungLaeuft: Boolean,
    bereitsAktuell: Boolean,
    autoUpdate: Boolean,
    onJetztPruefen: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { UpdateClient() }
    var ladend by remember { mutableStateOf(false) }
    var fortschritt by remember { mutableFloatStateOf(0f) }
    var fehler by remember { mutableStateOf<String?>(null) }

    Abschnittskarte("Version & Updates") {
        Text("Installiert: $version", color = Text2, fontSize = 14.sp)
        Schalter("Automatisch nach Updates suchen", "Kurz nach dem Start, unauffällig mit rotem Punkt", autoUpdate) { an ->
            Speicher.aendereEinstellungen { it.copy(autoUpdate = an) }
        }
        Spacer(Modifier.height(14.dp))
        if (info != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Akzent).padding(16.dp)) {
                Text("Neue Version ${info.version}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.changelog.trim(), color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 8)
                }
                Spacer(Modifier.height(10.dp))
                GrosserKnopf(if (ladend) "Lädt … ${(fortschritt * 100).toInt()} %" else "Jetzt aktualisieren", Color.White, Akzent, Modifier.fillMaxWidth()) {
                    if (ladend) return@GrosserKnopf
                    if (!kannUnbekannteQuellenInstallieren(context)) {
                        Toast.makeText(context, "Bitte DammZeit die Installation erlauben und dann erneut tippen", Toast.LENGTH_LONG).show()
                        oeffneUnbekannteQuellenEinstellungen(context)
                        return@GrosserKnopf
                    }
                    ladend = true
                    fehler = null
                    scope.launch {
                        val ziel = File(context.cacheDir, "dammzeit-update.apk")
                        val ergebnis = client.apkHerunterladen(info.herunterladenUrl, ziel, info.dateigroesseBytes) { fortschritt = it }
                        ladend = false
                        ergebnis.onSuccess { datei ->
                            when (pruefeUpdateApk(context, datei)) {
                                ApkPruefung.IN_ORDNUNG -> context.startActivity(installationsIntentFuer(context, datei))
                                ApkPruefung.ANDERE_SIGNATUR -> fehler = "Das Update ist anders signiert als die installierte Version. " +
                                    "Bitte DammZeit einmal deinstallieren und neu installieren."
                                ApkPruefung.UNGUELTIG -> fehler = "Die heruntergeladene Datei ist keine gültige DammZeit-Installationsdatei."
                            }
                        }.onFailure { fehler = "Herunterladen fehlgeschlagen: ${it.message}" }
                    }
                }
                fehler?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color.White, fontSize = 13.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GrosserKnopf(if (pruefungLaeuft) "Prüft …" else "Jetzt nach Updates suchen", Text1, Color.White, Modifier.weight(1f)) {
                    if (!pruefungLaeuft) onJetztPruefen()
                }
                if (pruefungLaeuft) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(color = Akzent, modifier = Modifier.size(28.dp))
                }
            }
            if (bereitsAktuell && !pruefungLaeuft) {
                Spacer(Modifier.height(8.dp))
                Text("Du hast bereits die neueste Version.", color = Text2, fontSize = 14.sp)
            }
        }
    }
}
