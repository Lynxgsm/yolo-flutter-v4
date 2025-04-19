package com.ultralytics.yolo

import android.content.Context
import android.util.Log
import android.view.View
import io.flutter.plugin.platform.PlatformView

/**
 * YoloPlatformView - Flutterからのネイティブビューブリッジ
 */
class YoloPlatformView(
    private val context: Context,
    viewId: Int,
    creationParams: Map<String?, Any?>?
) : PlatformView {

    private val yoloView: YoloView = YoloView(context)
    private val TAG = "YoloPlatformView"
    
    // 初期化フラグ
    private var initialized = false
    
    init {
        // Parse model path and task from creation params
        val modelPath = creationParams?.get("modelPath") as? String ?: "yolo11n"
        val taskString = creationParams?.get("task") as? String ?: "detect"
        
        try {
            // Convert task string to enum
            val task = YOLOTask.valueOf(taskString.uppercase())
            
            Log.d(TAG, "Initializing YoloPlatformView with model: $modelPath, task: $task")
            
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
            
            // Set up callback for inference results if needed
            yoloView.setOnInferenceCallback { result ->
                // ログ出力のみ
                Log.d(TAG, "Inference result received: ${result.boxes.size} detections")
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