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
    // Error callback interface
    interface ErrorCallback {
        fun onError(errorCode: Int, errorMessage: String)
    }
    
    companion object {
        private const val TAG = "VideoRecorder"
        
        const val ERROR_ALREADY_RECORDING = 1001
        const val ERROR_DIRECTORY_CREATE = 1002
        const val ERROR_FILE_ACCESS = 1003
        const val ERROR_MEDIA_RECORDER_INIT = 1004
        const val ERROR_MEDIA_RECORDER_PREPARE = 1005
        const val ERROR_MEDIA_RECORDER_START = 1006
        const val ERROR_MEDIA_RECORDER_ENCODE = 1007
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
    
    // Error callback reference
    private var errorCallback: ErrorCallback? = null
    
    /**
     * Sets the error callback to communicate errors to Flutter
     */
    fun setErrorCallback(callback: ErrorCallback) {
        this.errorCallback = callback
    }
    
    /**
     * Helper method to report errors both to logcat and through the callback
     * Returns a formatted error message that includes the error code
     */
    private fun reportError(errorCode: Int, errorMessage: String): String {
        val formattedMessage = "[$errorCode] $errorMessage"
        Log.e(TAG, formattedMessage)
        errorCallback?.onError(errorCode, errorMessage)
        return formattedMessage
    }
    
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
            val errorMsg = "Recording already in progress"
            val formattedError = reportError(ERROR_ALREADY_RECORDING, errorMsg)
            return Pair(false, formattedError)
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
                    val errorMsg = "Failed to create output directory: ${outputDir.absolutePath}"
                    val formattedError = reportError(ERROR_DIRECTORY_CREATE, errorMsg)
                    return Pair(false, formattedError)
                }
            }
            
            // Check file access permissions before proceeding
            try {
                // Verify directory is writable
                if (outputDir != null && !outputDir.canWrite()) {
                    val errorMsg = "Cannot write to output directory: ${outputDir.absolutePath}"
                    val formattedError = reportError(ERROR_FILE_ACCESS, errorMsg)
                    return Pair(false, formattedError)
                }
                
                // Check if file exists and can be overwritten
                if (outputFile.exists()) {
                    if (!outputFile.canWrite()) {
                        val errorMsg = "Cannot overwrite existing file: $finalOutputPath"
                        val formattedError = reportError(ERROR_FILE_ACCESS, errorMsg)
                        return Pair(false, formattedError)
                    }
                    // Delete existing file to ensure clean recording
                    if (!outputFile.delete()) {
                        val errorMsg = "Failed to delete existing file: $finalOutputPath"
                        val formattedError = reportError(ERROR_FILE_ACCESS, errorMsg)
                        return Pair(false, formattedError)
                    }
                }
                
                // Try to create an empty file to verify write permissions
                if (!outputFile.createNewFile()) {
                    val errorMsg = "Failed to create output file: $finalOutputPath"
                    val formattedError = reportError(ERROR_FILE_ACCESS, errorMsg)
                    return Pair(false, formattedError)
                }
            } catch (e: Exception) {
                val errorMsg = "File access error: ${e.message}"
                val formattedError = reportError(ERROR_FILE_ACCESS, errorMsg)
                return Pair(false, formattedError)
            }
            
            // Initialize media recorder
            val initResult = initializeMediaRecorder(width, height, finalOutputPath)
            if (!initResult.first) {
                return initResult
            }
            
            isRecording.set(true)
            return Pair(true, null)
            
        } catch (e: Exception) {
            val errorMsg = "Failed to start recording: ${e.message}"
            val formattedError = reportError(ERROR_MEDIA_RECORDER_INIT, errorMsg)
            e.printStackTrace()
            releaseRecorder()
            return Pair(false, formattedError)
        }
    }
    
    /**
     * Creates  a default output path for the recording
     */
    private fun createDefaultOutputPath(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.getExternalFilesDir(null), "recordings")
        if (!outputDir.exists()) {
            val created = outputDir.mkdirs()
            if (!created) {
                Log.e(TAG, "Failed to create default recording directory: ${outputDir.absolutePath}")
                // Fall back to using the root external files directory if we can't create the subfolder
                val fallbackDir = context.getExternalFilesDir(null)
                Log.w(TAG, "Using fallback directory: ${fallbackDir?.absolutePath}")
                return File(fallbackDir, "yolo_recording_$timestamp.mp4").absolutePath
            }
        }
        
        // Check if directory is writable 
        if (!outputDir.canWrite()) {
            Log.e(TAG, "Default recording directory is not writable: ${outputDir.absolutePath}")
            // Fall back to cache directory which should always be writable
            val fallbackDir = context.cacheDir
            Log.w(TAG, "Using fallback cache directory: ${fallbackDir.absolutePath}")
            return File(fallbackDir, "yolo_recording_$timestamp.mp4").absolutePath
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
                
                try {
                    // Prepare the recorder
                    Log.d(TAG, "Preparing MediaRecorder...")
                    prepare()
                    Log.d(TAG, "MediaRecorder prepare successful")
                } catch (e: IllegalStateException) {
                    val errorMsg = "MediaRecorder in illegal state: ${e.message}"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_PREPARE, errorMsg)
                    e.printStackTrace()
                    return Pair(false, formattedError)
                } catch (e: java.io.IOException) {
                    // Check if this is a file access issue
                    val outputFile = File(outputPath)
                    val errorMsg = when {
                        !outputFile.parentFile?.exists()!! -> "Directory doesn't exist: ${outputFile.parentFile?.absolutePath}"
                        !outputFile.parentFile?.canWrite()!! -> "Cannot write to directory: ${outputFile.parentFile?.absolutePath}"
                        outputFile.exists() && !outputFile.canWrite() -> "Cannot write to existing file: $outputPath"
                        else -> "File access issue: ${e.message}"
                    }
                    val prepareError = "MediaRecorder prepare failed: $errorMsg"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_PREPARE, prepareError)
                    e.printStackTrace()
                    return Pair(false, formattedError)
                } catch (e: Exception) {
                    val errorMsg = "MediaRecorder prepare failed with unexpected error: ${e.message}"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_PREPARE, errorMsg)
                    e.printStackTrace()
                    return Pair(false, formattedError)
                }
                
                // Get the surface to draw on AFTER preparing
                recordingSurface = surface
                
                if (recordingSurface == null) {
                    val errorMsg = "Failed to get recording surface after prepare"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_INIT, errorMsg)
                    return Pair(false, formattedError)
                }
                
                try {
                    Log.d(TAG, "Starting MediaRecorder...")
                    // Start recording immediately
                    start()
                    Log.d(TAG, "MediaRecorder start successful")
                } catch (e: IllegalStateException) {
                    val errorMsg = "MediaRecorder start error: ${e.message}"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_START, errorMsg)
                    e.printStackTrace()
                    return Pair(false, formattedError)
                } catch (e: Exception) {
                    val errorMsg = "MediaRecorder start error: ${e.message}"
                    val formattedError = reportError(ERROR_MEDIA_RECORDER_START, errorMsg)
                    e.printStackTrace()
                    return Pair(false, formattedError)
                }
            }
            
            return Pair(true, null)
        } catch (e: Exception) {
            val errorMsg = "Error initializing media recorder: ${e.message}"
            val formattedError = reportError(ERROR_MEDIA_RECORDER_INIT, errorMsg)
            e.printStackTrace()
            releaseRecorder()
            return Pair(false, formattedError)
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
            val errorMsg = "Error drawing to recording surface: ${e.message}"
            val formattedError = reportError(ERROR_MEDIA_RECORDER_ENCODE, errorMsg)
            Log.e(TAG, formattedError)
            stopRecording()
        }
    }
} 