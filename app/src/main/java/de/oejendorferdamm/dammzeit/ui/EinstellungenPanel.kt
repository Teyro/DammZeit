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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import de.oejendorferdamm.dammzeit.model.Laufrichtung
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
import de.oejendorferdamm.dammzeit.ui.m3.EinstellungenThema
import de.oejendorferdamm.dammzeit.ui.m3.M3Auswahl
import de.oejendorferdamm.dammzeit.ui.m3.M3Bereich
import de.oejendorferdamm.dammzeit.ui.m3.M3Knopf
import de.oejendorferdamm.dammzeit.ui.m3.M3Feld
import de.oejendorferdamm.dammzeit.ui.m3.M3Gruppe
import de.oejendorferdamm.dammzeit.ui.m3.M3Kopf
import de.oejendorferdamm.dammzeit.ui.m3.M3Schalter
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

    // Aufbau wie in den anderen Schul-Apps: Übersicht → Bereich, ‹ zurück, ✕ immer oben
    var seite by rememberSaveable { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = seite != null) { seite = null }
    val titel = when (seite) {
        "aussehen" -> "Aussehen"; "zifferblatt" -> "Zifferblatt"; "klingel" -> "Klingelton"; "pausen" -> "Pausen"
        "bedienung" -> "Bedienung"; "leistung" -> "Leistung"; "update" -> "Updates"; "ueber" -> "Über DammZeit"
        else -> "Einstellungen"
    }
    val sparsam = LokalSparsam.current
    EinstellungenThema {
    Column(modifier.shadow(if (sparsam) 0.dp else 16.dp).background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) {
        M3Kopf(titel, if (seite != null) ({ seite = null }) else null, onSchliessen)
        androidx.compose.runtime.key(seite) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        when (seite) {
            null -> {
                val an = { b: Boolean -> if (b) "an" else "aus" }
                M3Gruppe {
                BereichZeile("🎨", Color(0xFF7C3AED), "Aussehen", "${einstellungen.farbe.bezeichnung} · Hintergrund ${einstellungen.hintergrund.bezeichnung}") { seite = "aussehen" }
                BereichZeile("🕘", Color(0xFF0891B2), "Zifferblatt", "${einstellungen.skalaMinuten} Minuten · ${einstellungen.laufrichtung.bezeichnung}") { seite = "zifferblatt" }
                BereichZeile("🔔", Color(0xFFD97706), "Klingelton",
                    "${einstellungen.ton.bezeichnung} · ${if (einstellungen.klingelSekunden == 0) "bis zum Antippen" else klingelText(einstellungen.klingelSekunden)}") { seite = "klingel" }
                BereichZeile("⏰", Color(0xFFDB2777), "Pausen",
                    if (einstellungen.pausenZeiten.isEmpty()) "keine Zeiten" else einstellungen.pausenZeiten.joinToString(" · ")) { seite = "pausen" }
                BereichZeile("👆", Color(0xFF16A34A), "Bedienung",
                    "Sofort starten ${an(einstellungen.sofortStarten)} · Bildschirm anlassen ${an(einstellungen.bildschirmAn)} · Widget") { seite = "bedienung" }
                }
                M3Gruppe("Allgemein") {
                BereichZeile("⚡", Color(0xFFCA8A04), "Leistung", leistungText(einstellungen.leistung)) { seite = "leistung" }
                BereichZeile("⬆️", Color(0xFF2563EB), "Updates",
                    if (updateInfo != null) "Neue Version ${updateInfo.version} verfügbar" else "Installiert: $version · automatisch prüfen ${an(einstellungen.autoUpdate)}",
                    hinweis = updateInfo != null) { seite = "update" }
                BereichZeile("ℹ️", Color(0xFF6B7280), "Über DammZeit", "Version $version · freie Software") { seite = "ueber" }
                }
            }
            "aussehen" -> {
        Karte("Farbe der Zeitscheibe") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FarbModus.entries.forEach { modus ->
                    M3Auswahl(modus.bezeichnung, einstellungen.farbe == modus, { aendere { it.copy(farbe = modus) } }) {
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
                    M3Auswahl(h.bezeichnung, einstellungen.hintergrund == h, { aendere { it.copy(hintergrund = h) } }) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(Color(ZifferblattZeichner.flaechenFarbe(h)))
                                .border(1.dp, Color(0x55000000), CircleShape)
                        )
                    }
                }
            }
        }

            }
            "zifferblatt" -> {
        Karte("Zifferblatt") {
            Text("Minuten für eine volle Runde", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SKALEN.forEach { s ->
                    M3Auswahl("$s Min", einstellungen.skalaMinuten == s, { aendere { it.copy(skalaMinuten = s) } })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Laufrichtung", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Laufrichtung.entries.forEach { r ->
                    M3Auswahl(r.bezeichnung, einstellungen.laufrichtung == r, { aendere { it.copy(laufrichtung = r) } })
                }
            }
            M3Schalter("Zahlen am Rand", einstellungen.zahlenAnzeigen) { an -> aendere { it.copy(zahlenAnzeigen = an) } }
            M3Schalter("Überschrift (z. B. „14 MINUTEN-TIMER“)", einstellungen.titelAnzeigen) { an -> aendere { it.copy(titelAnzeigen = an) } }
            M3Schalter("Restzeit zusätzlich als Zahl", einstellungen.restzeitAnzeigen) { an -> aendere { it.copy(restzeitAnzeigen = an) } }
        }

            }
            "pausen" -> {
        Karte("Pausen und Stundenende") {
            Text("Für den Knopf „Bis zur Pause“ oben: er stellt die Uhr auf die Zeit bis zum nächsten Klingeln.", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                einstellungen.pausenZeiten.forEach { z ->
                    M3Auswahl("$z  ✕", false, { aendere { it.copy(pausenZeiten = it.pausenZeiten - z) } })
                }
            }
            Spacer(Modifier.height(12.dp))
            var stunde by remember { mutableIntStateOf(10) }
            var minute by remember { mutableIntStateOf(0) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RundKnopf("Stunde weniger", { stunde = (stunde + 23) % 24 }, groesse = 52.dp, farbe = Flaeche) { SymbolBild(Symbol.MINUS, Text1, Modifier.size(22.dp)) }
                Text("%02d".format(stunde), color = Text1, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                RundKnopf("Stunde mehr", { stunde = (stunde + 1) % 24 }, groesse = 52.dp, farbe = Flaeche) { SymbolBild(Symbol.PLUS, Text1, Modifier.size(22.dp)) }
                Text(":", color = Text1, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                RundKnopf("Fünf Minuten weniger", { minute = (minute + 55) % 60 }, groesse = 52.dp, farbe = Flaeche) { SymbolBild(Symbol.MINUS, Text1, Modifier.size(22.dp)) }
                Text("%02d".format(minute), color = Text1, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                RundKnopf("Fünf Minuten mehr", { minute = (minute + 5) % 60 }, groesse = 52.dp, farbe = Flaeche) { SymbolBild(Symbol.PLUS, Text1, Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                M3Knopf("Hinzufügen", betont = true) {
                    val z = "%02d:%02d".format(stunde, minute)
                    aendere { it.copy(pausenZeiten = (it.pausenZeiten + z).distinct().sorted()) }
                }
            }
        }

            }
            "klingel" -> {
        Karte("Klingelton") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Ton.entries.forEach { ton ->
                    val gewaehlt = einstellungen.ton == ton
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (gewaehlt) androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable(role = Role.RadioButton) { aendere { it.copy(ton = ton) } }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ton.bezeichnung, color = if (gewaehlt) androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer else Text1, fontWeight = if (gewaehlt) FontWeight.Medium else FontWeight.Normal, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        if (ton != Ton.STILL) {
                            RundKnopf("${ton.bezeichnung} anhören", { spieler.spiele(ton, wiederholen = false, lautstaerke = einstellungen.lautstaerke) }, groesse = 52.dp) {
                                SymbolBild(Symbol.LAUTSPRECHER, Text1, Modifier.size(26.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Klingeldauer (die letzten 3 Sekunden klingt der Ton sanft aus)", color = Text2, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(3 to "3 Sek", 5 to "5 Sek", 10 to "10 Sek", 30 to "30 Sek", 60 to "1 Min", 120 to "2 Min", 0 to "Bis zum Antippen").forEach { (s, text) ->
                    M3Auswahl(text, einstellungen.klingelSekunden == s, { aendere { it.copy(klingelSekunden = s) } })
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
                valueRange = 0.1f..1f
            )
        }

            }
            "bedienung" -> {
        Karte("Bedienung") {
            M3Schalter("Nach dem Einstellen sofort starten", einstellungen.sofortStarten) { an -> aendere { it.copy(sofortStarten = an) } }
            M3Schalter("Bildschirm anlassen, solange die Uhr läuft", einstellungen.bildschirmAn) { an -> aendere { it.copy(bildschirmAn = an) } }
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
                M3Knopf("Widget 2 × 2", Modifier.weight(1f)) { widgetAnheften(context, gross = false) }
                M3Knopf("Widget 4 × 4", Modifier.weight(1f)) { widgetAnheften(context, gross = true) }
            }
        }

            }
            "leistung" -> LeistungKarte(einstellungen.leistung) { neu -> aendere { it.copy(leistung = neu) } }
            "update" -> UpdateKarte(version, updateInfo, pruefungLaeuft, bereitsAktuell, einstellungen.autoUpdate, onJetztPruefen)
            else -> Karte("DammZeit $version") {
                Text("Freie Software unter der GNU GPL v3 · github.com/Teyro/DammZeit", color = Text2, fontSize = 18.sp)
                Spacer(Modifier.height(10.dp))
                Text("Ein Timer wie der Time Timer für die Tafeln der Schule am Öjendorfer Damm.", color = Text2, fontSize = 18.sp)
            }
        }
        }
        }
    }
    }
}

private fun klingelText(s: Int) = if (s < 60) "$s Sek" else "${s / 60} Min"

private fun leistungText(wahl: String) = when (wahl) {
    Leistung.VOLL -> "Voll – alle Animationen"
    Leistung.SPAR -> "Sparsam – für langsame Boards"
    else -> "Automatisch · hier ${if (Leistung.automatischSparsam) "sparsam (Android 9 oder älter)" else "voll (ab Android 10)"}"
}

/** Leistung: Automatik, voll oder sparsam – wirkt sofort */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeistungKarte(wahl: String, onWahl: (String) -> Unit) {
    Karte("Leistung") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            M3Auswahl("Automatisch", wahl == Leistung.AUTO, { onWahl(Leistung.AUTO) })
            M3Auswahl("Voll", wahl == Leistung.VOLL, { onWahl(Leistung.VOLL) })
            M3Auswahl("Sparsam", wahl == Leistung.SPAR, { onWahl(Leistung.SPAR) })
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Automatisch: ab Android 10 voll, auf älteren Boards sparsam. Sparsam heißt: keine Animationen, " +
                "Schatten und Verläufe, kein rieselnder Sand, die Uhr zeichnet nur viermal pro Sekunde neu. " +
                "Uhr, Klingeln und alle Funktionen bleiben gleich.",
            color = Text2, fontSize = 17.sp
        )
        Spacer(Modifier.height(10.dp))
        Text("Dieses Board: Android ${android.os.Build.VERSION.RELEASE} → ${if (Leistung.automatischSparsam) "sparsam" else "voll"}", color = Text1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Zeile der Übersicht: Symbol im Farbkreis, Titel, Kurzfassung, Pfeil – die ganze Zeile ist der Knopf. */
@Composable
private fun BereichZeile(symbol: String, farbe: Color, titel: String, text: String, hinweis: Boolean = false, onClick: () -> Unit) =
    M3Bereich(symbol, farbe, titel, text, hinweis, onClick)

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
private fun Karte(titel: String, inhalt: @Composable () -> Unit) = M3Gruppe(titel) { M3Feld { inhalt() } }

@Composable
fun Schalter(titel: String, an: Boolean, onAendern: (Boolean) -> Unit) {
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
        M3Schalter("Automatisch nach Updates suchen", autoUpdate) { an -> Speicher.aendereEinstellungen { it.copy(autoUpdate = an) } }
        Spacer(Modifier.height(14.dp))
        if (info != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer).padding(20.dp)) {
                Text("Neue Version ${info.version}", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (info.changelog.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(info.changelog.trim(), color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 15.sp, maxLines = 10)
                }
                Spacer(Modifier.height(12.dp))
                M3Knopf(if (ladend) "Lädt … ${(fortschritt * 100).toInt()} %" else "Jetzt aktualisieren", Modifier.fillMaxWidth(), betont = true) {
                    if (ladend) return@M3Knopf
                    if (!kannUnbekannteQuellenInstallieren(context)) {
                        Toast.makeText(context, "Bitte DammZeit die Installation erlauben und dann erneut tippen", Toast.LENGTH_LONG).show()
                        oeffneUnbekannteQuellenEinstellungen(context)
                        return@M3Knopf
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
                    Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 15.sp)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                M3Knopf(if (pruefungLaeuft) "Prüft …" else "Jetzt nach Updates suchen", Modifier.weight(1f)) {
                    if (!pruefungLaeuft) onJetztPruefen()
                }
                if (pruefungLaeuft) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            if (bereitsAktuell && !pruefungLaeuft) {
                Spacer(Modifier.height(8.dp))
                Text("Du hast bereits die neueste Version.", color = Text2, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        val context = LocalContext.current
        M3Knopf("Damm-Apps in F-Droid hinzufügen", Modifier.fillMaxWidth()) { oeffneFDroidRepo(context) }
    }
}

/** Öffnet F-Droid mit der Frage „Paketquelle hinzufügen?“ (alle Damm-Apps) – ohne F-Droid die Anleitungsseite mit QR-Code. */
private fun oeffneFDroidRepo(context: android.content.Context) {
    val repo = "teyro.github.io/damm-fdroid/repo?fingerprint=db7f158b6eb2c7eb17e79aa4a9ae0e9487c3c2228fe7de60b37a1979408c0604"
    for (adresse in listOf("fdroidrepos://$repo", "https://teyro.github.io/damm-fdroid/")) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(adresse)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: android.content.ActivityNotFoundException) {}
    }
    android.widget.Toast.makeText(context, "Weder F-Droid noch ein Browser ist installiert", android.widget.Toast.LENGTH_LONG).show()
}
