import 'dart:ui';

class YOLOResult {
  final int index;
  final String label;
  final double confidence;
  final Rect boundingBox;
  // For segmentation
  final List<List<double>>? mask;
  // For pose estimation
  final List<Point>? keypoints;

  YOLOResult({
    required this.index,
    required this.label,
    required this.confidence,
    required this.boundingBox,
    this.mask,
    this.keypoints,
  });

  factory YOLOResult.fromMap(Map<String, dynamic> map) {
    // Extract class information
    final classIdx = map['index'] ?? 0;

    // Handle class name which could be in 'label', 'class', or 'className' fields
    final cls = (map['label'] ?? map['class'] ?? map['className'] ?? 'unknown')
        .toString();

    // Extract confidence
    final conf = _parseDouble(map['confidence'], 0.0);

    // Handle different bounding box formats
    late Rect bbox;

    // Check if we have x1,y1,x2,y2 format (corner points)
    if (map.containsKey('x1') &&
        map.containsKey('y1') &&
        map.containsKey('x2') &&
        map.containsKey('y2')) {
      bbox = Rect.fromLTRB(
          _parseDouble(map['x1'], 0.0),
          _parseDouble(map['y1'], 0.0),
          _parseDouble(map['x2'], 0.0),
          _parseDouble(map['y2'], 0.0));
    } else {
      // Otherwise use x,y,width,height format (origin and dimensions)
      bbox = Rect.fromLTWH(
          _parseDouble(map['x'], 0.0),
          _parseDouble(map['y'], 0.0),
          _parseDouble(map['width'], 0.0),
          _parseDouble(map['height'], 0.0));
    }

    return YOLOResult(
      index: classIdx,
      label: cls,
      confidence: conf,
      boundingBox: bbox,
      mask: map['mask'] != null
          ? (map['mask'] as List)
              .map((row) => (row as List)
                  .map((value) => _parseDouble(value, 0.0))
                  .toList())
              .toList()
          : null,
      keypoints: map['keypoints'] != null
          ? (map['keypoints'] as List)
              .map((point) => Point(
                  x: _parseDouble(point['x'], 0.0),
                  y: _parseDouble(point['y'], 0.0)))
              .toList()
          : null,
    );
  }

  // Helper method to safely parse double values
  static double _parseDouble(dynamic value, double defaultValue) {
    if (value is double) return value;
    if (value == null) return defaultValue;
    return double.tryParse(value.toString()) ?? defaultValue;
  }

  Map<String, dynamic> toMap() {
    return {
      'index': index,
      'label': label,
      'confidence': confidence,
      'x': boundingBox.left,
      'y': boundingBox.top,
      'width': boundingBox.width,
      'height': boundingBox.height,
      if (mask != null) 'mask': mask,
      if (keypoints != null)
        'keypoints': keypoints?.map((point) => point.toMap()).toList(),
    };
  }

  @override
  String toString() {
    return 'YOLOResult{index: $index, label: $label, confidence: $confidence, boundingBox: $boundingBox, mask: ${mask != null ? '[...]' : 'null'}, keypoints: ${keypoints != null ? '[...]' : 'null'}}';
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is YOLOResult &&
        other.index == index &&
        other.label == label &&
        other.confidence == confidence &&
        other.boundingBox == boundingBox;
  }

  @override
  int get hashCode {
    return index.hashCode ^
        label.hashCode ^
        confidence.hashCode ^
        boundingBox.hashCode;
  }
}

class Point {
  final double x;
  final double y;

  Point({required this.x, required this.y});

  Map<String, dynamic> toMap() {
    return {
      'x': x,
      'y': y,
    };
  }

  factory Point.fromMap(Map<String, dynamic> map) {
    return Point(
      x: YOLOResult._parseDouble(map['x'], 0.0),
      y: YOLOResult._parseDouble(map['y'], 0.0),
    );
  }

  @override
  String toString() => 'Point{x: $x, y: $y}';

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is Point && other.x == x && other.y == y;
  }

  @override
  int get hashCode => x.hashCode ^ y.hashCode;
}
