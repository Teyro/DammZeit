package de.oejendorferdamm.dammzeit.data

import android.content.Context
import android.content.SharedPreferences
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Hintergrund
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Timer und Einstellungen, gespeichert in SharedPreferences. Bewusst synchron und schlicht:
 * Weckempfänger und Widgets müssen jederzeit direkt lesen können, auch wenn die App gerade
 * nicht läuft. Die Oberfläche beobachtet [timer] und [einstellungen].
 */
object Speicher {
    private lateinit var prefs: SharedPreferences

    private val _timer = MutableStateFlow(ZeitTimer())
    val timer: StateFlow<ZeitTimer> = _timer

    private val _einstellungen = MutableStateFlow(Einstellungen())
    val einstellungen: StateFlow<Einstellungen> = _einstellungen

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("dammzeit", Context.MODE_PRIVATE)
        _timer.value = ZeitTimer(
            dauerMs = prefs.getLong("dauer", 15 * 60_000L).coerceAtLeast(1000L),
            endeUm = prefs.getLong("ende", 0L),
            restBeiPauseMs = prefs.getLong("pause", -1L)
        )
        _einstellungen.value = Einstellungen(
            farbe = FarbModus.entries.find { it.name == prefs.getString("farbe", null) } ?: FarbModus.ROT,
            eigeneFarbe = prefs.getInt("eigene_farbe", 0xFF1E6FD9.toInt()),
            hintergrund = Hintergrund.entries.find { it.name == prefs.getString("hintergrund", null) } ?: Hintergrund.HELL,
            skalaMinuten = prefs.getInt("skala", 60),
            ton = Ton.entries.find { it.name == prefs.getString("ton", null) } ?: Ton.GONG,
            klingelSekunden = prefs.getInt("klingel_sekunden", 30),
            lautstaerke = prefs.getFloat("lautstaerke", 1f),
            zahlenAnzeigen = prefs.getBoolean("zahlen", true),
            restzeitAnzeigen = prefs.getBoolean("restzeit", false),
            titelAnzeigen = prefs.getBoolean("titel", true),
            sofortStarten = prefs.getBoolean("sofort_starten", true),
            bildschirmAn = prefs.getBoolean("bildschirm_an", true),
            autoUpdate = prefs.getBoolean("auto_update", true)
        )
    }

    fun finde(id: Long): ZeitTimer? = _timer.value.takeIf { it.id == id }

    @Synchronized
    fun speichere(timer: ZeitTimer) {
        // commit statt apply: Weckempfänger laufen in eigenen, kurzlebigen Prozessen.
        prefs.edit()
            .putLong("dauer", timer.dauerMs)
            .putLong("ende", timer.endeUm)
            .putLong("pause", timer.restBeiPauseMs)
            .commit()
        _timer.value = timer
    }

    @Synchronized
    fun aendereEinstellungen(aenderung: (Einstellungen) -> Einstellungen) {
        val neu = aenderung(_einstellungen.value)
        prefs.edit()
            .putString("farbe", neu.farbe.name)
            .putInt("eigene_farbe", neu.eigeneFarbe)
            .putString("hintergrund", neu.hintergrund.name)
            .putInt("skala", neu.skalaMinuten)
            .putString("ton", neu.ton.name)
            .putInt("klingel_sekunden", neu.klingelSekunden)
            .putFloat("lautstaerke", neu.lautstaerke)
            .putBoolean("zahlen", neu.zahlenAnzeigen)
            .putBoolean("restzeit", neu.restzeitAnzeigen)
            .putBoolean("titel", neu.titelAnzeigen)
            .putBoolean("sofort_starten", neu.sofortStarten)
            .putBoolean("bildschirm_an", neu.bildschirmAn)
            .putBoolean("auto_update", neu.autoUpdate)
            .apply()
        _einstellungen.value = neu
    }
}

/** Alles, was sich im Zahnrad-Menü einstellen lässt. */
data class Einstellungen(
    val farbe: FarbModus = FarbModus.ROT,
    val eigeneFarbe: Int = 0xFF1E6FD9.toInt(),
    val hintergrund: Hintergrund = Hintergrund.HELL,
    val skalaMinuten: Int = 60,
    val ton: Ton = Ton.GONG,
    /** 0 = klingelt, bis man auf die Uhr tippt. */
    val klingelSekunden: Int = 30,
    val lautstaerke: Float = 1f,
    val zahlenAnzeigen: Boolean = true,
    val restzeitAnzeigen: Boolean = false,
    val titelAnzeigen: Boolean = true,
    val sofortStarten: Boolean = true,
    val bildschirmAn: Boolean = true,
    val autoUpdate: Boolean = true
)
