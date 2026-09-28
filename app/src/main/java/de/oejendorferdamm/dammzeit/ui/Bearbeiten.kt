package de.oejendorferdamm.dammzeit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.SKALEN
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import de.oejendorferdamm.dammzeit.model.standardName
import de.oejendorferdamm.dammzeit.ton.TonSpieler

private val VORGABEN_MINUTEN = listOf(1, 2, 3, 5, 10, 15, 20, 25, 30, 45, 60, 90)

/** Timer anlegen oder ändern: Name, Dauer, Zifferblatt, Farbe und Ton. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Bearbeiten(
    ausgang: ZeitTimer,
    istNeu: Boolean,
    onSpeichern: (ZeitTimer) -> Unit,
    onLoeschen: () -> Unit,
    onAbbrechen: () -> Unit
) {
    val context = LocalContext.current
    var entwurf by remember { mutableStateOf(ausgang.copy(endeUm = 0L, restBeiPauseMs = -1L)) }
    // Name folgt der Dauer, solange er nicht selbst geändert wurde.
    var nameAutomatisch by remember { mutableStateOf(istNeu || ausgang.name == standardName(ausgang.dauerMs)) }
    val spieler = remember { TonSpieler(context) }
    DisposableEffect(Unit) { onDispose { spieler.stopp() } }

    fun setzeDauer(ms: Long) {
        val dauer = ms.coerceIn(1000L, 12 * 3_600_000L)
        // Passt die Dauer nicht mehr aufs gewählte Zifferblatt, wieder das Standard-Zifferblatt nehmen.
        val skala = if (entwurf.skalaMinuten > 0 && entwurf.skalaMinuten * 60_000L < dauer) 0 else entwurf.skalaMinuten
        entwurf = entwurf.copy(dauerMs = dauer, skalaMinuten = skala, name = if (nameAutomatisch) standardName(dauer) else entwurf.name)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Hintergrund)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RundKnopf("Abbrechen", onAbbrechen, groesse = 52.dp) {
                    SymbolBild(Symbol.ZURUECK_PFEIL, Text1, Modifier.size(26.dp))
                }
                Spacer(Modifier.size(14.dp))
                Text(if (istNeu) "Neuer Timer" else "Timer bearbeiten", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))

            Abschnitt("Name") {
                OutlinedTextField(
                    value = entwurf.name,
                    onValueChange = {
                        nameAutomatisch = false
                        entwurf = entwurf.copy(name = it.take(40))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Akzent, cursorColor = Akzent)
                )
            }

            Abschnitt("Dauer") {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Zifferblatt(
                        restMs = entwurf.dauerMs, dauerMs = entwurf.dauerMs, skalaMinuten = entwurf.wirksameSkala(),
                        farbe = entwurf.farbe, modifier = Modifier.size(260.dp), mitZahlen = true,
                        onDauer = { setzeDauer(it) }
                    )
                    Spacer(Modifier.size(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatiereDauer(entwurf.dauerMs), color = Text1, fontSize = 44.sp, fontWeight = FontWeight.Bold)
                        Text("Ziehen am Zifferblatt stellt die Zeit ein", color = Text2, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KleinerKnopf("− 1 Min") { setzeDauer(entwurf.dauerMs - 60_000L) }
                            KleinerKnopf("+ 1 Min") { setzeDauer(entwurf.dauerMs + 60_000L) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            KleinerKnopf("− 10 Sek") { setzeDauer(entwurf.dauerMs - 10_000L) }
                            KleinerKnopf("+ 10 Sek") { setzeDauer(entwurf.dauerMs + 10_000L) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VORGABEN_MINUTEN.forEach { min ->
                        Auswahl("$min Min", entwurf.dauerMs == min * 60_000L, { setzeDauer(min * 60_000L) })
                    }
                }
            }

            Abschnitt("Zifferblatt") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SKALEN.forEach { skala ->
                        Auswahl(
                            if (skala == 0) "Standard (60 Min)" else "$skala Min",
                            entwurf.skalaMinuten == skala,
                            {
                                // Eine zu kleine Skala würde die Dauer abschneiden – dann passend wählen.
                                val passt = skala == 0 || skala * 60_000L >= entwurf.dauerMs
                                entwurf = entwurf.copy(skalaMinuten = if (passt) skala else 0)
                            }
                        )
                    }
                }
            }

            Abschnitt("Farbe") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FarbModus.entries.forEach { modus ->
                        Auswahl(modus.bezeichnung, entwurf.farbe == modus, { entwurf = entwurf.copy(farbe = modus) }) {
                            FarbVorschau(modus)
                        }
                    }
                }
            }

            Abschnitt("Signalton") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ton.entries.forEach { ton ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (entwurf.ton == ton) Text1 else Karte)
                                .clickable(role = Role.RadioButton) { entwurf = entwurf.copy(ton = ton) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ton.bezeichnung, color = if (entwurf.ton == ton) Color.White else Text1,
                                fontSize = 17.sp, modifier = Modifier.weight(1f)
                            )
                            if (ton != Ton.STILL) {
                                RundKnopf("${ton.bezeichnung} anhören", { spieler.spiele(ton, wiederholen = false) }, groesse = 44.dp, farbe = Flaeche) {
                                    SymbolBild(Symbol.LAUTSPRECHER, Text1, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (!istNeu) {
                    GrosserKnopf("Löschen", Flaeche, Akzent, Modifier.weight(1f)) { onLoeschen() }
                }
                GrosserKnopf("Speichern", Akzent, Color.White, Modifier.weight(1f)) {
                    val name = entwurf.name.trim().ifEmpty { standardName(entwurf.dauerMs) }
                    onSpeichern(entwurf.copy(name = name))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Abschnitt(titel: String, inhalt: @Composable () -> Unit) {
    Text(titel, color = Text2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    inhalt()
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun KleinerKnopf(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Karte)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GrosserKnopf(text: String, farbe: Color, textFarbe: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(farbe)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textFarbe, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Zwei Punkte zeigen, wie die Farbe am Anfang und kurz vor Schluss aussieht. */
@Composable
fun FarbVorschau(modus: FarbModus) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        FarbPunkt(Color(ZifferblattZeichner.sektorFarbe(modus, 30 * 60_000L, 30 * 60_000L)))
        FarbPunkt(Color(ZifferblattZeichner.sektorFarbe(modus, 10_000L, 30 * 60_000L)))
    }
}
