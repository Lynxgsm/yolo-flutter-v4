// lib/yolo.dart

import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:ultralytics_yolo_android/yolo_task.dart';
import 'package:ultralytics_yolo_android/yolo_exceptions.dart';
import 'package:ultralytics_yolo_android/yolo_result.dart';
import 'dart:io' show Platform;

/// Exports all YOLO-related classes and enums
export 'yolo_task.dart';
export 'yolo_exceptions.dart';
export 'yolo_result.dart';

/// YOLO (You Only Look Once) is a class that provides machine learning inference
/// capabilities for object detection, segmentation, classification, pose estimation,
/// and oriented bounding box detection.
///
/// This class handles the initialization of YOLO models and provides methods
/// to perform inference on images.
///
/// Example usage:
/// ```dart
/// final yolo = YOLO(
///   modelPath: 'assets/models/yolo11n.tflite',
///   task: YOLOTask.detect,
/// );
///
/// await yolo.loadModel();
/// final results = await yolo.predict(imageBytes);
/// ```
class YOLO {
  // We'll store a method channel for calling native code
  static const _channel = MethodChannel('yolo_single_image_channel');

  /// Path to the YOLO model file. This should be a TFLite model file.
  final String modelPath;

  /// The type of task this YOLO model will perform (detection, segmentation, etc.)
  final YOLOTask task;

  /// Flag indicating if recording is currently active
  bool _isRecording = false;

  /// Creates a new YOLO instance with the specified model path and task.
  ///
  /// The [modelPath] should point to a valid TFLite model file.
  /// The [task] specifies what type of inference will be performed.
  YOLO({
    required this.modelPath,
    required this.task,
  });

  /// Loads the YOLO model for inference.
  ///
  /// This method must be called before [predict] to initialize the model.
  /// Returns `true` if the model was loaded successfully, `false` otherwise.
  ///
  /// Example:
  /// ```dart
  /// bool success = await yolo.loadModel();
  /// if (success) {
  ///   print('Model loaded successfully');
  /// } else {
  ///   print('Failed to load model');
  /// }
  /// ```
  ///
  /// @throws [ModelLoadingException] if the model file cannot be found
  /// @throws [PlatformException] if there's an issue with the platform-specific code
  Future<bool> loadModel() async {
    try {
      final result = await _channel.invokeMethod('loadModel', {
        'modelPath': modelPath,
        'task': task.name,
      });
      return result == true;
    } on PlatformException catch (e) {
      if (e.code == 'MODEL_NOT_FOUND') {
        throw ModelLoadingException('Model file not found: $modelPath');
      } else if (e.code == 'INVALID_MODEL') {
        throw ModelLoadingException('Invalid model format: $modelPath');
      } else if (e.code == 'UNSUPPORTED_TASK') {
        throw ModelLoadingException(
            'Unsupported task type: ${task.name} for model: $modelPath');
      } else {
        throw ModelLoadingException('Failed to load model: ${e.message}');
      }
    } catch (e) {
      throw ModelLoadingException('Unknown error loading model: $e');
    }
  }

  /// Runs inference on a single image.
  ///
  /// Takes raw image bytes as input and returns inference results as a list of YOLOResult objects and
  /// a map with additional data like annotated images.
  ///
  /// The model must be loaded with [loadModel] before calling this method.
  ///
  /// Example:
  /// ```dart
  /// final resultMap = await yolo.predict(imageBytes);
  /// final yoloResults = resultMap['results'] as List<YOLOResult>;
  /// for (var result in yoloResults) {
  ///   print('Class: ${result.label}, Confidence: ${result.confidence}');
  /// }
  /// ```
  ///
  /// Returns a map containing the YOLOResult objects and additional data. If inference fails, throws an exception.
  ///
  /// @param imageBytes The raw image data as a Uint8List
  /// @return A map containing the inference results with the YOLOResult objects under the 'results' key
  /// @throws [ModelNotLoadedException] if the model has not been loaded
  /// @throws [InferenceException] if there's an error during inference
  /// @throws [PlatformException] if there's an issue with the platform-specific code
  Future<Map<String, dynamic>> predict(Uint8List imageBytes) async {
    if (imageBytes.isEmpty) {
      throw InvalidInputException('Image data is empty');
    }

    try {
      final result = await _channel.invokeMethod('predictSingleImage', {
        'image': imageBytes,
      });

      if (result is Map) {
        // Convert Map<Object?, Object?> to Map<String, dynamic>
        final resultMap = Map<String, dynamic>.fromEntries(
            result.entries.map((e) => MapEntry(e.key.toString(), e.value)));

        // Process boxes to create a list of YOLOResult objects
        resultMap['results'] = _processDetections(resultMap);
        return resultMap;
      }

      throw InferenceException('Invalid result format returned from inference');
    } on PlatformException catch (e) {
      _handlePlatformException(e);
    } catch (e) {
      throw InferenceException('Unknown error during inference: $e');
    }
  }

  // Process the detection results from the raw map
  List<YOLOResult> _processDetections(Map<String, dynamic> resultMap) {
    final results = <YOLOResult>[];

    if (resultMap.containsKey('boxes') && resultMap['boxes'] is List) {
      final boxes = resultMap['boxes'] as List;

      for (var box in boxes) {
        if (box is Map) {
          // Convert each detection to a YOLOResult
          final detectionMap = Map<String, dynamic>.fromEntries(
              box.entries.map((e) => MapEntry(e.key.toString(), e.value)));

          results.add(YOLOResult.fromMap(detectionMap));
        }
      }
    }

    return results;
  }

  // Handle platform exceptions with appropriate error messages
  Never _handlePlatformException(PlatformException e) {
    if (e.code == 'MODEL_NOT_LOADED') {
      throw ModelNotLoadedException(
          'Model has not been loaded. Call loadModel() first.');
    } else if (e.code == 'INVALID_IMAGE') {
      throw InvalidInputException(
          'Invalid image format or corrupted image data');
    } else if (e.code == 'INFERENCE_ERROR') {
      throw InferenceException('Error during inference: ${e.message}');
    } else {
      throw InferenceException('Platform error during inference: ${e.message}');
    }
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

    try {
      final result = await _channel.invokeMethod('startRecording', {
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
  Future<String> stopRecording() async {
    if (!Platform.isAndroid) {
      throw PlatformNotSupportedException(
          'Video recording is only supported on Android');
    }

    if (!_isRecording) {
      throw RecordingException('No active recording to stop');
    }

    try {
      final result = await _channel.invokeMethod('stopRecording');
      _isRecording = false;
      return result as String;
    } on PlatformException catch (e) {
      throw RecordingException('Failed to stop recording: ${e.message}');
    } catch (e) {
      throw RecordingException('Unknown error stopping recording: $e');
    }
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
