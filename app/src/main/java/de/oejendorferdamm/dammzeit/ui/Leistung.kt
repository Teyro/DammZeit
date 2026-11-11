package de.oejendorferdamm.dammzeit.ui

import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Leistung je Board (wie im DammLauncher): ab Android 10 volle Darstellung, darunter sparsam –
 * keine Animationen und Schatten, kein Verlauf, kein rieselnder Sand, die Uhr zeichnet nur
 * viermal pro Sekunde statt in jedem Bild neu. In den Einstellungen (Leistung) festlegbar.
 */
object Leistung {
    const val AUTO = "auto"
    const val VOLL = "voll"
    const val SPAR = "spar"

    val automatischSparsam: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    fun sparsam(wahl: String): Boolean = when (wahl) {
        VOLL -> false
        SPAR -> true
        else -> automatischSparsam
    }
}

/** true = Sparmodus; an der Wurzel aus der Einstellung gesetzt, sonst die Automatik */
val LokalSparsam = staticCompositionLocalOf { Leistung.automatischSparsam }
