# YOLO Video Recording Feature

This feature allows you to record video footage while performing YOLO predictions on Android devices. The recording happens in the background while predictions are being made, and both operations can run simultaneously.

## Platform Support

Currently, video recording is **only supported on Android**. Attempting to use these features on iOS or other platforms will result in a `PlatformNotSupportedException`.

## Required Permissions

The following permissions are required and automatically requested by the plugin:

- Camera (`android.permission.CAMERA`)
- Storage permissions (depending on Android version)

## Usage

To use the video recording features, you must first load a YOLO model as usual:

```dart
final yolo = YOLO(
  modelPath: 'assets/models/yolo11n.tflite',
  task: YOLOTask.detect,
);

await yolo.loadModel();
```

### Start Recording

To start recording video while performing predictions:

```dart
try {
  // Start recording with default output path
  await yolo.startRecording();

  // Or specify a custom output path
  // await yolo.startRecording(outputPath: '/storage/emulated/0/Movies/my_yolo_video.mp4');

  // Now you can perform predictions while recording
  final result = await yolo.predict(imageBytes);

  // Process prediction results as usual
  // ...

} catch (e) {
  print('Error starting recording: $e');
}
```

If no `outputPath` is provided, the video will be saved to the default movies directory with a timestamp-based filename.

### Stop Recording

To stop the recording:

```dart
try {
  final videoPath = await yolo.stopRecording();

  print('Video saved to: $videoPath');

} catch (e) {
  print('Error stopping recording: $e');
}
```

## Error Handling

The following exceptions may be thrown:

- `PlatformNotSupportedException`: If the feature is used on a non-Android platform
- `ModelNotLoadedException`: If you try to start recording without loading a model first
- `RecordingException`: For other recording-related errors, including permission issues

## Video Specifications

The recorded videos have the following specifications:

- Format: MPEG-4
- Resolution: 1280x720 (HD)
- Frame rate: 30 FPS
- Video bitrate: 10 Mbps
- Video source: Default camera
- No audio recording
