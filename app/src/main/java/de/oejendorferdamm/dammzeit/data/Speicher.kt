package de.oejendorferdamm.dammzeit.data

import android.content.Context
import android.content.SharedPreferences
import de.oejendorferdamm.dammzeit.model.FarbModus
import de.oejendorferdamm.dammzeit.model.Laufrichtung
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
        // 2.2.1: neue Standards (Blau-Türkis, Zahlen im Uhrzeigersinn) – einmalig auch für Geräte,
        // die noch die alte Standardfarbe Rot hatten.
        if (!prefs.getBoolean("standard_2_2_1", false)) {
            val e = prefs.edit().putBoolean("standard_2_2_1", true)
            if (prefs.getString("farbe", null) == FarbModus.ROT.name) e.remove("farbe")
            e.commit()
        }
        _einstellungen.value = Einstellungen(
            farbe = FarbModus.entries.find { it.name == prefs.getString("farbe", null) } ?: FarbModus.PETROL,
            laufrichtung = Laufrichtung.entries.find { it.name == prefs.getString("laufrichtung", null) } ?: Laufrichtung.IM_UHRZEIGERSINN,
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
            autoUpdate = prefs.getBoolean("auto_update", true),
            pausenZeiten = (prefs.getString("pausen", null) ?: STANDARD_PAUSEN).split(',').map { it.trim() }.filter { ZEIT.matches(it) }.sorted(),
            laermGelb = prefs.getInt("laerm_gelb", 55),
            laermRot = prefs.getInt("laerm_rot", 70),
            laermTon = prefs.getBoolean("laerm_ton", false)
        )
        _favoriten.value = ladeFavoriten(prefs.getString("favoriten", "") ?: "")
    }

    // ------------------------------------------------------------ Favoriten

    private val _favoriten = MutableStateFlow<List<Favorit>>(emptyList())
    val favoriten: StateFlow<List<Favorit>> = _favoriten

    @Synchronized
    fun speichereFavoriten(liste: List<Favorit>) {
        val a = org.json.JSONArray()
        liste.forEach { f ->
            a.put(org.json.JSONObject().put("name", f.name).put("dauer", f.dauerMs).put("farbe", f.farbe.name)
                .put("eigene", f.eigeneFarbe).put("sanduhr", f.sanduhr))
        }
        prefs.edit().putString("favoriten", a.toString()).apply()
        _favoriten.value = liste
    }

    private fun ladeFavoriten(text: String): List<Favorit> = try {
        val a = org.json.JSONArray(text)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Favorit(
                o.optString("name").take(40).ifBlank { return@mapNotNull null },
                o.optLong("dauer", 0L).takeIf { it in 1000L..12 * 3_600_000L } ?: return@mapNotNull null,
                FarbModus.entries.find { it.name == o.optString("farbe") } ?: FarbModus.PETROL,
                o.optInt("eigene", 0xFF1E6FD9.toInt()),
                o.optBoolean("sanduhr", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private const val STANDARD_PAUSEN = "09:30,11:20,13:00"
    val ZEIT = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

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
            .putString("laufrichtung", neu.laufrichtung.name)
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
            .putString("pausen", neu.pausenZeiten.joinToString(","))
            .putInt("laerm_gelb", neu.laermGelb)
            .putInt("laerm_rot", neu.laermRot)
            .putBoolean("laerm_ton", neu.laermTon)
            .apply()
        _einstellungen.value = neu
    }
}

/** Alles, was sich im Zahnrad-Menü einstellen lässt. */
data class Einstellungen(
    val farbe: FarbModus = FarbModus.PETROL,
    val laufrichtung: Laufrichtung = Laufrichtung.IM_UHRZEIGERSINN,
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
    val autoUpdate: Boolean = true,
    /** Stundenende/Pausen („HH:mm“) für „Bis zur Pause“. */
    val pausenZeiten: List<String> = emptyList(),
    /** Lärmampel: ab diesem Pegel (0–100) gelb bzw. rot; Ton bei Rot. */
    val laermGelb: Int = 55,
    val laermRot: Int = 70,
    val laermTon: Boolean = false
)

/** Gespeicherte Vorlage: Name, Zeit, Farbe und ob als Sanduhr. */
data class Favorit(
    val name: String,
    val dauerMs: Long,
    val farbe: FarbModus,
    val eigeneFarbe: Int,
    val sanduhr: Boolean
)
