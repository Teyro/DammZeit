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
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/**
 * Kleines Auswahlfenster "Zeit einstellen", geöffnet vom Widget: schnelle Zeiten zum Antippen
 * und eine eigene Minutenzahl. Liegt als halbdurchsichtige Ebene über dem Startbildschirm –
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

private val SCHNELLWAHL = listOf(listOf(1, 2, 3, 5, 10), listOf(15, 20, 30, 45, 60))

@Composable
private fun ZeitWahl(onFertig: () -> Unit) {
    val context = LocalContext.current
    val einstellungen by Speicher.einstellungen.collectAsState()
    val timer by Speicher.timer.collectAsState()
    val aktuelleMinuten = (timer.dauerMs / 60_000L).toInt()
    var eigene by remember { mutableIntStateOf(aktuelleMinuten.coerceIn(1, 720)) }

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
                .width(1040.dp)
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
            Spacer(Modifier.height(28.dp))
            SCHNELLWAHL.forEach { reihe ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    reihe.forEach { minuten ->
                        val gewaehlt = minuten == aktuelleMinuten
                        Box(
                            Modifier
                                .weight(1f)
                                .height(112.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (gewaehlt) Akzent else Flaeche)
                                .clickable(role = Role.Button) { waehle(minuten) }
                                .semantics { contentDescription = "$minuten Minuten" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$minuten Min",
                                color = if (gewaehlt) Color.White else Text1,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Eigene Zeit", color = Text2, fontSize = 30.sp, modifier = Modifier.weight(1f))
                RundKnopf("Eine Minute weniger", { eigene = (eigene - 1).coerceAtLeast(1) }, groesse = 84.dp, farbe = Flaeche) {
                    SymbolBild(Symbol.MINUS, Text1, Modifier.size(36.dp))
                }
                Text(
                    "$eigene Min",
                    color = Text1, fontSize = 52.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(230.dp).padding(horizontal = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                RundKnopf("Eine Minute mehr", { eigene = (eigene + 1).coerceAtMost(720) }, groesse = 84.dp, farbe = Flaeche) {
                    SymbolBild(Symbol.PLUS, Text1, Modifier.size(36.dp))
                }
                Spacer(Modifier.width(24.dp))
                GrosserKnopf("Übernehmen", Akzent, Color.White, Modifier.width(250.dp)) { waehle(eigene) }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (einstellungen.sofortStarten) "Die Uhr startet sofort mit der gewählten Zeit."
                else "Danach mit dem Startknopf starten.",
                color = Text2, fontSize = 24.sp
            )
        }
    }
}
