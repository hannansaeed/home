package com.example.chatapp.ui.moth

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
 * Real MediaPlayer wrapper for playing voice notes with progress tracking.
 */
class VoiceNotePlayer(private val scope: CoroutineScope) {
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    var playingMessageId by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    fun togglePlay(messageId: String, localPath: String) {
        if (playingMessageId == messageId) {
            stop()
            return
        }
        stop()
        val file = File(localPath)
        if (!file.exists()) return
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    stop()
                }
                start()
            }
            player = mp
            playingMessageId = messageId
            progress = 0f

            progressJob = scope.launch(Dispatchers.Main) {
                while (isActive && playingMessageId == messageId) {
                    val p = player ?: break
                    if (p.isPlaying && p.duration > 0) {
                        progress = (p.currentPosition.toFloat() / p.duration.toFloat()).coerceIn(0f, 1f)
                    }
                    delay(120L)
                }
            }
        } catch (_: Exception) {
            stop()
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
        playingMessageId = null
        progress = 0f
    }
}
