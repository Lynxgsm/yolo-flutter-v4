package com.ultralytics.yolo_android

import android.graphics.*
import kotlin.math.max

/**
 * Utility class for rendering YOLO detection results onto bitmaps or canvases
 */
class YoloDetectionRenderer {
    // Colors for detection boxes
    private var customColors: List<Int>? = null
    private var applyConfidenceAlpha: Boolean = true
    
    // Label colors
    private var labelTextColor: Int? = null
    private var labelBackgroundColor: Int? = null
    private var labelBackgroundOpacity: Float = 0.7f
    
    // Filtering settings
    private var allowedClasses = listOf<String>() // Empty means show all
    private var minConfidence = 0.25f
    
    /**
     * Set custom colors for detection boxes and labels
     */
    fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        this.customColors = colors
        this.applyConfidenceAlpha = applyAlpha
    }
    
    /**
     * Reset colors to default
     */
    fun resetColors() {
        this.customColors = null
        this.labelTextColor = null
        this.labelBackgroundColor = null
        this.labelBackgroundOpacity = 0.7f
    }
    
    /**
     * Set label text color
     */
    fun setLabelTextColor(color: Int) {
        this.labelTextColor = color
    }
    
    /**
     * Set label background color
     */
    fun setLabelBackgroundColor(color: Int, opacity: Float) {
        this.labelBackgroundColor = color
        this.labelBackgroundOpacity = opacity.coerceIn(0f, 1f)
    }
    
    /**
     * Set allowed classes to display (empty list means show all)
     */
    fun setAllowedClasses(classes: List<String>) {
        this.allowedClasses = classes
    }
    
    /**
     * Set minimum confidence threshold for displayed detections
     */
    fun setMinConfidence(confidence: Float) {
        this.minConfidence = confidence
    }
    
    /**
     * Draws detection results onto a canvas
     */
    fun drawDetectionsOnCanvas(
        canvas: Canvas,
        result: YOLOResult,
        viewWidth: Int,
        viewHeight: Int
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        
        // Calculate scaling factors
        val iw = result.origShape.width.toFloat()
        val ih = result.origShape.height.toFloat()
        val vw = viewWidth.toFloat()
        val vh = viewHeight.toFloat()
        
        val scaleX = vw / iw
        val scaleY = vh / ih
        val scale = max(scaleX, scaleY)
        
        val scaledW = iw * scale
        val scaledH = ih * scale
        
        val dx = (vw - scaledW) / 2f
        val dy = (vh - scaledH) / 2f
        
        when (result.task) {
            YOLOTask.DETECT -> drawDetectionBoxes(canvas, result, paint, scale, dx, dy)
            YOLOTask.SEGMENT -> drawSegmentation(canvas, result, paint, scale, dx, dy, scaledW, scaledH)
            YOLOTask.CLASSIFY -> drawClassification(canvas, result, paint, vw, vh)
            YOLOTask.POSE -> drawPose(canvas, result, paint, scale, dx, dy, iw, ih)
            YOLOTask.OBB -> drawOrientedBoxes(canvas, result, paint, scale, dx, dy, scaledW, scaledH)
        }
    }
    
    private fun drawDetectionBoxes(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        scale: Float,
        dx: Float,
        dy: Float
    ) {
        for (box in result.boxes) {
            // Apply filtering
            if (box.conf < minConfidence) continue
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue
            
            val alpha = (box.conf * 255).toInt().coerceIn(0, 255)
            
            // Get color for this detection
            val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                customColors!![box.index % customColors!!.size]
            } else {
                YoloConstants.ultralyticsColors[box.index % YoloConstants.ultralyticsColors.size]
            }
            
            // Apply alpha based on confidence if needed
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
            paint.strokeWidth = YoloConstants.BOX_LINE_WIDTH
            canvas.drawRoundRect(
                left, top, right, bottom,
                YoloConstants.BOX_CORNER_RADIUS, YoloConstants.BOX_CORNER_RADIUS,
                paint
            )
            
            // Draw label
            drawLabel(canvas, paint, box.cls, box.conf, left, top, newColor)
        }
    }
    
    private fun drawSegmentation(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        scale: Float,
        dx: Float,
        dy: Float,
        scaledW: Float,
        scaledH: Float
    ) {
        // First draw boxes and labels
        drawDetectionBoxes(canvas, result, paint, scale, dx, dy)
        
        // Then draw segmentation mask
        result.masks?.combinedMask?.let { maskBitmap ->
            val src = Rect(0, 0, maskBitmap.width, maskBitmap.height)
            val dst = RectF(dx, dy, dx + scaledW, dy + scaledH)
            val maskPaint = Paint().apply { alpha = 128 }
            canvas.drawBitmap(maskBitmap, src, dst, maskPaint)
        }
    }
    
    private fun drawClassification(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        vw: Float,
        vh: Float
    ) {
        result.probs?.let { probs ->
            // Skip if doesn't meet confidence threshold
            if (probs.top1Conf < minConfidence) return
            // Skip if not in allowed classes list
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(probs.top1)) return
            
            val alpha = (probs.top1Conf * 255).toInt().coerceIn(0, 255)
            
            // Use custom colors if available, otherwise use default colors
            val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                customColors!![probs.top1Index % customColors!!.size]
            } else {
                YoloConstants.ultralyticsColors[probs.top1Index % YoloConstants.ultralyticsColors.size]
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
            
            val bgLeft = centerX - (textWidth / 2) - pad
            val bgTop = centerY - (textHeight / 2) - pad
            val bgRight = centerX + (textWidth / 2) + pad
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
    
    private fun drawPose(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        scale: Float,
        dx: Float,
        dy: Float,
        iw: Float,
        ih: Float
    ) {
        // Draw bounding boxes first
        drawDetectionBoxes(canvas, result, paint, scale, dx, dy)
        
        // Draw keypoints and skeletons
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
                    
                    val colorIdx = if (i < YoloConstants.kptColorIndices.size) YoloConstants.kptColorIndices[i] else 0
                    val rgbArray = YoloConstants.posePalette[colorIdx % YoloConstants.posePalette.size]
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
            
            // Draw skeleton connections
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = YoloConstants.KEYPOINT_LINE_WIDTH
            for ((idx, bone) in YoloConstants.skeleton.withIndex()) {
                val i1 = bone[0] - 1  // 1-indexed to 0-indexed
                val i2 = bone[1] - 1
                val p1 = points.getOrNull(i1)
                val p2 = points.getOrNull(i2)
                if (p1 != null && p2 != null) {
                    val limbColorIdx = if (idx < YoloConstants.limbColorIndices.size) YoloConstants.limbColorIndices[idx] else 0
                    val rgbArray = YoloConstants.posePalette[limbColorIdx % YoloConstants.posePalette.size]
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
    
    private fun drawOrientedBoxes(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        scale: Float,
        dx: Float,
        dy: Float,
        scaledW: Float,
        scaledH: Float
    ) {
        for (obbRes in result.obb) {
            // Apply filtering
            if (obbRes.confidence < minConfidence) continue
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(obbRes.cls)) continue
            
            val alpha = (obbRes.confidence * 255).toInt().coerceIn(0, 255)
            
            // Use custom colors if available, otherwise use default colors
            val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
                customColors!![obbRes.index % customColors!!.size]
            } else {
                YoloConstants.ultralyticsColors[obbRes.index % YoloConstants.ultralyticsColors.size]
            }
            
            val newColor = Color.argb(
                alpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
            
            paint.color = newColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = YoloConstants.BOX_LINE_WIDTH
            
            // Draw rotated rect as polygon
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
                
                // Draw label near first polygon point
                drawLabel(canvas, paint, obbRes.cls, obbRes.confidence, polygon[0].x, polygon[0].y, newColor)
            }
        }
    }
    
    private fun drawLabel(
        canvas: Canvas,
        paint: Paint,
        className: String,
        confidence: Float,
        x: Float,
        y: Float,
        boxColor: Int
    ) {
        val labelText = "$className ${"%.1f".format(confidence * 100)}%"
        paint.textSize = 40f
        val fm = paint.fontMetrics
        val textWidth = paint.measureText(labelText)
        val textHeight = fm.bottom - fm.top
        val pad = 8f
        
        val labelBoxHeight = textHeight + 2 * pad
        val labelBottom = y
        val labelTop = labelBottom - labelBoxHeight
        val labelLeft = x
        val labelRight = labelLeft + textWidth + 2 * pad
        
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
            boxColor
        }
        
        canvas.drawRoundRect(
            RectF(labelLeft, labelTop, labelRight, labelBottom),
            YoloConstants.BOX_CORNER_RADIUS, YoloConstants.BOX_CORNER_RADIUS,
            paint
        )
        
        paint.color = labelTextColor ?: Color.WHITE
        val centerY = (labelTop + labelBottom) / 2
        val baseline = centerY - (fm.descent + fm.ascent) / 2
        canvas.drawText(labelText, labelLeft + pad, baseline, paint)
    }
} 