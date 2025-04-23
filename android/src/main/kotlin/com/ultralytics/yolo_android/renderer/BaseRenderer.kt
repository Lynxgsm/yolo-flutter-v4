package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YoloConstants
import kotlin.math.max

/**
 * Base renderer implementation with common functionality
 */
abstract class BaseRenderer : YoloRenderer {
    // Colors for detection boxes
    protected open var customColors: List<Int>? = null
    protected open var applyConfidenceAlpha: Boolean = true
    
    // Label colors
    protected open var labelTextColor: Int? = null
    protected open var labelBackgroundColor: Int? = null
    protected open var labelBackgroundOpacity: Float = 0.7f
    
    // Filtering settings
    protected open var allowedClasses = listOf<String>() // Empty means show all
    protected open var minConfidence = 0.25f
    
    override fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        this.customColors = colors
        this.applyConfidenceAlpha = applyAlpha
    }
    
    override fun resetColors() {
        this.customColors = null
        this.labelTextColor = null
        this.labelBackgroundColor = null
        this.labelBackgroundOpacity = 0.7f
    }
    
    override fun setAllowedClasses(classes: List<String>) {
        this.allowedClasses = classes
    }
    
    override fun setMinConfidence(confidence: Float) {
        this.minConfidence = confidence
    }
    
    override fun setLabelTextColor(color: Int) {
        this.labelTextColor = color
    }
    
    override fun setLabelBackgroundColor(color: Int, opacity: Float) {
        this.labelBackgroundColor = color
        this.labelBackgroundOpacity = opacity.coerceIn(0f, 1f)
    }
    
    override fun draw(canvas: Canvas, result: YOLOResult, width: Int, height: Int) {
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
        
        drawContent(canvas, result, paint, scale, dx, dy, scaledW, scaledH, vw, vh, iw, ih)
    }
    
    /**
     * Draw the specific content for this renderer
     */
    protected abstract fun drawContent(
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
    )
    
    /**
     * Draw a label with background
     */
    protected fun drawLabel(
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
    
    /**
     * Get a color for an item based on its index and confidence
     */
    protected fun getColorForItem(index: Int, confidence: Float): Int {
        val alpha = (confidence * 255).toInt().coerceIn(0, 255)
        
        // Get color for this item
        val baseColor = if (customColors != null && customColors!!.isNotEmpty()) {
            customColors!![index % customColors!!.size]
        } else {
            YoloConstants.ultralyticsColors[index % YoloConstants.ultralyticsColors.size]
        }
        
        // Apply alpha based on confidence if needed
        return if (applyConfidenceAlpha) {
            Color.argb(
                alpha,
                Color.red(baseColor),
                Color.green(baseColor),
                Color.blue(baseColor)
            )
        } else {
            baseColor
        }
    }
} 