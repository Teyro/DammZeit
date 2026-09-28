package de.oejendorferdamm.dammzeit.zeit

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import de.oejendorferdamm.dammzeit.MainActivity
import de.oejendorferdamm.dammzeit.alarm.AlarmEmpfaenger
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer

/** Alles, was die Uhr verändert: einstellen, starten, pausieren – plus Wecker und Widgets. */
object TimerSteuerung {
    private val id = ZeitTimer.TIMER_ID

    fun starten(context: Context) {
        val t = Speicher.timer.value
        val jetzt = System.currentTimeMillis()
        val rest = if (t.pausiert) t.restBeiPauseMs else t.dauerMs
        val neu = t.copy(endeUm = jetzt + rest.coerceAtLeast(1000L), restBeiPauseMs = -1L)
        Speicher.speichere(neu)
        planeWecker(context, neu)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    fun pausieren(context: Context) {
        val t = Speicher.timer.value
        if (!t.laeuft) return
        Speicher.speichere(t.copy(endeUm = 0L, restBeiPauseMs = t.restMs(System.currentTimeMillis())))
        brichWeckerAb(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /**
     * Antippen der Uhr: klingelt sie, wird der Ton gestoppt und die Uhr steht wieder auf der
     * eingestellten Zeit; ist die Zeit um, geht es von vorn los; sonst Start bzw. Pause.
     */
    fun antippen(context: Context) {
        val t = Speicher.timer.value
        val jetzt = System.currentTimeMillis()
        when {
            KlingelDienst.klingelt.value != null -> {
                KlingelDienst.stoppe(context)
                zuruecksetzen(context)
            }
            t.abgelaufen(jetzt) -> {
                zuruecksetzen(context)
                starten(context)
            }
            t.laeuft -> pausieren(context)
            else -> starten(context)
        }
    }

    fun zuruecksetzen(context: Context) {
        Speicher.speichere(Speicher.timer.value.copy(endeUm = 0L, restBeiPauseMs = -1L))
        brichWeckerAb(context)
        KlingelDienst.stoppe(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Zeit einstellen (Ziehen am Zifferblatt) – hält eine laufende Uhr dabei an. */
    fun setzeDauer(context: Context, dauerMs: Long, starten: Boolean) {
        brichWeckerAb(context)
        KlingelDienst.stoppe(context)
        Speicher.speichere(ZeitTimer(dauerMs = dauerMs.coerceIn(1000L, MAX_DAUER)))
        if (starten) starten(context) else WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Nach einem Neustart des Geräts oder einem Update: laufende Uhr wieder einplanen. */
    fun alleNeuPlanen(context: Context) {
        val t = Speicher.timer.value
        if (t.laeuft && t.endeUm > System.currentTimeMillis()) planeWecker(context, t)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    private fun weckIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmEmpfaenger::class.java)
            .setAction(AlarmEmpfaenger.AKTION)
            .setData(Uri.parse("dammzeit://timer/$id"))
            .putExtra(AlarmEmpfaenger.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun oeffnenIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun planeWecker(context: Context, t: ZeitTimer) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = weckIntent(context)
        // Genauer Wecker (auch im Energiesparmodus pünktlich); falls das System das nicht
        // erlaubt, wenigstens ein ungefährer.
        val genauErlaubt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (genauErlaubt) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t.endeUm, oeffnenIntent(context)), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endeUm, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endeUm, pi)
        }
    }

    private fun brichWeckerAb(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(weckIntent(context))
    }

    const val MAX_DAUER = 12 * 60 * 60_000L
}
