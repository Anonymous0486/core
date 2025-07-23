package org.app.core.feature.model

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File
import java.nio.ByteBuffer

class MediaMergeWorker(val context: Context, params: WorkerParameters) : Worker(context, params) {
    private val TAG = "MediaMergeWorker"
    
    @SuppressLint("LogNotTimber")
    override fun doWork(): Result {
        val audioPath = inputData.getString("key_audio_path")
        val videoPath = inputData.getString("key_video_path")
        val outputPath = inputData.getString("key_output_path")
        val name = inputData.getString("key_worker_name")
    
        Log.d(TAG, "doWork: Start worker...$name")
        if (audioPath.isNullOrBlank() || videoPath.isNullOrBlank() || outputPath.isNullOrBlank()) {
            Log.d(TAG, "doWork: Failure...")
            return Result.failure()
        }
        
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoPath)
    
        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioPath)
    
        val videoTrack = getTrackIndex(videoExtractor, "video/")
        val videoFormat = videoExtractor.getTrackFormat(videoTrack)
        val audioTrack = getTrackIndex(audioExtractor, "audio/")
        val audioFormat = audioExtractor.getTrackFormat(audioTrack)

        try {
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val videoTrackIndex = muxer.addTrack(videoFormat)
            val audioTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val maxVideoSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            val videoBuffer = ByteBuffer.allocate(maxVideoSize)
            val maxAudioSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            val audioBuffer = ByteBuffer.allocate(maxAudioSize)

            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            videoExtractor.selectTrack(videoTrack)

            while (true) {
                val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                if (sampleSize < 0) {
                    break
                }
                videoBufferInfo.size = sampleSize
                videoBufferInfo.presentationTimeUs = videoExtractor.sampleTime
                videoBufferInfo.flags = if (videoExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_SYNC_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(videoTrackIndex, videoBuffer, videoBufferInfo)
                videoExtractor.advance()
            }

            audioExtractor.selectTrack(audioTrack)
            while (true) {
                val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                if (sampleSize < 0) {
                    break
                }
                audioBufferInfo.size = sampleSize
                audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                audioBufferInfo.flags = if (audioExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_SYNC_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(audioTrackIndex, audioBuffer, audioBufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()
        } catch (_: Exception) {
            Log.d(TAG, "doWork: Exception...")
            return Result.failure()
        }


        try {
            val videoFile = File(videoPath)
            videoFile.delete()

            val audioFile = File(audioPath)
            audioFile.delete()
        } catch (_: Exception) {}
    
        scanMedia(listOf(outputPath), context)
        Log.d(TAG, "doWork: End worker success")
        return Result.success()
    }
    
    private fun getTrackIndex(extractor: MediaExtractor, type: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(type) == true) {
                return i
            }
        }
        return -1
    }
    
    private fun scanMedia(files: List<String>, context: Context) {
        try {
            val paths = files.sortedByDescending { File(it).length() }
            runCatching { MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null) }
        } catch (e: Exception){
            e.printStackTrace()
        }
    }
}