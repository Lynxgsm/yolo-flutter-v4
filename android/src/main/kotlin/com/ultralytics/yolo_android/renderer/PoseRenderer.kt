package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask
import com.ultralytics.yolo_android.YoloConstants

/**
 * Renderer for drawing YOLO pose estimation results
 */
class PoseRenderer : BaseRenderer() {
    // Reuse the detection renderer for bounding boxes
    private val detectionRenderer = DetectionRenderer()
    
    // Override these properties to ensure state is synchronized between renderers
    override var customColors: List<Int>?
        get() = super.customColors
        set(value) {
            super.customColors = value
            detectionRenderer.setCustomColors(value ?: emptyList(), applyConfidenceAlpha)
        }
    
    override var applyConfidenceAlpha: Boolean
        get() = super.applyConfidenceAlpha
        set(value) {
            super.applyConfidenceAlpha = value
            detectionRenderer.setCustomColors(customColors ?: emptyList(), value)
        }
    
    override var labelTextColor: Int?
        get() = super.labelTextColor
        set(value) {
            super.labelTextColor = value
            detectionRenderer.setLabelTextColor(value ?: Color.WHITE)
        }
    
    override var labelBackgroundColor: Int?
        get() = super.labelBackgroundColor
        set(value) {
            super.labelBackgroundColor = value
            detectionRenderer.setLabelBackgroundColor(value ?: Color.BLACK, labelBackgroundOpacity)
        }
    
    override var labelBackgroundOpacity: Float
        get() = super.labelBackgroundOpacity
        set(value) {
            super.labelBackgroundOpacity = value
            detectionRenderer.setLabelBackgroundColor(labelBackgroundColor ?: Color.BLACK, value)
        }
    
    override var allowedClasses: List<String>
        get() = super.allowedClasses
        set(value) {
            super.allowedClasses = value
            detectionRenderer.setAllowedClasses(value)
        }
    
    override var minConfidence: Float
        get() = super.minConfidence
        set(value) {
            super.minConfidence = value
            detectionRenderer.setMinConfidence(value)
        }
    
    // Override resetColors to reset detectionRenderer too
    override fun resetColors() {
        super.resetColors()
        detectionRenderer.resetColors()
    }
    
    // No need to override other setters as they'll use the properties above
    
    override fun drawContent(
        canvas: Canvas,
        result: YOLOResult,
        paint: Paint,
        scale: Float,
        dx: Float,
        dy: Float,
        scaledW: Float,
        scaledH: Float,
        viewWidth: Float,
        viewHeight: Float,
        imageWidth: Float,
        imageHeight: Float
    ) {
        if (result.task != YOLOTask.POSE) return
        
        // First draw bounding boxes
        detectionRenderer.drawContent(
            canvas, result, paint, scale, dx, dy, scaledW, scaledH,
            viewWidth, viewHeight, imageWidth, imageHeight
        )
        
        // Then draw keypoints and skeletons
        result.keypoints?.forEach { keypoints ->
            val resultClass = keypoints.clazz
            val label = result.classLabels?.get(resultClass) ?: resultClass.toString()

            // Check if class is allowed
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(label)) return@forEach

            // Check confidence threshold
            if (keypoints.confidence < minConfidence) return@forEach

            // Set color for keypoint
            val colorIndex = resultClass % (customColors?.size ?: DEFAULT_COLORS.size)
            val color = customColors?.get(colorIndex) ?: DEFAULT_COLORS[colorIndex]
            
            // Draw keypoints
            keypoints.points.forEach { point ->
                val x = point.x * scale + dx
                val y = point.y * scale + dy
                val pointColor = if (applyConfidenceAlpha) {
                    Color.argb(
                        (point.confidence * 255).toInt(),
                        Color.red(color),
                        Color.green(color),
                        Color.blue(color)
                    )
                } else color
                
                paint.color = pointColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 8f, paint)
            }
            
            // Draw skeleton lines if available
            result.skeleton?.let { skeleton ->
                paint.strokeWidth = 3f
                
                for (connection in skeleton) {
                    if (connection.size >= 2) {
                        val startIdx = connection[0]
                        val endIdx = connection[1]
                        
                        if (startIdx < keypoints.points.size && endIdx < keypoints.points.size) {
                            val startPoint = keypoints.points[startIdx]
                            val endPoint = keypoints.points[endIdx]
                            
                            val startX = startPoint.x * scale + dx
                            val startY = startPoint.y * scale + dy
                            val endX = endPoint.x * scale + dx
                            val endY = endPoint.y * scale + dy
                            
                            paint.color = color
                            canvas.drawLine(startX, startY, endX, endY, paint)
                        }
                    }
                }
            }
        }
    }
} 