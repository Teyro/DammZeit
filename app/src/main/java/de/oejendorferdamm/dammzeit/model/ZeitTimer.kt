package de.oejendorferdamm.dammzeit.model

/** Farbe der ablaufenden Zeitscheibe. */
enum class FarbModus(val bezeichnung: String) {
    PETROL("Blau-Türkis (wie Time Timer MOD)"),
    ROT("Klassisch rot"),
    VERLAUF("Rot, am Ende dunkler"),
    AMPEL("Ampel: grün, gelb, rot"),
    EIGENE("Eigene Farbe")
}

/** In welche Richtung die Zahlen laufen – und damit, auf welcher Seite die Zeitscheibe liegt. */
enum class Laufrichtung(val bezeichnung: String) {
    IM_UHRZEIGERSINN("Zahlen im Uhrzeigersinn, Scheibe rechts"),
    GEGEN_UHRZEIGERSINN("Zahlen gegen den Uhrzeigersinn, Scheibe links")
}

/** Aussehen von Hintergrund und Zifferblatt. */
enum class Hintergrund(val bezeichnung: String) {
    HELL("Hell"),
    GRAU("Grau"),
    DUNKEL("Dunkel"),
    SCHWARZ("Schwarz")
}

/** Signalton am Ende. Die Töne werden in der App selbst erzeugt – keine fremden Tondateien. */
enum class Ton(val bezeichnung: String) {
    GONG("Gong"),
    GLOCKE("Glocke"),
    KLANGSCHALE("Klangschale"),
    PIEPEN("Piepen"),
    WECKER("Wecker"),
    SYSTEM("Wecker-Ton des Geräts"),
    STILL("Kein Ton")
}

/** Wählbare Zifferblätter in Minuten (eine volle Umdrehung). */
val SKALEN = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

/** Auswahl für eigene Farben der Zeitscheibe. */
val EIGENE_FARBEN = listOf(
    0xFF02546B, 0xFFE01E3C, 0xFF7A0014, 0xFFF06A1D, 0xFFF2B705, 0xFF2EA043, 0xFF0B7A5B,
    0xFF1E6FD9, 0xFF123E8C, 0xFF7B3FC4, 0xFFD6338A, 0xFF5B5F66, 0xFF1E1E1E
).map { it.toInt() }

/**
 * Der Timer. Läuft er, steht in [endeUm] der Zeitpunkt (Wanduhr, ms), an dem er abläuft – die
 * Restzeit wird daraus jedes Mal neu berechnet. Dadurch läuft die Uhr in Echtzeit weiter, auch
 * wenn die App geschlossen ist, und bleibt über Neustarts hinweg genau.
 */
data class ZeitTimer(
    val id: Long = TIMER_ID,
    val dauerMs: Long = 15 * 60_000L,
    val endeUm: Long = 0L,
    val restBeiPauseMs: Long = -1L
) {
    val laeuft: Boolean get() = endeUm > 0L
    val pausiert: Boolean get() = endeUm == 0L && restBeiPauseMs >= 0L

    fun restMs(jetzt: Long): Long = when {
        laeuft -> (endeUm - jetzt).coerceAtLeast(0L)
        pausiert -> restBeiPauseMs
        else -> dauerMs
    }

    /** Gestartet und die Zeit ist um (bis zum Zurücksetzen bleibt die Scheibe leer). */
    fun abgelaufen(jetzt: Long): Boolean = laeuft && endeUm <= jetzt

    companion object {
        /** Es gibt genau einen Timer – die Uhr an der Tafel. */
        const val TIMER_ID = 1L
    }
}

/** "14:00" oder "1:05:00". */
fun formatiereDauer(ms: Long): String {
    val sekundenGesamt = (ms + 999) / 1000
    val stunden = sekundenGesamt / 3600
    val minuten = (sekundenGesamt % 3600) / 60
    val sekunden = sekundenGesamt % 60
    return if (stunden > 0) "%d:%02d:%02d".format(stunden, minuten, sekunden) else "%d:%02d".format(minuten, sekunden)
}

/** Überschrift wie beim Time Timer: "14 MINUTEN-TIMER". */
fun titelFuer(dauerMs: Long): String {
    val sekunden = (dauerMs + 999) / 1000
    val minuten = sekunden / 60
    val rest = sekunden % 60
    return when {
        rest == 0L && minuten == 1L -> "1 MINUTEN-TIMER"
        rest == 0L -> "$minuten MINUTEN-TIMER"
        minuten == 0L -> "$rest SEKUNDEN-TIMER"
        else -> "$minuten:%02d MINUTEN-TIMER".format(rest)
    }
}
