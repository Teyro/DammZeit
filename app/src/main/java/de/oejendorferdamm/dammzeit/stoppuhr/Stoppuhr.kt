package de.oejendorferdamm.dammzeit.stoppuhr

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stoppuhr: läuft hoch, Runden, Stopp. Die Zeit hängt an der Wanduhr ([startUm]), damit sie auch
 * weiterläuft, wenn App oder Widget neu gestartet werden.
 */
data class Stoppuhr(
    val laeuft: Boolean = false,
    /** Wanduhr-Zeitpunkt (ms) des letzten Starts. */
    val startUm: Long = 0L,
    /** Bis zum letzten Stopp gelaufene Zeit (ms). */
    val bisherMs: Long = 0L,
    /** Gesamtzeit beim Drücken von "Runde" (ms), in Reihenfolge. */
    val runden: List<Long> = emptyList()
) {
    fun zeitMs(jetzt: Long): Long = bisherMs + if (laeuft) (jetzt - startUm).coerceAtLeast(0L) else 0L
    val frisch get() = !laeuft && bisherMs == 0L
}

/** Ein Eintrag der Bestenliste: wer, welche Challenge, welche Zeit. */
data class Bestzeit(
    val id: String,
    val name: String,
    val challenge: String,
    val ms: Long,
    val datum: Long,
    val runden: List<Long> = emptyList()
)

/** Speicherung von Stoppuhr und Bestenliste (SharedPreferences, JSON). */
object StoppuhrSpeicher {
    private lateinit var prefs: SharedPreferences
    private val _uhr = MutableStateFlow(Stoppuhr())
    private val _liste = MutableStateFlow<List<Bestzeit>>(emptyList())
    val uhr: StateFlow<Stoppuhr> = _uhr
    val liste: StateFlow<List<Bestzeit>> = _liste

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("stoppuhr", Context.MODE_PRIVATE)
        try {
            prefs.getString("uhr", null)?.let { s ->
                val o = JSONObject(s)
                _uhr.value = Stoppuhr(o.optBoolean("laeuft"), o.optLong("startUm"), o.optLong("bisherMs"), langListe(o.optJSONArray("runden")))
            }
            prefs.getString("liste", null)?.let { s ->
                val a = JSONArray(s)
                _liste.value = (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    Bestzeit(o.optString("id"), o.optString("name"), o.optString("challenge"), o.optLong("ms"), o.optLong("datum"), langListe(o.optJSONArray("runden")))
                }.filter { it.ms > 0 }
            }
        } catch (_: Exception) {
            // Beschädigte Daten: mit leerer Stoppuhr weitermachen
        }
    }

    private fun langListe(a: JSONArray?): List<Long> = (0 until (a?.length() ?: 0)).map { a!!.optLong(it) }

    @Synchronized
    fun setze(neu: Stoppuhr) {
        _uhr.value = neu
        prefs.edit().putString("uhr", JSONObject().apply {
            put("laeuft", neu.laeuft); put("startUm", neu.startUm); put("bisherMs", neu.bisherMs); put("runden", JSONArray(neu.runden))
        }.toString()).commit()
    }

    @Synchronized
    private fun speichereListe(neu: List<Bestzeit>) {
        _liste.value = neu
        prefs.edit().putString("liste", JSONArray().apply {
            neu.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id); put("name", b.name); put("challenge", b.challenge); put("ms", b.ms); put("datum", b.datum); put("runden", JSONArray(b.runden))
                })
            }
        }.toString()).commit()
    }

    /** Neuen Eintrag speichern; Rückgabe: Platz in seiner Challenge (1 = schnellste Zeit). */
    @Synchronized
    fun eintragen(name: String, challenge: String, ms: Long, runden: List<Long>): Pair<Bestzeit, Int> {
        val b = Bestzeit(UUID.randomUUID().toString(), name.trim().take(30).ifEmpty { "Ohne Namen" }, challenge.trim().take(40), ms, System.currentTimeMillis(), runden)
        speichereListe(_liste.value + b)
        return b to rangliste(b.challenge).indexOfFirst { it.id == b.id }.plus(1)
    }

    @Synchronized
    fun loeschen(id: String) = speichereListe(_liste.value.filter { it.id != id })

    @Synchronized
    fun challengeLeeren(challenge: String) = speichereListe(_liste.value.filter { it.challenge != challenge })

    /** Schnellste zuerst; bei gleicher Zeit gewinnt, wer sie zuerst geschafft hat. */
    fun rangliste(challenge: String): List<Bestzeit> =
        _liste.value.filter { it.challenge == challenge }.sortedWith(compareBy<Bestzeit> { it.ms }.thenBy { it.datum })

    /** Alle Challenges, zuletzt benutzte zuerst. */
    fun challenges(): List<String> = _liste.value.sortedByDescending { it.datum }.map { it.challenge }.distinct()
}

/** "1:23,4" bzw. "12:03:45,6" – Zehntelsekunden reichen für Challenges und sind gut lesbar. */
fun formatStoppzeit(ms: Long, hundertstel: Boolean = false): String {
    val gesamt = ms.coerceAtLeast(0L)
    val h = gesamt / 3_600_000
    val m = gesamt / 60_000 % 60
    val s = gesamt / 1000 % 60
    val bruch = if (hundertstel) "%02d".format(gesamt / 10 % 100) else "${gesamt / 100 % 10}"
    return if (h > 0) "%d:%02d:%02d,%s".format(h, m, s, bruch) else "%d:%02d,%s".format(m, s, bruch)
}

/** Was die App gerade zeigt (auch von außen setzbar, z. B. vom Widget). */
object Ansicht {
    val stoppuhr = MutableStateFlow(false)
    /** Bestenliste offen: Challenge (oder "" für die zuletzt benutzte), null = zu. */
    val bestenliste = MutableStateFlow<String?>(null)
    /** Gerade gespeicherter Eintrag (wird hervorgehoben). */
    val neu = MutableStateFlow<String?>(null)
}
