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
    
    // Store the original dimensions
    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    
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
            // Store original dimensions
            originalWidth = width
            originalHeight = height
            
            // Determine final output path
            val finalOutputPath = outputPath ?: createDefaultOutputPath()
            currentRecordingPath = finalOutputPath
            
            Log.d(TAG, "Starting recording to $finalOutputPath with dimensions ${width}x${height}")
            
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
     * Creates  a default output path for the recording
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
            
            // Determine if we should use landscape or portrait
            val isLandscape = width > height
            val recordWidth = if (isLandscape) width else height
            val recordHeight = if (isLandscape) height else width
            
            Log.d(TAG, "Configuring recorder with ${recordWidth}x${recordHeight} (isLandscape=$isLandscape)")
            
            mediaRecorder?.apply {
                // Only using SURFACE as video source - no audio source is set (video only recording)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(recordWidth, recordHeight)
                if (Build.MODEL.contains("Pixel 9")) {
                    setVideoFrameRate(24)
                    setVideoEncodingBitRate(8000000) // 8Mbps
                } else {
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(10000000) // 10Mbps
                }
                setOutputFile(outputPath)
                
                // Only set orientation hint if needed
                if (!isLandscape) {
                    setOrientationHint(90) // Portrait orientation
                }
                
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
     * Disposes of the recorder and releases all resources.
     * Should be called when the application is being closed or when the camera is no longer needed.
     */
    fun dispose() {
        try {
            Log.d(TAG, "Disposing VideoRecorder")
            
            // Stop any active recording
            if (isRecording.get()) {
                stopRecording()
            }
            
            // Release recorder resources
            releaseRecorder()
            
            // Clear recording path
            currentRecordingPath = null
            
            Log.d(TAG, "VideoRecorder disposed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during VideoRecorder disposal: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Encodes the current frame to video if recording is active
     * 
     * @param bitmap The frame to encode
     * @param drawDetections Callback to draw detection overlays on the frame (not used, as we only record camera feed)
     */
    fun encodeFrame(bitmap: Bitmap, drawDetections: ((Canvas, Int, Int) -> Unit)?) {
        val surface = recordingSurface
        if (!isRecording.get() || surface == null) return
        
        try {
            val canvas = surface.lockCanvas(null)
            try {
                // Clear canvas
                canvas.drawColor(android.graphics.Color.BLACK)
                
                // Get dimensions
                val canvasWidth = canvas.width
                val canvasHeight = canvas.height
                val bitmapWidth = bitmap.width
                val bitmapHeight = bitmap.height
                
                Log.d(TAG, "Canvas size: ${canvasWidth}x${canvasHeight}, Bitmap size: ${bitmapWidth}x${bitmapHeight}")
                
                // Calculate scaling factor to fill the canvas
                val isLandscape = originalWidth > originalHeight
                
                if (isLandscape) {
                    // Scale the bitmap to fill the width of the canvas
                    val scale = canvasWidth.toFloat() / bitmapWidth.toFloat()
                    val scaledHeight = bitmapHeight * scale
                    val yOffset = (canvasHeight - scaledHeight) / 2
                    
                    // Scale bitmap to fill the canvas width and center it vertically
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bitmap, 0f, yOffset / scale, null)
                } else {
                    // Scale the bitmap to fill the height of the canvas
                    val scale = canvasHeight.toFloat() / bitmapHeight.toFloat() 
                    val scaledWidth = bitmapWidth * scale
                    val xOffset = (canvasWidth - scaledWidth) / 2
                    
                    // Scale bitmap to fill the canvas height and center it horizontally
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bitmap, xOffset / scale, 0f, null)
                }
                
                // Do NOT call drawDetections here to avoid recording detection overlays
                
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing to recording surface: ${e.message}")
            stopRecording()
        }
    }
} 