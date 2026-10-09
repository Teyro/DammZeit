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
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.oejendorferdamm.dammzeit.R
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.model.Hintergrund
import de.oejendorferdamm.dammzeit.ui.ZeitWahlActivity
import de.oejendorferdamm.dammzeit.ui.ZifferblattZeichner
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/** Widget 2×2: die Uhr mit Knöpfen. */
class WidgetKlein : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, optionen: Bundle?) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Widget 4×4: dieselbe Uhr, größer. */
class WidgetGross : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, optionen: Bundle?) {
        Speicher.init(context)
        WidgetAktualisierer.aktualisiereAlle(context)
    }
}

/** Knöpfe und Antippen des Widgets. */
class WidgetAktionEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Speicher.init(context)
        when (intent.action) {
            AKTION_ZURUECKSETZEN -> TimerSteuerung.zuruecksetzen(context)
            // Zahl am Rand angetippt: Zeit einstellen (und je nach Einstellung gleich starten)
            AKTION_ZEIT -> {
                val ms = intent.getLongExtra(EXTRA_MS, 0L)
                if (ms > 0) TimerSteuerung.setzeDauer(context, ms, starten = Speicher.einstellungen.value.sofortStarten)
            }
            // Uhr antippen und Start/Stopp-Knopf: wie der Startknopf in der App.
            else -> TimerSteuerung.antippen(context)
        }
    }

    companion object {
        const val AKTION_ANTIPPEN = "de.oejendorferdamm.dammzeit.WIDGET_ANTIPPEN"
        const val AKTION_ZURUECKSETZEN = "de.oejendorferdamm.dammzeit.WIDGET_ZURUECKSETZEN"
        const val AKTION_ZEIT = "de.oejendorferdamm.dammzeit.WIDGET_ZEIT"
        const val EXTRA_MS = "ms"
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
 * Baut die Widgets. Die Restzeit auf der Scheibe ist ein Chronometer und zählt dadurch
 * sekundengenau live herunter; die farbige Scheibe selbst ist ein Bild und wird, solange die
 * Uhr läuft, alle paar Sekunden neu gezeichnet.
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
        for (id in klein) manager.updateAppWidget(id, baue(context, gross = false, manager.getAppWidgetOptions(id)))
        for (id in gross) manager.updateAppWidget(id, baue(context, gross = true, manager.getAppWidgetOptions(id)))
        val t = Speicher.timer.value
        takt(context, an = t.laeuft && !t.abgelaufen(System.currentTimeMillis()))
    }

    fun baue(context: Context, gross: Boolean, optionen: Bundle? = null): RemoteViews {
        val t = Speicher.timer.value
        val e = Speicher.einstellungen.value
        val jetzt = System.currentTimeMillis()
        val rest = t.restMs(jetzt)
        val abgelaufen = t.abgelaufen(jetzt)
        val laeuft = t.laeuft && !abgelaufen
        val frisch = !t.laeuft && !t.pausiert && !abgelaufen
        val views = RemoteViews(context.packageName, R.layout.widget_uhr)

        // Zifferblatt – mit großen Zahlen, damit man sie auch von hinten im Raum lesen kann.
        val seite = if (gross) 900 else 640
        val bild = Bitmap.createBitmap(seite, seite, Bitmap.Config.ARGB_8888)
        ZifferblattZeichner.zeichne(
            Canvas(bild), seite.toFloat(), seite.toFloat(), rest, t.dauerMs,
            e.skalaMinuten, e.farbe, e.eigeneFarbe, e.hintergrund,
            mitZahlen = e.zahlenAnzeigen, grosseZahlen = true,
            imUhrzeigersinn = e.laufrichtung == de.oejendorferdamm.dammzeit.model.Laufrichtung.IM_UHRZEIGERSINN
        )
        views.setImageViewBitmap(R.id.widget_zifferblatt, bild)

        // Restzeit auf der Scheibe: Größe passend zur Widgetgröße.
        val breiteDp = optionen?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val hoeheDp = optionen?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val seiteDp = minOf(breiteDp, hoeheDp).takeIf { it > 0 } ?: if (gross) 300 else 160
        val schrift = (seiteDp * 0.10f).coerceIn(14f, 64f)
        views.setTextViewTextSize(R.id.widget_rest, TypedValue.COMPLEX_UNIT_DIP, schrift)
        views.setTextViewTextSize(R.id.widget_rest_ende, TypedValue.COMPLEX_UNIT_DIP, schrift)
        val dunkel = e.hintergrund == Hintergrund.DUNKEL || e.hintergrund == Hintergrund.SCHWARZ
        views.setInt(R.id.widget_rest_rahmen, "setBackgroundResource", if (dunkel) R.drawable.widget_pille_dunkel else R.drawable.widget_pille_hell)
        views.setTextColor(R.id.widget_rest, if (dunkel) 0xFFF0F0F0.toInt() else 0xFF1F2124.toInt())

        if (abgelaufen) {
            views.setViewVisibility(R.id.widget_rest, View.GONE)
            views.setViewVisibility(R.id.widget_rest_ende, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_rest, View.VISIBLE)
            views.setViewVisibility(R.id.widget_rest_ende, View.GONE)
            views.setChronometerCountDown(R.id.widget_rest, true)
            views.setChronometer(R.id.widget_rest, SystemClock.elapsedRealtime() + rest, null, laeuft)
        }

        // Knöpfe
        views.setImageViewResource(
            R.id.widget_startstopp,
            when {
                abgelaufen -> R.drawable.ic_w_stopp
                laeuft -> R.drawable.ic_w_pause
                else -> R.drawable.ic_w_start
            }
        )
        views.setViewVisibility(R.id.widget_zuruecksetzen, if (frisch) View.INVISIBLE else View.VISIBLE)

        val antippen = aktion(context, WidgetAktionEmpfaenger.AKTION_ANTIPPEN, 0)
        // Tippflächen: Mitte = Start/Pause, Rand = Zeit der nächstliegenden Zahl
        val schritte = 12
        val imUhrzeigersinn = e.laufrichtung == de.oejendorferdamm.dammzeit.model.Laufrichtung.IM_UHRZEIGERSINN
        for (zr in 0 until RASTER) for (zs in 0 until RASTER) {
            val id = zellenIds(context)[zr * RASTER + zs]
            if (id == 0) continue
            val u = (zs + 0.5f) / RASTER - 0.5f
            val w = (zr + 0.5f) / RASTER - 0.5f
            if (kotlin.math.hypot(u, w) < 0.26f) {
                views.setOnClickPendingIntent(id, antippen)
                continue
            }
            // Winkel ab 12 Uhr im Uhrzeigersinn, dann auf die nächste Zahl runden
            var grad = Math.toDegrees(kotlin.math.atan2(u.toDouble(), (-w).toDouble()))
            if (grad < 0) grad += 360.0
            if (!imUhrzeigersinn) grad = (360.0 - grad) % 360.0
            var stufe = Math.round(grad / 360.0 * schritte).toInt() % schritte
            if (stufe == 0) stufe = schritte // die "0" oben = volle Runde
            val ms = e.skalaMinuten * 60_000L * stufe / schritte
            views.setOnClickPendingIntent(
                id,
                PendingIntent.getBroadcast(
                    context, 100 + zr * RASTER + zs,
                    Intent(context, WidgetAktionEmpfaenger::class.java).setAction(WidgetAktionEmpfaenger.AKTION_ZEIT).putExtra(WidgetAktionEmpfaenger.EXTRA_MS, ms),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        // Startknopf in der Farbe der Zeitscheibe
        views.setInt(
            R.id.widget_startstopp, "setBackgroundResource",
            when (e.farbe) {
                de.oejendorferdamm.dammzeit.model.FarbModus.PETROL -> R.drawable.widget_knopf_start_petrol
                de.oejendorferdamm.dammzeit.model.FarbModus.AMPEL -> R.drawable.widget_knopf_start_gruen
                de.oejendorferdamm.dammzeit.model.FarbModus.EIGENE -> R.drawable.widget_knopf_start_dunkel
                else -> R.drawable.widget_knopf_start_rot
            }
        )
        views.setOnClickPendingIntent(R.id.widget_zifferblatt, antippen)
        views.setOnClickPendingIntent(R.id.widget_rest_rahmen, antippen)
        views.setOnClickPendingIntent(R.id.widget_startstopp, antippen)
        views.setOnClickPendingIntent(R.id.widget_zuruecksetzen, aktion(context, WidgetAktionEmpfaenger.AKTION_ZURUECKSETZEN, 1))
        views.setOnClickPendingIntent(
            R.id.widget_zeit,
            PendingIntent.getActivity(
                context, 2,
                Intent(context, ZeitWahlActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return views
    }

    private const val RASTER = 10
    @Volatile private var ids: IntArray? = null

    /** Ids der Tippflächen z_<zeile>_<spalte> (einmal nachschlagen). */
    private fun zellenIds(context: Context): IntArray = ids ?: IntArray(RASTER * RASTER) { i ->
        context.resources.getIdentifier("z_${i / RASTER}_${i % RASTER}", "id", context.packageName)
    }.also { ids = it }

    private fun aktion(context: Context, aktion: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, WidgetAktionEmpfaenger::class.java).setAction(aktion),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

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
        // Spätestens genau zum Ablauf neu zeichnen, damit "Zeit um!" pünktlich erscheint.
        val t = Speicher.timer.value
        val jetzt = System.currentTimeMillis()
        val wann = minOf(jetzt + TAKT_MS, maxOf(t.endeUm + 50L, jetzt + 200L))
        val genau = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (genau) am.setExact(AlarmManager.RTC, wann, pi) else am.set(AlarmManager.RTC, wann, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, wann, pi)
        }
    }
}
