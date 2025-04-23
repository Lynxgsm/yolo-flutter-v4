package com.ultralytics.yolo_android

import android.graphics.Color

object YoloConstants {
    // Permission constants
    const val REQUEST_CODE_PERMISSIONS = 10
    val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    
    // Logging tag
    const val TAG = "YoloView"

    // Rendering constants
    const val BOX_LINE_WIDTH = 8f
    const val BOX_CORNER_RADIUS = 12f
    const val KEYPOINT_LINE_WIDTH = 6f

    // Ultralytics colors
    val ultralyticsColors = arrayOf(
        Color.argb(153, 4, 42, 255),
        Color.argb(153, 11, 219, 235),
        Color.argb(153, 243, 243, 243),
        Color.argb(153, 0, 223, 183),
        Color.argb(153, 17, 31, 104),
        Color.argb(153, 255, 111, 221),
        Color.argb(153, 255, 68, 79),
        Color.argb(153, 204, 237, 0),
        Color.argb(153, 0, 243, 68),
        Color.argb(153, 189, 0, 255),
        Color.argb(153, 0, 180, 255),
        Color.argb(153, 221, 0, 186),
        Color.argb(153, 0, 255, 255),
        Color.argb(153, 38, 192, 0),
        Color.argb(153, 1, 255, 179),
        Color.argb(153, 125, 36, 255),
        Color.argb(153, 123, 0, 104),
        Color.argb(153, 255, 27, 108),
        Color.argb(153, 252, 109, 47),
        Color.argb(153, 162, 255, 11)
    )

    // Pose palette and connections
    val posePalette = arrayOf(
        floatArrayOf(255f, 128f, 0f),
        floatArrayOf(255f, 153f, 51f),
        floatArrayOf(255f, 178f, 102f),
        floatArrayOf(230f, 230f, 0f),
        floatArrayOf(255f, 153f, 255f),
        floatArrayOf(153f, 204f, 255f),
        floatArrayOf(255f, 102f, 255f),
        floatArrayOf(255f, 51f, 255f),
        floatArrayOf(102f, 178f, 255f),
        floatArrayOf(51f, 153f, 255f),
        floatArrayOf(255f, 153f, 153f),
        floatArrayOf(255f, 102f, 102f),
        floatArrayOf(255f, 51f, 51f),
        floatArrayOf(153f, 255f, 153f),
        floatArrayOf(102f, 255f, 102f),
        floatArrayOf(51f, 255f, 51f),
        floatArrayOf(0f, 255f, 0f),
        floatArrayOf(0f, 0f, 255f),
        floatArrayOf(255f, 0f, 0f),
        floatArrayOf(255f, 255f, 255f),
    )

    val kptColorIndices = intArrayOf(
        16, 16, 16, 16, 16,
        9, 9, 9, 9, 9, 9,
        0, 0, 0, 0, 0, 0
    )

    val limbColorIndices = intArrayOf(
        0, 0, 0, 0,
        7, 7, 7,
        9, 9, 9, 9, 9,
        16, 16, 16, 16, 16, 16, 16
    )

    val skeleton = arrayOf(
        intArrayOf(16, 14),
        intArrayOf(14, 12),
        intArrayOf(17, 15),
        intArrayOf(15, 13),
        intArrayOf(12, 13),
        intArrayOf(6, 12),
        intArrayOf(7, 13),
        intArrayOf(6, 7),
        intArrayOf(6, 8),
        intArrayOf(7, 9),
        intArrayOf(8, 10),
        intArrayOf(9, 11),
        intArrayOf(2, 3),
        intArrayOf(1, 2),
        intArrayOf(1, 3),
        intArrayOf(2, 4),
        intArrayOf(3, 5),
        intArrayOf(4, 6),
        intArrayOf(5, 7)
    )
} 