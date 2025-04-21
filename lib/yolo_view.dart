// lib/yolo_view.dart

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ultralytics_yolo_android/yolo_task.dart';
import 'package:ultralytics_yolo_android/yolo_result.dart';

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
/// YoloView(
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

  const YoloView({
    super.key,
    required this.modelPath,
    required this.task,
    required this.onResult,
    this.showBoxes = true,
  });

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
        viewType: viewType,
        layoutDirection: TextDirection.ltr,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
        onPlatformViewCreated: _onPlatformViewCreated,
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
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
  }
}
