package com.ultralytics.yolo_android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.ultralytics.yolo_android.renderer.CompositeRenderer
import java.util.concurrent.Executors
import kotlin.math.max

class YoloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        // Use constants from YoloConstants
    }

    // ★ 推論結果を外部へ通知するためのコールバック
    private var inferenceCallback: ((YOLOResult) -> Unit)? = null
    
    // Flag to track whether predictions are paused
    private var isPredictionPaused = false

    /** コールバックをセット */
    fun setOnInferenceCallback(callback: (YOLOResult) -> Unit) {
        this.inferenceCallback = callback
    }

    // ★ モデルロード完了を通知するためのコールバック
    private var modelLoadCallback: ((Boolean) -> Unit)? = null

    /** Modelロード完了コールバックをセット (true: 成功) */
    fun setOnModelLoadCallback(callback: (Boolean) -> Unit) {
        this.modelLoadCallback = callback
    }

    // ★ カメラ初期化完了を通知するためのコールバック
    private var cameraCreatedCallback: ((Int, Int, Int) -> Unit)? = null

    /** カメラ初期化完了コールバックをセット (width, height, facing) */
    fun setOnCameraCreatedCallback(callback: (Int, Int, Int) -> Unit) {
        this.cameraCreatedCallback = callback
        cameraManager.setOnCameraCreatedCallback(callback)
    }

    // Use a PreviewView, forcing a TextureView under the hood
    internal val previewView: PreviewView = PreviewView(context).apply {
        // Force TextureView usage so the overlay can be on top
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    // Components
    private val overlayView = OverlayView(context)
    private val cameraManager = YoloCameraManager(context)
    private val renderer = CompositeRenderer()
    
    private var inferenceResult: YOLOResult? = null
    private var predictor: Predictor? = null
    private var task: YOLOTask = YOLOTask.DETECT
    private var modelName: String = "Model"

    // detection thresholds (外部から setter で変更可能に)
    private var confidenceThreshold = 0.25f
    private var iouThreshold = 0.45f
    private var numItemsThreshold = 30

    // Filter settings
    private var allowedClasses = listOf<String>() // Empty means show all
    private var minConfidence = 0.25f

    // Flag to control whether to show detection boxes
    private var showBoxes: Boolean = true
    
    /**
     * Set custom colors for detection boxes and labels
     * @param colors List of color values in ARGB format (0xAARRGGBB)
     * @param applyAlpha Whether to apply confidence as alpha
     */
    fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        renderer.setCustomColors(colors, applyAlpha)
        overlayView.invalidate() // Redraw with new colors
    }
    
    /**
     * Reset colors to default
     */
    fun resetColors() {
        renderer.resetColors()
        overlayView.invalidate() // Redraw with default colors
    }

    /**
     * Set whether to show detection boxes in the view
     */
    fun setShowBoxes(show: Boolean) {
        this.showBoxes = show
        // Update overlay visibility immediately
        overlayView.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Set allowed classes to display (empty list means show all)
     */
    fun setAllowedClasses(classes: List<String>) {
        renderer.setAllowedClasses(classes)
        overlayView.invalidate() // Redraw with new filter
    }

    /**
     * Set minimum confidence threshold for displayed detections
     */
    fun setMinConfidence(confidence: Float) {
        renderer.setMinConfidence(confidence)
        overlayView.invalidate() // Redraw with new filter
    }

    /**
     * Set label text color
     * @param color The color in ARGB format
     */
    fun setLabelTextColor(color: Int) {
        renderer.setLabelTextColor(color)
        overlayView.invalidate() // Redraw with new text color
    }
    
    /**
     * Set label background color
     * @param color The color in ARGB format
     * @param opacity Opacity value between 0.0 and 1.0
     */
    fun setLabelBackgroundColor(color: Int, opacity: Float) {
        renderer.setLabelBackgroundColor(color, opacity)
        overlayView.invalidate() // Redraw with new background color
    }

    // Video recording component
    internal val videoRecorder = VideoRecorder(context)

    init {
        // Clear any existing children
        removeAllViews()

        // 1) A container for the camera preview
        val previewContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        // 2) Add the previewView to that container
        previewContainer.addView(previewView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))

        // 3) Add that container
        addView(previewContainer)

        // 4) Add the overlay on top
        addView(overlayView, LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ))

        // Ensure overlay is visually above the preview container
        overlayView.elevation = 100f
        overlayView.translationZ = 100f
        previewContainer.elevation = 1f
        
        // Set up frame processor
        cameraManager.setFrameProcessor { imageProxy ->
            onFrame(imageProxy)
        }

        Log.d(YoloConstants.TAG, "YoloView init: forced TextureView usage for camera preview + overlay on top.")
    }

    // region threshold setters

    fun setConfidenceThreshold(conf: Double) {
        confidenceThreshold = conf.toFloat()
        (predictor as? ObjectDetector)?.setConfidenceThreshold(conf.toFloat())
    }

    fun setIouThreshold(iou: Double) {
        iouThreshold = iou.toFloat()
        (predictor as? ObjectDetector)?.setIouThreshold(iou.toFloat())
    }

    fun setNumItemsThreshold(n: Int) {
        numItemsThreshold = n
        (predictor as? ObjectDetector)?.setNumItemsThreshold(n)
    }

    // endregion

    // region Model / Task

    fun setModel(modelPath: String, task: YOLOTask, context: Context) {
        Executors.newSingleThreadExecutor().execute {
            try {
                // Make sure the when clause is exhaustive by assigning to a variable
                val newPredictor: Predictor = when (task) {
                    YOLOTask.DETECT -> ObjectDetector(context, modelPath, loadLabels(modelPath), useGpu = true).apply {
                        setConfidenceThreshold(confidenceThreshold.toFloat())
                        setIouThreshold(iouThreshold.toFloat())
                        setNumItemsThreshold(numItemsThreshold)
                    }
                    YOLOTask.SEGMENT -> Segmenter(context, modelPath, loadLabels(modelPath), useGpu = true)
                    YOLOTask.CLASSIFY -> Classifier(context, modelPath, loadLabels(modelPath), useGpu = true)
                    YOLOTask.POSE -> PoseEstimator(context, modelPath, loadLabels(modelPath), useGpu = true)
                    YOLOTask.OBB -> ObbDetector(context, modelPath, loadLabels(modelPath), useGpu = true)
                }

                post {
                    this.task = task
                    this.predictor = newPredictor
                    this.modelName = modelPath.substringAfterLast("/")
                    modelLoadCallback?.invoke(true)
                    Log.d(YoloConstants.TAG, "Model loaded successfully: $modelPath")
                }
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Failed to load model: $modelPath", e)
                post {
                    modelLoadCallback?.invoke(false)
                }
            }
        }
    }

    private fun loadLabels(modelPath: String): List<String> {
        // Dummy label loading
        return listOf("person","bicycle","car","motorbike","aeroplane","bus","train")
    }

    // endregion

    // region camera init

    fun initCamera() {
        cameraManager.initCamera(previewView)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        cameraManager.onRequestPermissionsResult(requestCode, permissions, grantResults, previewView)
    }

    fun startCamera() {
        cameraManager.startCamera(previewView)
    }

    fun switchCamera() {
        cameraManager.switchCamera(previewView)
    }

    /**
     * Get the current camera facing
     */
    fun getCurrentFacing(): Int {
        return cameraManager.getCurrentFacing()
    }

    // endregion

    // region onFrame (per frame inference)

    private fun onFrame(imageProxy: ImageProxy) {
        val w = imageProxy.width
        val h = imageProxy.height
        // Log.d(YoloConstants.TAG, "Processing frame: ${w}x${h}")

        val bitmap = ImageUtils.toBitmap(imageProxy) ?: run {
            Log.e(YoloConstants.TAG, "Failed to convert ImageProxy to Bitmap")
            imageProxy.close()
            return
        }

        // Skip prediction if paused, but still encode video frame if recording
        if (!isPredictionPaused) {
            predictor?.let { p ->
                try {
                    // For camera feed, we typically rotate the bitmap
                    val result = p.predict(bitmap, h, w, rotateForCamera = true)
                    inferenceResult = result
                    
                    // Update overlay with new result
                    overlayView.invalidate()

                    // Log
                    Log.d(YoloConstants.TAG, "Inference complete: ${result.boxes.size} boxes detected")

                    // Callback
                    inferenceCallback?.invoke(result)
                } catch (e: Exception) {
                    Log.e(YoloConstants.TAG, "Error during prediction", e)
                }
            }
        }
        
        // Add frame to video recording if active
        val isCurrentlyRecording = videoRecorder.isRecording()
        if (isCurrentlyRecording) {
            videoRecorder.encodeFrame(bitmap) { canvas, width, height ->
                // Draw detections on the bitmap for recording
                inferenceResult?.let { result ->
                    renderer.draw(canvas, result, width, height)
                }
            }
        }
        
        imageProxy.close()
    }

    // endregion

    // region OverlayView

    private inner class OverlayView(context: Context) : View(context) {
        private val paint = Paint().apply { isAntiAlias = true }

        init {
            // Make background transparent
            setBackgroundColor(Color.TRANSPARENT)
            // Use hardware layer for better z-order 
            setLayerType(LAYER_TYPE_HARDWARE, null)

            // Raise overlay
            elevation = 1000f
            translationZ = 1000f

            setWillNotDraw(false)

            // Log.d(YoloConstants.TAG, "OverlayView initialized with enhanced Z-order + hardware acceleration")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            // If no result or not showing boxes, don't draw anything
            if (!showBoxes || inferenceResult == null) return
            
            // Use the renderer to draw the results
            inferenceResult?.let { result ->
                renderer.draw(canvas, result, width, height)
            }
        }
    }

    // endregion

    /**
     * Captures the current camera frame with detection boxes as a JPEG byte array
     * 
     * @return ByteArray containing the JPEG-encoded image
     */
    fun takePictureAsBytes(): ByteArray {
        // Get the current frame from the PreviewView
        val previewBitmap = previewView.bitmap
        if (previewBitmap == null) {
            Log.e(YoloConstants.TAG, "Failed to get bitmap from PreviewView")
            // Return an empty array if we can't capture the preview
            return ByteArray(0)
        }
        
        // Create a mutable copy of the bitmap so we can draw on it
        val resultBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        
        // Draw the detection overlays onto the bitmap if showing boxes
        if (showBoxes) {
            inferenceResult?.let { result ->
                renderer.draw(canvas, result, resultBitmap.width, resultBitmap.height)
            }
        }
        
        // Compress the bitmap to JPEG format
        val outputStream = java.io.ByteArrayOutputStream()
        resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        
        // Clean up resources
        resultBitmap.recycle()
        previewBitmap.recycle()
        
        // Return the JPEG byte array
        return outputStream.toByteArray()
    }
    
    /**
     * Starts recording video of the camera feed with detection overlay
     * 
     * @param outputPath Path where the video should be saved. If null, a default path will be used.
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(outputPath: String?): Boolean {
        // Get viewport dimensions
        val width = previewView.width
        val height = previewView.height
        
        return videoRecorder.startRecording(width, height, outputPath)
    }
    
    /**
     * Stops the current video recording
     * 
     * @return Path to the saved video file or null if recording failed or file doesn't exist
     */
    fun stopRecording(): String? {
        try {
            val recordingPath = videoRecorder.stopRecording()
            Log.d(YoloConstants.TAG, "Video recording stopped, path: $recordingPath")
            
            // Verify the file actually exists
            if (recordingPath != null) {
                val videoFile = java.io.File(recordingPath)
                if (videoFile.exists() && videoFile.length() > 0) {
                    Log.d(YoloConstants.TAG, "Verified video file exists at path: $recordingPath with size: ${videoFile.length()} bytes")
                    return recordingPath
                } else {
                    Log.e(YoloConstants.TAG, "Video file doesn't exist or is empty at path: $recordingPath")
                    return null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(YoloConstants.TAG, "Error stopping video recording", e)
            return null
        }
    }

    /**
     * Releases the predictor resources
     */
    fun releasePredictor() {
        try {
            val predictorRef = predictor
            if (predictorRef != null) {
                try {
                    predictorRef.close()
                    Log.d(YoloConstants.TAG, "Predictor resources released")
                } catch (e: Exception) {
                    Log.e(YoloConstants.TAG, "Error closing predictor", e)
                }
                predictor = null
            }
        } catch (e: Exception) {
            Log.e(YoloConstants.TAG, "Error releasing predictor resources", e)
        }
    }

    /**
     * Disposes all resources used by the YoloView
     * This should be called when the view is no longer needed
     */
    fun dispose() {
        Log.d(YoloConstants.TAG, "Disposing YoloView resources")
        
        try {
            // Stop any active recording
            try {
                if (videoRecorder.isRecording()) {
                    videoRecorder.stopRecording()
                }
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Error stopping recording during dispose", e)
            }
            
            // Dispose VideoRecorder resources
            try {
                videoRecorder.dispose()
                Log.d(YoloConstants.TAG, "VideoRecorder resources disposed")
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Error disposing VideoRecorder", e)
            }
            
            // Clean up predictor resources
            try {
                releasePredictor()
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Error releasing predictor during dispose", e)
            }
            
            // Release camera resources
            try {
                cameraManager.releaseCamera()
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Error releasing camera during dispose", e)
            }
            
            // Remove callbacks
            inferenceCallback = null
            modelLoadCallback = null
            cameraCreatedCallback = null
            
            // Clear view
            try {
                removeAllViews()
            } catch (e: Exception) {
                Log.e(YoloConstants.TAG, "Error clearing views during dispose", e)
            }
            
            Log.d(YoloConstants.TAG, "YoloView resources disposed")
        } catch (e: Exception) {
            Log.e(YoloConstants.TAG, "Error during YoloView dispose", e)
        }
    }

    /**
     * Pauses or resumes live predictions. When paused, the camera feed continues
     * but no inference is performed, saving CPU/GPU resources.
     * 
     * @param pause True to pause predictions, false to resume
     * @return The new pause state
     */
    fun pauseLivePrediction(pause: Boolean): Boolean {
        isPredictionPaused = pause
        Log.d(YoloConstants.TAG, "Live predictions ${if (pause) "paused" else "resumed"}")
        return isPredictionPaused
    }
    
    /**
     * Toggles the prediction pause state
     * 
     * @return The new pause state (true if now paused)
     */
    fun togglePredictionPause(): Boolean {
        return pauseLivePrediction(!isPredictionPaused)
    }
    
    /**
     * Returns whether predictions are currently paused
     */
    fun isPredictionPaused(): Boolean {
        return isPredictionPaused
    }
}