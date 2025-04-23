package com.ultralytics.yolo_android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import android.view.View
import com.ultralytics.yolo_android.renderer.CompositeRenderer

/**
 * Overlay view that draws detection results on top of the camera preview
 */
class OverlayView(context: Context) : View(context) {
    // The renderer responsible for drawing detections
    private val renderer = CompositeRenderer()
    
    // Current inference result to display
    private var inferenceResult: YOLOResult? = null
    
    init {
        // Make background transparent
        setBackgroundColor(Color.TRANSPARENT)
        // Use hardware layer for better z-order 
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Raise overlay
        elevation = 1000f
        translationZ = 1000f
        
        setWillNotDraw(false)
    }
    
    /**
     * Set the current detection result
     */
    fun setDetectionResult(result: YOLOResult?) {
        this.inferenceResult = result
        invalidate()
    }
    
    /**
     * Set custom colors for detection boxes and labels
     */
    fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        renderer.setCustomColors(colors, applyAlpha)
        invalidate()
    }
    
    /**
     * Reset colors to default
     */
    fun resetColors() {
        renderer.resetColors()
        invalidate()
    }
    
    /**
     * Set label text color
     */
    fun setLabelTextColor(color: Int) {
        renderer.setLabelTextColor(color)
        invalidate()
    }
    
    /**
     * Set label background color
     */
    fun setLabelBackgroundColor(color: Int, opacity: Float) {
        renderer.setLabelBackgroundColor(color, opacity)
        invalidate()
    }
    
    /**
     * Set allowed classes to display (empty list means show all)
     */
    fun setAllowedClasses(classes: List<String>) {
        renderer.setAllowedClasses(classes)
        invalidate()
    }
    
    /**
     * Set minimum confidence threshold for displayed detections
     */
    fun setMinConfidence(confidence: Float) {
        renderer.setMinConfidence(confidence)
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        inferenceResult?.let { result ->
            Log.d(YoloConstants.TAG, "OverlayView onDraw: Drawing result with ${result.boxes.size} boxes")
            renderer.draw(canvas, result, width, height)
        }
    }
} 