# Device & Platform Support

This project is a Flutter plugin for YOLO (You Only Look Once) models, supporting object detection, segmentation, classification, pose estimation, and oriented bounding boxes (OBB).

## Supported Platforms

| Platform | Supported | Notes                                                                                                             |
| -------- | --------- | ----------------------------------------------------------------------------------------------------------------- |
| Android  | ✅        | Requires minSdkVersion 21. Supports real-time camera, video, and all YOLO tasks. Video recording is Android-only. |
| iOS      | ✅        | Supports all YOLO tasks. Camera and photo library integration.                                                    |
| Web      | ❌        | Not supported                                                                                                     |
| macOS    | ❌        | Not supported                                                                                                     |
| Windows  | ❌        | Not supported                                                                                                     |
| Linux    | ❌        | Not supported                                                                                                     |

## Android Details

- **Minimum SDK:** 21
- **Permissions:**
  - `android.permission.CAMERA`
  - `android.permission.READ_EXTERNAL_STORAGE` (for older Android)
  - `android.permission.WRITE_EXTERNAL_STORAGE` (for older Android)
  - `android.permission.READ_MEDIA_VIDEO` (Android 13+)
  - `android.permission.READ_MEDIA_IMAGES` (Android 13+)
- **Architectures:**
  - armeabi-v7a
  - arm64-v8a
  - x86
  - x86_64
- **Hardware:**
  - Camera required for real-time detection
  - GPU acceleration supported via TFLite GPU delegate (if available)
- **Notes:**
  - Video recording is only available on Android.
  - For best performance, use devices with a recent CPU/GPU.

## iOS Details

- **Permissions:**
  - `NSCameraUsageDescription` (Info.plist)
  - `NSPhotoLibraryUsageDescription` (Info.plist)
- **Hardware:**
  - Camera required for real-time detection
  - TFLite model compatibility required
- **Notes:**
  - Video recording is not available on iOS.

## General Notes

- **Model Format:** TFLite models only
- **Tasks Supported:**
  - Object Detection
  - Segmentation
  - Classification
  - Pose Estimation
  - Oriented Bounding Boxes (OBB)
- **Performance:**
  - Real-time inference is optimized for mobile devices, but performance may vary depending on device age and hardware.
  - For older devices, use smaller models (e.g., YOLOv8n) and lower input resolutions for better speed.

For more details, see the README and platform-specific setup instructions.
