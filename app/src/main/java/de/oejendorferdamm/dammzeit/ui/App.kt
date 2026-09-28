package de.oejendorferdamm.dammzeit.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.oejendorferdamm.dammzeit.BuildConfig
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.update.UpdateClient
import de.oejendorferdamm.dammzeit.update.UpdateInfo
import de.oejendorferdamm.dammzeit.update.istNeuereVersion
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Seite {
    data object Liste : Seite
    data class Ansicht(val id: Long) : Seite
    data class Bearbeiten(val id: Long?, val zurueckZurAnsicht: Boolean) : Seite
    data object Einstellungen : Seite
}

/** Wurzel der App: Timer-Liste, Großansicht, Bearbeiten und Einstellungen. */
@Composable
fun DammZeitApp(angefragterTimer: MutableState<Long?>) {
    val context = LocalContext.current
    val timer by Speicher.timer.collectAsState()
    val einstellungen by Speicher.einstellungen.collectAsState()
    val klingelt by KlingelDienst.klingelt.collectAsState()
    var seite by remember { mutableStateOf<Seite>(Seite.Liste) }

    // Aus Widget oder Benachrichtigung geöffnet: direkt zum Timer.
    LaunchedEffect(angefragterTimer.value) {
        val id = angefragterTimer.value ?: return@LaunchedEffect
        if (Speicher.finde(id) != null) {
            Speicher.widgetTimerId = id
            seite = Seite.Ansicht(id)
        }
        angefragterTimer.value = null
    }

    // Klingelt ein Timer, direkt dessen Ansicht mit "Ton stoppen" zeigen.
    LaunchedEffect(klingelt) {
        val id = klingelt ?: return@LaunchedEffect
        if (Speicher.finde(id) != null) seite = Seite.Ansicht(id)
    }

    // Ab Android 13 braucht der "Zeit ist um"-Hinweis die Erlaubnis für Benachrichtigungen.
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Updates: automatisch kurz nach dem Start (nur roter Punkt) oder von Hand.
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

    BackHandler(enabled = seite != Seite.Liste) {
        seite = when (val s = seite) {
            is Seite.Bearbeiten -> if (s.zurueckZurAnsicht && s.id != null) Seite.Ansicht(s.id) else Seite.Liste
            else -> Seite.Liste
        }
    }

    when (val s = seite) {
        is Seite.Liste -> Startseite(
            timer = timer,
            zeigeUpdatePunkt = updateInfo != null,
            onOeffnen = { id ->
                Speicher.widgetTimerId = id
                seite = Seite.Ansicht(id)
            },
            onNeu = { seite = Seite.Bearbeiten(null, zurueckZurAnsicht = false) },
            onEinstellungen = { seite = Seite.Einstellungen }
        )
        is Seite.Ansicht -> {
            val t = timer.firstOrNull { it.id == s.id }
            if (t == null) {
                seite = Seite.Liste
            } else {
                TimerAnsicht(
                    timer = t,
                    einstellungen = einstellungen,
                    klingelt = klingelt == t.id,
                    onZurueck = { seite = Seite.Liste },
                    onBearbeiten = { seite = Seite.Bearbeiten(t.id, zurueckZurAnsicht = true) }
                )
            }
        }
        is Seite.Bearbeiten -> {
            val vorhanden = s.id?.let { id -> timer.firstOrNull { it.id == id } }
            val ausgang = vorhanden ?: remember(s) {
                ZeitTimer(
                    id = Speicher.neueId(),
                    name = "10 Minuten-Timer",
                    dauerMs = 10 * 60_000L,
                    farbe = einstellungen.standardFarbe
                )
            }
            Bearbeiten(
                ausgang = ausgang,
                istNeu = vorhanden == null,
                onSpeichern = { neu ->
                    // Läuft der Timer gerade, bleibt er laufen; die neue Dauer gilt ab dem nächsten Start.
                    val gespeichert = if (vorhanden != null) neu.copy(endeUm = vorhanden.endeUm, restBeiPauseMs = vorhanden.restBeiPauseMs) else neu
                    Speicher.speichere(gespeichert)
                    TimerSteuerung.nachAenderung(context, gespeichert)
                    Speicher.widgetTimerId = gespeichert.id
                    seite = Seite.Ansicht(gespeichert.id)
                },
                onLoeschen = {
                    s.id?.let { TimerSteuerung.loeschen(context, it) }
                    seite = Seite.Liste
                },
                onAbbrechen = {
                    seite = if (s.zurueckZurAnsicht && s.id != null) Seite.Ansicht(s.id) else Seite.Liste
                }
            )
        }
        is Seite.Einstellungen -> EinstellungenSeite(
            einstellungen = einstellungen,
            version = BuildConfig.VERSION_NAME,
            updateInfo = updateInfo,
            pruefungLaeuft = pruefungLaeuft,
            bereitsAktuell = bereitsAktuell,
            onJetztPruefen = { scope.launch { pruefe() } },
            onZurueck = { seite = Seite.Liste }
        )
    }
}
