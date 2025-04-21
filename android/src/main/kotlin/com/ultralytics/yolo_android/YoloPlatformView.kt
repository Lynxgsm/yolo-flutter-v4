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
    
    init {
        // Create a unique channel for this view instance
        methodChannel = MethodChannel(messenger, "com.ultralytics.yolo_android/YoloMethodChannel_$viewId")
        
        // Set up method channel handler
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "setAllowedClasses" -> {
                    try {
                        val classes = call.argument<List<String>>("classes") ?: listOf()
                        yoloView.setAllowedClasses(classes)
                        result.success(null)
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
    }
}