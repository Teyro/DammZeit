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
import android.os.Build
import android.widget.RemoteViews
import de.oejendorferdamm.dammzeit.R
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.ui.ZifferblattZeichner
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/** Widget 2×2: nur die runde Uhr. Antippen startet/pausiert sie. */
class WidgetKlein : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Widget 4×4: dieselbe Uhr, größer und mit Zahlen. */
class WidgetGross : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Antippen des Widgets: wie Antippen der Uhr in der App (Start, Pause, Ton aus). */
class WidgetAktionEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        TimerSteuerung.antippen(context)
    }
}

/** Takt, solange die Uhr läuft: zeichnet die Scheibe der Widgets neu. */
class WidgetTaktEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/**
 * Baut die Widgets. Widgets können sich nicht flüssig bewegen – solange die Uhr läuft, wird
 * die Scheibe deshalb alle paar Sekunden neu gezeichnet (nur bei eingeschaltetem Bildschirm).
 */
object WidgetAktualisierer {
    private const val TAKT_MS = 5_000L

    fun aktualisiereAlle(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val klein = manager.getAppWidgetIds(ComponentName(context, WidgetKlein::class.java))
        val gross = manager.getAppWidgetIds(ComponentName(context, WidgetGross::class.java))
        if (klein.isEmpty() && gross.isEmpty()) {
            takt(context, an = false)
            return
        }
        if (klein.isNotEmpty()) manager.updateAppWidget(klein, baue(context, gross = false))
        if (gross.isNotEmpty()) manager.updateAppWidget(gross, baue(context, gross = true))
        val t = Speicher.timer.value
        takt(context, an = t.laeuft && !t.abgelaufen(System.currentTimeMillis()))
    }

    fun baue(context: Context, gross: Boolean): RemoteViews {
        val t = Speicher.timer.value
        val e = Speicher.einstellungen.value
        val views = RemoteViews(context.packageName, R.layout.widget_uhr)
        val seite = if (gross) 720 else 420
        val bild = Bitmap.createBitmap(seite, seite, Bitmap.Config.ARGB_8888)
        ZifferblattZeichner.zeichne(
            Canvas(bild), seite.toFloat(), seite.toFloat(), t.restMs(System.currentTimeMillis()), t.dauerMs,
            e.skalaMinuten, e.farbe, e.eigeneFarbe, e.hintergrund, mitZahlen = gross && e.zahlenAnzeigen
        )
        views.setImageViewBitmap(R.id.widget_zifferblatt, bild)
        val tippen = PendingIntent.getBroadcast(
            context, 0, Intent(context, WidgetAktionEmpfaenger::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_zifferblatt, tippen)
        return views
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
        val genau = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (genau) am.setExact(AlarmManager.RTC, wann, pi) else am.set(AlarmManager.RTC, wann, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, wann, pi)
        }
    }
}
