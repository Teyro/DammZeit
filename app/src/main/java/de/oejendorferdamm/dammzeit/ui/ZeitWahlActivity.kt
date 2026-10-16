package de.oejendorferdamm.dammzeit.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.SKALEN
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableStateOf
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/**
 * Auswahlfenster "Zeit einstellen", geöffnet vom Widget: links die Uhr zum Ziehen (wie in der
 * App – im Widget selbst kann Android kein Ziehen erkennen), rechts schnelle Zeiten, Ton und
 * Klingeldauer. Liegt als halbdurchsichtige Ebene über dem Startbildschirm –
 * antippen daneben schließt es wieder.
 */
class ZeitWahlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Akzent)) {
                VorbildRaster(faktor = 1f) {
                    ZeitWahl(onFertig = { finish() })
                }
            }
        }
    }
}

private val SCHNELLWAHL = listOf(listOf(1, 2, 3, 5), listOf(10, 15, 20, 30), listOf(45, 60))

/** Schnell umschaltbare Töne im Fenster – die übrigen gibt es im Zahnrad-Menü der App. */
private val SCHNELL_TOENE = listOf(Ton.GONG, Ton.GLOCKE, Ton.KLANGSCHALE, Ton.STILL)
private val SCHNELL_DAUER = listOf(3 to "3 Sek", 5 to "5 Sek", 10 to "10 Sek", 30 to "30 Sek")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZeitWahl(onFertig: () -> Unit) {
    val context = LocalContext.current
    val einstellungen by Speicher.einstellungen.collectAsState()
    val timer by Speicher.timer.collectAsState()
    val aktuelleMinuten = (timer.dauerMs / 60_000L).toInt()
    // Uhr zum Ziehen: zeigt während des Ziehens die neue Zeit, übernommen wird beim Loslassen
    var gezogen by remember { mutableStateOf<Long?>(null) }

    fun waehle(minuten: Int) {
        // Passt die Zeit nicht aufs Zifferblatt, nimmt die Uhr die nächstgrößere Skala.
        if (minuten > einstellungen.skalaMinuten) {
            val skala = SKALEN.firstOrNull { it >= minuten } ?: SKALEN.last()
            Speicher.aendereEinstellungen { it.copy(skalaMinuten = skala) }
        }
        TimerSteuerung.setzeDauer(context, minuten * 60_000L, starten = einstellungen.sofortStarten)
        onFertig()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFertig),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(1560.dp)
                .shadow(24.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Karte)
                // Klicks in die Karte nicht als "daneben" werten.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Zeit einstellen", color = Text1, fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RundKnopf("Schließen", onFertig, groesse = 72.dp, farbe = Flaeche) {
                    SymbolBild(Symbol.SCHLIESSEN, Text1, Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Row {
                // --- Links: die Uhr – mit dem Finger auf die gewünschte Zeit ziehen ---
                Column(Modifier.width(600.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val dauer = gezogen ?: timer.dauerMs
                    Zifferblatt(
                        restMs = dauer,
                        dauerMs = dauer,
                        einstellungen = einstellungen,
                        modifier = Modifier.size(580.dp),
                        onZiehen = { gezogen = it },
                        onLoslassen = {
                            gezogen?.let { TimerSteuerung.setzeDauer(context, it, starten = einstellungen.sofortStarten) }
                            gezogen = null
                            onFertig()
                        }
                    )
                    Text(
                        gezogen?.let { formatiereDauer(it) } ?: "Mit dem Finger ziehen",
                        color = if (gezogen != null) Text1 else Text2,
                        fontSize = if (gezogen != null) 44.sp else 26.sp,
                        fontWeight = if (gezogen != null) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(Modifier.width(36.dp))
                // --- Rechts: Schnellwahl, Ton, Klingeldauer ---
                Column(Modifier.weight(1f)) {
                    SCHNELLWAHL.forEach { reihe ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            reihe.forEach { minuten ->
                                val gewaehlt = minuten == aktuelleMinuten
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(96.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(if (gewaehlt) Akzent else Flaeche)
                                        .clickable(role = Role.Button) { waehle(minuten) }
                                        .semantics { contentDescription = "$minuten Minuten" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("$minuten Min", color = if (gewaehlt) Color.White else Text1, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            // letzte Reihe auffüllen, damit alle Knöpfe gleich breit sind
                            repeat(4 - reihe.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Signal am Ende", color = Text2, fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SCHNELL_TOENE.forEach { ton ->
                            Auswahl(ton.bezeichnung, einstellungen.ton == ton, { Speicher.aendereEinstellungen { it.copy(ton = ton) } }, schrift = 24.sp)
                        }
                    }
                    if (einstellungen.ton != Ton.STILL) {
                        Spacer(Modifier.height(16.dp))
                        Text("So lange klingeln (die letzten 3 Sekunden klingt es sanft aus)", color = Text2, fontSize = 24.sp)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SCHNELL_DAUER.forEach { (sek, name) ->
                                Auswahl(name, einstellungen.klingelSekunden == sek, { Speicher.aendereEinstellungen { it.copy(klingelSekunden = sek) } }, schrift = 24.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (einstellungen.sofortStarten) "Die Uhr startet sofort mit der gewählten Zeit."
                        else "Danach mit dem Startknopf starten.",
                        color = Text2, fontSize = 22.sp
                    )
                }
            }
        }
    }
}
