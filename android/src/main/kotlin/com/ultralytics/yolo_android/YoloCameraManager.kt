package com.ultralytics.yolo_android

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/**
 * Manager class for camera operations in YoloView
 */
class YoloCameraManager(private val context: Context) {
    
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    
    // Callback for when camera is initialized
    private var cameraCreatedCallback: ((Int, Int, Int) -> Unit)? = null
    
    // Function to process frames
    private var frameProcessor: ((ImageProxy) -> Unit)? = null
    
    /**
     * Set a callback to be invoked when camera is created
     * @param callback Function that receives width, height, and facing
     */
    fun setOnCameraCreatedCallback(callback: (Int, Int, Int) -> Unit) {
        this.cameraCreatedCallback = callback
    }
    
    /**
     * Set the frame processor function
     * @param processor Function that processes each frame
     */
    fun setFrameProcessor(processor: (ImageProxy) -> Unit) {
        this.frameProcessor = processor
    }
    
    /**
     * Initialize the camera after checking permissions
     * @param previewView The PreviewView to display camera feed
     */
    fun initCamera(previewView: PreviewView) {
        if (allPermissionsGranted()) {
            startCamera(previewView)
        } else {
            val activity = context as? Activity ?: return
            ActivityCompat.requestPermissions(
                activity,
                YoloConstants.REQUIRED_PERMISSIONS,
                YoloConstants.REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    /**
     * Handle permission request results
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        previewView: PreviewView
    ) {
        if (requestCode == YoloConstants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(previewView)
            } else {
                Toast.makeText(context, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    private fun allPermissionsGranted(): Boolean = YoloConstants.REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Start the camera with the given previewView
     */
    fun startCamera(previewView: PreviewView) {
        Log.d(YoloConstants.TAG, "Starting camera...")
        
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    Log.d(YoloConstants.TAG, "Camera provider obtained")
                    
                    val preview = Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                    
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build()
                    
                    val executor = Executors.newSingleThreadExecutor()
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        frameProcessor?.invoke(imageProxy) ?: imageProxy.close()
                    }
                    
                    val cameraSelector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    
                    Log.d(YoloConstants.TAG, "Unbinding all camera use cases")
                    cameraProvider.unbindAll()
                    
                    try {
                        val lifecycleOwner = context as? LifecycleOwner
                        if (lifecycleOwner == null) {
                            Log.e(YoloConstants.TAG, "Context is not a LifecycleOwner")
                            return@addListener
                        }
                        
                        Log.d(YoloConstants.TAG, "Binding camera use cases to lifecycle")
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        
                        Log.d(YoloConstants.TAG, "Setting surface provider to previewView")
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                        Log.d(YoloConstants.TAG, "Camera setup completed successfully")
                        
                        // Add a small delay to ensure the preview view has been measured
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val width = previewView.width
                            val height = previewView.height
                            Log.d(YoloConstants.TAG, "⭐️ About to send camera created callback: ${width}x${height}, facing: $lensFacing")
                            cameraCreatedCallback?.invoke(width, height, lensFacing)
                            Log.d(YoloConstants.TAG, "⭐️ Camera created callback invoked with dimensions: ${width}x${height}")
                        }, 300) // Small delay to ensure the view is measured
                    } catch (e: Exception) {
                        Log.e(YoloConstants.TAG, "Use case binding failed", e)
                    }
                } catch (e: Exception) {
                    Log.e(YoloConstants.TAG, "Error getting camera provider", e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(YoloConstants.TAG, "Error starting camera", e)
        }
    }
    
    /**
     * Switch between front and back camera
     */
    fun switchCamera(previewView: PreviewView) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera(previewView)
    }
    
    /**
     * Get the current camera facing
     */
    fun getCurrentFacing(): Int {
        return lensFacing
    }
    
    /**
     * Release camera resources
     */
    fun releaseCamera() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            Log.d(YoloConstants.TAG, "Camera resources released")
        } catch (e: Exception) {
            Log.e(YoloConstants.TAG, "Error releasing camera", e)
        }
    }
} 