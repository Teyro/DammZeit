package de.oejendorferdamm.dammzeit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import de.oejendorferdamm.dammzeit.alarm.KlingelDienst
import de.oejendorferdamm.dammzeit.data.Speicher
import de.oejendorferdamm.dammzeit.ui.Akzent
import de.oejendorferdamm.dammzeit.ui.DammZeitApp
import de.oejendorferdamm.dammzeit.widget.WidgetAktualisierer

class MainActivity : ComponentActivity() {
    private val angefragterTimer = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Speicher.init(this)
        enableEdgeToEdge()
        verarbeite(intent)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Akzent)) {
                DammZeitApp(angefragterTimer)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verarbeite(intent)
    }

    override fun onResume() {
        super.onResume()
        // Widgets auf den neuesten Stand bringen (z. B. nach längerer Pause).
        WidgetAktualisierer.aktualisiereAlle(this)
    }

    private fun verarbeite(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TIMER, -1L) ?: -1L
        if (id >= 0) angefragterTimer.value = id
        // Klingelt gerade ein Timer, die App auch über dem Sperrbildschirm zeigen.
        if (KlingelDienst.klingelt.value != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    companion object {
        const val EXTRA_TIMER = "timer"
    }
}
