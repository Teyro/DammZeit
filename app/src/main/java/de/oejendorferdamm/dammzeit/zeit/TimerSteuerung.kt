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

/** Alles, was einen Timer verändert: starten, pausieren, zurücksetzen – plus Wecker und Widgets. */
object TimerSteuerung {

    fun starten(context: Context, id: Long) {
        val t = Speicher.finde(id) ?: return
        val jetzt = System.currentTimeMillis()
        val rest = if (t.pausiert) t.restBeiPauseMs else t.dauerMs
        val neu = t.copy(endeUm = jetzt + rest.coerceAtLeast(1000L), restBeiPauseMs = -1L)
        Speicher.speichere(neu)
        Speicher.widgetTimerId = id
        planeWecker(context, neu)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    fun pausieren(context: Context, id: Long) {
        val t = Speicher.finde(id) ?: return
        if (!t.laeuft) return
        val rest = t.restMs(System.currentTimeMillis())
        Speicher.speichere(t.copy(endeUm = 0L, restBeiPauseMs = rest))
        brichWeckerAb(context, id)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    fun startenOderPausieren(context: Context, id: Long) {
        val t = Speicher.finde(id) ?: return
        when {
            t.abgelaufen(System.currentTimeMillis()) -> {
                zuruecksetzen(context, id)
                starten(context, id)
            }
            t.laeuft -> pausieren(context, id)
            else -> starten(context, id)
        }
    }

    fun zuruecksetzen(context: Context, id: Long) {
        val t = Speicher.finde(id) ?: return
        Speicher.speichere(t.copy(endeUm = 0L, restBeiPauseMs = -1L))
        brichWeckerAb(context, id)
        KlingelDienst.stoppe(context, id)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Laufende Zeit verlängern/verkürzen (+/− eine Minute), sonst die eingestellte Dauer ändern. */
    fun aendere(context: Context, id: Long, deltaMs: Long) {
        val t = Speicher.finde(id) ?: return
        val jetzt = System.currentTimeMillis()
        when {
            t.laeuft && !t.abgelaufen(jetzt) -> {
                val neuesEnde = (t.endeUm + deltaMs).coerceAtLeast(jetzt + 1000L)
                val neu = t.copy(endeUm = neuesEnde)
                Speicher.speichere(neu)
                planeWecker(context, neu)
            }
            t.pausiert -> Speicher.speichere(t.copy(restBeiPauseMs = (t.restBeiPauseMs + deltaMs).coerceIn(1000L, MAX_DAUER)))
            else -> Speicher.speichere(t.copy(dauerMs = (t.dauerMs + deltaMs).coerceIn(1000L, MAX_DAUER), endeUm = 0L))
        }
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Dauer direkt setzen (Ziehen am Zifferblatt, solange der Timer steht). */
    fun setzeDauer(context: Context, id: Long, dauerMs: Long) {
        val t = Speicher.finde(id) ?: return
        if (t.laeuft && !t.abgelaufen(System.currentTimeMillis())) return
        brichWeckerAb(context, id)
        Speicher.speichere(t.copy(dauerMs = dauerMs.coerceIn(1000L, MAX_DAUER), endeUm = 0L, restBeiPauseMs = -1L))
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Timer bearbeitet/gelöscht: Wecker passend nachziehen. */
    fun nachAenderung(context: Context, timer: ZeitTimer?) {
        if (timer != null && timer.laeuft) planeWecker(context, timer)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    fun loeschen(context: Context, id: Long) {
        brichWeckerAb(context, id)
        KlingelDienst.stoppe(context, id)
        Speicher.loesche(id)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    /** Nach einem Neustart des Geräts: alle laufenden Timer wieder einplanen. */
    fun alleNeuPlanen(context: Context) {
        val jetzt = System.currentTimeMillis()
        Speicher.timer.value.filter { it.laeuft && it.endeUm > jetzt }.forEach { planeWecker(context, it) }
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    private fun weckIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmEmpfaenger::class.java)
            .setAction(AlarmEmpfaenger.AKTION)
            .setData(Uri.parse("dammzeit://timer/$id"))
            .putExtra(AlarmEmpfaenger.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun oeffnenIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("dammzeit://oeffnen/$id"))
            .putExtra(MainActivity.EXTRA_TIMER, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun planeWecker(context: Context, t: ZeitTimer) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = weckIntent(context, t.id)
        // Genauer Wecker (auch im Energiesparmodus pünktlich); falls das System das nicht
        // erlaubt, wenigstens ein ungefährer.
        val genauErlaubt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (genauErlaubt) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(t.endeUm, oeffnenIntent(context, t.id)), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endeUm, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.endeUm, pi)
        }
    }

    private fun brichWeckerAb(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(weckIntent(context, id))
    }

    const val MAX_DAUER = 12 * 60 * 60_000L
}
