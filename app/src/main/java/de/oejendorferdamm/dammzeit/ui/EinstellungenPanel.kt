package de.oejendorferdamm.dammzeit.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.data.Einstellungen
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.EIGENE_FARBEN
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Hintergrund
import de.oejendorferdamm.dammzeit.model.SKALEN
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.ton.TonSpieler
import de.oejendorferdamm.dammzeit.update.ApkPruefung
import de.oejendorferdamm.dammzeit.update.UpdateClient
import de.oejendorferdamm.dammzeit.update.UpdateInfo
import de.oejendorferdamm.dammzeit.update.installationsIntentFuer
import de.oejendorferdamm.dammzeit.update.kannUnbekannteQuellenInstallieren
import de.oejendorferdamm.dammzeit.update.oeffneUnbekannteQuellenEinstellungen
import de.oejendorferdamm.dammzeit.update.pruefeUpdateApk
import de.oejendorferdamm.dammzeit.widget.WidgetGross
import de.oejendorferdamm.dammzeit.widget.WidgetKlein
import kotlinx.coroutines.launch
import java.io.File

/** Alle Einstellungen hinter dem Zahnrad. Jede Änderung ist sofort an der Uhr daneben zu sehen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EinstellungenPanel(
    einstellungen: Einstellungen,
    version: String,
    updateInfo: UpdateInfo?,
    pruefungLaeuft: Boolean,
    bereitsAktuell: Boolean,
    onJetztPruefen: () -> Unit,
    onSchliessen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spieler = remember { TonSpieler(context) }
    DisposableEffect(Unit) { onDispose { spieler.stopp() } }
    fun aendere(f: (Einstellungen) -> Einstellungen) = Speicher.aendereEinstellungen(f)

    Column(
        modifier
            .shadow(16.dp)
            .background(HellerGrund)
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Einstellungen", color = Text1, fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            RundKnopf("Einstellungen schließen", onSchliessen, groesse = 72.dp) {
                SymbolBild(Symbol.SCHLIESSEN, Text1, Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.height(20.dp))

        Karte("Farbe der Zeitscheibe") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FarbModus.entries.forEach { modus ->
                    Auswahl(modus.bezeichnung, einstellungen.farbe == modus, { aendere { it.copy(farbe = modus) } }, schrift = 18.sp) {
                        FarbVorschau(modus, einstellungen.eigeneFarbe)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Eigene Farbe", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EIGENE_FARBEN.forEach { farbe ->
                    val gewaehlt = einstellungen.farbe == FarbModus.EIGENE && einstellungen.eigeneFarbe == farbe
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(farbe))
                            .border(if (gewaehlt) 5.dp else 0.dp, Text1, CircleShape)
                            .clickable(role = Role.RadioButton) { aendere { it.copy(farbe = FarbModus.EIGENE, eigeneFarbe = farbe) } }
                            .semantics { contentDescription = "Eigene Farbe" }
                    )
                }
            }
        }

        Karte("Hintergrund") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Hintergrund.entries.forEach { h ->
                    Auswahl(h.bezeichnung, einstellungen.hintergrund == h, { aendere { it.copy(hintergrund = h) } }, schrift = 18.sp) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(Color(ZifferblattZeichner.flaechenFarbe(h)))
                                .border(1.dp, Color(0x55000000), CircleShape)
                        )
                    }
                }
            }
        }

        Karte("Zifferblatt") {
            Text("Minuten für eine volle Runde", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SKALEN.forEach { s ->
                    Auswahl("$s Min", einstellungen.skalaMinuten == s, { aendere { it.copy(skalaMinuten = s) } }, schrift = 18.sp)
                }
            }
            Schalter("Zahlen am Rand", einstellungen.zahlenAnzeigen) { an -> aendere { it.copy(zahlenAnzeigen = an) } }
            Schalter("Überschrift (z. B. „14 MINUTEN-TIMER“)", einstellungen.titelAnzeigen) { an -> aendere { it.copy(titelAnzeigen = an) } }
            Schalter("Restzeit zusätzlich als Zahl", einstellungen.restzeitAnzeigen) { an -> aendere { it.copy(restzeitAnzeigen = an) } }
        }

        Karte("Klingelton") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Ton.entries.forEach { ton ->
                    val gewaehlt = einstellungen.ton == ton
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (gewaehlt) Text1 else Flaeche)
                            .clickable(role = Role.RadioButton) { aendere { it.copy(ton = ton) } }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ton.bezeichnung, color = if (gewaehlt) Color.White else Text1, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        if (ton != Ton.STILL) {
                            RundKnopf("${ton.bezeichnung} anhören", { spieler.spiele(ton, wiederholen = false, lautstaerke = einstellungen.lautstaerke) }, groesse = 52.dp) {
                                SymbolBild(Symbol.LAUTSPRECHER, Text1, Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Klingeldauer", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(5 to "5 Sek", 10 to "10 Sek", 30 to "30 Sek", 60 to "1 Min", 120 to "2 Min", 0 to "Bis zum Antippen").forEach { (s, text) ->
                    Auswahl(text, einstellungen.klingelSekunden == s, { aendere { it.copy(klingelSekunden = s) } }, schrift = 18.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Lautstärke", color = Text2, fontSize = 17.sp)
            var lautstaerke by remember(einstellungen.lautstaerke) { mutableFloatStateOf(einstellungen.lautstaerke) }
            Slider(
                value = lautstaerke,
                onValueChange = { lautstaerke = it },
                onValueChangeFinished = {
                    val wert = lautstaerke
                    aendere { it.copy(lautstaerke = wert) }
                    spieler.spiele(einstellungen.ton, wiederholen = false, lautstaerke = wert)
                },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = Akzent, activeTrackColor = Akzent)
            )
        }

        Karte("Bedienung") {
            Schalter("Nach dem Einstellen sofort starten", einstellungen.sofortStarten) { an -> aendere { it.copy(sofortStarten = an) } }
            Schalter("Bildschirm anlassen, solange die Uhr läuft", einstellungen.bildschirmAn) { an -> aendere { it.copy(bildschirmAn = an) } }
            Spacer(Modifier.height(10.dp))
            Text(
                "An der Uhr ziehen stellt die Zeit ein. Kurz antippen: starten, anhalten – und " +
                    "wenn sie klingelt, den Ton stoppen.",
                color = Text2, fontSize = 16.sp
            )
        }

        Karte("Widget für den Startbildschirm") {
            Text("Nur die runde Uhr – antippen startet oder hält sie an.", color = Text2, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GrosserKnopf("Widget 2 × 2", Flaeche, Text1, Modifier.weight(1f)) { widgetAnheften(context, gross = false) }
                GrosserKnopf("Widget 4 × 4", Flaeche, Text1, Modifier.weight(1f)) { widgetAnheften(context, gross = true) }
            }
        }

        UpdateKarte(version, updateInfo, pruefungLaeuft, bereitsAktuell, einstellungen.autoUpdate, onJetztPruefen)

        Text(
            "DammZeit $version · freie Software unter der GNU GPL v3 · github.com/Teyro/DammZeit",
            color = Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)
        )
    }
}

private fun widgetAnheften(context: Context, gross: Boolean) {
    val manager = AppWidgetManager.getInstance(context)
    val ziel = ComponentName(context, if (gross) WidgetGross::class.java else WidgetKlein::class.java)
    if (manager != null && manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ziel, null, null)
    } else {
        Toast.makeText(context, "Bitte über den Startbildschirm hinzufügen: lange drücken → Widgets → DammZeit", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Karte(titel: String, inhalt: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 18.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).padding(24.dp)
    ) {
        Text(titel, color = Text1, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        inhalt()
    }
}

@Composable
private fun Schalter(titel: String, an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, color = Text1, fontSize = 19.sp, modifier = Modifier.weight(1f))
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

    Karte("Updates") {
        Text("Installiert: $version", color = Text2, fontSize = 17.sp)
        Schalter("Automatisch nach Updates suchen", autoUpdate) { an -> Speicher.aendereEinstellungen { it.copy(autoUpdate = an) } }
        Spacer(Modifier.height(14.dp))
        if (info != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Akzent).padding(20.dp)) {
                Text("Neue Version ${info.version}", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.changelog.trim(), color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, maxLines = 10)
                }
                Spacer(Modifier.height(12.dp))
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
                    Text(it, color = Color.White, fontSize = 15.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GrosserKnopf(if (pruefungLaeuft) "Prüft …" else "Jetzt nach Updates suchen", Text1, Color.White, Modifier.weight(1f)) {
                    if (!pruefungLaeuft) onJetztPruefen()
                }
                if (pruefungLaeuft) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(color = Akzent, modifier = Modifier.size(32.dp))
                }
            }
            if (bereitsAktuell && !pruefungLaeuft) {
                Spacer(Modifier.height(8.dp))
                Text("Du hast bereits die neueste Version.", color = Text2, fontSize = 16.sp)
            }
        }
    }
}
