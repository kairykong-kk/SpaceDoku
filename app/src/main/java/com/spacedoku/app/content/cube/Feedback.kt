package com.spacedoku.app.content.cube

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.VibratorManager
import com.spacedoku.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Audio + haptic feedback:
 *  - place(): low-latency click (hardware_confirm.wav via SoundPool)
 *  - ambient: calm loop (ambient_bg.wav), soft while solving
 *  - win(): swells the ambient AND fades in a slow, rising, ethereal pad melody (looping) for the
 *    meditative completion; resetWin() returns to the solving state.
 */
class Feedback(context: Context) {

    private val sampleRate = 44100 // synth (vibraphone arpeggio) rate

    // Completion progression in G (root-morphing chords): G, GM7, G7, G6, Gm6, Gm7, Gdim7, Gaug.
    // Played as an ARPEGGIO over the ambient: one note at a time, 2 beats each at 60 bpm.
    private val chords = arrayOf(
        doubleArrayOf(196.00, 246.94, 293.66),         // G    (G B D)
        doubleArrayOf(196.00, 246.94, 293.66, 369.99), // GM7  (+F#)
        doubleArrayOf(196.00, 246.94, 293.66, 349.23), // G7   (+F)
        doubleArrayOf(196.00, 246.94, 293.66, 329.63), // G6   (+E)
        doubleArrayOf(196.00, 233.08, 293.66, 329.63), // Gm6  (Bb,E)
        doubleArrayOf(196.00, 233.08, 293.66, 349.23), // Gm7  (Bb,F)
        doubleArrayOf(196.00, 233.08, 277.18, 329.63), // Gdim7(Bb,Db,E)
        doubleArrayOf(196.00, 246.94, 311.13),         // Gaug (G B D#)
    )
    private val noteDurSec = 0.25 // 4 notes per beat at 60 bpm (gentle flowing 16ths)
    private val arpNotes = chords.flatMap { it.asList() }.toDoubleArray()
    private val melodyFrames = (sampleRate * noteDurSec * arpNotes.size).toInt()

    private val vibrator = runCatching {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }.getOrNull()

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val clickId = runCatching { pool.load(context, R.raw.hardware_confirm, 1) }.getOrDefault(0)

    private val ambient = runCatching {
        MediaPlayer.create(context, R.raw.ambient_bg)?.apply {
            isLooping = true
            setVolume(FAINT, FAINT)
        }
    }.getOrNull()

    private val melodyTrack = makeTrack(buildArpeggio())?.also {
        runCatching { it.setLoopPoints(0, melodyFrames, -1) } // loop the arpeggio
    }

    private var swelling = false
    private var swellStartMs = 0L

    fun startAmbient() {
        android.util.Log.i("SpaceDoku", "startAmbient: ambient=${ambient != null}, click=$clickId")
        runCatching { ambient?.start() }
    }

    fun place() {
        runCatching { if (clickId != 0) pool.play(clickId, 1f, 1f, 1, 0, 1f) }
        vibrate(12)
    }

    /** Completion: swell ambient faint -> full and start the looping rising melody. */
    fun win() {
        swelling = true
        swellStartMs = System.currentTimeMillis()
        runCatching {
            melodyTrack?.let {
                it.stop(); it.reloadStaticData()
                it.setLoopPoints(0, melodyFrames, -1) // re-arm the loop (reloadStaticData clears it)
                it.play()
            }
        }
        vibrate(80)
    }

    /** Back to solving state (new game): stop the melody, restore the faint ambient. */
    fun resetWin() {
        swelling = false
        runCatching { melodyTrack?.stop() }
        runCatching { ambient?.setVolume(FAINT, FAINT) }
    }

    fun updateAmbient(nowMs: Long) {
        if (!swelling) return
        val t = ((nowMs - swellStartMs).toFloat() / SWELL_MS).coerceIn(0f, 1f)
        val v = FAINT + (FULL - FAINT) * t
        runCatching { ambient?.setVolume(v, v) }
        if (t >= 1f) swelling = false
    }

    fun release() {
        runCatching { ambient?.stop(); ambient?.release() }
        runCatching { melodyTrack?.stop(); melodyTrack?.release() }
        runCatching { pool.release() }
    }

    private fun vibrate(ms: Long) {
        runCatching { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun makeTrack(pcm: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }.getOrNull()

    /**
     * Looping vibraphone arpeggio: each chord tone struck individually every [noteDurSec]; each note
     * rings ~1 s (notes overlap into a soft shimmer) with a gentle tremolo (the vibraphone sound).
     */
    private fun buildArpeggio(): ShortArray {
        val total = melodyFrames
        val buf = DoubleArray(total)
        val noteSamp = (noteDurSec * sampleRate).toInt()
        val ring = (1.0 * sampleRate).toInt() // each note rings ~1 s
        val attack = (0.006 * sampleRate).toInt()
        val tremHz = 5.5
        val tremDepth = 0.3
        for (k in arpNotes.indices) {
            val f = arpNotes[k]
            val start = k * noteSamp
            for (j in 0 until ring) {
                val idx = (start + j) % total // wrap -> seamless loop (ring tails into the start)
                val t = j.toDouble() / sampleRate
                var env = exp(-t * 4.5)
                if (j < attack) env *= j.toDouble() / attack
                val trem = 1.0 - tremDepth * 0.5 * (1.0 - cos(2 * PI * tremHz * t))
                val tone = sin(2 * PI * f * t) +
                    sin(2 * PI * f * 4.0 * t) * 0.5 + // vibraphone 2-octave partial
                    sin(2 * PI * f * 9.2 * t) * 0.12  // faint high partial
                buf[idx] += tone * env * trem * 0.6
            }
        }
        var peak = 0.0
        for (v in buf) peak = max(peak, abs(v))
        val norm = if (peak > 0) 0.5 / peak else 1.0
        return ShortArray(total) { i ->
            (buf[i] * norm).coerceIn(-1.0, 1.0).times(Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
    }

    private companion object {
        const val FAINT = 0.4253f // solving + menu (another 10% below)
        const val FULL = 0.5468f  // completion swell (another 10% below)
        const val SWELL_MS = 2500f
    }
}
