package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask
import com.ultralytics.yolo_android.YoloConstants

/**
 * Renderer for drawing YOLO object detection results
 */
class DetectionRenderer : BaseRenderer() {
    
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
        if (result.task != YOLOTask.DETECT) return
        
        for (box in result.boxes) {
            // Apply filtering
            if (box.conf < minConfidence) continue
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(box.cls)) continue
            
            // Get color for this detection
            val color = getColorForItem(box.index, box.conf)
            
            // Draw bounding box
            val left = box.xywh.left * scale + dx
            val top = box.xywh.top * scale + dy
            val right = box.xywh.right * scale + dx
            val bottom = box.xywh.bottom * scale + dy
            
            paint.color = color
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = YoloConstants.BOX_LINE_WIDTH
            canvas.drawRoundRect(
                left, top, right, bottom,
                YoloConstants.BOX_CORNER_RADIUS, YoloConstants.BOX_CORNER_RADIUS,
                paint
            )
            
            // Draw label
            drawLabel(canvas, paint, box.cls, box.conf, left, top, color)
        }
    }
} 