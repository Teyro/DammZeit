package de.oejendorferdamm.dammzeit.laerm

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Misst die Lautstärke im Raum über das Mikrofon. Es wird NICHTS aufgenommen oder gespeichert:
 * aus jedem kurzen Stück (ca. 1/15 s) wird nur der Pegel berechnet, die Tondaten werden sofort
 * verworfen. Ergebnis: 0 (still) … 100 (sehr laut), geglättet – schnell lauter, langsam leiser,
 * damit die Ampel nicht flackert.
 */
object Pegelmesser {
    private const val RATE = 16_000

    /** 0–100, etwa 15 Werte pro Sekunde, solange gesammelt wird. Braucht RECORD_AUDIO. */
    @SuppressLint("MissingPermission")
    fun pegel(): Flow<Float> = flow {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return@flow
        val stueck = RATE / 15
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, stueck * 4))
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@flow
        }
        val puffer = ShortArray(stueck)
        var glatt = 0f
        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = rec.read(puffer, 0, puffer.size)
                if (n <= 0) continue
                var summe = 0.0
                for (i in 0 until n) { val v = puffer[i].toDouble(); summe += v * v }
                val rms = sqrt(summe / n)
                // dBFS: -90 (Stille) … 0 (Vollausschlag) → grob 0–100 (Gespräch ≈ 40–60, Toben ≈ 75+)
                val db = if (rms < 1.0) -90.0 else 20.0 * log10(rms / 32768.0)
                val wert = ((db + 80.0) / 70.0 * 100.0).toFloat().coerceIn(0f, 100f)
                glatt = if (wert > glatt) glatt + (wert - glatt) * 0.45f else glatt + (wert - glatt) * 0.08f
                emit(glatt)
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
    }.flowOn(Dispatchers.IO)
}
