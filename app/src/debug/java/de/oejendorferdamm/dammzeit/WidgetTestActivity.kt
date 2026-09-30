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

/** Zeigt beide Widgets so, wie der Startbildschirm sie aus den RemoteViews aufbaut – inklusive echter Knöpfe. */
class WidgetTestActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var klein: FrameLayout
    private lateinit var gross: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        val d = resources.displayMetrics.density
        klein = FrameLayout(this)
        gross = FrameLayout(this)
        val zeile = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF3A6EA5.toInt())
            addView(klein, LinearLayout.LayoutParams((260 * d).toInt(), (260 * d).toInt()).apply { rightMargin = (60 * d).toInt() })
            addView(gross, LinearLayout.LayoutParams((460 * d).toInt(), (460 * d).toInt()))
        }
        setContentView(zeile)
    }

    private val neu = object : Runnable {
        override fun run() {
            klein.removeAllViews()
            gross.removeAllViews()
            klein.addView(WidgetAktualisierer.baue(this@WidgetTestActivity, gross = false, groesse(260)).apply(this@WidgetTestActivity, klein))
            gross.addView(WidgetAktualisierer.baue(this@WidgetTestActivity, gross = true, groesse(460)).apply(this@WidgetTestActivity, gross))
            handler.postDelayed(this, 1000)
        }
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
