// lib/yolo_view.dart

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io';
import 'dart:typed_data';
import 'package:ultralytics_yolo_android/yolo_task.dart';
import 'package:ultralytics_yolo_android/yolo_result.dart';

/// Controller for YoloView
///
/// Used to control filtering settings for YoloView
class YoloViewController {
  final GlobalKey<_YoloViewState> _key = GlobalKey<_YoloViewState>();
  MethodChannel? _methodChannel;
  bool _isRecording = false;

  /// Set allowed classes for filtering detections
  ///
  /// Pass an empty list to show all classes.
  Future<void> setAllowedClasses(List<String> classes) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setAllowedClasses', {
          'classes': classes,
        });
      } catch (e) {
        print('Error setting allowed classes: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Set minimum confidence threshold for showing detections
  ///
  /// Value should be between 0.0 and 1.0
  Future<void> setMinConfidence(double confidence) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setMinConfidence', {
          'confidence': confidence,
        });
      } catch (e) {
        print('Error setting min confidence: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Initialize the camera if it hasn't been initialized yet
  ///
  /// This is useful if you need to manually trigger camera initialization
  Future<void> initCamera() async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('initCamera');
      } catch (e) {
        print('Error initializing camera: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Switch between front and back camera
  Future<void> switchCamera() async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('switchCamera');
      } catch (e) {
        print('Error switching camera: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Set whether to show detection boxes
  ///
  /// This allows toggling detection box visibility at runtime
  Future<void> setShowBoxes(bool show) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setShowBoxes', {
          'show': show,
        });
      } catch (e) {
        print('Error setting show boxes: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Set custom colors for detection boxes and labels
  ///
  /// @param colors List of color values in ARGB format (0xAARRGGBB)
  /// @param applyAlpha Whether to apply confidence as alpha. If true, the alpha channel will be adjusted based on detection confidence
  Future<void> setCustomColors(List<int> colors,
      {bool applyAlpha = true}) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setCustomColors', {
          'colors': colors,
          'applyAlpha': applyAlpha,
        });
      } catch (e) {
        print('Error setting custom colors: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Reset colors to default
  Future<void> resetColors() async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('resetColors');
      } catch (e) {
        print('Error resetting colors: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Set label text color
  ///
  /// @param color The color value in ARGB format (0xAARRGGBB)
  Future<void> setLabelTextColor(int color) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setLabelTextColor', {
          'color': color,
        });
      } catch (e) {
        print('Error setting label text color: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Set label background color with optional transparency
  ///
  /// @param color The base color value in ARGB format (0xAARRGGBB)
  /// @param opacity The opacity value between 0.0 (fully transparent) and 1.0 (fully opaque)
  Future<void> setLabelBackgroundColor(int color,
      {double opacity = 0.7}) async {
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('setLabelBackgroundColor', {
          'color': color,
          'opacity': opacity,
        });
      } catch (e) {
        print('Error setting label background color: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
  }

  /// Get current camera information asynchronously
  ///
  /// Returns a map with camera details such as:
  /// - width: The width of the camera preview
  /// - height: The height of the camera preview
  /// - facing: "front" or "back" indicating which camera is active
  ///
  /// Returns an empty map if the camera information is not available.
  Future<Map<String, dynamic>> getCameraInfo() async {
    print('YoloViewController.getCameraInfo, channel: $_methodChannel');
    if (_methodChannel != null) {
      try {
        final result = await _methodChannel!.invokeMethod('getCameraInfo');
        if (result != null && result is Map) {
          return Map<String, dynamic>.from(result);
        }
      } catch (e) {
        print('Error getting camera info: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
    }
    return <String, dynamic>{};
  }

  /// Checks if video recording is currently in progress
  ///
  /// Returns true if recording is active, false otherwise
  bool isRecording() {
    return _isRecording;
  }

  /// Starts recording video while performing YOLO predictions.
  ///
  /// This method is only available on Android devices. On other platforms,
  /// it throws a [PlatformNotSupportedException].
  ///
  /// [outputPath] is the file path where the video will be saved.
  /// If not provided, a default path will be used.
  ///
  /// Returns true if recording started successfully, false otherwise.
  ///
  /// @throws [PlatformNotSupportedException] if called on non-Android platform
  /// @throws [ModelNotLoadedException] if the model has not been loaded
  /// @throws [RecordingException] if there's an error starting the recording
  Future<bool> startRecording({String? outputPath}) async {
    if (!Platform.isAndroid) {
      throw PlatformNotSupportedException(
          'Video recording is only supported on Android');
    }

    if (_isRecording) {
      return true; // Already recording
    }

    if (_methodChannel != null) {
      try {
        final result = await _methodChannel!.invokeMethod('startRecording', {
          'outputPath': outputPath,
        });

        _isRecording = result == true;
        return _isRecording;
      } on PlatformException catch (e) {
        if (e.code == 'MODEL_NOT_LOADED') {
          throw ModelNotLoadedException(
              'Model has not been loaded. Call loadModel() first.');
        } else if (e.code == 'CAMERA_PERMISSION_DENIED') {
          throw RecordingException('Camera permission denied');
        } else if (e.code == 'STORAGE_PERMISSION_DENIED') {
          throw RecordingException('Storage permission denied');
        } else {
          throw RecordingException('Failed to start recording: ${e.message}');
        }
      } catch (e) {
        throw RecordingException('Unknown error starting recording: $e');
      }
    } else {
      print('Warning: Method channel not initialized yet');
      return false;
    }
  }

  /// Stops the current video recording session.
  ///
  /// This method is only available on Android devices. On other platforms,
  /// it throws a [PlatformNotSupportedException].
  ///
  /// Returns the path to the saved video file.
  ///
  /// @throws [PlatformNotSupportedException] if called on non-Android platform
  /// @throws [RecordingException] if there's an error stopping the recording
  /// Returns null if recording couldn't be saved properly.
  Future<String?> stopRecording() async {
    if (!Platform.isAndroid) {
      throw PlatformNotSupportedException(
          'Video recording is only supported on Android');
    }

    if (!_isRecording) {
      throw RecordingException('No active recording to stop');
    }

    if (_methodChannel != null) {
      try {
        final result = await _methodChannel!.invokeMethod('stopRecording');
        _isRecording = false;
        if (result == null) {
          return null;
        }
        return result as String;
      } on PlatformException catch (e) {
        throw RecordingException('Failed to stop recording: ${e.message}');
      } catch (e) {
        throw RecordingException('Unknown error stopping recording: $e');
      }
    } else {
      throw RecordingException('Method channel not initialized');
    }
  }

  /// Captures the current camera view as a byte array.
  ///
  /// This method takes a snapshot of what's currently displayed in the camera view,
  /// including any detection boxes if they are enabled.
  ///
  /// Returns the image as a Uint8List containing the JPEG-encoded image data.
  ///
  /// @throws [PlatformNotSupportedException] if called on non-Android platform
  /// @throws [CaptureException] if there's an error capturing the image
  Future<Uint8List> takePictureAsBytes() async {
    if (!Platform.isAndroid) {
      throw PlatformNotSupportedException(
          'Picture capture is only supported on Android');
    }

    if (_methodChannel != null) {
      try {
        final result = await _methodChannel!.invokeMethod('takePictureAsBytes');
        if (result is Uint8List) {
          return result;
        } else {
          throw CaptureException('Invalid return type from takePictureAsBytes');
        }
      } on PlatformException catch (e) {
        throw CaptureException('Failed to capture picture: ${e.message}');
      } catch (e) {
        throw CaptureException('Unknown error capturing picture: $e');
      }
    } else {
      throw CaptureException('Method channel not initialized');
    }
  }

  /// Pauses or resumes live predictions. When paused, the camera feed continues
  /// but no inference is performed, saving CPU/GPU resources.
  ///
  /// @param pause True to pause predictions, false to resume
  /// @return The new pause state
  Future<bool> pauseLivePrediction(bool pause) async {
    if (_methodChannel != null) {
      try {
        final result =
            await _methodChannel!.invokeMethod('pauseLivePrediction', {
          'pause': pause,
        });
        return result ?? pause;
      } catch (e) {
        print('Error changing prediction pause state: $e');
        return pause;
      }
    } else {
      print('Warning: Method channel not initialized yet');
      return pause;
    }
  }

  /// Toggles the prediction pause state
  ///
  /// @return The new pause state (true if now paused)
  Future<bool> togglePredictionPause() async {
    if (_methodChannel != null) {
      try {
        final result =
            await _methodChannel!.invokeMethod('togglePredictionPause');
        return result ?? false;
      } catch (e) {
        print('Error toggling prediction pause state: $e');
        return false;
      }
    } else {
      print('Warning: Method channel not initialized yet');
      return false;
    }
  }

  /// Checks if predictions are currently paused
  ///
  /// @return True if predictions are paused, false otherwise
  Future<bool> isPredictionPaused() async {
    if (_methodChannel != null) {
      try {
        final result = await _methodChannel!.invokeMethod('isPredictionPaused');
        return result ?? false;
      } catch (e) {
        print('Error checking prediction pause state: $e');
        return false;
      }
    } else {
      print('Warning: Method channel not initialized yet');
      return false;
    }
  }

  /// Disposes the controller and releases any resources.
  ///
  /// This should be called when the controller is no longer needed to ensure
  /// proper cleanup of resources.
  Future<void> dispose() async {
    // Stop any active recording first
    if (_isRecording && Platform.isAndroid) {
      try {
        await stopRecording();
      } catch (e) {
        print('Error stopping recording during dispose: $e');
      }
    }

    // Invoke native dispose if available
    if (_methodChannel != null) {
      try {
        await _methodChannel!.invokeMethod('dispose');
      } catch (e) {
        print('Error disposing native resources: $e');
      }
    }

    // Clear references
    _methodChannel = null;
  }

  /// Internal key used by YoloView
  GlobalKey<_YoloViewState> get key => _key;

  /// Internal method to set the method channel
  void _setMethodChannel(MethodChannel channel) {
    _methodChannel = channel;
    print('YoloViewController: Method channel set to $_methodChannel');
  }
}

/// Exception thrown when recording video fails
class RecordingException implements Exception {
  final String message;
  RecordingException(this.message);

  @override
  String toString() => 'RecordingException: $message';
}

/// Exception thrown when a feature is not supported on the current platform
class PlatformNotSupportedException implements Exception {
  final String message;
  PlatformNotSupportedException(this.message);

  @override
  String toString() => 'PlatformNotSupportedException: $message';
}

/// Exception thrown when a model is not loaded
class ModelNotLoadedException implements Exception {
  final String message;
  ModelNotLoadedException(this.message);

  @override
  String toString() => 'ModelNotLoadedException: $message';
}

/// Exception thrown when capturing a picture fails
class CaptureException implements Exception {
  final String message;
  CaptureException(this.message);

  @override
  String toString() => 'CaptureException: $message';
}

/// A Flutter widget that displays a platform view for YOLO object detection.
///
/// This widget creates a native view that runs YOLO models directly using the device's
/// camera or for processing static images. It supports object detection, segmentation,
/// classification, pose estimation, and oriented bounding box detection.
///
/// The widget handles platform-specific implementations for both Android and iOS.
///
/// Example usage:
/// ```dart
/// // Create a controller
/// final controller = YoloViewController();
///
/// YoloView(
///   controller: controller,
///   modelPath: 'assets/models/yolo11n.tflite',
///   task: YOLOTask.detect,
///   onResult: (results) {
///     // Handle the detection results
///     for (var result in results) {
///       print('Detected: ${result.label}');
///     }
///   },
///   showBoxes: true, // Control visibility of detection boxes
/// )
///
/// // Later, filter detections
/// controller.setAllowedClasses(['person', 'car']);
/// controller.setMinConfidence(0.5);
/// ```
class YoloView extends StatefulWidget {
  /// Path to the YOLO model file. This should be a TFLite model file.
  final String modelPath;

  /// The type of task this YOLO model will perform (detection, segmentation, etc.)
  final YOLOTask task;

  /// The [onResult] is a callback function that will be called when the inference results are available.
  final Function(List<YOLOResult>) onResult;

  /// Optional callback that is invoked when the camera is initialized and ready.
  /// The camera info map contains camera details such as:
  /// - width: The width of the camera preview
  /// - height: The height of the camera preview
  /// - facing: "front" or "back" indicating which camera is active
  final Function(Map<String, dynamic> cameraInfo)? onCameraCreated;

  /// Whether to display detection boxes in the view.
  final bool showBoxes;

  /// Optional controller to manage filtering and other settings.
  final YoloViewController? controller;

  /// Creates a YoloView widget for live camera detection
  const YoloView({
    super.key,
    required this.modelPath,
    required this.task,
    required this.onResult,
    this.showBoxes = true,
    this.controller,
    this.onCameraCreated,
  });

  /// Set allowed classes for filtering detections
  ///
  /// Pass an empty list to show all classes.
  static Future<void> setAllowedClasses(
      BuildContext context, List<String> classes) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setAllowedClasses(classes);
    }
  }

  /// Set minimum confidence threshold for showing detections
  ///
  /// Value should be between 0.0 and 1.0
  static Future<void> setMinConfidence(
      BuildContext context, double confidence) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setMinConfidence(confidence);
    }
  }

  /// Set whether to show detection boxes
  ///
  /// This allows toggling detection box visibility at runtime
  static Future<void> setShowBoxes(BuildContext context, bool show) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setShowBoxes(show);
    }
  }

  /// Set custom colors for detection boxes and labels
  ///
  /// @param colors List of color values in ARGB format (0xAARRGGBB)
  /// @param applyAlpha Whether to apply confidence as alpha. If true, the alpha channel will be adjusted based on detection confidence
  static Future<void> setCustomColors(BuildContext context, List<int> colors,
      {bool applyAlpha = true}) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setCustomColors(colors, applyAlpha: applyAlpha);
    }
  }

  /// Reset colors to default
  static Future<void> resetColors(BuildContext context) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.resetColors();
    }
  }

  /// Set label text color
  ///
  /// @param color The color value in ARGB format (0xAARRGGBB)
  static Future<void> setLabelTextColor(BuildContext context, int color) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setLabelTextColor(color);
    }
  }

  /// Set label background color with optional transparency
  ///
  /// @param color The base color value in ARGB format (0xAARRGGBB)
  /// @param opacity The opacity value between 0.0 (fully transparent) and 1.0 (fully opaque)
  static Future<void> setLabelBackgroundColor(BuildContext context, int color,
      {double opacity = 0.7}) async {
    final state = context.findAncestorStateOfType<_YoloViewState>();
    if (state != null) {
      await state.setLabelBackgroundColor(color, opacity: opacity);
    }
  }

  @override
  State<YoloView> createState() => _YoloViewState();
}

class _YoloViewState extends State<YoloView> {
  late MethodChannel _methodChannel;

  @override
  void initState() {
    super.initState();
    // Initialize the method channel for communication with the native code
    _methodChannel =
        const MethodChannel('com.ultralytics.yolo_android/YoloMethodChannel');
    _methodChannel.setMethodCallHandler(_handleMethodCall);
  }

  // Handle incoming method calls from the native side
  Future<dynamic> _handleMethodCall(MethodCall call) async {
    print(
        'YoloView received method call: ${call.method}, args: ${call.arguments}');
    switch (call.method) {
      case 'onDetectionResult':
        // Convert results to a list of YOLOResult objects and call the callback
        final resultMap = Map<String, dynamic>.from(call.arguments);
        final results = _extractDetections(resultMap);
        widget.onResult(results);
        return null;
      case 'onCameraCreated':
        print(
            'Camera created and ready, calling onCameraCreated with: ${call.arguments}');
        if (widget.onCameraCreated != null) {
          final cameraInfo = call.arguments != null
              ? Map<String, dynamic>.from(call.arguments)
              : <String, dynamic>{};
          widget.onCameraCreated!(cameraInfo);
          print('onCameraCreated callback executed');
        } else {
          print('onCameraCreated callback is null');
        }
        return null;
      default:
        return null;
    }
  }

  /// Set allowed classes for filtering detections
  ///
  /// Pass an empty list to show all classes.
  Future<void> setAllowedClasses(List<String> classes) async {
    try {
      print(
          '_YoloViewState.setAllowedClasses: $classes using channel $_methodChannel');
      final result = await _methodChannel.invokeMethod('setAllowedClasses', {
        'classes': classes,
      });
      print('setAllowedClasses result: $result');
    } catch (e) {
      print('Error setting allowed classes: $e');
    }
  }

  /// Set minimum confidence threshold for showing detections
  ///
  /// Value should be between 0.0 and 1.0
  Future<void> setMinConfidence(double confidence) async {
    try {
      await _methodChannel.invokeMethod('setMinConfidence', {
        'confidence': confidence,
      });
    } catch (e) {
      print('Error setting min confidence: $e');
    }
  }

  /// Set whether to show detection boxes
  ///
  /// This allows toggling detection box visibility at runtime
  Future<void> setShowBoxes(bool show) async {
    try {
      await _methodChannel.invokeMethod('setShowBoxes', {
        'show': show,
      });
    } catch (e) {
      print('Error setting show boxes: $e');
    }
  }

  /// Set custom colors for detection boxes and labels
  ///
  /// @param colors List of color values in ARGB format (0xAARRGGBB)
  /// @param applyAlpha Whether to apply confidence as alpha. If true, the alpha channel will be adjusted based on detection confidence
  Future<void> setCustomColors(List<int> colors,
      {bool applyAlpha = true}) async {
    try {
      await _methodChannel.invokeMethod('setCustomColors', {
        'colors': colors,
        'applyAlpha': applyAlpha,
      });
    } catch (e) {
      print('Error setting custom colors: $e');
    }
  }

  /// Reset colors to default
  Future<void> resetColors() async {
    try {
      await _methodChannel.invokeMethod('resetColors');
    } catch (e) {
      print('Error resetting colors: $e');
    }
  }

  /// Set label text color
  ///
  /// @param color The color value in ARGB format (0xAARRGGBB)
  Future<void> setLabelTextColor(int color) async {
    try {
      await _methodChannel.invokeMethod('setLabelTextColor', {
        'color': color,
      });
    } catch (e) {
      print('Error setting label text color: $e');
    }
  }

  /// Set label background color with optional transparency
  ///
  /// @param color The base color value in ARGB format (0xAARRGGBB)
  /// @param opacity The opacity value between 0.0 (fully transparent) and 1.0 (fully opaque)
  Future<void> setLabelBackgroundColor(int color,
      {double opacity = 0.7}) async {
    try {
      await _methodChannel.invokeMethod('setLabelBackgroundColor', {
        'color': color,
        'opacity': opacity,
      });
    } catch (e) {
      print('Error setting label background color: $e');
    }
  }

  // Extract detection results from the result map
  List<YOLOResult> _extractDetections(Map<String, dynamic> resultMap) {
    final results = <YOLOResult>[];

    if (resultMap.containsKey('boxes') && resultMap['boxes'] is List) {
      final boxes = resultMap['boxes'] as List;

      for (var box in boxes) {
        if (box is Map) {
          final detectionMap = Map<String, dynamic>.fromEntries(
              box.entries.map((e) => MapEntry(e.key.toString(), e.value)));

          results.add(YOLOResult.fromMap(detectionMap));
        }
      }
    }

    return results;
  }

  @override
  Widget build(BuildContext context) {
    const viewType = 'com.ultralytics.yolo_android/YoloPlatformView';
    final creationParams = <String, dynamic>{
      'modelPath': widget.modelPath,
      'task': widget.task.name,
      'showBoxes': widget.showBoxes,
    };

    if (defaultTargetPlatform == TargetPlatform.android) {
      return AndroidView(
        key: widget.controller?.key,
        viewType: viewType,
        layoutDirection: TextDirection.ltr,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
        key: widget.controller?.key,
        viewType: viewType,
        layoutDirection: TextDirection.ltr,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    }

    // fallback for unsupported platforms
    return const Text('Platform not supported for YoloView');
  }

  // Called when the platform view is created
  void _onPlatformViewCreated(int id) {
    print('YoloView: Platform view created with ID $id');

    // Update the method channel with the unique id
    final channelName = 'com.ultralytics.yolo_android/YoloMethodChannel_$id';
    print('Setting up method channel: $channelName');

    // Dispose of the previous method channel handler if any
    _methodChannel.setMethodCallHandler(null);

    // Create the new method channel with the correct ID
    _methodChannel = MethodChannel(channelName);

    // Set up the method call handler
    _methodChannel.setMethodCallHandler(_handleMethodCall);
    print('Method call handler registered for channel: $_methodChannel');

    // If a controller was provided, update its method channel
    if (widget.controller != null) {
      widget.controller!._setMethodChannel(_methodChannel);
      print('Controller method channel updated: $_methodChannel');
    }
  }
}
