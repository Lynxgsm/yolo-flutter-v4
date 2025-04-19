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
- **Flexible Format Support**: Handles different bounding box coordinate formats (x,y,width,height or x1,y1,x2,y2)

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
            // Handle multiple detection results
            print('Detected ${results.length} objects');
            for (var detection in results) {
              print('${detection.label}: ${detection.confidence.toStringAsFixed(2)}');
            }
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
      print('Detected: ${result.label}, Confidence: ${result.confidence}');
    }
  },
)
```

### Single Image Inference

```dart
// Initialize YOLO
final yolo = YOLO(
  modelPath: 'assets/models/yolo11n.tflite',
  task: YOLOTask.detect,
);

// Load model
await yolo.loadModel();

// Process an image
Uint8List imageBytes = await yourImageLoadingFunction();
final resultMap = await yolo.predict(imageBytes);

// Access all detection results
List<YOLOResult> detections = resultMap['results'];
for (var detection in detections) {
  print('Class: ${detection.label}, Confidence: ${detection.confidence}');
  print('Bounding box: ${detection.boundingBox}');
}

// Access the annotated image if available
if (resultMap.containsKey('annotatedImage')) {
  Uint8List annotatedImage = resultMap['annotatedImage'];
  // Use the annotated image in your UI
}
```

### Image Segmentation

```dart
YoloView(
  task: YOLOTask.segment,
  modelPath: 'assets/models/yolo11n-seg.tflite',
  threshold: 0.5,
  onResult: (results) {
    // Process segmentation results
    for (var result in results) {
      if (result.mask != null) {
        // Access segmentation mask data
        var mask = result.mask!;
        // Do something with the mask
      }
    }
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
    for (var result in results) {
      if (result.keypoints != null) {
        for (var point in result.keypoints!) {
          print('Keypoint at: (${point.x}, ${point.y})');
        }
      }
    }
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

// Methods
Future<bool> loadModel(); // Load the model
Future<Map<String, dynamic>> predict(Uint8List imageBytes); // Run inference
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
  required Function(List<YOLOResult>) onResult,
});
```

#### YOLOResult

Contains detection results with serialization/deserialization support.

```dart
class YOLOResult {
  final int index;
  final String label;
  final double confidence;
  final Rect boundingBox;
  // For segmentation
  final List<List<double>>? mask;
  // For pose estimation
  final List<Point>? keypoints;

  // Constructor
  YOLOResult({
    required this.index,
    required this.label,
    required this.confidence,
    required this.boundingBox,
    this.mask,
    this.keypoints,
  });

  // Create from map - supports both x,y,width,height and x1,y1,x2,y2 formats
  factory YOLOResult.fromMap(Map<String, dynamic> map);

  // Convert to map
  Map<String, dynamic> toMap();

  // String representation
  @override
  String toString();

  // Equality operators
  @override
  bool operator ==(Object other);

  @override
  int get hashCode;
}

class Point {
  final double x;
  final double y;

  Point({required this.x, required this.y});

  // Serialization methods
  factory Point.fromMap(Map<String, dynamic> map);
  Map<String, dynamic> toMap();
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
| :-----: | :-: | :-: | :---: | :-----: | :---: |
|   ✅    | ✅  | ❌  |  ❌   |   ❌    |  ❌   |

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

4. **No detection results**
   - Verify that the onResult callback is correctly implemented to handle a list of results
   - Check that your model path is correct
   - Make sure the image format is compatible with the model

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0) - see the [LICENSE](LICENSE) file for details.
