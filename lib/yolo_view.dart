// lib/yolo_view.dart

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ultralytics_yolo_android/yolo_task.dart';
import 'package:ultralytics_yolo_android/yolo_result.dart';

/// Controller for YoloView
///
/// Used to control filtering settings for YoloView
class YoloViewController {
  final GlobalKey<_YoloViewState> _key = GlobalKey<_YoloViewState>();
  MethodChannel? _methodChannel;

  /// Set allowed classes for filtering detections
  ///
  /// Pass an empty list to show all classes.
  Future<void> setAllowedClasses(List<String> classes) async {
    print(
        'YoloViewController.setAllowedClasses: $classes, channel: $_methodChannel');
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
    print(
        'YoloViewController.setMinConfidence: $confidence, channel: $_methodChannel');
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

  /// Internal key used by YoloView
  GlobalKey<_YoloViewState> get key => _key;

  /// Internal method to set the method channel
  void _setMethodChannel(MethodChannel channel) {
    _methodChannel = channel;
    print('YoloViewController: Method channel set to $_methodChannel');
  }
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
    switch (call.method) {
      case 'onDetectionResult':
        // Convert results to a list of YOLOResult objects and call the callback
        final resultMap = Map<String, dynamic>.from(call.arguments);
        final results = _extractDetections(resultMap);
        widget.onResult(results);
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
    // Update the method channel with the unique id
    final channelName = 'com.ultralytics.yolo_android/YoloMethodChannel_$id';
    _methodChannel = MethodChannel(channelName);
    _methodChannel.setMethodCallHandler(_handleMethodCall);

    // If a controller was provided, update its method channel
    if (widget.controller != null) {
      widget.controller!._setMethodChannel(_methodChannel);
    }

    print(
        'YoloView: Platform view created with ID $id, channel: $_methodChannel');
  }
}
