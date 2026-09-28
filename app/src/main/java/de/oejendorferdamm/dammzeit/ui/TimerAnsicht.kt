package de.oejendorferdamm.dammzeit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Einstellungen
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/** Großansicht eines Timers – wie der Time Timer an der Tafel: Titel, Zifferblatt, Knöpfe. */
@Composable
fun TimerAnsicht(
    timer: ZeitTimer,
    einstellungen: Einstellungen,
    klingelt: Boolean,
    onZurueck: () -> Unit,
    onBearbeiten: () -> Unit
) {
    val context = LocalContext.current
    val jetzt by rememberJetzt(aktiv = timer.laeuft)
    val rest = timer.restMs(jetzt)
    val abgelaufen = timer.abgelaufen(jetzt)
    val laeuft = timer.laeuft && !abgelaufen

    // Während der Timer läuft, bleibt der Bildschirm an (abschaltbar in den Einstellungen).
    val view = LocalView.current
    val anlassen = laeuft && einstellungen.bildschirmAn
    DisposableEffect(anlassen) {
        view.keepScreenOn = anlassen
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Hintergrund).systemBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val quer = maxWidth > maxHeight
            if (quer) {
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Titel(timer.name)
                        GrossesZifferblatt(timer, rest, laeuft, Modifier.weight(1f))
                    }
                    Column(Modifier.width(260.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Anzeige(einstellungen, rest, abgelaufen)
                        Spacer(Modifier.height(24.dp))
                        Steuerung(timer, laeuft, abgelaufen, onBearbeiten, context)
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(48.dp))
                    Titel(timer.name)
                    GrossesZifferblatt(timer, rest, laeuft, Modifier.weight(1f))
                    Anzeige(einstellungen, rest, abgelaufen)
                    Spacer(Modifier.height(16.dp))
                    Steuerung(timer, laeuft, abgelaufen, onBearbeiten, context)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        RundKnopf("Zurück", onZurueck, Modifier.padding(12.dp), groesse = 52.dp) {
            SymbolBild(Symbol.ZURUECK_PFEIL, Text1, Modifier.size(26.dp))
        }

        if (klingelt) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier
                        .shadow(12.dp, RoundedCornerShape(28.dp))
                        .clip(RoundedCornerShape(28.dp))
                        .background(Karte)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Zeit ist um!", color = Akzent, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    Text(timer.name, color = Text2, fontSize = 18.sp)
                    Spacer(Modifier.height(24.dp))
                    RundKnopf("Ton stoppen", { KlingelDienst.stoppe(context) }, groesse = 96.dp, farbe = Akzent) {
                        SymbolBild(Symbol.STOPP, Color.White, Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Ton stoppen", color = Text1, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Titel(name: String) {
    Text(
        name.uppercase(),
        color = Text1,
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        textAlign = TextAlign.Center,
        maxLines = 2
    )
}

@Composable
private fun GrossesZifferblatt(timer: ZeitTimer, rest: Long, laeuft: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Zifferblatt(
            restMs = rest,
            dauerMs = timer.dauerMs,
            skalaMinuten = timer.wirksameSkala(),
            farbe = timer.farbe,
            modifier = Modifier.fillMaxSize().aspectRatio(1f, matchHeightConstraintsFirst = true),
            // Einstellen durch Ziehen nur, solange der Timer steht – wie beim echten Gerät.
            onDauer = if (laeuft) null else { neu -> TimerSteuerung.setzeDauer(context, timer.id, neu) }
        )
    }
}

@Composable
private fun Anzeige(einstellungen: Einstellungen, rest: Long, abgelaufen: Boolean) {
    if (abgelaufen) {
        Text("Zeit ist um", color = Akzent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    } else if (einstellungen.zahlAnzeigen) {
        Text(formatiereDauer(rest), color = Text1, fontSize = 40.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Steuerung(
    timer: ZeitTimer,
    laeuft: Boolean,
    abgelaufen: Boolean,
    onBearbeiten: () -> Unit,
    context: android.content.Context
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        RundKnopf("Eine Minute weniger", { TimerSteuerung.aendere(context, timer.id, -60_000L) }, aktiviert = !abgelaufen) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SymbolBild(Symbol.MINUS, Text1, Modifier.size(22.dp))
                Text("1 Min", color = Text2, fontSize = 11.sp)
            }
        }
        RundKnopf(
            if (laeuft) "Pause" else "Start",
            { TimerSteuerung.startenOderPausieren(context, timer.id) },
            groesse = 88.dp,
            farbe = Akzent
        ) {
            SymbolBild(if (laeuft) Symbol.PAUSE else Symbol.START, Color.White, Modifier.size(38.dp))
        }
        RundKnopf("Eine Minute mehr", { TimerSteuerung.aendere(context, timer.id, 60_000L) }, aktiviert = !abgelaufen) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SymbolBild(Symbol.PLUS, Text1, Modifier.size(22.dp))
                Text("1 Min", color = Text2, fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RundKnopf("Zurücksetzen", { TimerSteuerung.zuruecksetzen(context, timer.id) }, groesse = 56.dp) {
            SymbolBild(Symbol.ZURUECK, Text1, Modifier.size(28.dp))
        }
        RundKnopf("Bearbeiten", onBearbeiten, groesse = 56.dp) {
            SymbolBild(Symbol.STIFT, Text1, Modifier.size(28.dp))
        }
    }
}
