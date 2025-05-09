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
import java.util.concurrent.Executors
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YoloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        private const val TAG = "YoloView"

        // 線の太さ・角丸半径
        private const val BOX_LINE_WIDTH = 8f
        private const val BOX_CORNER_RADIUS = 12f
        private const val KEYPOINT_LINE_WIDTH = 6f

        // Ultralytics由来の色
        private val ultralyticsColors = arrayOf(
            Color.argb(153, 4,   42,  255),
            Color.argb(153, 11,  219, 235),
            Color.argb(153, 243, 243, 243),
            Color.argb(153, 0,   223, 183),
            Color.argb(153, 17,  31,  104),
            Color.argb(153, 255, 111, 221),
            Color.argb(153, 255, 68,  79),
            Color.argb(153, 204, 237, 0),
            Color.argb(153, 0,   243, 68),
            Color.argb(153, 189, 0,   255),
            Color.argb(153, 0,   180, 255),
            Color.argb(153, 221, 0,   186),
            Color.argb(153, 0,   255, 255),
            Color.argb(153, 38,  192, 0),
            Color.argb(153, 1,   255, 179),
            Color.argb(153, 125, 36,  255),
            Color.argb(153, 123, 0,   104),
            Color.argb(153, 255, 27,  108),
            Color.argb(153, 252, 109, 47),
            Color.argb(153, 162, 255, 11)
        )

        // Pose
        private val posePalette = arrayOf(
            floatArrayOf(255f, 128f,  0f),
            floatArrayOf(255f, 153f,  51f),
            floatArrayOf(255f, 178f, 102f),
            floatArrayOf(230f, 230f,   0f),
            floatArrayOf(255f, 153f, 255f),
            floatArrayOf(153f, 204f, 255f),
            floatArrayOf(255f, 102f, 255f),
            floatArrayOf(255f,  51f, 255f),
            floatArrayOf(102f, 178f, 255f),
            floatArrayOf( 51f, 153f, 255f),
            floatArrayOf(255f, 153f, 153f),
            floatArrayOf(255f, 102f, 102f),
            floatArrayOf(255f,  51f,  51f),
            floatArrayOf(153f, 255f, 153f),
            floatArrayOf(102f, 255f, 102f),
            floatArrayOf( 51f, 255f,  51f),
            floatArrayOf(  0f, 255f,   0f),
            floatArrayOf(  0f,   0f, 255f),
            floatArrayOf(255f,   0f,   0f),
            floatArrayOf(255f, 255f, 255f),
        )

        private val kptColorIndices = intArrayOf(
            16,16,16,16,16,
            9, 9, 9, 9, 9, 9,
            0, 0, 0, 0, 0, 0
        )

        private val limbColorIndices = intArrayOf(
            0, 0, 0, 0,
            7, 7, 7,
            9, 9, 9, 9, 9,
            16,16,16,16,16,16,16
        )

        private val skeleton = arrayOf(
            intArrayOf(16, 14),
            intArrayOf(14, 12),
            intArrayOf(17, 15),
            intArrayOf(15, 13),
            intArrayOf(12, 13),
            intArrayOf(6, 12),
            intArrayOf(7, 13),
            intArrayOf(6, 7),
            intArrayOf(6, 8),
            intArrayOf(7, 9),
            intArrayOf(8, 10),
            intArrayOf(9, 11),
            intArrayOf(2, 3),
            intArrayOf(1, 2),
            intArrayOf(1, 3),
            intArrayOf(2, 4),
            intArrayOf(3, 5),
            intArrayOf(4, 6),
            intArrayOf(5, 7)
        )
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
    }

    // Use a PreviewView, forcing a TextureView under the hood
    internal val previewView: PreviewView = PreviewView(context).apply {
        // Force TextureView usage so the overlay can be on top
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    // The overlay for bounding boxes
    private val overlayView: OverlayView = OverlayView(context)

    private var inferenceResult: YOLOResult? = null
    private var predictor: Predictor? = null
    private var task: YOLOTask = YOLOTask.DETECT
    private var modelName: String = "Model"

    // Camera config
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    // detection thresholds (外部から setter で変更可能に)
    private var confidenceThreshold = 0.25f
    private var iouThreshold = 0.45f
    private var numItemsThreshold = 30

    // Filter settings
    private var allowedClasses = listOf<String>() // Empty means show all
    private var minConfidence = 0.25f

    // Flag to control whether to show detection boxes
    private var showBoxes: Boolean = true
    
    // Custom colors for detection boxes
    private var customColors: List<Int>? = null
    private var applyConfidenceAlpha: Boolean = true
    
    // Label colors
    private var labelTextColor: Int? = null
    private var labelBackgroundColor: Int? = null
    private var labelBackgroundOpacity: Float = 0.7f
    
    /**
     * Set custom colors for detection boxes and labels
     * @param colors List of color values in ARGB format (0xAARRGGBB)
     * @param applyAlpha Whether to apply confidence as alpha
     */
    fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        this.customColors = colors
        this.applyConfidenceAlpha = applyAlpha
        overlayView.invalidate() // Redraw with new colors
    }
    
    /**
     * Reset colors to default
     */
    fun resetColors() {
        this.customColors = null
        this.labelTextColor = null
        this.labelBackgroundColor = null
        this.labelBackgroundOpacity = 0.7f
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
        this.allowedClasses = classes
        overlayView.invalidate() // Redraw with new filter
    }

    /**
     * Set minimum confidence threshold for displayed detections
     */
    fun setMinConfidence(confidence: Float) {
        this.minConfidence = confidence
        overlayView.invalidate() // Redraw with new filter
    }

    /**
     * Set label text color
     * @param color The color in ARGB format
     */
    fun setLabelTextColor(color: Int) {
        this.labelTextColor = color
        overlayView.invalidate() // Redraw with new text color
    }
    
    /**
     * Set label background color
     * @param color The color in ARGB format
     * @param opacity Opacity value between 0.0 and 1.0
     */
    fun setLabelBackgroundColor(color: Int, opacity: Float) {
        this.labelBackgroundColor = color
        this.labelBackgroundOpacity = opacity.coerceIn(0f, 1f)
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

        Log.d(TAG, "YoloView init: forced TextureView usage for camera preview + overlay on top.")
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
                    Log.d(TAG, "Model loaded successfully: $modelPath")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: $modelPath", e)
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
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            val activity = context as? Activity ?: return
            ActivityCompat.requestPermissions(
                activity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(context, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startCamera() {
        Log.d(TAG, "Starting camera...")

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    Log.d(TAG, "Camera provider obtained")

                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()

                    val executor = Executors.newSingleThreadExecutor()
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        onFrame(imageProxy)
                    }

                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    Log.d(TAG, "Unbinding all camera use cases")
                    cameraProvider.unbindAll()

                    try {
                        val lifecycleOwner = context as? LifecycleOwner
                        if (lifecycleOwner == null) {
                            Log.e(TAG, "Context is not a LifecycleOwner")
                            return@addListener
                        }

                        Log.d(TAG, "Binding camera use cases to lifecycle")
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )

                        Log.d(TAG, "Setting surface provider to previewView")
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        Log.d(TAG, "Camera setup completed successfully")

                        // Add a small delay to ensure the preview view has been measured
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val width = previewView.width
                            val height = previewView.height
                            Log.d(TAG, "⭐️ About to send camera created callback: ${width}x${height}, facing: $lensFacing")
                            cameraCreatedCallback?.invoke(width, height, lensFacing)
                            Log.d(TAG, "⭐️ Camera created callback invoked with dimensions: ${width}x${height}")
                        }, 300) // Small delay to ensure the view is measured
                    } catch (e: Exception) {
                        Log.e(TAG, "Use case binding failed", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting camera provider", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "Error starting camera", e)
        }
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    /**
     * Get the current camera facing
     */
    fun getCurrentFacing(): Int {
        return lensFacing
    }

    // endregion

    // region onFrame (per frame inference)

    private fun onFrame(imageProxy: ImageProxy) {
        val w = imageProxy.width
        val h = imageProxy.height
        // Log.d(TAG, "Processing frame: ${w}x${h}")

        val bitmap = ImageUtils.toBitmap(imageProxy) ?: run {
            Log.e(TAG, "Failed to convert ImageProxy to Bitmap")
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

                    // Log
                    // Log.d(TAG, "Inference complete: ${result.boxes.size} boxes detected")

                    // Callback
                    inferenceCallback?.invoke(result)

                    // Update overlay
                    post {
                        overlayView.invalidate()
                        // Log.d(TAG, "Overlay invalidated for redraw")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during prediction", e)
                }
            }
        }
        
        // Add frame to video recording if active
        // Use explicit block structure and intermediate variable to avoid Kotlin treating this as an expression
        val isCurrentlyRecording = videoRecorder.isRecording()
        if (isCurrentlyRecording) {
            videoRecorder.encodeFrame(bitmap) { canvas, width, height ->
                drawDetectionsOnBitmap(canvas, width, height)
            }
        } else {
            // Empty else branch to explicitly show this is not an expression
        }
        
        imageProxy.close()
    }
    
    /**
     * Draws detection results onto a bitmap canvas
     */
    private fun drawDetectionsOnBitmap(canvas: Canvas, width: Int, height: Int) {
        val result = inferenceResult ?: return
        val paint = Paint().apply { isAntiAlias = true }
        
        // Calculate scaling factors
        val iw = result.origShape.width.toFloat()
        val ih = result.origShape.height.toFloat()
        val vw = width.toFloat()
        val vh = height.toFloat()
        
        val scaleX = vw / iw
        val scaleY = vh / ih
        val scale = max(scaleX, scaleY)
        
        val scaledW = iw * scale
        val scaledH = ih * scale
        
        val dx = (vw - scaledW) / 2f
        val dy = (vh - scaledH) / 2f
        
        // Draw detection boxes and labels (simplified from OverlayView.onDraw)
        for (box in result.boxes) {
            // Apply filtering
            if (box.conf < minConfidence) continue
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue
            
            val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
            val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                customColors!![box.index % customColors!!.size]
            } else {
                ultralyticsColors[box.index % ultralyticsColors.size]
            }
            
            val newColor = if (applyConfidenceAlpha) {
                Color.argb(
                    alpha,
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
            } else {
                baseColor
            }
            
            // Draw bounding box
            val left = box.xywh.left * scale + dx
            val top = box.xywh.top * scale + dy
            val right = box.xywh.right * scale + dx
            val bottom = box.xywh.bottom * scale + dy
            
            paint.color = newColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = BOX_LINE_WIDTH
            canvas.drawRoundRect(
                left, top, right, bottom,
                BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                paint
            )
            
            // Draw label
            val labelText = "${box.cls} ${"%.1f".format(box.conf * 100)}%"
            paint.textSize = 40f
            val fm = paint.fontMetrics
            val textWidth = paint.measureText(labelText)
            val textHeight = fm.bottom - fm.top
            val pad = 8f
            
            val labelBoxHeight = textHeight + 2 * pad
            val labelBottom = top
            val labelTop = labelBottom - labelBoxHeight
            val labelLeft = left
            val labelRight = left + textWidth + 2 * pad
            
            paint.style = Paint.Style.FILL
            paint.color = if (labelBackgroundColor != null) {
                val alpha = (labelBackgroundOpacity * 255).toInt().coerceIn(0, 255)
                Color.argb(
                    alpha,
                    Color.red(labelBackgroundColor!!),
                    Color.green(labelBackgroundColor!!),
                    Color.blue(labelBackgroundColor!!)
                )
            } else {
                newColor
            }
            
            canvas.drawRoundRect(
                RectF(labelLeft, labelTop, labelRight, labelBottom),
                BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                paint
            )
            
            paint.color = labelTextColor ?: Color.WHITE
            val centerY = (labelTop + labelBottom) / 2
            val baseline = centerY - (fm.descent + fm.ascent) / 2
            canvas.drawText(labelText, labelLeft + pad, baseline, paint)
        }
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

            // Log.d(TAG, "OverlayView initialized with enhanced Z-order + hardware acceleration")
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val result = inferenceResult ?: return
            
            // Log.d(TAG, "OverlayView onDraw: Drawing result with ${result.boxes.size} boxes")

            val iw = result.origShape.width.toFloat()
            val ih = result.origShape.height.toFloat()

            val vw = width.toFloat()
            val vh = height.toFloat()
            
            // Log.d(TAG, "OverlayView dimensions: View(${vw}x${vh}), Image(${iw}x${ih})")

            // カメラ画像 → View への拡大倍率
            val scaleX = vw / iw
            val scaleY = vh / ih
            val scale = max(scaleX, scaleY)

            val scaledW = iw * scale
            val scaledH = ih * scale

            val dx = (vw - scaledW) / 2f
            val dy = (vh - scaledH) / 2f
            
            // Log.d(TAG, "OverlayView scaling: scale=${scale}, dx=${dx}, dy=${dy}")

            // Make the 'when' expression exhaustive
            when (task) {
                // ----------------------------------------
                // DETECT
                // ----------------------------------------
                YOLOTask.DETECT -> {
                    // Log.d(TAG, "Drawing DETECT boxes: ${result.boxes.size}")
                    Log.d(TAG, "Allowed classes: $allowedClasses")
                    Log.d(TAG, "Min confidence: $minConfidence")
                    for (box in result.boxes) {
                        // Apply filtering - skip this detection if it doesn't meet criteria
                        if (box.conf < minConfidence) continue
                        if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue

                        // confidence に応じてアルファ調整
                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        
                        // Use custom colors if available, otherwise use default colors
                        val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                            // Use modulo to cycle through the custom colors
                            customColors!![box.index % customColors!!.size]
                        } else {
                            ultralyticsColors[box.index % ultralyticsColors.size]
                        }
                        
                        // Apply alpha based on confidence if applyConfidenceAlpha is true
                        val newColor = if (applyConfidenceAlpha) {
                            Color.argb(
                                alpha,
                                Color.red(baseColor),
                                Color.green(baseColor),
                                Color.blue(baseColor)
                            )
                        } else {
                            baseColor
                        }

                        // 元のbox.xywhの値をログ出力
                        // Log.d(TAG, "Box raw coords: L=${box.xywh.left}, T=${box.xywh.top}, R=${box.xywh.right}, B=${box.xywh.bottom}, cls=${box.cls}, conf=${box.conf}")
                        
                        // 元のコードのようにバウンディングボックス描画
                        val left   = box.xywh.left   * scale + dx
                        val top    = box.xywh.top    * scale + dy
                        val right  = box.xywh.right  * scale + dx
                        val bottom = box.xywh.bottom * scale + dy
                        
                        // Log.d(TAG, "Drawing box for ${box.cls}: L=$left, T=$top, R=$right, B=$bottom, conf=${box.conf}")

                        paint.color = newColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = BOX_LINE_WIDTH
                        canvas.drawRoundRect(
                            left, top, right, bottom,
                            BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                            paint
                        )

                        // ラベルテキスト
                        val labelText = "${box.cls} ${"%.1f".format(box.conf * 100)}%"
                        paint.textSize = 40f
                        val fm = paint.fontMetrics
                        val textWidth = paint.measureText(labelText)
                        val textHeight = fm.bottom - fm.top
                        val pad = 8f

                        // ラベル背景の高さは (テキスト高さ + 2*padding)
                        val labelBoxHeight = textHeight + 2 * pad
                        // ボックス上辺の外側に重ねるイメージ
                        val labelBottom = top
                        val labelTop = labelBottom - labelBoxHeight

                        // ラベル背景用の矩形
                        val labelLeft = left
                        val labelRight = left + textWidth + 2 * pad
                        val bgRect = RectF(labelLeft, labelTop, labelRight, labelBottom)

                        // 背景を描画
                        paint.style = Paint.Style.FILL
                        // Use custom label background color if set, otherwise use detection box color
                        paint.color = if (labelBackgroundColor != null) {
                            // Apply the configured opacity
                            val alpha = (labelBackgroundOpacity * 255).toInt().coerceIn(0, 255)
                            Color.argb(
                                alpha,
                                Color.red(labelBackgroundColor!!),
                                Color.green(labelBackgroundColor!!),
                                Color.blue(labelBackgroundColor!!)
                            )
                        } else {
                            newColor
                        }
                        canvas.drawRoundRect(bgRect, BOX_CORNER_RADIUS, BOX_CORNER_RADIUS, paint)

                        // テキストを矩形内で縦中央に合わせる
                        // Use custom label text color if set, otherwise use white
                        paint.color = labelTextColor ?: Color.WHITE
                        val centerY = (labelTop + labelBottom) / 2
                        // ベースライン = centerY - (fm.descent + fm.ascent)/2
                        val baseline = centerY - (fm.descent + fm.ascent) / 2
                        // X座標は left寄り+pad
                        val textX = labelLeft + pad

                        canvas.drawText(labelText, textX, baseline, paint)
                    }
                }
                // ----------------------------------------
                // SEGMENT
                // ----------------------------------------
                YOLOTask.SEGMENT -> {
                    // バウンディングボックス & ラベル
                    for (box in result.boxes) {
                        // Apply filtering - skip this detection if it doesn't meet criteria
                        if (box.conf < minConfidence) continue
                        if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue

                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        
                        // Use custom colors if available, otherwise use default colors
                        val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                            // Use modulo to cycle through the custom colors
                            customColors!![box.index % customColors!!.size]
                        } else {
                            ultralyticsColors[box.index % ultralyticsColors.size]
                        }
                        
                        // Apply alpha based on confidence if applyConfidenceAlpha is true
                        val newColor = if (applyConfidenceAlpha) {
                            Color.argb(
                                alpha,
                                Color.red(baseColor),
                                Color.green(baseColor),
                                Color.blue(baseColor)
                            )
                        } else {
                            baseColor
                        }

                        // バウンディングボックス描画
                        val left   = box.xywh.left   * scale + dx
                        val top    = box.xywh.top    * scale + dy
                        val right  = box.xywh.right  * scale + dx
                        val bottom = box.xywh.bottom * scale + dy

                        paint.color = newColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = BOX_LINE_WIDTH
                        canvas.drawRoundRect(
                            left, top, right, bottom,
                            BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                            paint
                        )

                        // ラベル背景 + テキスト (縦中央に配置)
                        val labelText = "${box.cls} ${"%.1f".format(box.conf * 100)}%"
                        paint.textSize = 40f
                        val fm = paint.fontMetrics
                        val textWidth = paint.measureText(labelText)
                        val textHeight = fm.bottom - fm.top
                        val pad = 8f

                        val labelBoxHeight = textHeight + 2 * pad
                        val labelBottom = top
                        val labelTop = labelBottom - labelBoxHeight
                        val labelLeft = left
                        val labelRight = left + textWidth + 2 * pad
                        val bgRect = RectF(labelLeft, labelTop, labelRight, labelBottom)

                        paint.style = Paint.Style.FILL
                        paint.color = if (labelBackgroundColor != null) {
                            // Apply the configured opacity
                            val alpha = (labelBackgroundOpacity * 255).toInt().coerceIn(0, 255)
                            Color.argb(
                                alpha,
                                Color.red(labelBackgroundColor!!),
                                Color.green(labelBackgroundColor!!),
                                Color.blue(labelBackgroundColor!!)
                            )
                        } else {
                            newColor
                        }
                        
                        canvas.drawRoundRect(bgRect, BOX_CORNER_RADIUS, BOX_CORNER_RADIUS, paint)

                        paint.color = labelTextColor ?: Color.WHITE
                        val centerY = (labelTop + labelBottom) / 2
                        val baseline = centerY - (fm.descent + fm.ascent) / 2
                        canvas.drawText(labelText, labelLeft + pad, baseline, paint)
                    }

                    // セグメンテーションマスク
                    result.masks?.combinedMask?.let { maskBitmap ->
                        val src = Rect(0, 0, maskBitmap.width, maskBitmap.height)
                        val dst = RectF(dx, dy, dx + scaledW, dy + scaledH)
                        val maskPaint = Paint().apply { alpha = 128 }
                        canvas.drawBitmap(maskBitmap, src, dst, maskPaint)
                    }
                }
                // ----------------------------------------
                // CLASSIFY (中央に大きく表示)
                // ----------------------------------------
                YOLOTask.CLASSIFY -> {
                    result.probs?.let { probs ->
                        // Skip if doesn't meet confidence threshold - use run block to allow early exit
                        run {
                            // Skip if doesn't meet confidence threshold
                            if (probs.top1Conf < minConfidence) return@run
                            // Skip if not in allowed classes list
                            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(probs.top1)) return@run

                            val alpha = (probs.top1Conf * 255).toInt().coerceIn(0, 255)
                            
                            // Use custom colors if available, otherwise use default colors
                            val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                                // Use modulo to cycle through the custom colors
                                customColors!![probs.top1Index % customColors!!.size]
                            } else {
                                ultralyticsColors[probs.top1Index % ultralyticsColors.size]
                            }
                            
                            val newColor = Color.argb(
                                alpha,
                                Color.red(baseColor),
                                Color.green(baseColor),
                                Color.blue(baseColor)
                            )

                            val labelText = "${probs.top1} ${"%.1f".format(probs.top1Conf * 100)}%"
                            paint.textSize = 60f
                            val textWidth = paint.measureText(labelText)
                            val fm = paint.fontMetrics
                            val textHeight = fm.bottom - fm.top
                            val pad = 16f

                            // 画面中央
                            val centerX = vw / 2f
                            val centerY = vh / 2f

                            val bgLeft   = centerX - (textWidth / 2) - pad
                            val bgTop    = centerY - (textHeight / 2) - pad
                            val bgRight  = centerX + (textWidth / 2) + pad
                            val bgBottom = centerY + (textHeight / 2) + pad

                            paint.style = Paint.Style.FILL
                            val bgRect = RectF(bgLeft, bgTop, bgRight, bgBottom)
                            
                            // Use custom label background color if set, otherwise use detection box color
                            paint.color = if (labelBackgroundColor != null) {
                                // Apply the configured opacity
                                val alpha = (labelBackgroundOpacity * 255).toInt().coerceIn(0, 255)
                                Color.argb(
                                    alpha,
                                    Color.red(labelBackgroundColor!!),
                                    Color.green(labelBackgroundColor!!),
                                    Color.blue(labelBackgroundColor!!)
                                )
                            } else {
                                newColor
                            }
                            canvas.drawRoundRect(bgRect, 20f, 20f, paint)

                            paint.color = labelTextColor ?: Color.WHITE
                            val baseline = centerY - (fm.descent + fm.ascent)/2
                            canvas.drawText(labelText, centerX - (textWidth / 2), baseline, paint)
                        }
                    }
                }
                // ----------------------------------------
                // POSE
                // ----------------------------------------
                YOLOTask.POSE -> {
                    // バウンディングボックス
                    for (box in result.boxes) {
                        // Apply filtering - skip this detection if it doesn't meet criteria
                        if (box.conf < minConfidence) continue
                        if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue

                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        
                        // Use custom colors if available, otherwise use default colors
                        val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                            // Use modulo to cycle through the custom colors
                            customColors!![box.index % customColors!!.size]
                        } else {
                            ultralyticsColors[box.index % ultralyticsColors.size]
                        }
                        
                        val newColor = Color.argb(
                            alpha,
                            Color.red(baseColor),
                            Color.green(baseColor),
                            Color.blue(baseColor)
                        )

                        val left   = box.xywh.left   * scale + dx
                        val top    = box.xywh.top    * scale + dy
                        val right  = box.xywh.right  * scale + dx
                        val bottom = box.xywh.bottom * scale + dy

                        paint.color = newColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = BOX_LINE_WIDTH
                        canvas.drawRoundRect(
                            left, top, right, bottom,
                            BOX_CORNER_RADIUS, BOX_CORNER_RADIUS,
                            paint
                        )
                    }

                    // キーポイント & スケルトン
                    for (person in result.keypointsList) {
                        val points = arrayOfNulls<PointF>(person.xyn.size)
                        for (i in person.xyn.indices) {
                            val kp = person.xyn[i]
                            val conf = person.conf[i]
                            if (conf > 0.25f) {
                                val pxCam = kp.first * iw
                                val pyCam = kp.second * ih
                                val px = pxCam * scale + dx
                                val py = pyCam * scale + dy

                                val colorIdx = if (i < kptColorIndices.size) kptColorIndices[i] else 0
                                val rgbArray = posePalette[colorIdx % posePalette.size]
                                paint.color = Color.argb(
                                    255,
                                    rgbArray[0].toInt().coerceIn(0,255),
                                    rgbArray[1].toInt().coerceIn(0,255),
                                    rgbArray[2].toInt().coerceIn(0,255)
                                )
                                paint.style = Paint.Style.FILL
                                canvas.drawCircle(px, py, 8f, paint)

                                points[i] = PointF(px, py)
                            }
                        }

                        // スケルトン接続
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = KEYPOINT_LINE_WIDTH
                        for ((idx, bone) in skeleton.withIndex()) {
                            val i1 = bone[0] - 1  // 1-indexed to 0-indexed
                            val i2 = bone[1] - 1
                            val p1 = points.getOrNull(i1)
                            val p2 = points.getOrNull(i2)
                            if (p1 != null && p2 != null) {
                                val limbColorIdx = if (idx < limbColorIndices.size) limbColorIndices[idx] else 0
                                val rgbArray = posePalette[limbColorIdx % posePalette.size]
                                paint.color = Color.argb(
                                    255,
                                    rgbArray[0].toInt().coerceIn(0,255),
                                    rgbArray[1].toInt().coerceIn(0,255),
                                    rgbArray[2].toInt().coerceIn(0,255)
                                )
                                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                            }
                        }
                    }
                }
                // ----------------------------------------
                // OBB
                // ----------------------------------------
                YOLOTask.OBB -> {
                    for (obbRes in result.obb) {
                        // Apply filtering - skip this detection if it doesn't meet criteria
                        if (obbRes.confidence < minConfidence) continue
                        if (allowedClasses.isNotEmpty() && !allowedClasses.contains(obbRes.cls)) continue

                        val alpha = (obbRes.confidence * 255).toInt().coerceIn(0, 255)
                        
                        // Use custom colors if available, otherwise use default colors
                        val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                            // Use modulo to cycle through the custom colors
                            customColors!![obbRes.index % customColors!!.size]
                        } else {
                            ultralyticsColors[obbRes.index % ultralyticsColors.size]
                        }
                        
                        val newColor = Color.argb(
                            alpha,
                            Color.red(baseColor),
                            Color.green(baseColor),
                            Color.blue(baseColor)
                        )

                        paint.color = newColor
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = BOX_LINE_WIDTH

                        // 回転矩形(多角形)をパスで描画
                        val polygon = obbRes.box.toPolygon().map { pt ->
                            PointF(pt.x * scaledW + dx, pt.y * scaledH + dy)
                        }
                        if (polygon.size >= 4) {
                            val path = Path().apply {
                                moveTo(polygon[0].x, polygon[0].y)
                                for (p in polygon.drop(1)) {
                                    lineTo(p.x, p.y)
                                }
                                close()
                            }
                            canvas.drawPath(path, paint)

                            // ラベルテキスト
                            val labelText = "${obbRes.cls} ${"%.1f".format(obbRes.confidence * 100)}%"
                            paint.textSize = 40f
                            paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

                            val fm = paint.fontMetrics
                            val textWidth = paint.measureText(labelText)
                            val textHeight = fm.bottom - fm.top
                            val padding = 10f
                            val cornerRadius = 8f

                            // 背景矩形をポリゴン[0]付近に表示
                            val labelBoxHeight = textHeight + 2 * padding
                            val labelBottom = polygon[0].y
                            val labelTop = labelBottom - labelBoxHeight
                            val labelLeft = polygon[0].x
                            val labelRight = labelLeft + textWidth + 2 * padding

                            val bgRect = RectF(labelLeft, labelTop, labelRight, labelBottom)
                            paint.style = Paint.Style.FILL
                            
                            // Use custom label background color if set, otherwise use detection box color
                            paint.color = if (labelBackgroundColor != null) {
                                // Apply the configured opacity
                                val alpha = (labelBackgroundOpacity * 255).toInt().coerceIn(0, 255)
                                Color.argb(
                                    alpha,
                                    Color.red(labelBackgroundColor!!),
                                    Color.green(labelBackgroundColor!!),
                                    Color.blue(labelBackgroundColor!!)
                                )
                            } else {
                                newColor
                            }
                            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)

                            // テキストを縦中央揃え
                            paint.color = labelTextColor ?: Color.WHITE
                            val centerY = (labelTop + labelBottom) / 2
                            val baseline = centerY - (fm.descent + fm.ascent) / 2
                            val textX = labelLeft + padding
                            canvas.drawText(labelText, textX, baseline, paint)
                        }
                    }
                }
            }
        }
    }

    /**
     * Captures the current camera frame with detection boxes as a JPEG byte array
     * 
     * @return ByteArray containing the JPEG-encoded image
     */
    fun takePictureAsBytes(): ByteArray {
        // Get the current frame from the PreviewView
        val previewBitmap = previewView.bitmap
        if (previewBitmap == null) {
            Log.e(TAG, "Failed to get bitmap from PreviewView")
            // Return an empty array if we can't capture the preview
            return ByteArray(0)
        }
        
        // Create a mutable copy of the bitmap so we can draw on it
        val resultBitmap = previewBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        
        // Draw the detection overlays onto the bitmap
        inferenceResult?.let { result ->
            val drawPaint = Paint().apply { isAntiAlias = true }
            
            // Scale factors for drawing on the bitmap
            val iw = result.origShape.width.toFloat()
            val ih = result.origShape.height.toFloat()
            val vw = resultBitmap.width.toFloat()
            val vh = resultBitmap.height.toFloat()
            
            val scaleX = vw / iw
            val scaleY = vh / ih
            val scale = maxOf(scaleX, scaleY)
            
            val scaledW = iw * scale
            val scaledH = ih * scale
            
            val dx = (vw - scaledW) / 2f
            val dy = (vh - scaledH) / 2f
            
            // Draw boxes and labels only if showBoxes is true
            if (showBoxes) {
                // Use the shared drawing function
                drawDetectionsOnBitmap(canvas, resultBitmap.width, resultBitmap.height)
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
     * @return Triple of (success boolean, error message or null if successful, dimensions Map with width and height)
     */
    fun startRecording(outputPath: String?): Triple<Boolean, String?, Map<String, Int>> {
        var width = previewView.width
        var height = previewView.height

        Log.d(TAG, "Initial dimensions for recording - PreviewView: ${width}x${height}")

        if (width <= 0 || height <= 0) {
            Log.w(TAG, "PreviewView dimensions are zero! Using fallback dimensions.")
            
            val displayMetrics = context.resources.displayMetrics
            Log.d(TAG, "Display metrics - width: ${displayMetrics.widthPixels}, height: ${displayMetrics.heightPixels}, density: ${displayMetrics.density}")
            
            if (displayMetrics.widthPixels > 0 && displayMetrics.heightPixels > 0) {
                width = displayMetrics.widthPixels
                height = displayMetrics.heightPixels
                Log.d(TAG, "Using screen dimensions as fallback: ${width}x${height}")
            } else {
                width = 720
                height = 1280
                Log.d(TAG, "Using default dimensions: ${width}x${height}")
            }
        }

        if (inferenceResult != null) {
            Log.d(TAG, "Original image dimensions from inference result: ${inferenceResult?.origShape?.width}x${inferenceResult?.origShape?.height}")
        }

        val recordingResult = videoRecorder.startRecording(width, height, outputPath)
        
        val dimensions = HashMap<String, Int>()
        dimensions["width"] = width
        dimensions["height"] = height
    
        return Triple(recordingResult.first, recordingResult.second, dimensions)
    }
    
    /**
     * Stops the current video recording
     * 
     * @return Path to the saved video file or null if recording failed or file doesn't exist
     */
    fun stopRecording(): String? {
        try {
            val recordingPath = videoRecorder.stopRecording()
            Log.d(TAG, "Video recording stopped, path: $recordingPath")
            
            // Verify the file actually exists
            if (recordingPath != null) {
                val videoFile = java.io.File(recordingPath)
                if (videoFile.exists() && videoFile.length() > 0) {
                    Log.d(TAG, "Verified video file exists at path: $recordingPath with size: ${videoFile.length()} bytes")
                    return recordingPath
                } else {
                    Log.e(TAG, "Video file doesn't exist or is empty at path: $recordingPath")
                    return null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping video recording", e)
            return null
        }
    }

    /**
     * Sets a callback to receive error notifications from the VideoRecorder.
     * 
     * @param callback The function to call when an error occurs
     */
    fun setVideoRecorderErrorCallback(callback: (Int, String) -> Unit) {
        videoRecorder.setErrorCallback(object : VideoRecorder.ErrorCallback {
            override fun onError(errorCode: Int, errorMessage: String) {
                callback(errorCode, errorMessage)
            }
        })
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
                    Log.d(TAG, "Predictor resources released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing predictor", e)
                }
                predictor = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing predictor resources", e)
        }
    }

    /**
     * Disposes all resources used by the YoloView
     * This should be called when the view is no longer needed
     */
    fun dispose() {
        Log.d(TAG, "Disposing YoloView resources")
        
        // Use a coroutine scope tied to the view's lifecycle
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Stop recording
                if (videoRecorder.isRecording()) {
                    withContext(Dispatchers.IO) {
                        videoRecorder.stopRecording()
                    }
                }
                
                // Dispose VideoRecorder
                videoRecorder.dispose()
                Log.d(TAG, "VideoRecorder disposed")
                
                // Release predictor
                releasePredictor() // Ensure this is thread-safe
                
                // Release camera resources - must be on main thread
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
                
                // Clear callbacks and views on UI thread
                inferenceCallback = null
                modelLoadCallback = null
                cameraCreatedCallback = null
                removeAllViews()
                
                Log.d(TAG, "YoloView resources disposed")
            } catch (e: Exception) {
                Log.e(TAG, "Error during YoloView dispose", e)
            }
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
        Log.d(TAG, "Live predictions ${if (pause) "paused" else "resumed"}")
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