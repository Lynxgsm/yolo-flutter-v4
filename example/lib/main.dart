// example/lib/main.dart
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:ultralytics_yolo/yolo.dart';
import 'package:ultralytics_yolo/yolo_view.dart';
import 'package:ultralytics_yolo/yolo_result.dart';
import 'package:image_picker/image_picker.dart';

void main() {
  runApp(const YoloExampleApp());
}

class YoloExampleApp extends StatelessWidget {
  const YoloExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: 'Yolo Plugin Example',
      home: HomeScreen(),
    );
  }
}

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('YOLO Plugin Example')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                      builder: (context) => const CameraInferenceScreen()),
                );
              },
              child: const Text('Camera Inference'),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                      builder: (context) => const SingleImageScreen()),
                );
              },
              child: const Text('Single Image Inference'),
            ),
          ],
        ),
      ),
    );
  }
}

class CameraInferenceScreen extends StatelessWidget {
  const CameraInferenceScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Camera Inference'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: Column(
        children: [
          const SizedBox(height: 10),
          Expanded(
            child: Container(
              color: Colors.black12,
              child: YoloView(
                modelPath: 'yolo11n.tflite',
                task: YOLOTask.detect,
                onResult: (results) {
                  // Log number of detections
                  debugPrint('Detected ${results.length} objects');

                  // Log each detection
                  for (var result in results) {
                    debugPrint(result.toString());
                  }
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class SingleImageScreen extends StatefulWidget {
  const SingleImageScreen({super.key});

  @override
  State<SingleImageScreen> createState() => _SingleImageScreenState();
}

class _SingleImageScreenState extends State<SingleImageScreen> {
  final _picker = ImagePicker();
  List<YOLOResult> _results = [];
  Uint8List? _imageBytes;
  Uint8List? _annotatedImage;

  // Configure the single-image YOLO
  late YOLO _yolo;

  @override
  void initState() {
    super.initState();
    // Create the YOLO instance for single-image inference
    _yolo = YOLO(modelPath: 'yolo11n.tflite', task: YOLOTask.detect);

    // Optionally load model ahead of time
    _yolo.loadModel();
  }

  Future<void> _pickAndPredict() async {
    final XFile? file = await _picker.pickImage(source: ImageSource.gallery);
    if (file == null) return;

    final bytes = await file.readAsBytes();
    final resultMap = await _yolo.predict(bytes);
    setState(() {
      // Get the list of YOLOResult objects from the result map
      _results = resultMap['results'] as List<YOLOResult>? ?? [];

      // Check if annotated image exists
      if (resultMap.containsKey('annotatedImage') &&
          resultMap['annotatedImage'] is Uint8List) {
        _annotatedImage = resultMap['annotatedImage'] as Uint8List;
      } else {
        _annotatedImage = null;
      }

      _imageBytes = bytes;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Single Image Inference'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: Column(
        children: [
          const SizedBox(height: 20),
          ElevatedButton(
            onPressed: _pickAndPredict,
            child: const Text('Pick Image & Run Inference'),
          ),
          const SizedBox(height: 10),
          Expanded(
            child: SingleChildScrollView(
              child: Column(
                children: [
                  if (_annotatedImage != null)
                    SizedBox(
                      height: 300,
                      width: double.infinity,
                      child: Image.memory(_annotatedImage!),
                    )
                  else if (_imageBytes != null)
                    SizedBox(
                      height: 300,
                      width: double.infinity,
                      child: Image.memory(_imageBytes!),
                    ),
                  const SizedBox(height: 10),
                  Text('Detections: ${_results.length}'),
                  ..._results.map((result) => Padding(
                        padding: const EdgeInsets.all(8.0),
                        child: Text(
                          'Class: ${result.label}\n'
                          'Confidence: ${result.confidence.toStringAsFixed(2)}\n'
                          'Bounding Box: ${result.boundingBox}\n'
                          'Has Mask: ${result.mask != null}\n'
                          'Has Keypoints: ${result.keypoints != null}',
                        ),
                      )),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
