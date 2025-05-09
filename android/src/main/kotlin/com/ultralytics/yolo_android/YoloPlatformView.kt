package com.ultralytics.yolo_android

import android.content.Context
import android.util.Log
import android.view.View
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.common.BinaryMessenger
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector

/**
 * YoloPlatformView - Flutterからのネイティブビューブリッジ
 */
class YoloPlatformView(
    private val context: Context,
    viewId: Int,
    creationParams: Map<String?, Any?>?,
    private val messenger: BinaryMessenger
) : PlatformView {

    private val yoloView: YoloView = YoloView(context)
    private val TAG = "YoloPlatformView"
    private val methodChannel: MethodChannel
    
    // 初期化フラグ
    private var initialized = false
    
    // Add a disposed flag at the class level
    private var disposed = false
    
    init {
        // Create a unique channel for this view instance
        methodChannel = MethodChannel(messenger, "com.ultralytics.yolo_android/YoloMethodChannel_$viewId")
        
        // Set up method channel handler
        methodChannel.setMethodCallHandler { call, result ->
            if (disposed) {
                result.error("DISPOSED", "YoloPlatformView has been disposed", null)
                return@setMethodCallHandler
            }
            
            when (call.method) {
                "setAllowedClasses" -> {
                    try {
                        val classes = call.argument<List<String>>("classes")
                        if (classes != null) {
                            Log.d(TAG, "Setting allowed classes: $classes")
                            yoloView.setAllowedClasses(classes)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARGUMENT", "Classes list cannot be null", null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting allowed classes: ${e.message}", e)
                        result.error("SET_ALLOWED_CLASSES_ERROR", e.message, null)
                    }
                }
                "setMinConfidence" -> {
                    try {
                        val confidence = call.argument<Double>("confidence") ?: 0.25
                        yoloView.setMinConfidence(confidence.toFloat())
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting min confidence: ${e.message}", e)
                        result.error("SET_MIN_CONFIDENCE_ERROR", e.message, null)
                    }
                }
                "initCamera" -> {
                    try {
                        yoloView.initCamera()
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing camera: ${e.message}", e)
                        result.error("INIT_CAMERA_ERROR", e.message, null)
                    }
                }
                "switchCamera" -> {
                    try {
                        yoloView.switchCamera()
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error switching camera: ${e.message}", e)
                        result.error("SWITCH_CAMERA_ERROR", e.message, null)
                    }
                }
                "setShowBoxes" -> {
                    try {
                        val show = call.argument<Boolean>("show") ?: true
                        Log.d(TAG, "Setting show boxes: $show")
                        yoloView.setShowBoxes(show)
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting show boxes: ${e.message}", e)
                        result.error("SET_SHOW_BOXES_ERROR", e.message, null)
                    }
                }
                "setCustomColors" -> {
                    try {
                        val colors = call.argument<List<Int>>("colors")
                        val applyAlpha = call.argument<Boolean>("applyAlpha") ?: true
                        
                        if (colors == null || colors.isEmpty()) {
                            result.error("INVALID_COLORS", "Colors list cannot be empty", null)
                            return@setMethodCallHandler
                        }
                        
                        Log.d(TAG, "Setting custom colors: $colors, applyAlpha: $applyAlpha")
                        yoloView.setCustomColors(colors, applyAlpha)
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting custom colors: ${e.message}", e)
                        result.error("SET_CUSTOM_COLORS_ERROR", e.message, null)
                    }
                }
                "resetColors" -> {
                    try {
                        Log.d(TAG, "Resetting colors to default")
                        yoloView.resetColors()
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resetting colors: ${e.message}", e)
                        result.error("RESET_COLORS_ERROR", e.message, null)
                    }
                }
                "setLabelTextColor" -> {
                    try {
                        val color = call.argument<Int>("color")
                        if (color == null) {
                            result.error("INVALID_COLOR", "Color value cannot be null", null)
                            return@setMethodCallHandler
                        }
                        
                        Log.d(TAG, "Setting label text color: $color")
                        yoloView.setLabelTextColor(color)
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting label text color: ${e.message}", e)
                        result.error("SET_LABEL_TEXT_COLOR_ERROR", e.message, null)
                    }
                }
                "setLabelBackgroundColor" -> {
                    try {
                        val color = call.argument<Int>("color")
                        val opacity = call.argument<Double>("opacity") ?: 0.7
                        
                        if (color == null) {
                            result.error("INVALID_COLOR", "Color value cannot be null", null)
                            return@setMethodCallHandler
                        }
                        
                        Log.d(TAG, "Setting label background color: $color with opacity: $opacity")
                        yoloView.setLabelBackgroundColor(color, opacity.toFloat())
                        result.success(null)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting label background color: ${e.message}", e)
                        result.error("SET_LABEL_BG_COLOR_ERROR", e.message, null)
                    }
                }
                "getCameraInfo" -> {
                    try {
                        val width = yoloView.previewView.width
                        val height = yoloView.previewView.height
                        val facing = yoloView.getCurrentFacing()
                        val cameraInfo = mapOf(
                            "width" to width,
                            "height" to height,
                            "facing" to if (facing == CameraSelector.LENS_FACING_FRONT) "front" else "back"
                        )
                        result.success(cameraInfo)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting camera info: ${e.message}", e)
                        result.error("GET_CAMERA_INFO_ERROR", e.message, null)
                    }
                }
                "takePictureAsBytes" -> {
                    try {
                        Log.d(TAG, "Taking picture as bytes")
                        val imageBytes = yoloView.takePictureAsBytes()
                        result.success(imageBytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error taking picture: ${e.message}", e)
                        result.error("TAKE_PICTURE_ERROR", e.message, null)
                    }
                }
                "startRecording" -> {
                    try {
                        val outputPath = call.argument<String>("outputPath")
                        Log.d(TAG, "Starting recording with output path: $outputPath")
                        val recordingResult = yoloView.startRecording(outputPath)
                        
                        // Extract width and height directly from the dimensions map
                        val width = recordingResult.third["width"] ?: 0
                        val height = recordingResult.third["height"] ?: 0
                        
                        // Verify the types
                        Log.d(TAG, "Width: $width (${width.javaClass.name}), Height: $height (${height.javaClass.name})")
                        
                        // Create a simple result map with primitives
                        val resultMap = HashMap<String, Any?>()
                        resultMap["success"] = recordingResult.first
                        resultMap["reason"] = recordingResult.second
                        resultMap["width"] = width
                        resultMap["height"] = height
                        
                        // Log what we're sending to Flutter
                        Log.d(TAG, "Sending recording result to Flutter: $resultMap")
                        result.success(resultMap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting recording: ${e.message}", e)
                        result.error("START_RECORDING_ERROR", e.message, null)
                    }
                }
                "stopRecording" -> {
                    try {
                        Log.d(TAG, "Stopping recording")
                        val videoPath = yoloView.stopRecording()
                        result.success(videoPath)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping recording: ${e.message}", e)
                        result.error("STOP_RECORDING_ERROR", e.message, null)
                    }
                }
                "pauseLivePrediction" -> {
                    try {
                        val pause = call.argument<Boolean>("pause") ?: false
                        Log.d(TAG, "Setting prediction pause state: $pause")
                        val newState = yoloView.pauseLivePrediction(pause)
                        result.success(newState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting prediction pause state: ${e.message}", e)
                        result.error("PAUSE_PREDICTION_ERROR", e.message, null)
                    }
                }
                "togglePredictionPause" -> {
                    try {
                        Log.d(TAG, "Toggling prediction pause state")
                        val newState = yoloView.togglePredictionPause()
                        result.success(newState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling prediction pause state: ${e.message}", e)
                        result.error("TOGGLE_PAUSE_ERROR", e.message, null)
                    }
                }
                "isPredictionPaused" -> {
                    try {
                        val isPaused = yoloView.isPredictionPaused()
                        result.success(isPaused)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking prediction pause state: ${e.message}", e)
                        result.error("IS_PAUSED_ERROR", e.message, null)
                    }
                }
                "dispose" -> {
                    try {
                        Log.d(TAG, "Disposing YoloPlatformView")
                        dispose()
                        result.success(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during dispose: ${e.message}", e)
                        result.error("DISPOSE_ERROR", e.message, null)
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
        
        // Main thread handler
        val mainHandler = Handler(Looper.getMainLooper())
        
        // Parse model path and task from creation params
        val modelPath = creationParams?.get("modelPath") as? String ?: "yolo11n"
        val taskString = creationParams?.get("task") as? String ?: "detect"
        // Get showBoxes parameter with default value of true
        val showBoxes = creationParams?.get("showBoxes") as? Boolean ?: true
        
        try {
            // Convert task string to enum
            val task = YOLOTask.valueOf(taskString.uppercase())
            
            Log.d(TAG, "Initializing YoloPlatformView with model: $modelPath, task: $task, showBoxes: $showBoxes")
            
            // Set up callback for model loading result
            yoloView.setOnModelLoadCallback { success ->
                if (success) {
                    Log.d(TAG, "Model loaded successfully: $modelPath")
                    
                    // Initialize camera after model is loaded
                    if (!initialized) {
                        initialized = true
                        Log.d(TAG, "Initializing camera")
                        yoloView.initCamera()
                    }
                } else {
                    Log.e(TAG, "Failed to load model: $modelPath")
                }
            }
            
            // Set whether to show detection boxes
            yoloView.setShowBoxes(showBoxes)
            
            // Set up video recorder error callback
            yoloView.setVideoRecorderErrorCallback { errorCode, errorMessage ->
                Log.d(TAG, "⭐️ VideoRecorder error callback received: [$errorCode] $errorMessage")
                val errorInfo = mapOf(
                    "errorCode" to errorCode,
                    "errorMessage" to errorMessage
                )
                mainHandler.post {
                    methodChannel.invokeMethod("onVideoRecorderError", errorInfo)
                    Log.d(TAG, "⭐️ onVideoRecorderError method invoked on channel: $methodChannel")
                }
            }
            
            // Set up callback for camera creation
            yoloView.setOnCameraCreatedCallback { width, height, facing ->
                Log.d(TAG, "⭐️ Camera created callback received: ${width}x${height}, facing: $facing")
                val cameraInfo = mapOf(
                    "width" to width,
                    "height" to height,
                    "facing" to if (facing == CameraSelector.LENS_FACING_FRONT) "front" else "back"
                )
                Log.d(TAG, "⭐️ Sending onCameraCreated event to Flutter with data: $cameraInfo")
                mainHandler.post {
                    methodChannel.invokeMethod("onCameraCreated", cameraInfo)
                    Log.d(TAG, "⭐️ onCameraCreated method invoked on channel: $methodChannel")
                }
            }
            
            // Set up callback for inference results
            yoloView.setOnInferenceCallback { result ->
                Log.d(TAG, "Inference result received: ${result.boxes.size} detections")
                
                // Convert result to map for Flutter
                val resultMap = HashMap<String, Any>()
                
                // Convert boxes to list of maps
                resultMap["boxes"] = result.boxes.map { box ->
                    mapOf(
                        "x1" to box.xywh.left,
                        "y1" to box.xywh.top,
                        "x2" to box.xywh.right,
                        "y2" to box.xywh.bottom,
                        "label" to box.cls,
                        "index" to box.index,
                        "confidence" to box.conf
                    )
                }
                
                // Add task-specific data
                when (task) {
                    YOLOTask.SEGMENT -> {
                        // Handle segmentation masks if available
                        result.masks?.let { masks ->
                            resultMap["hasMasks"] = true
                            // We don't send the actual bitmap data through the channel
                            // Just a flag indicating masks are available
                        }
                    }
                    YOLOTask.POSE -> {
                        // Include pose keypoints if available
                        if (result.keypointsList.isNotEmpty()) {
                            resultMap["keypoints"] = result.keypointsList.map { keypoints ->
                                mapOf(
                                    "coordinates" to keypoints.xyn.mapIndexed { i, (x, y) ->
                                        mapOf("x" to x, "y" to y, "confidence" to keypoints.conf[i])
                                    }
                                )
                            }
                        }
                    }
                    YOLOTask.CLASSIFY -> {
                        // Include classification results if available
                        result.probs?.let { probs ->
                            resultMap["classification"] = mapOf(
                                "topClass" to probs.top1,
                                "topConfidence" to probs.top1Conf,
                                "top5Classes" to probs.top5,
                                "top5Confidences" to probs.top5Confs
                            )
                        }
                    }
                    YOLOTask.OBB -> {
                        // Include oriented bounding boxes if available
                        if (result.obb.isNotEmpty()) {
                            resultMap["obb"] = result.obb.map { obb ->
                                val poly = obb.box.toPolygon()
                                mapOf(
                                    "points" to poly.map { mapOf("x" to it.x, "y" to it.y) },
                                    "label" to obb.cls,
                                    "index" to obb.index,
                                    "confidence" to obb.confidence
                                )
                            }
                        }
                    }
                    else -> {} // DETECT is handled by boxes
                }
                
                // Send result to Flutter
                mainHandler.post {
                    methodChannel.invokeMethod("onDetectionResult", resultMap)
                }
            }
            
            // Load model with the specified path and task
            yoloView.setModel(modelPath, task, context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing YoloPlatformView", e)
        }
    }

    override fun getView(): View {
        Log.d(TAG, "【ログ追加】Getting view: ${yoloView.javaClass.simpleName}, context is: ${context.javaClass.simpleName}")
        
        // コンテキストがLifecycleOwnerかチェック
        if (context is androidx.lifecycle.LifecycleOwner) {
            val lifecycleOwner = context as androidx.lifecycle.LifecycleOwner
            Log.d(TAG, "【ログ追加】Context is a LifecycleOwner with state: ${lifecycleOwner.lifecycle.currentState}")
        } else {
            Log.e(TAG, "【ログ追加】Context is NOT a LifecycleOwner! This may cause camera issues.")
        }
        
        // カスタムレイアウトパラメータを設定してみる
        if (yoloView.layoutParams == null) {
            yoloView.layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            Log.d(TAG, "【ログ追加】Set layout params for YoloView")
        }
        
        return yoloView
    }

    override fun dispose() {
        Log.d(TAG, "Disposing YoloPlatformView")
        try {
            // Mark the view as disposed to prevent further method calls
            disposed = true
            
            // Dispose of YoloView resources first
            yoloView.dispose()

            // Clear method channel handler to prevent further calls
            methodChannel.setMethodCallHandler(null)
            
            Log.d(TAG, "YoloPlatformView disposed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error disposing YoloPlatformView", e)
        }
    }
}