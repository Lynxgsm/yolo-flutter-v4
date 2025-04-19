# Ultralytics YOLO Flutter Package

Flutter plugin for YOLO (You Only Look Once) models, supporting object detection, segmentation, classification, pose estimation and oriented bounding boxes (OBB) on both Android and iOS.

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

## Features

- **Object Detection**: Identify and locate objects in images and camera feeds with bounding boxes
- **Segmentation**: Perform pixel-level segmentation of objects
- **Classification**: Classify objects in images
- **Pose Estimation**: Detect human poses and keypoints
- **Oriented Bounding Boxes (OBB)**: Detect rotated or oriented bounding boxes for objects
- **Cross-Platform**: Works on both Android and iOS
- **Real-time Processing**: Optimized for real-time inference on mobile devices
- **Camera Integration**: Easy integration with device cameras

## Installation

Add this to your package's `pubspec.yaml` file:

```yaml
dependencies:
  ultralytics_yolo: ^0.0.4
```

Then run:

```bash
flutter pub get
```

## Platform-Specific Setup

### Android

Add the following permissions to your `AndroidManifest.xml` file:

```xml
<!-- For camera access -->
<uses-permission android:name="android.permission.CAMERA" />

<!-- For accessing images from storage -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

Set minimum SDK version in your `android/app/build.gradle`:

```gradle
minSdkVersion 21
```

### iOS

Add these entries to your `Info.plist`:

```xml
<key>NSCameraUsageDescription</key>
<string>This app needs camera access to detect objects</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>This app needs photos access to get images for object detection</string>
```

## Usage

### Basic Example

```dart
import 'package:flutter/material.dart';
import 'package:ultralytics_yolo/yolo.dart';
import 'package:ultralytics_yolo/yolo_view.dart';
import 'package:ultralytics_yolo/yolo_task.dart';

class YoloDemo extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('YOLO Object Detection')),
      body: Center(
        child: YoloView(
          task: YOLOTask.detect,
          modelPath: 'assets/models/yolo11n.tflite',
          threshold: 0.5,
          onResult: (results) {
            // Handle detection results
            print('Detected ${results.length} objects');
          },
        ),
      ),
    );
  }
}
```

### Object Detection with Camera Feed

```dart
YoloView(
  task: YOLOTask.detect,
  modelPath: 'assets/models/yolo11n.tflite',
  useCamera: true,
  cameraResolution: '720p',
  threshold: 0.5,
  onResult: (results) {
    for (var result in results) {
      print('Detected: ${result.className}, Confidence: ${result.confidence}');
    }
  },
)
```

### Image Segmentation

```dart
YoloView(
  task: YOLOTask.segment,
  modelPath: 'assets/models/yolo11n-seg.tflite',
  threshold: 0.5,
  onResult: (results) {
    // Process segmentation results
  },
)
```

### Pose Estimation

```dart
YoloView(
  task: YOLOTask.pose,
  modelPath: 'assets/models/yolo11n-pose.tflite',
  threshold: 0.5,
  onResult: (results) {
    // Process pose keypoints
  },
)
```

## API Reference

### Classes

#### YOLO

Main class for YOLO operations.

```dart
YOLO({
  required String modelPath,
  required YOLOTask task,
  double threshold = 0.5,
});
```

#### YoloView

Flutter widget to display YOLO detection results.

```dart
YoloView({
  required YOLOTask task,
  required String modelPath,
  double threshold = 0.5,
  bool useCamera = false,
  String cameraResolution = '720p',
  Function(List<YOLOResult>)? onResult,
});
```

#### YOLOResult

Contains detection results.

```dart
class YOLOResult {
  final int classIndex;
  final String className;
  final double confidence;
  final Rect boundingBox;
  // For segmentation
  final List<List<double>>? mask;
  // For pose estimation
  final List<Point>? keypoints;
}
```

### Enums

#### YOLOTask

```dart
enum YOLOTask {
  detect,   // Object detection
  segment,  // Image segmentation
  classify, // Image classification
  pose,     // Pose estimation
  obb,      // Oriented bounding boxes
}
```

## Platform Support

| Android | iOS | Web | macOS | Windows | Linux |
|:-------:|:---:|:---:|:-----:|:-------:|:-----:|
|    ✅    |  ✅  |  ❌  |   ❌   |    ❌    |   ❌   |

## Troubleshooting

### Common Issues

1. **Model loading fails**
   - Make sure your model file is correctly placed in the assets directory
   - Verify that the model path is correctly specified
   - Check that the model format is compatible with TFLite

2. **Low performance on older devices**
   - Try using smaller models (e.g., YOLOv8n instead of YOLOv8l)
   - Reduce input image resolution
   - Adjust threshold values to reduce the number of detections

3. **Camera permission issues**
   - Ensure that your app has the proper permissions in the manifest or Info.plist
   - Handle runtime permissions properly in your app

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) - see the [LICENSE](LICENSE) file for details.