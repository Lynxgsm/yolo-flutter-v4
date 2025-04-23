package com.ultralytics.yolo_android.renderer

import android.graphics.Canvas
import android.graphics.Paint
import com.ultralytics.yolo_android.YOLOResult

/**
 * Base interface for all YOLO renderers
 */
interface YoloRenderer {
    /**
     * Set custom colors for detection visualization
     */
    fun setCustomColors(colors: List<Int>, applyAlpha: Boolean)
    
    /**
     * Reset colors to default values
     */
    fun resetColors()
    
    /**
     * Set allowed classes to filter results
     */
    fun setAllowedClasses(classes: List<String>)
    
    /**
     * Set minimum confidence threshold for displayed results
     */
    fun setMinConfidence(confidence: Float)
    
    /**
     * Set label text color
     */
    fun setLabelTextColor(color: Int)
    
    /**
     * Set label background color and opacity
     */
    fun setLabelBackgroundColor(color: Int, opacity: Float)
    
    /**
     * Draw detection results on the provided canvas
     */
    fun draw(canvas: Canvas, result: YOLOResult, width: Int, height: Int)
} 