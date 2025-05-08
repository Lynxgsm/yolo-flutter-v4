package com.ultralytics.yolo_android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import java.io.File
import java.io.IOException
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
        private const val VIDEO_FRAME_RATE = 30
        private const val VIDEO_BIT_RATE = 10_000_000 // 10 Mbps
        private const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100 MB
        private const val FILE_DATE_FORMAT = "yyyyMMdd_HHmmss"
    }
    
    // Recording state
    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null
    private var currentRecordingPath: String? = null
    private val isRecording = AtomicBoolean(false)
    private var framesEncoded: Int = 0
    
    // Video dimensions
    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    
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
            // Reset state and store dimensions
            resetRecordingState(width, height)
            
            // Determine and create output path
            val (finalPath, pathResult) = prepareOutputPath(outputPath)
            if (!pathResult.first) {
                return tryFallbackPath(width, height, pathResult.second)
            }
            
            // Initialize media recorder
            val initResult = initializeMediaRecorder(width, height, finalPath)
            if (!initResult.first) {
                return tryFallbackPath(width, height, initResult.second)
            }
            
            isRecording.set(true)
            return Pair(true, null)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            e.printStackTrace()
            return tryFallbackPath(width, height, "Exception: ${e.message}")
        }
    }
    
    /**
     * Reset the recording state for a new recording
     */
    private fun resetRecordingState(width: Int, height: Int) {
        originalWidth = width
        originalHeight = height
        framesEncoded = 0
    }
    
    /**
     * Try using a fallback path when the primary path fails
     */
    private fun tryFallbackPath(width: Int, height: Int, errorMsg: String?): Pair<Boolean, String?> {
        Log.d(TAG, "Primary path failed ($errorMsg), trying fallback path")
        
        try {
            val fallbackPath = createFallbackPath()
            currentRecordingPath = fallbackPath
            
            val fallbackResult = initializeMediaRecorder(width, height, fallbackPath)
            if (fallbackResult.first) {
                isRecording.set(true)
                return Pair(true, null)
            }
            
            releaseRecorder()
            return Pair(false, "Both primary and fallback paths failed. Original: $errorMsg, Fallback: ${fallbackResult.second}")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback also failed: ${e.message}")
            releaseRecorder()
            return Pair(false, "Failed to start recording, both paths failed: $errorMsg")
        }
    }
    
    /**
     * Prepare the output path for recording
     * @return Pair of (path, result) where result is (success, error message)
     */
    private fun prepareOutputPath(customPath: String?): Pair<String, Pair<Boolean, String?>> {
        val finalPath = customPath ?: createDefaultOutputPath()
        currentRecordingPath = finalPath
        
        Log.d(TAG, "Preparing output path: $finalPath")
        
        // Ensure the directory exists
        val outputFile = File(finalPath)
        val outputDir = outputFile.parentFile
        
        if (outputDir != null && !outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                Log.e(TAG, "Failed to create output directory: ${outputDir.absolutePath}")
                return Pair(finalPath, Pair(false, "Failed to create output directory"))
            }
        }
        
        return Pair(finalPath, Pair(true, null))
    }
    
    /**
     * Creates a fallback output path in app's cache directory
     */
    private fun createFallbackPath(): String {
        val timestamp = SimpleDateFormat(FILE_DATE_FORMAT, Locale.US).format(Date())
        val fallbackDir = File(context.cacheDir, "recordings")
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return File(fallbackDir, "recording_fallback_$timestamp.mp4").absolutePath
    }
    
    /**
     * Creates a default output path for the recording
     */
    private fun createDefaultOutputPath(): String {
        val timestamp = SimpleDateFormat(FILE_DATE_FORMAT, Locale.US).format(Date())
        val outputDir = File(context.getExternalFilesDir(null), "recordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, "recording_$timestamp.mp4").absolutePath
    }
    
    /**
     * Initialize the MediaRecorder with the appropriate settings
     * @return Pair of (success boolean, error message or null if successful)
     */
    private fun initializeMediaRecorder(width: Int, height: Int, outputPath: String): Pair<Boolean, String?> {
        releaseRecorder() // Release any existing recorder
        
        try {
            Log.d(TAG, "Creating MediaRecorder instance")
            mediaRecorder = createMediaRecorder()
            
            // Determine orientation
            val isLandscape = width > height
            val recordWidth = if (isLandscape) width else height
            val recordHeight = if (isLandscape) height else width
            
            Log.d(TAG, "Configuring recorder: ${recordWidth}x${recordHeight} (isLandscape=$isLandscape)")
            
            return configureMediaRecorder(outputPath, recordWidth, recordHeight, isLandscape)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media recorder: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            releaseRecorder()
            return Pair(false, "Error initializing media recorder: ${e.message}")
        }
    }
    
    /**
     * Create appropriate MediaRecorder instance based on API level
     */
    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }
    
    /**
     * Configure the MediaRecorder with settings
     */
    private fun configureMediaRecorder(
        outputPath: String, 
        recordWidth: Int, 
        recordHeight: Int, 
        isLandscape: Boolean
    ): Pair<Boolean, String?> {
        try {
            mediaRecorder?.apply {
                // Configure video source and format
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                
                // Configure video dimensions and quality
                setVideoSize(recordWidth, recordHeight)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                
                // Set output file
                setOutputFile(outputPath)
                
                // Set orientation if needed
                if (!isLandscape) {
                    setOrientationHint(90) // Portrait orientation
                }
                
                // Set max file size
                setMaxFileSize(MAX_FILE_SIZE)
                
                // Prepare the recorder
                return prepareAndStartRecorder(recordWidth, recordHeight, outputPath)
            }
            
            return Pair(false, "Failed to configure MediaRecorder (null instance)")
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaRecorder configuration: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return Pair(false, "Error configuring MediaRecorder: ${e.message}")
        }
    }
    
    /**
     * Prepare and start the MediaRecorder
     */
    private fun prepareAndStartRecorder(
        recordWidth: Int,
        recordHeight: Int,
        outputPath: String
    ): Pair<Boolean, String?> {
        mediaRecorder?.apply {
            try {
                prepare()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "MediaRecorder prepare failed due to illegal state: ${e.message}")
                return Pair(false, "MediaRecorder prepare failed: Illegal state - ${e.message}")
            } catch (e: IOException) {
                return handleIOException(e, outputPath, recordWidth, recordHeight)
            } catch (e: RuntimeException) {
                return handleRuntimeException(e, recordWidth, recordHeight)
            }
            
            // Get the surface to draw on AFTER preparing
            recordingSurface = surface
            
            // Start recording immediately
            start()
            
            if (recordingSurface == null) {
                Log.e(TAG, "Failed to get recording surface after prepare")
                return Pair(false, "Failed to get recording surface after prepare")
            }
            
            return Pair(true, null)
        }
        
        return Pair(false, "MediaRecorder is null")
    }
    
    /**
     * Handle IO exceptions during recorder preparation
     */
    private fun handleIOException(
        e: IOException, 
        outputPath: String,
        recordWidth: Int,
        recordHeight: Int
    ): Pair<Boolean, String?> {
        // Log file path issues
        val outputFile = File(outputPath)
        val canWrite = if (outputFile.exists()) outputFile.canWrite() else outputFile.parentFile?.canWrite() ?: false
        val parentPath = outputFile.parentFile?.absolutePath ?: "unknown"
        
        Log.e(TAG, "MediaRecorder prepare failed: IO error with output file: $outputPath")
        Log.e(TAG, "File exists: ${outputFile.exists()}, parent exists: ${outputFile.parentFile?.exists()}, can write: $canWrite")
        Log.e(TAG, "Parent directory: $parentPath")
        
        // Try with fallback path
        val fallbackPath = createFallbackPath()
        Log.d(TAG, "Attempting with fallback path: $fallbackPath")
        
        return tryReconfigureWithFallbackPath(fallbackPath, recordWidth, recordHeight, isLandscape = recordWidth > recordHeight, e)
    }
    
    /**
     * Try to reconfigure the recorder with a fallback path
     */
    private fun tryReconfigureWithFallbackPath(
        fallbackPath: String, 
        recordWidth: Int, 
        recordHeight: Int, 
        isLandscape: Boolean,
        originalException: Exception
    ): Pair<Boolean, String?> {
        try {
            mediaRecorder?.apply {
                // Reset and reconfigure with the fallback path
                reset()
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(recordWidth, recordHeight)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setOutputFile(fallbackPath)
                
                // Set orientation if needed
                if (!isLandscape) {
                    setOrientationHint(90)
                }
                
                // Set max file size
                setMaxFileSize(MAX_FILE_SIZE)
                
                // Prepare again with fallback path
                prepare()
                
                // Update the current recording path
                currentRecordingPath = fallbackPath
                
                // Get recording surface
                recordingSurface = surface
                
                // Start recording
                start()
                
                Log.d(TAG, "Successfully prepared with fallback path")
                return Pair(true, null)
            }
            return Pair(false, "MediaRecorder is null during fallback attempt")
        } catch (e: Exception) {
            Log.e(TAG, "Fallback path also failed: ${e.message}")
            return Pair(false, "Both primary and fallback paths failed. Primary: ${originalException.message}, Fallback: ${e.message}")
        }
    }
    
    /**
     * Handle runtime exceptions during recorder preparation
     */
    private fun handleRuntimeException(e: RuntimeException, recordWidth: Int, recordHeight: Int): Pair<Boolean, String?> {
        Log.e(TAG, "MediaRecorder prepare failed with runtime exception: ${e.message}")
        Log.e(TAG, "This may indicate invalid video dimensions (${recordWidth}x${recordHeight}) or unsupported encoder settings")
        
        try {
            // Log codec capabilities to help diagnose issues
            logCodecCapabilities()
            
            return Pair(false, "MediaRecorder prepare failed: ${e.message}. Possible invalid dimensions (${recordWidth}x${recordHeight}). Check logs for details.")
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to query codec capabilities: ${ex.message}")
            return Pair(false, "MediaRecorder prepare failed: ${e.message}. Could not query codec capabilities: ${ex.message}")
        }
    }
    
    /**
     * Log supported codec capabilities for debugging
     */
    private fun logCodecCapabilities() {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecs = codecList.codecInfos.filter { 
            it.supportedTypes.contains(MediaFormat.MIMETYPE_VIDEO_AVC) 
        }
        Log.d(TAG, "Available H.264 codecs: ${codecs.map { it.name }}")
        
        codecs.forEach { codec ->
            if (codec.isEncoder) {
                val caps = codec.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                val videoCapabilities = caps.videoCapabilities
                Log.d(TAG, "Codec ${codec.name} supported width: ${videoCapabilities.supportedWidths}, height: ${videoCapabilities.supportedHeights}")
            }
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
            
            mediaRecorder?.stop()
            Log.d(TAG, "Recording stopped successfully")
            
            return validateRecordedFile(savedPath)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
            e.printStackTrace()
            
            // Log fallback path for next recording
            val fallbackPath = createFallbackPath()
            Log.d(TAG, "Recording failed with exception, next recording will use fallback path: $fallbackPath")
            return null
        } finally {
            releaseRecorder()
        }
    }
    
    /**
     * Validate that the recorded file exists and has content
     */
    private fun validateRecordedFile(filePath: String?): String? {
        if (filePath == null) return null
        
        val outputFile = File(filePath)
        if (!outputFile.exists() || outputFile.length() == 0L) {
            Log.e(TAG, "Recording file is empty or does not exist: ${outputFile.absolutePath}")
            if (outputFile.exists()) {
                Log.e(TAG, "File size: ${outputFile.length()} bytes")
            }
            
            // Log fallback path for reference
            val fallbackPath = createFallbackPath()
            Log.d(TAG, "Original recording failed, next recording will use fallback path: $fallbackPath")
            return null
        }
        
        Log.d(TAG, "Recording validated: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
        return filePath
    }
    
    /**
     * Returns whether recording is currently active
     */
    fun isRecording(): Boolean = isRecording.get()
    
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
            
            if (isRecording.get()) {
                stopRecording()
            }
            
            releaseRecorder()
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
     * @param drawDetections Callback to draw detection overlays on the frame (not used)
     */
    fun encodeFrame(bitmap: Bitmap, drawDetections: ((Canvas, Int, Int) -> Unit)?) {
        val surface = recordingSurface
        if (!isRecording.get() || surface == null) return
        
        try {
            val canvas = surface.lockCanvas(null)
            try {
                drawFrameToCanvas(canvas, bitmap)
                framesEncoded++
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing to recording surface: ${e.message}")
            stopRecording()
        }
    }
    
    /**
     * Draw a frame to the canvas with proper scaling
     */
    private fun drawFrameToCanvas(canvas: Canvas, bitmap: Bitmap) {
        // Clear canvas
        canvas.drawColor(Color.BLACK)
        
        // Get dimensions
        val canvasWidth = canvas.width
        val canvasHeight = canvas.height
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        
        // Log dimensions occasionally
        if (framesEncoded == 0 || framesEncoded % 100 == 0) {
            Log.d(TAG, "Canvas: ${canvasWidth}x${canvasHeight}, Bitmap: ${bitmapWidth}x${bitmapHeight}, Frames: $framesEncoded")
        }
        
        // Apply appropriate scaling based on orientation
        scaleAndDrawBitmap(canvas, bitmap, canvasWidth, canvasHeight, bitmapWidth, bitmapHeight)
    }
    
    /**
     * Scale and draw the bitmap to fit the canvas
     */
    private fun scaleAndDrawBitmap(
        canvas: Canvas, 
        bitmap: Bitmap, 
        canvasWidth: Int, 
        canvasHeight: Int, 
        bitmapWidth: Int, 
        bitmapHeight: Int
    ) {
        val isLandscape = originalWidth > originalHeight
        
        if (isLandscape) {
            // Scale to fill width and center vertically
            val scale = canvasWidth.toFloat() / bitmapWidth.toFloat()
            val scaledHeight = bitmapHeight * scale
            val yOffset = (canvasHeight - scaledHeight) / 2
            
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, yOffset / scale, null)
        } else {
            // Scale to fill height and center horizontally
            val scale = canvasHeight.toFloat() / bitmapHeight.toFloat() 
            val scaledWidth = bitmapWidth * scale
            val xOffset = (canvasWidth - scaledWidth) / 2
            
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, xOffset / scale, 0f, null)
        }
    }
} 