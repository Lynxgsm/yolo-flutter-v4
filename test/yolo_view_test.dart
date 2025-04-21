import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ultralytics_yolo_android/yolo_view.dart';
import 'package:ultralytics_yolo_android/yolo_task.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('YoloView passes correct parameters to platform view', () {
    final view = YoloView(
      modelPath: 'test_model.tflite',
      task: YOLOTask.segment,
      onResult: (result) {
        debugPrint(result.toString());
      },
    );

    // Verify properties are correctly set
    expect(view.modelPath, equals('test_model.tflite'));
    expect(view.task, equals(YOLOTask.segment));
  });

  // Platform-specific widget tests are skipped because they're difficult to test
  // in the CI environment due to platform detection complexities.
  // These would ideally be tested in an integration testing environment.
}
