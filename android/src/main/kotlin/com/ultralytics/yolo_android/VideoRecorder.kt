package com.ultralytics.yolo_android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles video recording of the YOLO detection feed
 */
class VideoRecorder(private val context: Context) {
    companion object {
        private const val TAG = "VideoRecorder"
    }
    
    // Recording components
    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null
    private var currentRecordingPath: String? = null
    private val isRecording = AtomicBoolean(false)
    
    /**
     * Starts recording video of the camera feed
     * 
     * @param width Width of the video
     * @param height Height of the video
     * @param outputPath Optional custom output path
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(width: Int, height: Int, outputPath: String?): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Recording already in progress")
            return false
        }
        
        try {
            // Determine final output path
            val finalOutputPath = outputPath ?: createDefaultOutputPath()
            currentRecordingPath = finalOutputPath
            
            Log.d(TAG, "Starting recording to $finalOutputPath")
            
            // Create output directory if needed
            val outputFile = File(finalOutputPath)
            val outputDir = outputFile.parentFile
            if (outputDir != null && !outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    Log.e(TAG, "Failed to create output directory")
                    return false
                }
            }
            
            // Initialize media recorder
            if (!initializeMediaRecorder(width, height, finalOutputPath)) {
                Log.e(TAG, "Failed to initialize media recorder")
                return false
            }
            
            isRecording.set(true)
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            e.printStackTrace()
            releaseRecorder()
            return false
        }
    }
    
    /**
     * Creates a default output path for the recording
     */
    private fun createDefaultOutputPath(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.getExternalFilesDir(null), "recordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, "yolo_recording_$timestamp.mp4").absolutePath
    }
    
    /**
     * Initialize the MediaRecorder with the appropriate settings
     * @return true if initialization was successful, false otherwise
     */
    private fun initializeMediaRecorder(width: Int, height: Int, outputPath: String): Boolean {
        releaseRecorder() // Release any existing recorder
        
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                // Only using SURFACE as video source - no audio source is set (video only recording)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(10000000) // 10Mbps
                setOutputFile(outputPath)
                
                // Set orientation hint
                setOrientationHint(90) // Portrait orientation
                
                // Prepare the recorder
                prepare()
                
                // Get the surface to draw on AFTER preparing
                recordingSurface = surface
                
                // Start recording immediately
                start()
                
                if (recordingSurface == null) {
                    Log.e(TAG, "Failed to get recording surface after prepare")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media recorder: ${e.message}")
            e.printStackTrace()
            releaseRecorder()
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
        
        val savedPath = currentRecordingPath
        
        try {
            mediaRecorder?.apply {
                stop()
                Log.d(TAG, "Recording stopped successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            e.printStackTrace()
        } finally {
            releaseRecorder()
        }
        
        return savedPath
    }
    
    /**
     * Returns whether recording is currently active
     */
    fun isRecording(): Boolean {
        return isRecording.get()
    }
    
    /**
     * Releases all recording resources
     */
    private fun releaseRecorder() {
        try {
            recordingSurface = null
            
            mediaRecorder?.apply {
                reset()
                release()
            }
            mediaRecorder = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder: ${e.message}")
        }
    }
    
    /**
     * Encodes the current frame to video if recording is active
     * 
     * @param bitmap The frame to encode
     * @param drawDetections Callback to draw detection overlays on the frame
     */
    fun encodeFrame(bitmap: Bitmap, drawDetections: ((Canvas, Int, Int) -> Unit)?) {
        val surface = recordingSurface
        if (!isRecording.get() || surface == null) return
        
        try {
            val canvas = surface.lockCanvas(null)
            try {
                // Clear canvas
                canvas.drawColor(android.graphics.Color.BLACK)
                
                // Draw the bitmap
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                
                // Draw detection overlays if provided
                drawDetections?.invoke(canvas, bitmap.width, bitmap.height)
                
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing to recording surface: ${e.message}")
            stopRecording()
        }
    }
} 