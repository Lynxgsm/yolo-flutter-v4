package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask

/**
 * Renderer for drawing YOLO classification results
 */
class ClassificationRenderer : BaseRenderer() {
    // Reuse the detection renderer for visualization
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

    private var renderBoxes = true
    private var cornerRadius = 10f
    private var textSize = 14f
    private var lineWidth = 2f
    private var textPadding = 8f
    private var isTextFilled = true
    
    // Background overlay for classification results
    private var displayOverlay = true
    private var overlayColor = Color.parseColor("#88000000")

    /**
     * Set whether to display boxes in classification mode
     */
    fun setDisplayBoxes(display: Boolean) {
        renderBoxes = display
    }

    /**
     * Set whether to display a background overlay
     */
    fun setDisplayOverlay(display: Boolean) {
        displayOverlay = display
    }

    /**
     * Set the color of the background overlay
     */
    fun setOverlayColor(color: Int) {
        overlayColor = color
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
        if (result.task != YOLOTask.CLASSIFICATION) return
        
        // Draw background overlay if enabled
        if (displayOverlay) {
            paint.color = overlayColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(dx, dy, dx + scaledW, dy + scaledH, paint)
        }
        
        // For classification, we draw results on the top part
        val results = result.classificationResults ?: return
        if (results.isEmpty()) return
        
        // Sort results by confidence (highest first)
        val sortedResults = results.sortedByDescending { it.confidence }
        
        // Calculate text dimensions
        paint.textSize = textSize * scale
        val fontMetrics = paint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val padding = textPadding * scale
        
        var yOffset = dy + padding * 3 // Start from top with some padding
        
        // Draw the top N results (or all if fewer)
        val maxResultsToShow = 5
        for (i in 0 until minOf(maxResultsToShow, sortedResults.size)) {
            val classResult = sortedResults[i]
            val resultClass = classResult.clazz
            val label = result.classLabels?.get(resultClass) ?: resultClass.toString()
            val confidence = classResult.confidence
            
            // Skip if below confidence threshold
            if (confidence < minConfidence) continue
            
            // Skip if class not in allowed classes
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(label)) continue
            
            // Calculate color based on class and confidence
            val colorIndex = resultClass % (customColors?.size ?: DEFAULT_COLORS.size)
            val baseColor = customColors?.get(colorIndex) ?: DEFAULT_COLORS[colorIndex]
            val color = if (applyConfidenceAlpha) {
                Color.argb(
                    (confidence * 255).toInt(),
                    Color.red(baseColor),
                    Color.green(baseColor),
                    Color.blue(baseColor)
                )
            } else baseColor
            
            // Create text
            val text = "$label: ${String.format("%.1f", confidence * 100)}%"
            val textWidth = paint.measureText(text)
            
            // Position text
            val left = dx + padding
            val right = left + textWidth + padding * 2
            val top = yOffset
            val bottom = top + textHeight + padding
            
            // Draw background rect
            paint.color = if (labelBackgroundColor != null) {
                Color.argb(
                    (255 * labelBackgroundOpacity).toInt(),
                    Color.red(labelBackgroundColor!!),
                    Color.green(labelBackgroundColor!!),
                    Color.blue(labelBackgroundColor!!)
                )
            } else {
                color
            }
            
            paint.style = Paint.Style.FILL
            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            
            // Draw text
            paint.color = labelTextColor ?: Color.WHITE
            canvas.drawText(
                text,
                left + padding,
                top + textHeight,
                paint
            )
            
            // Update for next result
            yOffset = bottom + padding
        }
    }
} 