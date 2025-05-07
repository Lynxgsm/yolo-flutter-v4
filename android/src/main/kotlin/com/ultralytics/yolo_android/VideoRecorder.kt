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
    
    // Track if any frames have been encoded
    private var framesEncoded: Int = 0
    
    /**
     * Starts recording video of the camera feed
     * 
     * @param width Width of the video
     * @param height Height of the video
     * @param outputPath Optional custom output path
     * @return Pair of (success boolean, error message or null if successful)
     */
    fun startRecording(width: Int, height: Int, outputPath: String?): Pair<Boolean, String?> {
        if (isRecording.get()) {
            return Pair(false, "Recording already in progress")
        }
        
        try {
            // Store original dimensions
            originalWidth = width
            originalHeight = height
            
            // Reset frame counter
            framesEncoded = 0
            
            // Determine final output path
            val finalOutputPath = outputPath ?: createDefaultOutputPath()
            currentRecordingPath = finalOutputPath
            
            Log.d(TAG, "Starting recording to $finalOutputPath with dimensions ${width}x${height}")
            
            // Create output directory if needed
            val outputFile = File(finalOutputPath)
            val outputDir = outputFile.parentFile
            if (outputDir != null && !outputDir.exists()) {
                if (!outputDir.mkdirs()) {
                    return Pair(false, "Failed to create output directory")
                }
            }
            
            // Initialize media recorder
            val initResult = initializeMediaRecorder(width, height, finalOutputPath)
            if (!initResult.first) {
                return Pair(false, initResult.second)
            }
            
            isRecording.set(true)
            return Pair(true, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            e.printStackTrace()
            releaseRecorder()
            return Pair(false, "Failed to start recording: ${e.message}")
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
     * @return Pair of (success boolean, error message or null if successful)
     */
    private fun initializeMediaRecorder(width: Int, height: Int, outputPath: String): Pair<Boolean, String?> {
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
                setVideoFrameRate(30)
                setVideoEncodingBitRate(10000000) // 10Mbps
                
                setOutputFile(outputPath)
                
                // Only set orientation hint if needed
                if (!isLandscape) {
                    setOrientationHint(90) // Portrait orientation
                }
                
                // Set max file size to prevent empty files (100MB)
                setMaxFileSize(100 * 1024 * 1024)
                
                // Prepare the recorder
                prepare()
                
                // Get the surface to draw on AFTER preparing
                recordingSurface = surface
                
                // Start recording immediately
                start()
                
                if (recordingSurface == null) {
                    return Pair(false, "Failed to get recording surface after prepare")
                }
            }
            
            return Pair(true, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media recorder: ${e.message}")
            e.printStackTrace()
            releaseRecorder()
            return Pair(false, "Error initializing media recorder: ${e.message}")
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
            Log.d(TAG, "Stopping recording. Frames encoded: $framesEncoded")
            
            // If we haven't encoded any frames, don't try to stop
            if (framesEncoded == 0) {
                Log.w(TAG, "No frames were encoded during recording. Releasing without stop()")
                releaseRecorder()
                return null
            }
            
            mediaRecorder?.apply {
                stop()
                Log.d(TAG, "Recording stopped successfully")
            }
            
            // Verify the file exists and has non-zero size
            val outputFile = File(savedPath)
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Recording saved: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            } else {
                Log.e(TAG, "Recording file is empty or does not exist: ${outputFile.absolutePath}")
                if (outputFile.exists()) {
                    Log.e(TAG, "File size: ${outputFile.length()} bytes")
                }
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
                
                // Only log dimensions occasionally
                if (framesEncoded == 0 || framesEncoded % 100 == 0) {
                    Log.d(TAG, "Canvas size: ${canvasWidth}x${canvasHeight}, Bitmap size: ${bitmapWidth}x${bitmapHeight}, Frames: $framesEncoded")
                }
                
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
                
                // Increment frame counter
                framesEncoded++
                
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing to recording surface: ${e.message}")
            stopRecording()
        }
    }
} 