package com.ultralytics.yolo_android.renderer

import android.graphics.*
import com.ultralytics.yolo_android.YOLOResult
import com.ultralytics.yolo_android.YOLOTask
import com.ultralytics.yolo_android.YoloConstants

/**
 * Renderer for drawing YOLO oriented bounding boxes
 */
class ObbRenderer : BaseRenderer() {
    
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
        if (result.task != YOLOTask.OBB) return
        
        for (obbRes in result.obb) {
            // Apply filtering
            if (obbRes.confidence < minConfidence) continue
            if (allowedClasses.isNotEmpty() && !allowedClasses.contains(obbRes.cls)) continue
            
            // Get color for this detection
            val color = getColorForItem(obbRes.index, obbRes.confidence)
            
            paint.color = color
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
                drawLabel(canvas, paint, obbRes.cls, obbRes.confidence, polygon[0].x, polygon[0].y, color)
            }
        }
    }
} 