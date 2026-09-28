package de.oejendorferdamm.dammzeit.ton

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import de.oejendorferdamm.dammzeit.model.Ton
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Spielt die Signaltöne. Alle Töne (außer dem Gerätewecker) werden hier rechnerisch erzeugt –
 * dadurch braucht die App keine fremden Tondateien mit unklarer Lizenz.
 * Wiedergabe über den Wecker-Kanal, damit der Ton auch bei stumm geschalteten Medien klingt.
 */
class TonSpieler(private val context: Context) {
    private var spur: AudioTrack? = null
    private var geraeteTon: MediaPlayer? = null

    private val attribute = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Startet den Ton; [wiederholen] = in Schleife bis [stopp]. */
    @Synchronized
    fun spiele(ton: Ton, wiederholen: Boolean, lautstaerke: Float = 1f) {
        stopp()
        this.lautstaerke = lautstaerke.coerceIn(0.05f, 1f)
        when (ton) {
            Ton.STILL -> Unit
            Ton.SYSTEM -> spieleGeraeteTon(wiederholen)
            else -> spieleErzeugt(ton, wiederholen)
        }
    }

    private var lautstaerke = 1f

    @Synchronized
    fun stopp() {
        spur?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        spur = null
        geraeteTon?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        geraeteTon = null
    }

    private fun spieleGeraeteTon(wiederholen: Boolean) {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return spieleErzeugt(Ton.GLOCKE, wiederholen)
        try {
            geraeteTon = MediaPlayer().apply {
                setAudioAttributes(attribute)
                setDataSource(context, uri)
                isLooping = wiederholen
                setVolume(lautstaerke, lautstaerke)
                prepare()
                start()
            }
        } catch (e: Exception) {
            geraeteTon = null
            spieleErzeugt(Ton.GLOCKE, wiederholen)
        }
    }

    private fun spieleErzeugt(ton: Ton, wiederholen: Boolean) {
        val daten = erzeuge(ton)
        val track = AudioTrack.Builder()
            .setAudioAttributes(attribute)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(daten.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(daten, 0, daten.size)
        if (wiederholen) track.setLoopPoints(0, daten.size, -1)
        track.setVolume(lautstaerke)
        track.play()
        spur = track
    }

    companion object {
        private const val RATE = 44_100

        /** Ein Durchgang des Tons (inkl. Pause danach), als 16-Bit-PCM. */
        fun erzeuge(ton: Ton): ShortArray = when (ton) {
            Ton.GONG -> klang(3.2f, listOf(1f to 196f, 0.6f to 294f, 0.45f to 392f, 0.3f to 523f, 0.18f to 784f), abklingen = 1.1f)
            Ton.GLOCKE -> klang(2.2f, listOf(1f to 880f, 0.55f to 1760f, 0.35f to 2112f, 0.25f to 2640f, 0.12f to 3700f), abklingen = 2.4f)
            Ton.KLANGSCHALE -> klang(4.5f, listOf(1f to 432f, 0.8f to 433.8f, 0.35f to 1180f, 0.2f to 2210f), abklingen = 0.7f)
            Ton.PIEPEN -> piepen()
            Ton.WECKER -> wecker()
            Ton.SYSTEM, Ton.STILL -> ShortArray(RATE / 10)
        }

        /** Angeschlagener Klang: mehrere Teiltöne, weicher Einsatz, exponentielles Ausklingen. */
        private fun klang(sekunden: Float, teiltoene: List<Pair<Float, Float>>, abklingen: Float): ShortArray {
            val n = (sekunden * RATE).toInt()
            val summe = teiltoene.sumOf { it.first.toDouble() }.toFloat()
            return ShortArray(n) { i ->
                val t = i.toFloat() / RATE
                val einsatz = (t / 0.008f).coerceAtMost(1f)
                var wert = 0f
                teiltoene.forEachIndexed { index, (staerke, frequenz) ->
                    // Hohe Teiltöne klingen schneller ab, wie bei echten Glocken.
                    val abfall = exp(-abklingen * (1f + index * 0.6f) * t)
                    wert += staerke * abfall * sin(2 * PI * frequenz * t).toFloat()
                }
                (wert / summe * einsatz * 0.85f * Short.MAX_VALUE).toInt().toShort()
            }
        }

        private fun piepen(): ShortArray {
            val gesamt = (1.4f * RATE).toInt()
            return ShortArray(gesamt) { i ->
                val t = i.toFloat() / RATE
                // Drei kurze Pieptöne, dann Pause.
                val phase = t % 0.25f
                val an = t < 0.75f && phase < 0.14f
                if (!an) 0 else {
                    val rampe = minOf(phase / 0.005f, (0.14f - phase) / 0.005f, 1f)
                    (sin(2 * PI * 1046.5 * t).toFloat() * rampe * 0.7f * Short.MAX_VALUE).toInt().toShort()
                }
            }
        }

        private fun wecker(): ShortArray {
            val gesamt = (1.2f * RATE).toInt()
            return ShortArray(gesamt) { i ->
                val t = i.toFloat() / RATE
                val phase = t % 0.12f
                val an = t < 0.72f && phase < 0.08f
                if (!an) 0 else {
                    val frequenz = if ((t / 0.12f).toInt() % 2 == 0) 1318.5 else 1568.0
                    val rampe = minOf(phase / 0.004f, (0.08f - phase) / 0.004f, 1f)
                    // Leicht rechteckig für den typischen Weckerklang.
                    val s = sin(2 * PI * frequenz * t).toFloat()
                    val klang = s + 0.3f * sin(6 * PI * frequenz * t).toFloat()
                    (klang / 1.3f * rampe * 0.7f * Short.MAX_VALUE).toInt().toShort()
                }
            }
        }
    }
}
