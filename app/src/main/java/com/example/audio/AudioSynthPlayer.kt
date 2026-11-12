package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.example.model.TrackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AudioSynthPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var isTrackPlaying = false
    private var cachedTimeSlices: List<AudioTimeSlice> = emptyList()

    /**
     * Imports an uploaded audio file (MP3, WAV, FLAC, M4A, OGG) from a Content URI:
     * - Copies the content stream into internal cache so MediaPlayer and MediaExtractor
     *   have permanent reliable access.
     * - Extracts ID3 / metadata (Title, Artist, Duration).
     */
    suspend fun importAudioFile(uri: Uri, rawFileName: String): TrackInfo = withContext(Dispatchers.IO) {
        val cleanName = rawFileName.substringBeforeLast('.').ifBlank { "내 음악 트랙" }
        val extension = rawFileName.substringAfterLast('.', "mp3")
        val trackId = "track_${System.currentTimeMillis()}"
        val targetFile = File(context.cacheDir, "$trackId.$extension")

        // Copy input stream to cache file
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        var title = cleanName
        var artist = "내 오디오 CD"
        var durationMs = 0L

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(targetFile.absolutePath)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaDuration.isNullOrBlank()) durationMs = metaDuration.toLongOrNull() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            Log.w("AudioSynthPlayer", "Could not read ID3 metadata: ${e.message}")
        }

        if (durationMs <= 0L) {
            durationMs = 60000L // Default estimate if undetermined
        }

        val fileSizeMb = targetFile.length().toFloat() / (1024 * 1024)
        val formattedSize = String.format("%.1f MB", fileSizeMb)

        TrackInfo(
            id = trackId,
            title = title,
            artist = artist,
            bpm = 128,
            durationMs = durationMs,
            isCustom = true,
            customUriString = uri.toString(),
            localFilePath = targetFile.absolutePath,
            fileSizeFormatted = formattedSize
        )
    }

    /**
     * Pre-buffering & waveform analysis (선 읽기 버퍼링):
     * Decodes the audio into 16-bit PCM and runs the frequency matching
     * and composite obstacle generator algorithm.
     */
    suspend fun prepareTrack(
        track: TrackInfo,
        difficulty: Difficulty,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): TrackAnalysisResult = withContext(Dispatchers.IO) {
        val audioFile = track.localFilePath?.let { File(it) }

        val pcm = if (audioFile != null && audioFile.exists()) {
            onProgress(0.15f, "오디오 파일 읽기 및 디코딩 준비 중...")
            decodeAudioFileToPcm(audioFile, onProgress)
        } else if (track.customUriString != null) {
            onProgress(0.15f, "오디오 URI 스트림 디코딩 중...")
            decodeUriToPcm(Uri.parse(track.customUriString), onProgress)
        } else {
            onProgress(0.2f, "테스트 신디사이저 트랙 생성 중...")
            generateDemoTrackPcm()
        }

        onProgress(0.65f, "실시간 오디오 파형(Waveform) 스펙트럼 분석 중...")
        val slices = AudioAnalyzer.processPcmSamples(pcm)
        cachedTimeSlices = slices

        onProgress(0.85f, "음향 데이터 & 합성 장애물(Composite) 매칭 계산 중...")
        val result = AudioAnalyzer.analyzeWaveform(slices, difficulty)

        onProgress(1.0f, "선 읽기 완료 (100% 버퍼링됨)")
        result
    }

    fun startPlayback(track: TrackInfo, onCompletion: () -> Unit) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                val localFile = track.localFilePath?.let { File(it) }
                if (localFile != null && localFile.exists()) {
                    setDataSource(localFile.absolutePath)
                } else if (track.customUriString != null) {
                    setDataSource(context, Uri.parse(track.customUriString))
                } else {
                    // Demo fallback
                    val demoFile = File(context.cacheDir, "${track.id}.wav")
                    setDataSource(demoFile.absolutePath)
                }
                prepare()
                setOnCompletionListener {
                    isTrackPlaying = false
                    onCompletion()
                }
                start()
            }
            isTrackPlaying = true
        } catch (e: Exception) {
            Log.e("AudioSynthPlayer", "Error starting playback", e)
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isTrackPlaying = false
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                isTrackPlaying = true
            }
        }
    }

    fun stopPlayback() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioSynthPlayer", "Error stopping playback", e)
        } finally {
            mediaPlayer = null
            isTrackPlaying = false
        }
    }

    fun getCurrentPositionMs(): Long {
        return try {
            mediaPlayer?.currentPosition?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun isPlaying(): Boolean = isTrackPlaying && (mediaPlayer?.isPlaying == true)

    fun getCurrentSlice(timeMs: Long): AudioTimeSlice {
        if (cachedTimeSlices.isEmpty()) {
            return AudioTimeSlice(timeMs, 0.15f, 0.15f, 0.15f, 0.15f)
        }
        val idx = (timeMs / 40).toInt().coerceIn(0, cachedTimeSlices.lastIndex)
        return cachedTimeSlices[idx]
    }

    /**
     * Decodes an audio file directly into 16-bit PCM shorts using Android MediaExtractor & MediaCodec.
     */
    private fun decodeAudioFileToPcm(
        file: File,
        onProgress: (Float, String) -> Unit
    ): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return extractPcmWithCodec(extractor, onProgress)
        } catch (e: Exception) {
            Log.e("AudioSynthPlayer", "Decode file error", e)
            return generateDemoTrackPcm()
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeUriToPcm(
        uri: Uri,
        onProgress: (Float, String) -> Unit
    ): ShortArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return extractPcmWithCodec(extractor, onProgress)
        } catch (e: Exception) {
            Log.e("AudioSynthPlayer", "Decode URI error", e)
            return generateDemoTrackPcm()
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun extractPcmWithCodec(
        extractor: MediaExtractor,
        onProgress: (Float, String) -> Unit
    ): ShortArray {
        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                break
            }
        }

        if (audioTrackIndex < 0) {
            return generateDemoTrackPcm()
        }

        extractor.selectTrack(audioTrackIndex)
        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val info = MediaCodec.BufferInfo()
        val shortList = ArrayList<Short>(44100 * 30)
        var isEOS = false

        val maxSamples = 44100 * 120 // Up to 2 minutes analysis
        var sampleCount = 0

        while (!isEOS && sampleCount < maxSamples) {
            val inIndex = decoder.dequeueInputBuffer(5000)
            if (inIndex >= 0) {
                val buffer = decoder.getInputBuffer(inIndex)
                buffer?.clear()
                val sampleSize = buffer?.let { extractor.readSampleData(it, 0) } ?: -1
                if (sampleSize < 0) {
                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    isEOS = true
                } else {
                    decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }

            var outIndex = decoder.dequeueOutputBuffer(info, 5000)
            while (outIndex >= 0) {
                val outBuf = decoder.getOutputBuffer(outIndex)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    outBuf.order(ByteOrder.LITTLE_ENDIAN)
                    val shortBuf = outBuf.asShortBuffer()
                    val len = shortBuf.remaining()
                    for (s in 0 until len) {
                        if (sampleCount < maxSamples) {
                            shortList.add(shortBuf.get())
                            sampleCount++
                        }
                    }
                }
                decoder.releaseOutputBuffer(outIndex, false)
                outIndex = decoder.dequeueOutputBuffer(info, 1000)
            }
        }

        decoder.stop()
        decoder.release()

        val arr = ShortArray(shortList.size)
        for (i in shortList.indices) {
            arr[i] = shortList[i]
        }
        return if (arr.isNotEmpty()) arr else generateDemoTrackPcm()
    }

    /**
     * Demo track generator only used when user explicitly tests without an audio file.
     */
    fun createDemoTestTrack(): TrackInfo {
        val trackId = "demo_vibribbon_test"
        val demoFile = File(context.cacheDir, "$trackId.wav")
        val pcm = generateDemoTrackPcm()
        savePcmToWav(pcm, demoFile)

        return TrackInfo(
            id = trackId,
            title = "Vib-Test Track (샘플 데모)",
            artist = "Vibri Sound Engine",
            bpm = 138,
            durationMs = 32000L,
            isCustom = true,
            localFilePath = demoFile.absolutePath,
            fileSizeFormatted = "2.8 MB"
        )
    }

    private fun generateDemoTrackPcm(): ShortArray {
        val sampleRate = 44100
        val totalSamples = sampleRate * 32
        val pcm = ShortArray(totalSamples)
        val bpm = 138
        val beatSec = 60.0 / bpm

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val beatIndex = (t / beatSec).toInt()
            val beatFrac = (t % beatSec) / beatSec

            // Kick drum
            var kick = 0.0
            if (beatIndex % 2 == 0 && beatFrac < 0.22) {
                val decay = 1.0 - (beatFrac / 0.22)
                kick = sin(2.0 * PI * (130.0 * decay + 45.0) * beatFrac * beatSec) * decay * 0.75
            }

            // Snare / high peak
            var snare = 0.0
            if (beatIndex % 2 == 1 && beatFrac < 0.2) {
                val decay = 1.0 - (beatFrac / 0.2)
                snare = (Math.random() * 2.0 - 1.0) * decay * 0.55
            }

            // Bass & Melody
            val baseFreq = if ((beatIndex / 4) % 2 == 0) 146.8 else 174.6
            val bass = sin(2.0 * PI * baseFreq * t) * 0.3
            val lead = sin(2.0 * PI * (baseFreq * 2.0) * t) * 0.2

            val mixed = (kick + snare + bass + lead).coerceIn(-1.0, 1.0)
            pcm[i] = (mixed * 32000).toInt().toShort()
        }
        return pcm
    }

    private fun savePcmToWav(pcmData: ShortArray, outputFile: File) {
        val totalAudioLen = (pcmData.size * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val sampleRate = 44100L
        val channels = 1
        val byteRate = 16 * sampleRate * channels / 8

        FileOutputStream(outputFile).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte(); header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte(); header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0
            header[24] = (sampleRate and 0xff).toByte(); header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte(); header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte(); header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte(); header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xff).toByte(); header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xff).toByte(); header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
            out.write(header, 0, 44)

            val byteBuf = ByteBuffer.allocate(pcmData.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                byteBuf.putShort(s)
            }
            out.write(byteBuf.array())
        }
    }
}
