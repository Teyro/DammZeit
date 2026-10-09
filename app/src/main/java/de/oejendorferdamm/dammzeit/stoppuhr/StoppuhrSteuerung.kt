package de.oejendorferdamm.dammzeit.stoppuhr

import android.content.Context

/** Bedienung der Stoppuhr – gemeinsam für App und Widget. */
object StoppuhrSteuerung {
    fun startStopp(context: Context) {
        StoppuhrSpeicher.init(context)
        val u = StoppuhrSpeicher.uhr.value
        val jetzt = System.currentTimeMillis()
        StoppuhrSpeicher.setze(
            if (u.laeuft) u.copy(laeuft = false, bisherMs = u.zeitMs(jetzt)) else u.copy(laeuft = true, startUm = jetzt)
        )
        StoppuhrWidget.aktualisiereAlle(context)
    }

    fun runde(context: Context) {
        StoppuhrSpeicher.init(context)
        val u = StoppuhrSpeicher.uhr.value
        if (!u.laeuft) return
        StoppuhrSpeicher.setze(u.copy(runden = (u.runden + u.zeitMs(System.currentTimeMillis())).takeLast(99)))
        StoppuhrWidget.aktualisiereAlle(context)
    }

    fun zuruecksetzen(context: Context) {
        StoppuhrSpeicher.init(context)
        StoppuhrSpeicher.setze(Stoppuhr())
        StoppuhrWidget.aktualisiereAlle(context)
    }

    /** Hält an (falls nötig) und trägt die Zeit in die Bestenliste ein. Rückgabe: Platz. */
    fun speichern(context: Context, name: String, challenge: String): Pair<Bestzeit, Int>? {
        StoppuhrSpeicher.init(context)
        val u = StoppuhrSpeicher.uhr.value
        val zeit = u.zeitMs(System.currentTimeMillis())
        if (zeit <= 0L) return null
        if (u.laeuft) StoppuhrSpeicher.setze(u.copy(laeuft = false, bisherMs = zeit))
        val ergebnis = StoppuhrSpeicher.eintragen(name, challenge, zeit, u.runden)
        StoppuhrWidget.aktualisiereAlle(context)
        return ergebnis
    }
}
