package de.oejendorferdamm.dammzeit

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer
import de.oejendorferdamm.dammzeit.zeit.TimerSteuerung

/** Zeigt beide Widgets so, wie der Startbildschirm sie aus den RemoteViews aufbaut – inklusive echter Knöpfe. */
class WidgetTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var klein: FrameLayout
    private lateinit var gross: FrameLayout
    private lateinit var stoppuhr: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        val d = resources.displayMetrics.density
        klein = FrameLayout(this)
        gross = FrameLayout(this)
        stoppuhr = FrameLayout(this)
        de.oejendorferdamm.dammzeit.stoppuhr.StoppuhrSpeicher.init(this)
        // Für den Test: mit ruhender Uhr beginnen, damit uiautomator die Knöpfe findet
        if (intent.getBooleanExtra("zuruecksetzen", false)) TimerSteuerung.zuruecksetzen(this)
        val zeile = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF3A6EA5.toInt())
            addView(klein, LinearLayout.LayoutParams((260 * d).toInt(), (260 * d).toInt()).apply { rightMargin = (60 * d).toInt() })
            addView(gross, LinearLayout.LayoutParams((460 * d).toInt(), (460 * d).toInt()))
            addView(stoppuhr, LinearLayout.LayoutParams((400 * d).toInt(), (220 * d).toInt()).apply { leftMargin = (60 * d).toInt() })
        }
        setContentView(zeile)
    }

    private val neu = object : Runnable {
        override fun run() {
            // Wie der Startbildschirm: vorhandene Ansicht nur aktualisieren (reapply), nicht neu aufbauen.
            zeige(klein, WidgetAktualisierer.baue(this@WidgetTestActivity, gross = false, groesse(260)))
            zeige(gross, WidgetAktualisierer.baue(this@WidgetTestActivity, gross = true, groesse(460)))
            zeige(stoppuhr, de.oejendorferdamm.dammzeit.stoppuhr.StoppuhrWidget.baue(this@WidgetTestActivity, Bundle().apply {
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 400)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
            }))
            handler.postDelayed(this, 2000)
        }
    }

    private fun zeige(rahmen: FrameLayout, views: android.widget.RemoteViews) {
        if (rahmen.childCount == 0) rahmen.addView(views.apply(this, rahmen)) else views.reapply(this, rahmen.getChildAt(0))
    }

    private fun groesse(dp: Int) = Bundle().apply {
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, dp)
        putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, dp)
    }

    override fun onResume() {
        super.onResume()
        handler.post(neu)
    }

    override fun onPause() {
        handler.removeCallbacks(neu)
        super.onPause()
    }
}
