package com.example.yolo

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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.math.max

class YoloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val textureView: TextureView = TextureView(context)
    private val overlayView: OverlayView = OverlayView(context)

    private var inferenceResult: YOLOResult? = null
    private var predictor: Predictor? = null
    private var task: YOLOTask = YOLOTask.DETECT
    private var modelName: String = "Model"

    // --- camera setup ---
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    // ---- detection thresholds (外部から setter で変更可能に) ----
    private var confidenceThreshold = 0.25  // 初期値
    private var iouThreshold = 0.45
    private var numItemsThreshold = 30

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

    init {
        // TextureViewとオーバーレイを追加
        addView(textureView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(overlayView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    // ---- 外部からしきい値をセットできるメソッド ----
    fun setConfidenceThreshold(conf: Double) {
        confidenceThreshold = conf
        if (predictor is ObjectDetector) {
            (predictor as ObjectDetector).setConfidenceThreshold(conf.toFloat())
        }
    }
    fun setIouThreshold(iou: Double) {
        iouThreshold = iou
        if (predictor is ObjectDetector) {
            (predictor as ObjectDetector).setIouThreshold(iou.toFloat())
        }
    }
    fun setNumItemsThreshold(n: Int) {
        numItemsThreshold = n
        if (predictor is ObjectDetector) {
            (predictor as ObjectDetector).setNumItemsThreshold(n)
        }
    }

    // ---- Model / Task ----
    /**
     * 時間のかかるモデルロードをバックグラウンドで実行し、
     * 完了したら modelLoadCallback を呼び出す
     */
    fun setModel(modelPath: String, task: YOLOTask, context: Context) {
        // バックグラウンドスレッドでロード
        Executors.newSingleThreadExecutor().execute {
            try {
                // 実際に interpreter を初期化するなど時間のかかる処理
                val newPredictor = when (task) {
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

                // ロード成功 → メインスレッドでUIに反映
                post {
                    this.task = task
                    this.predictor = newPredictor
                    this.modelName = modelPath.substringAfterLast("/")
                    modelLoadCallback?.invoke(true) // 成功通知
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: $modelPath", e)
                // 失敗通知
                post {
                    modelLoadCallback?.invoke(false)
                }
            }
        }
    }

    // ---- カメラ起動 / パーミッション ----
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                onFrame(imageProxy)
            }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            cameraProvider.unbindAll()
            try {
                val lifecycleOwner = context as? LifecycleOwner
                    ?: throw IllegalStateException("Context is not a LifecycleOwner")

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                // TextureViewにSurfaceProviderを設定
                val surfaceProvider = androidx.camera.core.SurfaceRequest.TransformationInfo::class.java.let {
                    preview.setSurfaceProvider { request ->
                        val surface = Surface(textureView.surfaceTexture)
                        request.provideSurface(surface, Executors.newSingleThreadExecutor()) { }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }

    // ---- 1フレーム処理 ----
    private fun onFrame(imageProxy: ImageProxy) {
        val w = imageProxy.width
        val h = imageProxy.height

        val bitmap = ImageUtils.toBitmap(imageProxy) ?: run {
            imageProxy.close()
            return
        }

        predictor?.let { p ->
            // For camera feed in YoloView, we always need to rotate the bitmap
            val result = p.predict(bitmap, h, w, rotateForCamera = true)
            inferenceResult = result

            // ★ 推論結果をコールバックで通知
            inferenceCallback?.invoke(result)

            post { overlayView.invalidate() }
        }
        imageProxy.close()
    }

    // ---- 仮のラベル読み込み実装 ----
    private fun loadLabels(modelPath: String): List<String> {
        // 実際には metadata などから読み取る実装に置き換え
        return listOf("person", "bicycle", "car", "motorbike", "aeroplane", "bus", "train")
    }

    // ---- オーバーレイ描画 ----
    private inner class OverlayView(context: Context) : View(context) {
        private val paint = Paint().apply { isAntiAlias = true }

        init {
            // オーバーレイビューを透明に設定
            setBackgroundColor(Color.TRANSPARENT)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val result = inferenceResult ?: return

            val iw = result.origShape.width.toFloat()
            val ih = result.origShape.height.toFloat()

            val vw = width.toFloat()
            val vh = height.toFloat()

            // カメラ画像 → View への拡大倍率
            val scaleX = vw / iw
            val scaleY = vh / ih
            val scale = max(scaleX, scaleY)

            val scaledW = iw * scale
            val scaledH = ih * scale

            val dx = (vw - scaledW) / 2f
            val dy = (vh - scaledH) / 2f

            when (task) {
                // ----------------------------------------
                // DETECT
                // ----------------------------------------
                YOLOTask.DETECT -> {
                    for (box in result.boxes) {
                        // confidence に応じてアルファ調整
                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        val baseColor = ultralyticsColors[box.index % ultralyticsColors.size]
                        val newColor = Color.argb(
                            alpha,
                            Color.red(baseColor),
                            Color.green(baseColor),
                            Color.blue(baseColor)
                        )

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
                        paint.color = newColor
                        canvas.drawRoundRect(bgRect, BOX_CORNER_RADIUS, BOX_CORNER_RADIUS, paint)

                        // テキストを矩形内で縦中央に合わせる
                        paint.color = Color.WHITE
                        // 中心位置 = (bgRect.top + bgRect.bottom)/2
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
                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        val baseColor = ultralyticsColors[box.index % ultralyticsColors.size]
                        val newColor = Color.argb(
                            alpha,
                            Color.red(baseColor),
                            Color.green(baseColor),
                            Color.blue(baseColor)
                        )

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
                        paint.color = newColor
                        canvas.drawRoundRect(bgRect, BOX_CORNER_RADIUS, BOX_CORNER_RADIUS, paint)

                        paint.color = Color.WHITE
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
                        val alpha = (probs.top1Conf * 255).toInt().coerceIn(0, 255)
                        // top1Index で色を選択
                        val baseColor = ultralyticsColors[probs.top1Index % ultralyticsColors.size]
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

                        paint.color = newColor
                        paint.style = Paint.Style.FILL
                        val bgRect = RectF(bgLeft, bgTop, bgRight, bgBottom)
                        canvas.drawRoundRect(bgRect, 20f, 20f, paint)

                        paint.color = Color.WHITE
                        val baseline = centerY - (fm.descent + fm.ascent)/2
                        canvas.drawText(labelText, centerX - (textWidth / 2), baseline, paint)
                    }
                }
                // ----------------------------------------
                // POSE
                // ----------------------------------------
                YOLOTask.POSE -> {
                    // バウンディングボックス
                    for (box in result.boxes) {
                        val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
                        val baseColor = ultralyticsColors[box.index % ultralyticsColors.size]
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
                            val i1 = bone[0] - 1
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
                        val alpha = (obbRes.confidence * 255).toInt().coerceIn(0, 255)
                        val baseColor = ultralyticsColors[obbRes.index % ultralyticsColors.size]
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
                                for (i in 1 until polygon.size) {
                                    lineTo(polygon[i].x, polygon[i].y)
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
                            paint.color = newColor
                            canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)

                            // テキストを縦中央揃え
                            paint.color = Color.WHITE
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
}