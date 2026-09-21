package com.nexar.assistant.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import com.nexar.assistant.utils.NexarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class NexarAudioManager(private val context: Context) {

    companion object {
        private const val TAG = "NexarAudio"
        private const val SAMPLE_RATE_MIC = 16000
        private const val SAMPLE_RATE_OUT = 24000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_MS = 100 // 100ms chunks
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val isCapturing = AtomicBoolean(false)
    private val isPlaying = AtomicBoolean(false)
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private val systemAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    val chunkSize: Int
        get() = SAMPLE_RATE_MIC * 2 * CHUNK_SIZE_MS / 1000  // bytes

    fun startMicCapture(onAudioData: (ByteArray) -> Unit) {
        if (isCapturing.get()) {
            NexarLogger.d(TAG, "Already capturing")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_MIC, CHANNEL_IN, ENCODING)
        val bufferSize = maxOf(minBufferSize * 4, chunkSize * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_MIC,
                CHANNEL_IN,
                ENCODING,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                NexarLogger.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isCapturing.set(true)

            captureJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(chunkSize)
                NexarLogger.d(TAG, "Started mic capture")
                while (isActive && isCapturing.get()) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        onAudioData(buffer.copyOf(read))
                    } else if (read < 0) {
                        NexarLogger.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            NexarLogger.e(TAG, "Microphone permission denied", e)
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Failed to start mic capture", e)
        }
    }

    fun stopMicCapture() {
        if (!isCapturing.get()) return
        isCapturing.set(false)
        captureJob?.cancel()
        captureJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error stopping AudioRecord", e)
        }
        NexarLogger.d(TAG, "Stopped mic capture")
    }

    /**
     * Play PCM audio data. Queues chunks for seamless streaming.
     */
    fun playAudio(audioData: ByteArray, mimeType: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // Determine sample rate from mime type
                val sampleRate = when {
                    mimeType.contains("rate=24000") -> 24000
                    mimeType.contains("rate=22050") -> 22050
                    mimeType.contains("rate=16000") -> 16000
                    mimeType.contains("rate=44100") -> 44100
                    mimeType.contains("rate=48000") -> 48000
                    else -> SAMPLE_RATE_OUT
                }

                ensureAudioTrack(sampleRate)
                audioTrack?.write(audioData, 0, audioData.size)

                if (!isPlaying.get()) {
                    isPlaying.set(true)
                    audioTrack?.play()
                }
            } catch (e: Exception) {
                NexarLogger.e(TAG, "Playback error", e)
            }
        }
    }

    private fun ensureAudioTrack(sampleRate: Int) {
        if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) return

        audioTrack?.release()

        val minBufSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT, ENCODING)
        val bufSize = maxOf(minBufSize * 4, 8192)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun stopPlayback() {
        try {
            isPlaying.set(false)
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error stopping playback", e)
        }
    }

    fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                .build()
            audioFocusRequest = focusRequest
            systemAudioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            systemAudioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { systemAudioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            systemAudioManager.abandonAudioFocus(null)
        }
    }

    fun release() {
        stopMicCapture()
        stopPlayback()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            NexarLogger.e(TAG, "Error releasing AudioTrack", e)
        }
        releaseAudioFocus()
    }
}
