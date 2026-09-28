package de.oejendorferdamm.dammzeit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.model.formatiereDauer

/** Übersicht aller Timer als Kacheln, jede mit eigenem, live laufendem Zifferblatt. */
@Composable
fun Startseite(
    timer: List<ZeitTimer>,
    zeigeUpdatePunkt: Boolean,
    onOeffnen: (Long) -> Unit,
    onNeu: () -> Unit,
    onEinstellungen: () -> Unit
) {
    val jetzt by rememberJetzt(aktiv = timer.any { it.laeuft })
    Column(Modifier.fillMaxSize().background(Hintergrund).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("DammZeit", color = Text1, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Tippe auf einen Timer, um ihn groß anzuzeigen.", color = Text2, fontSize = 14.sp)
            }
            Box {
                RundKnopf("Einstellungen", onEinstellungen, groesse = 52.dp) {
                    SymbolBild(Symbol.ZAHNRAD, Text1, Modifier.size(26.dp))
                }
                if (zeigeUpdatePunkt) {
                    Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(Akzent))
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 220.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(timer, key = { it.id }) { t ->
                TimerKachel(t, jetzt, onClick = { onOeffnen(t.id) })
            }
            item(key = "neu") {
                NeuKachel(onNeu)
            }
        }
    }
}

@Composable
private fun TimerKachel(t: ZeitTimer, jetzt: Long, onClick: () -> Unit) {
    val rest = t.restMs(jetzt)
    val status = when {
        t.abgelaufen(jetzt) -> "Zeit ist um"
        t.laeuft -> "läuft · noch ${formatiereDauer(rest)}"
        t.pausiert -> "pausiert · noch ${formatiereDauer(rest)}"
        else -> formatiereDauer(t.dauerMs)
    }
    Column(
        modifier = Modifier
            .shadow(2.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Karte)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Timer ${t.name}" }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Zifferblatt(
            restMs = rest, dauerMs = t.dauerMs, skalaMinuten = t.wirksameSkala(), farbe = t.farbe,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        )
        Spacer(Modifier.size(6.dp))
        Text(t.name, color = Text1, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            status,
            color = if (t.laeuft) Akzent else Text2,
            fontSize = 14.sp,
            fontWeight = if (t.laeuft) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NeuKachel(onNeu: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(24.dp))
            .background(Flaeche)
            .clickable(role = Role.Button, onClick = onNeu)
            .semantics { contentDescription = "Neuer Timer" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                SymbolBild(Symbol.PLUS, Akzent, Modifier.size(34.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text("Neuer Timer", color = Text1, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
