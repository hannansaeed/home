package com.example.chatapp.ui.moth

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VoiceFilter(val id: String, val label: String) {
    NONE("none", "normal"),
    CHIPMUNK("chipmunk", "chipmunk"),
    DEEP("deep", "deep"),
    ROBOT("robot", "robot"),
    ECHO("echo", "echo"),
}

/**
 * Real voice note recorder supporting standard compressed AAC (.m4a) and
 * filtered PCM-16 (.wav) voice effects (chipmunk, deep, robot, echo).
 */
class VoiceNoteRecorder(
    private val context: Context,
    private val mediaDir: File,
    private val scope: CoroutineScope,
) {
    private var recorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var pcmRecordJob: Job? = null
    private var pcmOutputStream: ByteArrayOutputStream? = null
    private val pcmLock = Any()
    private var recordingFilter: VoiceFilter = VoiceFilter.NONE
    private var currentFile: File? = null
    private var segmentStartMs: Long = 0L
    private var accumulatedActiveMs: Long = 0L
    private var timerJob: Job? = null

    var selectedFilter by mutableStateOf(VoiceFilter.NONE)

    var isRecording by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var elapsedSec by mutableIntStateOf(0)
        private set

    private fun currentElapsedMs(): Long {
        return accumulatedActiveMs + if (!isPaused && segmentStartMs > 0L) {
            (System.currentTimeMillis() - segmentStartMs).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return false
        val activeFilter = selectedFilter
        if (activeFilter != VoiceFilter.NONE && startFilteredPcmRecording(activeFilter)) {
            return true
        }
        return startStandardMediaRecording()
    }

    @SuppressLint("MissingPermission")
    private fun startFilteredPcmRecording(filter: VoiceFilter): Boolean {
        return try {
            val sampleRate = PCM_SAMPLE_RATE
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) return false
            val bufBytes = maxOf(minBuf * 2, 4096)
            val ar = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufBytes,
            )
            if (ar.state != AudioRecord.STATE_INITIALIZED) {
                ar.release()
                return false
            }

            val outFile = File(mediaDir, "voice_${System.currentTimeMillis()}.wav")
            val stream = ByteArrayOutputStream(32 * 1024)
            ar.startRecording()

            audioRecord = ar
            pcmOutputStream = stream
            recordingFilter = filter
            currentFile = outFile
            accumulatedActiveMs = 0L
            segmentStartMs = System.currentTimeMillis()
            elapsedSec = 0
            isPaused = false
            isRecording = true

            val maxRawBytes = sampleRate * 2 * MAX_PCM_DURATION_SEC
            val readBuf = ShortArray(bufBytes / 2)
            pcmRecordJob?.cancel()
            pcmRecordJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRecording) {
                    val readCount = ar.read(readBuf, 0, readBuf.size)
                    if (readCount > 0 && !isPaused) {
                        synchronized(pcmLock) {
                            val currentSize = stream.size()
                            if (currentSize < maxRawBytes) {
                                val bytesToWrite = minOf(readCount * 2, maxRawBytes - currentSize)
                                val shortsToWrite = bytesToWrite / 2
                                for (i in 0 until shortsToWrite) {
                                    val s = readBuf[i].toInt()
                                    stream.write(s and 0xFF)
                                    stream.write((s shr 8) and 0xFF)
                                }
                            }
                        }
                    }
                }
            }

            timerJob?.cancel()
            timerJob = scope.launch(Dispatchers.Main) {
                while (isActive && isRecording) {
                    elapsedSec = (currentElapsedMs() / 1000L).toInt()
                    if (elapsedSec >= MAX_PCM_DURATION_SEC) {
                        break
                    }
                    delay(250L)
                }
            }
            true
        } catch (_: Exception) {
            cancelRecording()
            false
        }
    }

    private fun startStandardMediaRecording(): Boolean {
        return try {
            val outFile = File(mediaDir, "voice_${System.currentTimeMillis()}.m4a")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(16000)
            mr.setAudioEncodingBitRate(48000)
            mr.setOutputFile(outFile.absolutePath)
            mr.prepare()
            mr.start()

            recorder = mr
            recordingFilter = VoiceFilter.NONE
            currentFile = outFile
            accumulatedActiveMs = 0L
            segmentStartMs = System.currentTimeMillis()
            elapsedSec = 0
            isPaused = false
            isRecording = true

            timerJob?.cancel()
            timerJob = scope.launch(Dispatchers.Main) {
                while (isActive && isRecording) {
                    elapsedSec = (currentElapsedMs() / 1000L).toInt()
                    if (elapsedSec >= 60) {
                        break
                    }
                    delay(250L)
                }
            }
            true
        } catch (_: Exception) {
            cancelRecording()
            false
        }
    }

    fun pauseRecording(): Boolean {
        if (!isRecording || isPaused) return false
        return try {
            if (audioRecord == null) {
                recorder?.pause()
            }
            accumulatedActiveMs = currentElapsedMs()
            segmentStartMs = 0L
            isPaused = true
            elapsedSec = (accumulatedActiveMs / 1000L).toInt()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resumeRecording(): Boolean {
        if (!isRecording || !isPaused) return false
        return try {
            if (audioRecord == null) {
                recorder?.resume()
            }
            segmentStartMs = System.currentTimeMillis()
            isPaused = false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun stopRecordingAndProcess(): Pair<File, Int>? {
        if (!isRecording) return null
        timerJob?.cancel()
        val duration = (currentElapsedMs() / 1000L).toInt().coerceAtLeast(1)
        isRecording = false
        isPaused = false
        accumulatedActiveMs = 0L
        segmentStartMs = 0L

        val activeAr = audioRecord
        if (activeAr != null) {
            audioRecord = null
            val outFile = currentFile
            currentFile = null
            val stream = pcmOutputStream
            pcmOutputStream = null
            val filter = recordingFilter
            val recJob = pcmRecordJob
            pcmRecordJob = null
            return withContext(Dispatchers.IO) {
                try {
                    recJob?.cancel()
                    try { activeAr.stop() } catch (_: Exception) {}
                    try { activeAr.release() } catch (_: Exception) {}
                    recJob?.join()

                    val rawBytes = synchronized(pcmLock) { stream?.toByteArray() ?: ByteArray(0) }
                    if (rawBytes.size < 64 || outFile == null) {
                        outFile?.delete()
                        return@withContext null
                    }
                    val inputSamples = bytesToShortArray(rawBytes)
                    val processed = applyVoiceFilter(inputSamples, PCM_SAMPLE_RATE, filter)
                    writeWavFile(outFile, PCM_SAMPLE_RATE, processed)
                    if (outFile.exists() && outFile.length() > 44L) outFile to duration else null
                } catch (_: Exception) {
                    outFile?.delete()
                    null
                }
            }
        }

        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            val f = currentFile
            currentFile = null
            if (f != null && f.exists() && f.length() > 0L) f to duration else null
        } catch (_: Exception) {
            recorder?.release()
            recorder = null
            currentFile?.delete()
            currentFile = null
            null
        }
    }

    fun cancelRecording() {
        timerJob?.cancel()
        pcmRecordJob?.cancel()
        pcmRecordJob = null
        isRecording = false
        isPaused = false
        accumulatedActiveMs = 0L
        segmentStartMs = 0L
        elapsedSec = 0
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        synchronized(pcmLock) {
            pcmOutputStream = null
        }
        currentFile?.delete()
        currentFile = null
    }

    companion object {
        private const val PCM_SAMPLE_RATE = 11025
        private const val MAX_PCM_DURATION_SEC = 23

        private fun bytesToShortArray(bytes: ByteArray): ShortArray {
            val count = bytes.size / 2
            val out = ShortArray(count)
            for (i in 0 until count) {
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt()
                out[i] = ((hi shl 8) or lo).toShort()
            }
            return out
        }

        private fun writeWavFile(file: File, sampleRate: Int, samples: ShortArray) {
            val dataByteCount = samples.size * 2
            val totalDataLen = dataByteCount + 36
            val byteRate = sampleRate * 2
            FileOutputStream(file).use { fos ->
                val header = byteArrayOf(
                    'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
                    (totalDataLen and 0xff).toByte(),
                    ((totalDataLen shr 8) and 0xff).toByte(),
                    ((totalDataLen shr 16) and 0xff).toByte(),
                    ((totalDataLen shr 24) and 0xff).toByte(),
                    'W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte(),
                    'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte(),
                    16, 0, 0, 0,
                    1, 0,
                    1, 0,
                    (sampleRate and 0xff).toByte(),
                    ((sampleRate shr 8) and 0xff).toByte(),
                    ((sampleRate shr 16) and 0xff).toByte(),
                    ((sampleRate shr 24) and 0xff).toByte(),
                    (byteRate and 0xff).toByte(),
                    ((byteRate shr 8) and 0xff).toByte(),
                    ((byteRate shr 16) and 0xff).toByte(),
                    ((byteRate shr 24) and 0xff).toByte(),
                    2, 0,
                    16, 0,
                    'd'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
                    (dataByteCount and 0xff).toByte(),
                    ((dataByteCount shr 8) and 0xff).toByte(),
                    ((dataByteCount shr 16) and 0xff).toByte(),
                    ((dataByteCount shr 24) and 0xff).toByte(),
                )
                fos.write(header)
                val pcmBytes = ByteArray(dataByteCount)
                for (i in samples.indices) {
                    val v = samples[i].toInt()
                    pcmBytes[i * 2] = (v and 0xFF).toByte()
                    pcmBytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                fos.write(pcmBytes)
            }
        }

        private fun applyVoiceFilter(
            samples: ShortArray,
            sampleRate: Int,
            filter: VoiceFilter,
        ): ShortArray {
            return when (filter) {
                VoiceFilter.NONE -> samples
                VoiceFilter.CHIPMUNK -> pitchShiftPreserveLength(samples, 1.45f)
                VoiceFilter.DEEP -> pitchShiftPreserveLength(samples, 0.72f)
                VoiceFilter.ROBOT -> {
                    val out = ShortArray(samples.size)
                    val combDelay = (sampleRate * 0.014f).roundToInt().coerceAtLeast(1)
                    val ringBuf = FloatArray(combDelay)
                    var ringIdx = 0
                    for (i in samples.indices) {
                        val dry = samples[i].toFloat()
                        val t = i.toFloat() / sampleRate.toFloat()
                        val mod = 0.65f * sin(2.0 * PI * 58.0 * t).toFloat() +
                            0.35f * sin(2.0 * PI * 116.0 * t).toFloat()
                        val modulated = dry * mod
                        val comb = ringBuf[ringIdx]
                        val nextSample = 0.78f * modulated + 0.22f * dry + 0.32f * comb
                        ringBuf[ringIdx] = nextSample
                        ringIdx = (ringIdx + 1) % combDelay
                        // Gentle 8-bit style stair-step crunch for crisp robot texture
                        val quantized = ((nextSample / 384f).roundToInt() * 384f)
                        val mixed = 0.65f * nextSample + 0.35f * quantized
                        out[i] = mixed.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    out
                }
                VoiceFilter.ECHO -> {
                    val tailSamples = (sampleRate * 0.55f).roundToInt()
                    val outLen = samples.size + tailSamples
                    val work = FloatArray(outLen)
                    val d1 = (sampleRate * 0.19f).roundToInt()
                    val d2 = (sampleRate * 0.38f).roundToInt()
                    val d3 = (sampleRate * 0.57f).roundToInt()
                    var maxAbs = 1f
                    for (i in 0 until outLen) {
                        val dry = if (i < samples.size) samples[i].toFloat() else 0f
                        val e1 = if (i >= d1 && i - d1 < samples.size) samples[i - d1] * 0.48f else 0f
                        val e2 = if (i >= d2 && i - d2 < samples.size) samples[i - d2] * 0.28f else 0f
                        val e3 = if (i >= d3 && i - d3 < samples.size) samples[i - d3] * 0.14f else 0f
                        val v = dry + e1 + e2 + e3
                        work[i] = v
                        val absV = if (v < 0f) -v else v
                        if (absV > maxAbs) maxAbs = absV
                    }
                    val scale = if (maxAbs > 32000f) 32000f / maxAbs else 1f
                    val out = ShortArray(outLen)
                    for (i in 0 until outLen) {
                        out[i] = (work[i] * scale).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    out
                }
            }
        }

        private fun pitchShiftPreserveLength(samples: ShortArray, pitchFactor: Float): ShortArray {
            if (samples.size < 512) return samples
            // Step 1: OLA time-stretch by pitchFactor while keeping original pitch
            val winSize = 352
            val synthHop = 176
            val analysisHop = (synthHop / pitchFactor).roundToInt().coerceAtLeast(32)
            val stretchedLen = (samples.size * pitchFactor).roundToInt().coerceAtLeast(winSize)
            val acc = FloatArray(stretchedLen + winSize)
            val norm = FloatArray(stretchedLen + winSize)
            val hann = FloatArray(winSize) { i ->
                (0.5 * (1.0 - cos(2.0 * PI * i / (winSize - 1)))).toFloat()
            }

            var inPos = 0
            var outPos = 0
            while (inPos + winSize <= samples.size && outPos + winSize <= acc.size) {
                for (k in 0 until winSize) {
                    val w = hann[k]
                    acc[outPos + k] += samples[inPos + k] * w
                    norm[outPos + k] += w
                }
                inPos += analysisHop
                outPos += synthHop
            }

            val stretched = FloatArray(stretchedLen)
            for (i in stretched.indices) {
                val n = norm[i]
                stretched[i] = if (n > 0.001f) acc[i] / n else 0f
            }

            // Step 2: Resample by pitchFactor to restore original duration and shift pitch
            val out = ShortArray(samples.size)
            for (i in out.indices) {
                val srcPos = i * pitchFactor
                val idx0 = srcPos.toInt().coerceIn(0, stretched.lastIndex)
                val idx1 = (idx0 + 1).coerceIn(0, stretched.lastIndex)
                val frac = srcPos - idx0
                val interp = stretched[idx0] * (1f - frac) + stretched[idx1] * frac
                out[i] = interp.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            return out
        }
    }
}

/**
 * Real MediaPlayer wrapper for playing voice notes with pause/resume, progress tracking, and seeking.
 */
class VoiceNotePlayer(private val scope: CoroutineScope) {
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null
    private val savedFractions = mutableStateMapOf<String, Float>()

    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var activeMessageId by mutableStateOf<String?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var currentPositionMs by mutableIntStateOf(0)
        private set
    var durationMs by mutableIntStateOf(0)
        private set

    fun progressFor(messageId: String): Float {
        return if (activeMessageId == messageId) progress else (savedFractions[messageId] ?: 0f)
    }

    fun positionMsFor(messageId: String, fallbackSec: Int): Int {
        if (activeMessageId == messageId && durationMs > 0) return currentPositionMs
        val saved = savedFractions[messageId] ?: return 0
        return (saved * fallbackSec * 1000).toInt()
    }

    fun togglePlay(messageId: String, localPath: String): Boolean {
        if (activeMessageId == messageId && player != null) {
            val mp = player ?: return false
            return try {
                if (mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                    playingMessageId = null
                    progressJob?.cancel()
                    false
                } else {
                    val dur = runCatching { mp.duration }.getOrDefault(0)
                    val pos = runCatching { mp.currentPosition }.getOrDefault(0)
                    if (dur > 0 && pos >= dur - 120) {
                        mp.seekTo(0)
                        currentPositionMs = 0
                        progress = 0f
                        savedFractions.remove(messageId)
                    }
                    mp.start()
                    isPlaying = true
                    playingMessageId = messageId
                    startProgressLoop(messageId)
                    true
                }
            } catch (_: Exception) {
                stop()
                false
            }
        }

        stop()
        val file = File(localPath)
        if (!file.exists()) return false
        return try {
            val saved = savedFractions[messageId] ?: 0f
            val initialFraction = if (saved >= 0.95f) {
                savedFractions.remove(messageId)
                0f
            } else {
                saved.coerceIn(0f, 0.95f)
            }
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                val dur = duration.coerceAtLeast(1)
                if (initialFraction > 0f) {
                    seekTo((initialFraction * dur).toInt())
                }
                setOnCompletionListener {
                    scope.launch(Dispatchers.Main) {
                        completePlayback(messageId)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    scope.launch(Dispatchers.Main) {
                        stop()
                    }
                    true
                }
                start()
            }
            player = mp
            activeMessageId = messageId
            playingMessageId = messageId
            isPlaying = true
            durationMs = mp.duration.coerceAtLeast(0)
            currentPositionMs = mp.currentPosition.coerceAtLeast(0)
            progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else initialFraction
            startProgressLoop(messageId)
            true
        } catch (_: Exception) {
            stop()
            false
        }
    }

    fun seekTo(messageId: String, localPath: String, fraction: Float) {
        val clamped = fraction.coerceIn(0f, 0.99f)
        savedFractions[messageId] = clamped
        if (activeMessageId == messageId && player != null) {
            val mp = player ?: return
            try {
                val dur = mp.duration
                if (dur > 0) {
                    val targetMs = (clamped * dur).toInt().coerceIn(0, dur)
                    mp.seekTo(targetMs)
                    durationMs = dur
                    currentPositionMs = targetMs
                    progress = clamped
                }
            } catch (_: Exception) {}
        } else {
            val file = File(localPath)
            if (file.exists() && activeMessageId == null) {
                progress = clamped
            }
        }
    }

    private fun startProgressLoop(messageId: String) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            var stoppedTicks = 0
            while (isActive && activeMessageId == messageId && isPlaying) {
                val p = player ?: break
                try {
                    val currentlyPlaying = p.isPlaying
                    val dur = p.duration
                    val pos = p.currentPosition
                    if (currentlyPlaying && dur > 0) {
                        stoppedTicks = 0
                        durationMs = dur
                        currentPositionMs = pos
                        val frac = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        progress = frac
                        savedFractions[messageId] = frac
                    } else if (!currentlyPlaying) {
                        stoppedTicks++
                        if (stoppedTicks >= 2 || (dur > 0 && pos >= dur - 80)) {
                            completePlayback(messageId)
                            break
                        }
                    }
                } catch (_: Exception) {
                    completePlayback(messageId)
                    break
                }
                delay(60L)
            }
        }
    }

    private fun completePlayback(expectedMessageId: String) {
        savedFractions.remove(expectedMessageId)
        if (activeMessageId == expectedMessageId) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        val oldPlayer = player
        player = null
        if (oldPlayer != null) {
            try { oldPlayer.setOnCompletionListener(null) } catch (_: Exception) {}
            try { oldPlayer.setOnErrorListener(null) } catch (_: Exception) {}
            try {
                if (oldPlayer.isPlaying) oldPlayer.stop()
            } catch (_: Exception) {}
            try { oldPlayer.reset() } catch (_: Exception) {}
            try { oldPlayer.release() } catch (_: Exception) {}
        }
        playingMessageId = null
        activeMessageId = null
        isPlaying = false
        progress = 0f
        currentPositionMs = 0
        durationMs = 0
    }
}
