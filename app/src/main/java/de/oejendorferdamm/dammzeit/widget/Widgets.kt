package de.oejendorferdamm.dammzeit.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import de.oejendorferdamm.dammzeit.R
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import de.oejendorferdamm.dammzeit.ui.ZifferblattZeichner
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/** Widget 2×2: Zifferblatt mit Restzeit. */
class WidgetKlein : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Widget 4×4: großes Zifferblatt, Name, Restzeit und Knöpfe Start/Pause und Zurücksetzen. */
class WidgetGross : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Knöpfe im großen Widget. */
class WidgetAktionEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        when (intent.action) {
            AKTION_START_PAUSE -> TimerSteuerung.startenOderPausieren(context, id)
            AKTION_ZURUECK -> TimerSteuerung.zuruecksetzen(context, id)
        }
    }

    companion object {
        const val AKTION_START_PAUSE = "de.oejendorferdamm.dammzeit.WIDGET_START_PAUSE"
        const val AKTION_ZURUECK = "de.oejendorferdamm.dammzeit.WIDGET_ZURUECK"
        const val EXTRA_ID = "timer_id"
    }
}

/** Takt, solange ein Timer läuft: zeichnet die Scheibe der Widgets neu. */
class WidgetTaktEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/**
 * Baut die Widgets. Widgets können sich nicht selbst flüssig bewegen – deshalb:
 * die Restzeit als Zahl zählt als Chronometer vom System jede Sekunde live herunter, und die
 * farbige Scheibe wird, solange der Timer läuft, alle paar Sekunden neu gezeichnet.
 */
object WidgetAktualisierer {
    private const val TAKT_MS = 10_000L

    fun aktualisiereAlle(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val klein = manager.getAppWidgetIds(ComponentName(context, WidgetKlein::class.java))
        val gross = manager.getAppWidgetIds(ComponentName(context, WidgetGross::class.java))
        if (klein.isEmpty() && gross.isEmpty()) {
            takt(context, an = false)
            return
        }
        val timer = Speicher.widgetTimer()
        val jetzt = System.currentTimeMillis()
        if (klein.isNotEmpty()) manager.updateAppWidget(klein, baue(context, timer, jetzt, gross = false))
        if (gross.isNotEmpty()) manager.updateAppWidget(gross, baue(context, timer, jetzt, gross = true))
        takt(context, an = timer != null && timer.laeuft && !timer.abgelaufen(jetzt))
    }

    private fun baue(context: Context, timer: ZeitTimer?, jetzt: Long, gross: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, if (gross) R.layout.widget_gross else R.layout.widget_klein)
        if (timer == null) {
            views.setTextViewText(R.id.widget_name, "Kein Timer")
            return views
        }
        val rest = timer.restMs(jetzt)
        val seite = if (gross) 720 else 400
        val bild = Bitmap.createBitmap(seite, seite, Bitmap.Config.ARGB_8888)
        ZifferblattZeichner.zeichne(
            Canvas(bild), seite.toFloat(), seite.toFloat(), rest, timer.dauerMs,
            timer.wirksameSkala(), timer.farbe, mitZahlen = gross
        )
        views.setImageViewBitmap(R.id.widget_zifferblatt, bild)
        views.setTextViewText(R.id.widget_name, timer.name)

        // Restzeit: läuft der Timer, zählt der Chronometer selbst sekündlich herunter.
        val laeuft = timer.laeuft && !timer.abgelaufen(jetzt)
        views.setChronometer(R.id.widget_restzeit, SystemClock.elapsedRealtime() + rest, null, laeuft)
        views.setChronometerCountDown(R.id.widget_restzeit, true)

        val oeffnen = TimerSteuerung.oeffnenIntent(context, timer.id)
        views.setOnClickPendingIntent(R.id.widget_zifferblatt, oeffnen)
        views.setOnClickPendingIntent(R.id.widget_name, oeffnen)

        if (gross) {
            views.setImageViewResource(R.id.widget_start, if (laeuft) R.drawable.ic_pause else R.drawable.ic_start)
            views.setContentDescription(R.id.widget_start, if (laeuft) "Pause" else "Start")
            views.setOnClickPendingIntent(R.id.widget_start, aktion(context, WidgetAktionEmpfaenger.AKTION_START_PAUSE, timer.id))
            views.setOnClickPendingIntent(R.id.widget_zurueck, aktion(context, WidgetAktionEmpfaenger.AKTION_ZURUECK, timer.id))
            views.setViewVisibility(R.id.widget_hinweis, if (timer.abgelaufen(jetzt)) View.VISIBLE else View.GONE)
        }
        return views
    }

    private fun aktion(context: Context, aktion: String, id: Long): PendingIntent {
        val intent = Intent(context, WidgetAktionEmpfaenger::class.java)
            .setAction(aktion)
            .setData(Uri.parse("dammzeit://widget/$aktion/$id"))
            .putExtra(WidgetAktionEmpfaenger.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun takt(context: Context, an: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetTaktEmpfaenger::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (!an) {
            am.cancel(pi)
            return
        }
        val wann = System.currentTimeMillis() + TAKT_MS
        // Nur solange das Gerät wach ist (RTC ohne WAKEUP) – bei ausgeschaltetem Bildschirm
        // sieht ohnehin niemand das Widget, und der Akku wird geschont.
        val genau = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (genau) am.setExact(AlarmManager.RTC, wann, pi) else am.set(AlarmManager.RTC, wann, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, wann, pi)
        }
    }
}
