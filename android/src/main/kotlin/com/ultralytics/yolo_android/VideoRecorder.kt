package com.ultralytics.yolo_android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles video recording of the YOLO detection feed
 */
class VideoRecorder(private val context: Context) {
    companion object {
        private const val TAG = "VideoRecorder"
    }
    
    // Video recording components
    private var videoEncoder: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private val isRecording = AtomicBoolean(false)
    private var frameCount: Long = 0
    private var recordingStartTime: Long = 0
    private var currentOutputPath: String? = null
    private val recordingExecutor = Executors.newSingleThreadExecutor()
    
    /**
     * Starts recording video of the camera feed with detection overlay
     * 
     * @param width Width of the video
     * @param height Height of the video
     * @param outputPath Path where the video should be saved. If null, a default path will be used.
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(width: Int, height: Int, outputPath: String?): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress")
            return false
        }
        
        try {
            // Determine output path
            val finalOutputPath = outputPath ?: "${context.getExternalFilesDir(null)}/yolo_recording_${System.currentTimeMillis()}.mp4"
            currentOutputPath = finalOutputPath
            
            if (width <= 0 || height <= 0) {
                Log.e(TAG, "Invalid dimensions for recording: ${width}x${height}")
                return false
            }
            
            // Initialize MediaCodec for video encoding
            // Avoid using MediaCodecList directly, instead rely on direct creation
            try {
                // Try to create an encoder directly for H.264
                videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            } catch (e: Exception) {
                Log.e(TAG, "No suitable encoder found for H.264 video", e)
                return false
            }
            
            // Configure video format
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            val bitRate = width * height * 4 // Reasonable bitrate based on resolution
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second between I-frames
            
            // Configure and start the encoder
            videoEncoder?.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            
            // Create MediaMuxer for MP4 output
            mediaMuxer = MediaMuxer(finalOutputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            // Reset state
            videoTrackIndex = -1
            frameCount = 0
            recordingStartTime = System.currentTimeMillis()
            
            // Flag as recording
            isRecording.set(true)
            
            // Start encoder
            videoEncoder?.start()
            
            Log.d(TAG, "Started recording with output path: $finalOutputPath")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            releaseRecordingResources()
            return false
        }
    }
    
    /**
     * Stops the current video recording
     * 
     * @return Path to the saved video file or null if recording failed
     */
    fun stopRecording(): String? {
        if (!isRecording.getAndSet(false)) {
            Log.w(TAG, "No recording in progress")
            return null
        }
        
        // Save path before any operations that might reset it
        val savedPath = currentOutputPath
        
        try {
            // Only proceed if we have a valid path
            if (savedPath == null) {
                Log.e(TAG, "Recording path was null, cannot finish recording properly")
                releaseRecordingResources()
                return null
            }
            
            // Wait for any pending frame processing to complete
            try {
                recordingExecutor.shutdown()
                if (!recordingExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Recording executor didn't terminate in time, forcing shutdown")
                    recordingExecutor.shutdownNow()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down recording executor", e)
            }
            
            // Make sure we have the encoder and muxer
            if (videoEncoder == null || mediaMuxer == null || videoTrackIndex < 0) {
                Log.e(TAG, "Recording resources were not properly initialized")
                releaseRecordingResources()
                return savedPath
            }
            
            try {
                // Send end-of-stream signal to encoder
                val encoderRef = videoEncoder
                if (encoderRef != null) {
                    try {
                        encoderRef.signalEndOfInputStream()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error signaling end of input stream", e)
                    }
                    
                    // Process any remaining output
                    val bufferInfo = MediaCodec.BufferInfo()
                    var outputBufferIndex = -1
                    try {
                        outputBufferIndex = encoderRef.dequeueOutputBuffer(bufferInfo, 5000)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error dequeuing output buffer", e)
                    }
                    
                    while (outputBufferIndex >= 0) {
                        // Process remaining frames
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                            try {
                                val encodedData = encoderRef.getOutputBuffer(outputBufferIndex)
                                if (encodedData != null) {
                                    encodedData.position(bufferInfo.offset)
                                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                    
                                    try {
                                        mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error writing final samples", e)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing output buffer", e)
                            }
                        }
                        
                        try {
                            encoderRef.releaseOutputBuffer(outputBufferIndex, false)
                            outputBufferIndex = encoderRef.dequeueOutputBuffer(bufferInfo, 0)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing output buffer", e)
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finalizing video", e)
                // Continue with cleanup even after error
            }
            
            // Final cleanup
            releaseRecordingResources()
            
            Log.d(TAG, "Stopped recording, saved to: $savedPath")
            return savedPath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            releaseRecordingResources()
            // Return the saved path even after error, as the file may still be usable
            return savedPath
        }
    }
    
    /**
     * Returns whether recording is currently active
     */
    fun isRecording(): Boolean {
        return isRecording.get()
    }
    
    /**
     * Encodes the current frame to video if recording is active
     * 
     * @param bitmap The frame to encode
     * @param drawDetections Callback to draw detection overlays on the frame
     */
    fun encodeFrame(bitmap: Bitmap, drawDetections: ((Canvas, Int, Int) -> Unit)?) {
        if (!isRecording.get() || videoEncoder == null) return
        
        recordingExecutor.execute {
            try {
                // Check if recording was stopped while this task was waiting
                if (!isRecording.get()) {
                    Log.d(TAG, "Skipping frame processing - recording stopped")
                    return@execute
                }
                
                // Get the current frame with overlays
                val shouldDrawOverlays = drawDetections != null
                val frameBitmap = if (shouldDrawOverlays) {
                    // Create a copy of the bitmap with detection overlays
                    val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(resultBitmap)
                    
                    // Draw overlays using the provided callback
                    drawDetections?.invoke(canvas, resultBitmap.width, resultBitmap.height)
                    
                    resultBitmap
                } else {
                    bitmap
                }
                
                // Get an input buffer
                val encoderRef = videoEncoder
                if (encoderRef == null || !isRecording.get()) {
                    if (frameBitmap != bitmap) {
                        frameBitmap.recycle()
                    }
                    return@execute
                }
                
                val inputBufferIndex = encoderRef.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoderRef.getInputBuffer(inputBufferIndex)
                    
                    if (inputBuffer != null) {
                        // Convert the bitmap to buffer data
                        val yuvData = convertBitmapToNV21(frameBitmap)
                        inputBuffer.clear()
                        inputBuffer.put(yuvData)
                        
                        // Calculate presentation time in microseconds
                        val presentationTimeUs = (System.currentTimeMillis() - recordingStartTime) * 1000
                        
                        // Queue the encoded frame
                        encoderRef.queueInputBuffer(
                            inputBufferIndex, 0, yuvData.size,
                            presentationTimeUs, 0
                        )
                    }
                }
                
                // Process the output buffer
                val bufferInfo = MediaCodec.BufferInfo()
                var outputBufferIndex = encoderRef.dequeueOutputBuffer(bufferInfo, 10000)
                
                while (outputBufferIndex >= 0 && isRecording.get()) {
                    if (videoTrackIndex == -1) {
                        // Add track to muxer
                        val format = encoderRef.getOutputFormat()
                        if (format != null) {
                            val muxerRef = mediaMuxer
                            if (muxerRef != null) {
                                videoTrackIndex = muxerRef.addTrack(format)
                                muxerRef.start()
                            }
                        }
                    }
                    
                    if (videoTrackIndex >= 0) {
                        val encodedData = encoderRef.getOutputBuffer(outputBufferIndex)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                            encodedData?.position(bufferInfo.offset)
                            encodedData?.limit(bufferInfo.offset + bufferInfo.size)
                            
                            mediaMuxer?.writeSampleData(videoTrackIndex, encodedData!!, bufferInfo)
                        }
                        
                        encoderRef.releaseOutputBuffer(outputBufferIndex, false)
                        frameCount++
                    }
                    
                    outputBufferIndex = encoderRef.dequeueOutputBuffer(bufferInfo, 0)
                }
                
                // Clean up
                if (frameBitmap != bitmap) {
                    frameBitmap.recycle()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error encoding video frame", e)
                stopRecording()
            }
        }
    }
    
    /**
     * Clean up recording resources
     */
    private fun releaseRecordingResources() {
        try {
            // Make sure executor is shutdown first to prevent access to resources being released
            try {
                if (!recordingExecutor.isShutdown) {
                    recordingExecutor.shutdownNow()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error shutting down recording executor", e)
            }
            
            // Safely close encoder
            if (videoEncoder != null) {
                try {
                    videoEncoder?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping encoder", e)
                }
                
                try {
                    videoEncoder?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing encoder", e)
                }
                videoEncoder = null
            }
            
            // Safely close muxer
            if (mediaMuxer != null) {
                try {
                    if (videoTrackIndex >= 0) {
                        mediaMuxer?.stop()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping muxer", e)
                }
                
                try {
                    mediaMuxer?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing muxer", e)
                }
                mediaMuxer = null
            }
            
            videoTrackIndex = -1
            isRecording.set(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recording resources", e)
        }
    }
    
    /**
     * Converts bitmap to NV21 format (YUV420SP) required by many encoders
     */
    private fun convertBitmapToNV21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val yuvByteArray = ByteArray(width * height * 3 / 2)
        
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        
        encodeYUV420SP(yuvByteArray, argb, width, height)
        return yuvByteArray
    }
    
    /**
     * Encodes RGB array to YUV420SP format
     */
    private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        
        var yIndex = 0
        var uvIndex = frameSize
        
        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val R = (argb[index] and 0xff0000) shr 16
                val G = (argb[index] and 0xff00) shr 8
                val B = argb[index] and 0xff
                index++
                
                // RGB to YUV formula
                val Y = ((66 * R + 129 * G + 25 * B + 128) shr 8) + 16
                val U = ((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128
                val V = ((112 * R - 94 * G - 18 * B + 128) shr 8) + 128
                
                val clampedY = Y.coerceIn(0, 255)
                val clampedU = U.coerceIn(0, 255)
                val clampedV = V.coerceIn(0, 255)
                
                yuv420sp[yIndex++] = clampedY.toByte()
                
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv420sp[uvIndex++] = clampedV.toByte()
                    yuv420sp[uvIndex++] = clampedU.toByte()
                }
            }
        }
    }
} 