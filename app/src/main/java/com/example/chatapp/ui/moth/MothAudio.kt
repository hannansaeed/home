package com.example.chatapp.ui.moth

import android.content.Context
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
import java.io.File

/**
 * Real MediaRecorder wrapper for recording compressed AAC (.m4a) voice notes.
 */
class VoiceNoteRecorder(
    private val context: Context,
    private val mediaDir: File,
    private val scope: CoroutineScope,
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startMs: Long = 0L
    private var timerJob: Job? = null

    var isRecording by mutableStateOf(false)
        private set
    var elapsedSec by mutableIntStateOf(0)
        private set

    fun startRecording(): Boolean {
        if (isRecording) return false
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
            currentFile = outFile
            startMs = System.currentTimeMillis()
            elapsedSec = 0
            isRecording = true

            timerJob?.cancel()
            timerJob = scope.launch(Dispatchers.Main) {
                while (isActive && isRecording) {
                    elapsedSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt()
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

    fun stopRecording(): Pair<File, Int>? {
        if (!isRecording) return null
        timerJob?.cancel()
        val duration = ((System.currentTimeMillis() - startMs) / 1000L).toInt().coerceAtLeast(1)
        isRecording = false
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
        isRecording = false
        elapsedSec = 0
        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        currentFile?.delete()
        currentFile = null
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
