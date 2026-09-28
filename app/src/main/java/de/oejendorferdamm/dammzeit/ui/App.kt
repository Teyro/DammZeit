package de.oejendorferdamm.dammzeit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzeit.BuildConfig
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.formatiereDauer
import de.oejendorferdamm.dammzeit.model.titelFuer
import de.oejendorferdamm.dammzeit.update.UpdateClient
import de.oejendorferdamm.dammzeit.update.UpdateInfo
import de.oejendorferdamm.dammzeit.update.istNeuereVersion
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Die ganze App: die Uhr und ein Zahnrad – mehr nicht. Ausgelegt für Touch-Boards im
 * Querformat (Full HD und 4K); alle Maße sind "Vorbild-Pixel" eines 1920×1080-Bildschirms
 * und wachsen proportional mit (siehe Skalierung.kt).
 */
@Composable
fun DammZeitApp() {
    val context = LocalContext.current
    val timer by Speicher.timer.collectAsState()
    val einstellungen by Speicher.einstellungen.collectAsState()
    val klingelt by KlingelDienst.klingelt.collectAsState()
    var menueOffen by remember { mutableStateOf(false) }
    // Während des Ziehens zeigt die Uhr die neue Zeit, gespeichert wird erst beim Loslassen.
    var gezogeneDauer by remember { mutableStateOf<Long?>(null) }

    val jetzt by rememberJetzt(aktiv = timer.laeuft)
    val abgelaufen = timer.abgelaufen(jetzt)
    val laeuft = timer.laeuft && !abgelaufen
    val anzeigeDauer = gezogeneDauer ?: timer.dauerMs
    val anzeigeRest = gezogeneDauer ?: timer.restMs(jetzt)

    val view = LocalView.current
    val anlassen = laeuft && einstellungen.bildschirmAn
    DisposableEffect(anlassen) {
        view.keepScreenOn = anlassen
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = menueOffen) { menueOffen = false }

    // Benachrichtigung "Zeit ist um" braucht ab Android 13 eine Erlaubnis.
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Updates: automatisch kurz nach dem Start (nur roter Punkt am Zahnrad) oder von Hand.
    val scope = rememberCoroutineScope()
    val updateClient = remember { UpdateClient() }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var pruefungLaeuft by remember { mutableStateOf(false) }
    var bereitsAktuell by remember { mutableStateOf(false) }
    suspend fun pruefe() {
        pruefungLaeuft = true
        bereitsAktuell = false
        updateClient.neuesteVersionAbrufen().onSuccess { info ->
            if (istNeuereVersion(BuildConfig.VERSION_NAME, info.version)) updateInfo = info else bereitsAktuell = true
        }
        pruefungLaeuft = false
    }
    LaunchedEffect(einstellungen.autoUpdate) {
        if (einstellungen.autoUpdate) {
            delay(8_000)
            pruefe()
        }
    }

    val grund = Color(ZifferblattZeichner.flaechenFarbe(einstellungen.hintergrund))
    val schrift = Color(ZifferblattZeichner.schriftFarbe(einstellungen.hintergrund))

    Box(Modifier.fillMaxSize().background(grund)) {
        VorbildRaster(faktor = 1f) {
            Row(Modifier.fillMaxSize().systemBarsPadding()) {
                // --- Die Uhr ---
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (einstellungen.titelAnzeigen) {
                            Text(
                                titelFuer(anzeigeDauer), color = schrift, fontSize = 52.sp,
                                fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp
                            )
                        }
                        Zifferblatt(
                            restMs = anzeigeRest,
                            dauerMs = anzeigeDauer,
                            einstellungen = einstellungen,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onZiehen = { neu -> gezogeneDauer = neu },
                            onLoslassen = {
                                gezogeneDauer?.let { TimerSteuerung.setzeDauer(context, it, starten = einstellungen.sofortStarten) }
                                gezogeneDauer = null
                            },
                            onTippen = { TimerSteuerung.antippen(context) }
                        )
                        when {
                            klingelt != null || abgelaufen -> Text(
                                "Zeit ist um – zum Stoppen auf die Uhr tippen", color = Akzent, fontSize = 44.sp,
                                fontWeight = FontWeight.Bold
                            )
                            einstellungen.restzeitAnzeigen -> Text(
                                formatiereDauer(anzeigeRest), color = schrift, fontSize = 72.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // --- Das Zahnrad ---
                    if (!menueOffen) {
                        Box(Modifier.align(Alignment.TopEnd).padding(28.dp)) {
                            RundKnopf("Einstellungen", { menueOffen = true }, groesse = 84.dp) {
                                SymbolBild(Symbol.ZAHNRAD, Text1, Modifier.size(42.dp))
                            }
                            if (updateInfo != null) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).size(22.dp)
                                        .background(Akzent, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                        }
                    }
                }

                // --- Einstellungen: rechts daneben, die Uhr bleibt sichtbar ---
                AnimatedVisibility(
                    visible = menueOffen,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it }
                ) {
                    EinstellungenPanel(
                        einstellungen = einstellungen,
                        version = BuildConfig.VERSION_NAME,
                        updateInfo = updateInfo,
                        pruefungLaeuft = pruefungLaeuft,
                        bereitsAktuell = bereitsAktuell,
                        onJetztPruefen = { scope.launch { pruefe() } },
                        onSchliessen = { menueOffen = false },
                        modifier = Modifier.width(760.dp).fillMaxHeight()
                    )
                }
            }
        }
    }
}
