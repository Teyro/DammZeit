package de.oejendorferdamm.dammzeit.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.oejendorferdamm.dammzeit.R
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.ton.TonSpieler
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Wird vom Wecker ausgelöst, wenn ein Timer abläuft. */
class AlarmEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
        KlingelDienst.starte(context, id)
    }

    companion object {
        const val AKTION = "de.oejendorferdamm.dammzeit.ABGELAUFEN"
        const val EXTRA_ID = "timer_id"
    }
}

/** Nach dem Einschalten des Geräts laufende Timer wieder einplanen. */
class StartEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Speicher.init(context)
        TimerSteuerung.alleNeuPlanen(context)
    }
}

/**
 * Spielt den Signalton, wenn ein Timer abgelaufen ist – auch wenn die App geschlossen ist.
 * Läuft als Vordergrunddienst mit Benachrichtigung ("Stopp"), endet nach der eingestellten
 * Klingeldauer von selbst.
 */
class KlingelDienst : Service() {
    private var spieler: TonSpieler? = null
    private val handler = Handler(Looper.getMainLooper())
    private val ende = Runnable { beenden() }
    private var aktuelleId = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Speicher.init(this)
        if (intent?.action == AKTION_STOPP) {
            beenden()
            return START_NOT_STICKY
        }
        val id = intent?.getLongExtra(AlarmEmpfaenger.EXTRA_ID, -1L) ?: -1L
        val timer = Speicher.finde(id)
        if (timer == null) {
            // Nach startForegroundService muss startForeground in jedem Fall kommen.
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, benachrichtigung(this, "Timer", id),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            )
            beenden()
            return START_NOT_STICKY
        }
        aktuelleId = id
        val einstellungen = Speicher.einstellungen.value
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, benachrichtigung(this, timer.name, id),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
        _klingelt.value = id
        val neuerSpieler = spieler ?: TonSpieler(this).also { spieler = it }
        neuerSpieler.spiele(timer.ton, wiederholen = true)
        if (einstellungen.vibration) vibriere()
        handler.removeCallbacks(ende)
        handler.postDelayed(ende, einstellungen.klingelSekunden * 1000L)
        return START_NOT_STICKY
    }

    private fun vibriere() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600), -1))
        } catch (_: Exception) {
        }
    }

    private fun beenden() {
        handler.removeCallbacks(ende)
        spieler?.stopp()
        spieler = null
        _klingelt.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ende)
        spieler?.stopp()
        _klingelt.value = null
        super.onDestroy()
    }

    companion object {
        private const val KANAL = "klingeln"
        private const val NOTIFICATION_ID = 42
        const val AKTION_STOPP = "de.oejendorferdamm.dammzeit.STOPP"

        private val _klingelt = MutableStateFlow<Long?>(null)

        /** Id des Timers, der gerade klingelt (für die "Stopp"-Anzeige in der App). */
        val klingelt: StateFlow<Long?> = _klingelt

        fun starte(context: Context, id: Long) {
            val intent = Intent(context, KlingelDienst::class.java).putExtra(AlarmEmpfaenger.EXTRA_ID, id)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Darf der Dienst nicht starten (z. B. ungenauer Wecker unter Android 12+),
                // wenigstens eine Benachrichtigung mit Ton zeigen.
                val name = Speicher.finde(id)?.name ?: "Timer"
                kanalAnlegen(context)
                context.getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, benachrichtigung(context, name, id))
            }
        }

        fun stoppe(context: Context, id: Long? = null) {
            if (id != null && _klingelt.value != id) return
            if (_klingelt.value == null) return
            try {
                context.startService(Intent(context, KlingelDienst::class.java).setAction(AKTION_STOPP))
            } catch (_: Exception) {
            }
        }

        private fun kanalAnlegen(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(KANAL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(KANAL, "Timer abgelaufen", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Hinweis, wenn ein Timer abgelaufen ist"
                        setSound(null, null)
                    }
                )
            }
        }

        fun benachrichtigung(context: Context, name: String, id: Long): Notification {
            kanalAnlegen(context)
            val stopp = PendingIntent.getService(
                context, 1, Intent(context, KlingelDienst::class.java).setAction(AKTION_STOPP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val oeffnen = TimerSteuerung.oeffnenIntent(context, id)
            return NotificationCompat.Builder(context, KANAL)
                .setSmallIcon(R.drawable.ic_benachrichtigung)
                .setContentTitle("Zeit ist um!")
                .setContentText("„$name“ ist abgelaufen.")
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(oeffnen)
                .setFullScreenIntent(oeffnen, true)
                .setDeleteIntent(stopp)
                .addAction(0, "Stopp", stopp)
                .setAutoCancel(true)
                .build()
        }
    }
}
