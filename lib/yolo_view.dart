// lib/yolo_view.dart

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ultralytics_yolo/yolo_task.dart';

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
/// )
/// ```
class YoloView extends StatelessWidget {
  /// Path to the YOLO model file. This should be a TFLite model file.
  final String modelPath;
  
  /// The type of task this YOLO model will perform (detection, segmentation, etc.)
  final YOLOTask task;

  /// Creates a YoloView widget that displays a platform-specific native view
  /// for YOLO object detection and other computer vision tasks.
  ///
  /// The [modelPath] should point to a valid TFLite model file in the assets.
  /// The [task] specifies what type of inference will be performed.
  const YoloView({
    super.key,
    required this.modelPath,
    required this.task,
  });

  @override
  Widget build(BuildContext context) {
    const viewType = 'com.ultralytics.yolo/YoloPlatformView';
    final creationParams = <String, dynamic>{
      'modelPath': modelPath,
      'task': task.name, // "detect" / "classify" etc.
    };

    if (defaultTargetPlatform == TargetPlatform.android) {
      return AndroidView(
        viewType: viewType,
        layoutDirection: TextDirection.ltr,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      return UiKitView(
        viewType: viewType,
        layoutDirection: TextDirection.ltr,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }
    // fallback for unsupported platforms
    return const Text('Platform not supported for YoloView');
  }
}

