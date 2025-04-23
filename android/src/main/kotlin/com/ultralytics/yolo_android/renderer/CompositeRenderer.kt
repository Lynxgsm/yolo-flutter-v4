package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask

/**
 * Composite renderer that delegates to task-specific renderers based on result type
 */
class CompositeRenderer : YoloRenderer {
    // Store all renderers in a list for easy iteration
    private val renderers = listOf(
        DetectionRenderer(),
        SegmentationRenderer(),
        ClassificationRenderer(),
        PoseRenderer(),
        ObbRenderer()
    )
    
    // Map of task types to their specific renderers for efficient lookup
    private val taskRendererMap = mapOf(
        YOLOTask.DETECT to renderers[0],
        YOLOTask.SEGMENT to renderers[1],
        YOLOTask.CLASSIFY to renderers[2],
        YOLOTask.POSE to renderers[3],
        YOLOTask.OBB to renderers[4]
    )
    
    // Apply action to all renderers
    private fun applyToAll(action: (YoloRenderer) -> Unit) {
        renderers.forEach(action)
    }
    
    override fun setCustomColors(colors: List<Int>, applyAlpha: Boolean) {
        applyToAll { it.setCustomColors(colors, applyAlpha) }
    }
    
    override fun resetColors() {
        applyToAll { it.resetColors() }
    }
    
    override fun setAllowedClasses(classes: List<String>) {
        applyToAll { it.setAllowedClasses(classes) }
    }
    
    override fun setMinConfidence(confidence: Float) {
        applyToAll { it.setMinConfidence(confidence) }
    }
    
    override fun setLabelTextColor(color: Int) {
        applyToAll { it.setLabelTextColor(color) }
    }
    
    override fun setLabelBackgroundColor(color: Int, opacity: Float) {
        applyToAll { it.setLabelBackgroundColor(color, opacity) }
    }
    
    override fun draw(canvas: Canvas, result: YOLOResult, width: Int, height: Int) {
        // Get the appropriate renderer for this task
        taskRendererMap[result.task]?.draw(canvas, result, width, height)
    }
} 