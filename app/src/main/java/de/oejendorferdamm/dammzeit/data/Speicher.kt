package de.oejendorferdamm.dammzeit.data

import android.content.Context
import android.content.SharedPreferences
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Ton
import de.oejendorferdamm.dammzeit.model.ZeitTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Timer und Einstellungen, gespeichert in SharedPreferences. Bewusst synchron und schlicht:
 * Weckempfänger und Widgets müssen jederzeit direkt lesen können, auch wenn die App gerade
 * nicht läuft. Die Oberfläche beobachtet [timer] und [einstellungen].
 */
object Speicher {
    private lateinit var prefs: SharedPreferences

    private val _timer = MutableStateFlow<List<ZeitTimer>>(emptyList())
    val timer: StateFlow<List<ZeitTimer>> = _timer

    private val _einstellungen = MutableStateFlow(Einstellungen())
    val einstellungen: StateFlow<Einstellungen> = _einstellungen

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("dammzeit", Context.MODE_PRIVATE)
        val geladen = ladeTimer()
        _timer.value = if (geladen == null) standardTimer().also { schreibeTimer(it) } else geladen
        _einstellungen.value = ladeEinstellungen()
    }

    fun finde(id: Long): ZeitTimer? = _timer.value.firstOrNull { it.id == id }

    @Synchronized
    fun speichere(timer: ZeitTimer) {
        val liste = _timer.value.toMutableList()
        val index = liste.indexOfFirst { it.id == timer.id }
        if (index >= 0) liste[index] = timer else liste.add(timer)
        schreibeTimer(liste)
        _timer.value = liste
    }

    @Synchronized
    fun loesche(id: Long) {
        val liste = _timer.value.filterNot { it.id == id }
        schreibeTimer(liste)
        _timer.value = liste
    }

    private var letzteId = 0L

    @Synchronized
    fun neueId(): Long {
        val kandidat = maxOf(System.currentTimeMillis(), letzteId + 1, (_timer.value.maxOfOrNull { it.id } ?: 0L) + 1)
        letzteId = kandidat
        return kandidat
    }

    /** Der Timer, den die Widgets anzeigen: zuletzt gestartet oder geöffnet. */
    var widgetTimerId: Long
        get() = prefs.getLong("widget_timer", -1L)
        set(wert) {
            prefs.edit().putLong("widget_timer", wert).apply()
        }

    fun widgetTimer(): ZeitTimer? = finde(widgetTimerId) ?: _timer.value.firstOrNull { it.laeuft } ?: _timer.value.firstOrNull()

    @Synchronized
    fun aendereEinstellungen(aenderung: (Einstellungen) -> Einstellungen) {
        val neu = aenderung(_einstellungen.value)
        prefs.edit()
            .putInt("klingel_sekunden", neu.klingelSekunden)
            .putString("standard_farbe", neu.standardFarbe.name)
            .putBoolean("bildschirm_an", neu.bildschirmAn)
            .putBoolean("zahl_anzeigen", neu.zahlAnzeigen)
            .putBoolean("auto_update", neu.autoUpdate)
            .putBoolean("vibration", neu.vibration)
            .apply()
        _einstellungen.value = neu
    }

    private fun ladeEinstellungen() = Einstellungen(
        klingelSekunden = prefs.getInt("klingel_sekunden", 30),
        standardFarbe = FarbModus.entries.find { it.name == prefs.getString("standard_farbe", null) } ?: FarbModus.ROT,
        bildschirmAn = prefs.getBoolean("bildschirm_an", true),
        zahlAnzeigen = prefs.getBoolean("zahl_anzeigen", true),
        autoUpdate = prefs.getBoolean("auto_update", true),
        vibration = prefs.getBoolean("vibration", true)
    )

    private fun standardTimer(): List<ZeitTimer> {
        val basis = System.currentTimeMillis()
        return listOf(
            ZeitTimer(basis, "5 Minuten-Timer", 5 * 60_000L, 0, FarbModus.ROT, Ton.GONG),
            ZeitTimer(basis + 1, "Stillarbeit", 20 * 60_000L, 0, FarbModus.VERLAUF, Ton.KLANGSCHALE),
            ZeitTimer(basis + 2, "Pause", 15 * 60_000L, 0, FarbModus.AMPEL, Ton.GLOCKE),
            ZeitTimer(basis + 3, "45 Minuten-Timer", 45 * 60_000L, 60, FarbModus.ROT, Ton.GONG)
        )
    }

    private fun ladeTimer(): List<ZeitTimer>? {
        val text = prefs.getString("timer", null) ?: return null
        return try {
            val feld = JSONArray(text)
            (0 until feld.length()).mapNotNull { i ->
                val o = feld.optJSONObject(i) ?: return@mapNotNull null
                ZeitTimer(
                    id = o.getLong("id"),
                    name = o.optString("name", "Timer"),
                    dauerMs = o.optLong("dauer", 5 * 60_000L).coerceAtLeast(1000L),
                    skalaMinuten = o.optInt("skala", 0),
                    farbe = FarbModus.entries.find { it.name == o.optString("farbe") } ?: FarbModus.ROT,
                    ton = Ton.entries.find { it.name == o.optString("ton") } ?: Ton.GONG,
                    endeUm = o.optLong("ende", 0L),
                    restBeiPauseMs = o.optLong("pause", -1L)
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun schreibeTimer(liste: List<ZeitTimer>) {
        val feld = JSONArray()
        liste.forEach { t ->
            feld.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("dauer", t.dauerMs)
                    .put("skala", t.skalaMinuten)
                    .put("farbe", t.farbe.name)
                    .put("ton", t.ton.name)
                    .put("ende", t.endeUm)
                    .put("pause", t.restBeiPauseMs)
            )
        }
        // commit statt apply: Weckempfänger laufen in eigenen, kurzlebigen Prozessen.
        prefs.edit().putString("timer", feld.toString()).commit()
    }
}

data class Einstellungen(
    val klingelSekunden: Int = 30,
    val standardFarbe: FarbModus = FarbModus.ROT,
    val bildschirmAn: Boolean = true,
    val zahlAnzeigen: Boolean = true,
    val autoUpdate: Boolean = true,
    val vibration: Boolean = true
)
