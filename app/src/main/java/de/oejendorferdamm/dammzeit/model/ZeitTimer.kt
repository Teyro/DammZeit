package de.oejendorferdamm.dammzeit.model

/** Farbe der ablaufenden Zeitscheibe. */
enum class FarbModus(val bezeichnung: String) {
    ROT("Klassisch rot"),
    VERLAUF("Rot, am Ende dunkler"),
    AMPEL("Ampel: grün, gelb, rot"),
    BLAU("Blau"),
    GRUEN("Grün"),
    LILA("Lila")
}

/** Signalton am Ende. Die Töne werden in der App selbst erzeugt – keine fremden Tondateien. */
enum class Ton(val bezeichnung: String) {
    GONG("Gong"),
    GLOCKE("Glocke"),
    KLANGSCHALE("Klangschale"),
    PIEPEN("Piepen"),
    WECKER("Wecker"),
    SYSTEM("Wecker-Ton des Geräts"),
    STILL("Kein Ton (nur Hinweis)")
}

/** Mögliche Einteilungen des Zifferblatts in Minuten; 0 = passend zur Dauer. */
val SKALEN = listOf(0, 5, 10, 15, 20, 30, 45, 60, 120)

/** Kleinste Skala, auf die die Dauer passt – wie beim Time Timer meist 60 Minuten. */
fun automatischeSkala(dauerMs: Long): Int {
    val minuten = dauerMs / 60_000.0
    return listOf(5, 10, 15, 20, 30, 45, 60, 120).firstOrNull { it >= minuten - 0.0001 } ?: 120
}

/**
 * Ein Timer. Läuft er, steht in [endeUm] der Zeitpunkt (Wanduhr, ms), an dem er abläuft – die
 * Restzeit wird daraus jedes Mal neu berechnet. Dadurch läuft die Uhr in Echtzeit weiter, auch
 * wenn die App geschlossen ist, und bleibt über Neustarts hinweg genau.
 */
data class ZeitTimer(
    val id: Long,
    val name: String,
    val dauerMs: Long,
    val skalaMinuten: Int = 0,
    val farbe: FarbModus = FarbModus.ROT,
    val ton: Ton = Ton.GONG,
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

    fun wirksameSkala(): Int = if (skalaMinuten > 0) skalaMinuten else automatischeSkala(dauerMs)
}

/** "14:00" oder "1:05:00". */
fun formatiereDauer(ms: Long): String {
    val sekundenGesamt = (ms + 999) / 1000
    val stunden = sekundenGesamt / 3600
    val minuten = (sekundenGesamt % 3600) / 60
    val sekunden = sekundenGesamt % 60
    return if (stunden > 0) "%d:%02d:%02d".format(stunden, minuten, sekunden) else "%d:%02d".format(minuten, sekunden)
}

/** Vorschlag für den Namen: "14 Minuten-Timer", "1 Minute 30 Sekunden". */
fun standardName(dauerMs: Long): String {
    val sekunden = dauerMs / 1000
    val minuten = sekunden / 60
    val rest = sekunden % 60
    return when {
        rest == 0L && minuten == 1L -> "1 Minute"
        rest == 0L -> "$minuten Minuten-Timer"
        minuten == 0L -> "$rest Sekunden"
        else -> "$minuten Min. $rest Sek."
    }
}
