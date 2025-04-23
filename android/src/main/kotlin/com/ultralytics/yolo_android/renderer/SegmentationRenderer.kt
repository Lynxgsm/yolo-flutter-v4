package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask

/**
 * Renderer for drawing YOLO segmentation results
 */
class SegmentationRenderer : BaseRenderer() {
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
    
    private var maskOpacity = 0.5f
    private var drawBoxes = true
    
    /**
     * Set opacity for the segmentation masks (0-1)
     */
    fun setMaskOpacity(opacity: Float) {
        maskOpacity = opacity.coerceIn(0f, 1f)
    }
    
    /**
     * Set whether to draw detection boxes alongside segmentation masks
     */
    fun setDrawBoxes(draw: Boolean) {
        drawBoxes = draw
    }

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
        if (result.task != YOLOTask.SEGMENT) return
        
        // First draw the segmentation masks
        result.masks?.combinedMask?.let { maskBitmap ->
            val src = Rect(0, 0, maskBitmap.width, maskBitmap.height)
            val dst = RectF(dx, dy, dx + scaledW, dy + scaledH)
            val maskPaint = Paint().apply { alpha = 128 }
            canvas.drawBitmap(maskBitmap, src, dst, maskPaint)
        }
        
        // Then draw detection boxes on top
        detectionRenderer.drawContent(
            canvas, result, paint, scale, dx, dy, scaledW, scaledH,
            viewWidth, viewHeight, imageWidth, imageHeight
        )
    }
} 