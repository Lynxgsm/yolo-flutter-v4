package com.ultralytics.yolo_android

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.util.Log
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.BinaryMessenger
import java.io.ByteArrayOutputStream
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class YoloPlugin : FlutterPlugin, ActivityAware, MethodChannel.MethodCallHandler {

  private lateinit var methodChannel: MethodChannel
  private var yolo: YOLO? = null
  private lateinit var applicationContext: android.content.Context
  private var activity: Activity? = null
  private val TAG = "YoloPlugin"
  private lateinit var viewFactory: YoloPlatformViewFactory
  private lateinit var messenger: BinaryMessenger
  
  // Video recording fields
  private var mediaRecorder: MediaRecorder? = null
  private var isRecording = false
  private var videoOutputFile: File? = null
  private var recordingStartTime: Long = 0
  
  // Request codes for permissions
  companion object {
    private const val REQUEST_CAMERA_PERMISSION = 100
    private const val REQUEST_RECORD_AUDIO_PERMISSION = 101
    private const val REQUEST_STORAGE_PERMISSION = 102
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    // Store application context for later use
    applicationContext = flutterPluginBinding.applicationContext
    
    // Store messenger reference
    messenger = flutterPluginBinding.binaryMessenger

    // Create and store the view factory with messenger for later activity updates
    viewFactory = YoloPlatformViewFactory(messenger)
    
    // Register platform view
    flutterPluginBinding.platformViewRegistry.registerViewFactory(
      "com.ultralytics.yolo_android/YoloPlatformView",
      viewFactory
    )

    // Register method channel for single-image
    methodChannel = MethodChannel(
      flutterPluginBinding.binaryMessenger,
      "yolo_single_image_channel"
    )
    methodChannel.setMethodCallHandler(this)
    
    Log.d(TAG, "YoloPlugin attached to engine")
  }
  
  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    // Update the view factory with the activity reference
    viewFactory.setActivity(activity)
    Log.d(TAG, "YoloPlugin attached to activity: ${activity?.javaClass?.simpleName}")
  }

  override fun onDetachedFromActivityForConfigChanges() {
    Log.d(TAG, "YoloPlugin detached from activity for config changes")
    activity = null
    viewFactory.setActivity(null)
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    // Update the view factory with the activity reference
    viewFactory.setActivity(activity)
    Log.d(TAG, "YoloPlugin reattached to activity: ${activity?.javaClass?.simpleName}")
  }

  override fun onDetachedFromActivity() {
    Log.d(TAG, "YoloPlugin detached from activity")
    activity = null
    viewFactory.setActivity(null)
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    methodChannel.setMethodCallHandler(null)
    releaseMediaRecorder() // Release media recorder resources when plugin is detached
    Log.d(TAG, "YoloPlugin detached from engine")
    // YOLO class doesn't need explicit release
  }

  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "loadModel" -> {
        try {
          val args = call.arguments as? Map<*, *>
          val modelPath = args?.get("modelPath") as? String ?: "yolo11n"
          val taskString = args?.get("task") as? String ?: "detect"
          
          // Convert task string to enum
          val task = YOLOTask.valueOf(taskString.uppercase())
          
          // Load labels (in real implementation, you would load from metadata)
          val labels = loadLabels(modelPath)
          
          // Initialize YOLO with new implementation
          yolo = YOLO(
            context = applicationContext,
            modelPath = modelPath,
            task = task,
            labels = labels,
            useGpu = true
          )
          
          Log.d(TAG, "Model loaded successfully: $modelPath for task: $task")
          result.success(true)
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load model", e)
          result.error("model_error", "Failed to load model: ${e.message}", null)
        }
      }

      "predictSingleImage" -> {
        try {
          val args = call.arguments as? Map<*, *>
          val imageData = args?.get("image") as? ByteArray

          if (imageData == null) {
            result.error("bad_args", "No image data", null)
            return
          }
          
          if (yolo == null) {
            result.error("not_initialized", "Model not loaded", null)
            return
          }
          
          // Convert byte array to bitmap
          val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
          if (bitmap == null) {
            result.error("image_error", "Failed to decode image", null)
            return
          }
          
          // Run inference with new YOLO implementation
          val yoloResult = yolo!!.predict(bitmap, rotateForCamera = false)
          
          // Create response
          val response = HashMap<String, Any>()
          
          // Convert boxes to map for Flutter
          response["boxes"] = yoloResult.boxes.map { box ->
            mapOf(
              "x1" to box.xywh.left,
              "y1" to box.xywh.top,
              "x2" to box.xywh.right,
              "y2" to box.xywh.bottom,
              "class" to box.cls,
              "confidence" to box.conf
            )
          }
          
          // Add task-specific data to response
          when (yolo!!.task) {
            YOLOTask.SEGMENT -> {
              // Include segmentation mask if available
              yoloResult.masks?.combinedMask?.let { mask ->
                val stream = ByteArrayOutputStream()
                mask.compress(Bitmap.CompressFormat.PNG, 90, stream)
                response["mask"] = stream.toByteArray()
              }
            }
            YOLOTask.CLASSIFY -> {
              // Include classification results if available
              yoloResult.probs?.let { probs ->
                response["classification"] = mapOf(
                  "topClass" to probs.top1,
                  "topConfidence" to probs.top1Conf,
                  "top5Classes" to probs.top5,
                  "top5Confidences" to probs.top5Confs
                )
              }
            }
            YOLOTask.POSE -> {
              // Include pose keypoints if available
              if (yoloResult.keypointsList.isNotEmpty()) {
                response["keypoints"] = yoloResult.keypointsList.map { keypoints ->
                  mapOf(
                    "coordinates" to keypoints.xyn.mapIndexed { i, (x, y) ->
                      mapOf("x" to x, "y" to y, "confidence" to keypoints.conf[i])
                    }
                  )
                }
              }
            }
            YOLOTask.OBB -> {
              // Include oriented bounding boxes if available
              if (yoloResult.obb.isNotEmpty()) {
                response["obb"] = yoloResult.obb.map { obb ->
                  val poly = obb.box.toPolygon()
                  mapOf(
                    "points" to poly.map { mapOf("x" to it.x, "y" to it.y) },
                    "class" to obb.cls,
                    "confidence" to obb.confidence
                  )
                }
              }
            }
            else -> {} // DETECT is handled by boxes
          }
          
          // Include annotated image in response
          yoloResult.annotatedImage?.let { annotated ->
            val stream = ByteArrayOutputStream()
            annotated.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            response["annotatedImage"] = stream.toByteArray()
          }
          
          // Include inference speed
          response["speed"] = yoloResult.speed
          
          result.success(response)
        } catch (e: Exception) {
          Log.e(TAG, "Error during prediction", e)
          result.error("prediction_error", "Error during prediction: ${e.message}", null)
        }
      }
      
      "startRecording" -> {
        try {
          val args = call.arguments as? Map<*, *>
          var outputPath = args?.get("outputPath") as? String
          
          // Check if the model is loaded
          if (yolo == null) {
            result.error("MODEL_NOT_LOADED", "Model not loaded. Call loadModel() first.", null)
            return
          }
          
          // Check if we have the necessary permissions
          if (!checkAndRequestPermissions()) {
            result.error("CAMERA_PERMISSION_DENIED", "Camera or storage permission denied", null)
            return
          }
          
          // Check if already recording
          if (isRecording) {
            result.success(true) // Already recording
            return
          }
          
          // Create output file if not provided
          if (outputPath == null) {
            val videoFile = createVideoFile()
            outputPath = videoFile.absolutePath
            videoOutputFile = videoFile
          } else {
            videoOutputFile = File(outputPath)
          }
          
          // Set up media recorder
          if (setupMediaRecorder(videoOutputFile!!)) {
            // Start recording
            try {
              mediaRecorder?.start()
              isRecording = true
              recordingStartTime = System.currentTimeMillis()
              Log.d(TAG, "Video recording started: $outputPath")
              result.success(true)
            } catch (e: Exception) {
              Log.e(TAG, "Failed to start recording", e)
              releaseMediaRecorder()
              result.error("RECORDING_ERROR", "Failed to start recording: ${e.message}", null)
            }
          } else {
            result.error("RECORDING_ERROR", "Failed to set up media recorder", null)
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error in startRecording", e)
          result.error("RECORDING_ERROR", "Error starting recording: ${e.message}", null)
        }
      }
      
      "stopRecording" -> {
        try {
          if (!isRecording || mediaRecorder == null) {
            result.error("RECORDING_ERROR", "No active recording to stop", null)
            return
          }
          
          try {
            // Stop recording
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            
            // Get file path
            val videoPath = videoOutputFile?.absolutePath ?: ""
            
            Log.d(TAG, "Video recording stopped: $videoPath")
            
            // Reset recording state
            isRecording = false
            releaseMediaRecorder()
            
            result.success(videoPath)
          } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            releaseMediaRecorder()
            result.error("RECORDING_ERROR", "Failed to stop recording: ${e.message}", null)
          }
        } catch (e: Exception) {
          Log.e(TAG, "Error in stopRecording", e)
          result.error("RECORDING_ERROR", "Error stopping recording: ${e.message}", null)
        }
      }

      else -> result.notImplemented()
    }
  }
  
  // Helper function to load labels
  private fun loadLabels(modelPath: String): List<String> {
    // This is a placeholder - in a real implementation, you would load labels from metadata
    return listOf(
      "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
      "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
      "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack"
    )
  }
  
  // Helper methods for video recording
  
  private fun checkAndRequestPermissions(): Boolean {
    if (activity == null) return false
    
    val requiredPermissions = mutableListOf<String>()
    
    // Check camera permission
    if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) 
        != PackageManager.PERMISSION_GRANTED) {
      requiredPermissions.add(Manifest.permission.CAMERA)
    }
    
    // Check storage permissions based on Android version
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
      if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
          != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }
      if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_EXTERNAL_STORAGE) 
          != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
      }
    } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
      if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_EXTERNAL_STORAGE) 
          != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
      }
    } else {
      if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_MEDIA_VIDEO) 
          != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.READ_MEDIA_VIDEO)
      }
      if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.READ_MEDIA_IMAGES) 
          != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
      }
    }
    
    if (requiredPermissions.isNotEmpty()) {
      ActivityCompat.requestPermissions(
        activity!!,
        requiredPermissions.toTypedArray(),
        REQUEST_CAMERA_PERMISSION
      )
      return false
    }
    
    return true
  }
  
  private fun createVideoFile(): File {
    // Create a unique filename for the video
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val videoFileName = "YOLO_VIDEO_$timeStamp"
    
    // Get the directory for storing videos
    val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    } else {
      Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }
    
    // Create the storage directory if it does not exist
    if (storageDir != null && !storageDir.exists()) {
      storageDir.mkdirs()
    }
    
    return File(storageDir, "$videoFileName.mp4")
  }
  
  private fun setupMediaRecorder(outputFile: File): Boolean {
    try {
      // Initialize MediaRecorder
      mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(applicationContext)
      } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
      }
      
      // Configure MediaRecorder
      mediaRecorder?.apply {
        setVideoSource(MediaRecorder.VideoSource.CAMERA)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setVideoSize(1280, 720) // HD resolution
        setVideoFrameRate(30)
        setVideoEncodingBitRate(10000000) // 10 Mbps
        setOutputFile(outputFile.absolutePath)
        
        try {
          prepare()
          return true
        } catch (e: IOException) {
          Log.e(TAG, "Error preparing media recorder", e)
          releaseMediaRecorder()
          return false
        }
      }
      
      return false
    } catch (e: Exception) {
      Log.e(TAG, "Error setting up media recorder", e)
      return false
    }
  }
  
  private fun releaseMediaRecorder() {
    try {
      mediaRecorder?.reset()
      mediaRecorder?.release()
      mediaRecorder = null
      isRecording = false
    } catch (e: Exception) {
      Log.e(TAG, "Error releasing media recorder", e)
    }
  }
}