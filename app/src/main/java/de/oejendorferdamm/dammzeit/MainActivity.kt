package de.oejendorferdamm.dammzeit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.ui.Akzent
import de.oejendorferdamm.dammzeit.ui.DammZeitApp
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer

/** Die Uhr im Vollbild – auf dem Board sollen keine Systemleisten ablenken. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        enableEdgeToEdge()
        vollbild()
        verarbeite()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Akzent)) {
                DammZeitApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verarbeite()
    }

    override fun onResume() {
        super.onResume()
        vollbild()
        WidgetAktualisierer.aktualisiereAlle(this)
    }

    private fun vollbild() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            // Leisten kurz einblenden, wenn man vom Rand wischt – danach wieder weg.
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun verarbeite() {
        // Klingelt die Uhr, die App auch über dem Sperrbildschirm zeigen.
        if (KlingelDienst.klingelt.value != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
    }
}
