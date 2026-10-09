package de.oejendorferdamm.dammzeit.stoppuhr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.oejendorferdamm.dammzeit.R

/** Widget "Stoppuhr" (3 × 2): läuft live hoch, Runden, Speichern für die Bestenliste. */
class StoppuhrWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = StoppuhrWidget.aktualisiereAlle(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, optionen: Bundle?) = StoppuhrWidget.aktualisiereAlle(context)
}

/** Knöpfe des Stoppuhr-Widgets. */
class StoppuhrAktion : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            START_STOPP -> StoppuhrSteuerung.startStopp(context)
            RUNDE -> StoppuhrSteuerung.runde(context)
            ZURUECK -> StoppuhrSteuerung.zuruecksetzen(context)
        }
    }

    companion object {
        const val START_STOPP = "de.oejendorferdamm.dammzeit.STOPPUHR_START_STOPP"
        const val RUNDE = "de.oejendorferdamm.dammzeit.STOPPUHR_RUNDE"
        const val ZURUECK = "de.oejendorferdamm.dammzeit.STOPPUHR_ZURUECK"
    }
}

object StoppuhrWidget {
    fun aktualisiereAlle(context: Context) {
        StoppuhrSpeicher.init(context)
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (id in manager.getAppWidgetIds(ComponentName(context, StoppuhrWidgetProvider::class.java))) {
            try {
                manager.updateAppWidget(id, baue(context, manager.getAppWidgetOptions(id)))
            } catch (_: Exception) {
            }
        }
    }

    fun baue(context: Context, optionen: Bundle?): RemoteViews {
        val u = StoppuhrSpeicher.uhr.value
        val zeit = u.zeitMs(System.currentTimeMillis())
        val hoeheDp = optionen?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)?.takeIf { it > 0 } ?: 110
        val breiteDp = optionen?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: 250
        // Flache Widgets: Zeit links, Knöpfe rechts; sonst Knöpfe unter der Zeit
        val flach = hoeheDp < 180
        val v = RemoteViews(context.packageName, if (flach) R.layout.widget_stoppuhr_breit else R.layout.widget_stoppuhr)

        // Schriftgröße nach dem Platz, der neben bzw. über den Knöpfen bleibt – groß genug für die hinteren Reihen
        val schrift = if (flach) minOf((hoeheDp - 16 - 34) / 1.2f, (breiteDp - 16 - 110) * 0.25f)
        else minOf((hoeheDp - 20 - 22 - 20 - 66) / 1.2f, breiteDp * 0.2f)
        v.setTextViewTextSize(R.id.stopp_zeit, TypedValue.COMPLEX_UNIT_DIP, schrift.coerceIn(22f, 140f))
        v.setViewVisibility(R.id.stopp_kopf, if (flach && hoeheDp < 120) View.GONE else View.VISIBLE)

        // Chronometer zählt live hoch; angehalten zeigt er die gestoppte Zeit
        v.setChronometerCountDown(R.id.stopp_zeit, false)
        v.setChronometer(R.id.stopp_zeit, SystemClock.elapsedRealtime() - zeit, null, u.laeuft)
        v.setTextColor(R.id.stopp_zeit, if (u.laeuft) 0xFF1F2124.toInt() else if (u.frisch) 0xFF9AA0A8.toInt() else 0xFF0B6B88.toInt())

        val rundenText = when {
            u.runden.isNotEmpty() -> "Runde ${u.runden.size}: ${formatStoppzeit(u.runden.last() - (u.runden.getOrNull(u.runden.size - 2) ?: 0L))}"
            u.laeuft -> "läuft …"
            u.frisch -> "Antippen zum Starten"
            else -> "gestoppt"
        }
        v.setTextViewText(R.id.stopp_runden, rundenText)
        v.setImageViewResource(R.id.stopp_startstopp, if (u.laeuft) R.drawable.ic_w_stopp else R.drawable.ic_w_start)
        v.setViewVisibility(R.id.stopp_zurueck, if (u.frisch) View.INVISIBLE else View.VISIBLE)
        v.setViewVisibility(R.id.stopp_runde, if (u.laeuft) View.VISIBLE else View.INVISIBLE)
        v.setViewVisibility(R.id.stopp_speichern, if (!u.laeuft && !u.frisch) View.VISIBLE else View.INVISIBLE)

        v.setOnClickPendingIntent(R.id.stopp_startstopp, aktion(context, StoppuhrAktion.START_STOPP, 20))
        v.setOnClickPendingIntent(R.id.stopp_zeit, aktion(context, StoppuhrAktion.START_STOPP, 20))
        v.setOnClickPendingIntent(R.id.stopp_runde, aktion(context, StoppuhrAktion.RUNDE, 21))
        v.setOnClickPendingIntent(R.id.stopp_zurueck, aktion(context, StoppuhrAktion.ZURUECK, 22))
        v.setOnClickPendingIntent(
            R.id.stopp_speichern,
            PendingIntent.getActivity(
                context, 23,
                Intent(context, SpeichernActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        return v
    }

    private fun aktion(context: Context, aktion: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code, Intent(context, StoppuhrAktion::class.java).setAction(aktion),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
