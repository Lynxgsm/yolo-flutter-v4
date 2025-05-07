/// Base exception class for all YOLO-related exceptions.
///
/// This is the parent class for all exceptions that can be thrown by the YOLO plugin.
/// Applications can catch this exception type to handle all YOLO-related errors in one place.
class YoloException implements Exception {
  /// A human-readable error message
  final String message;

  /// Creates a new YoloException with the given error message
  YoloException(this.message);

  @override
  String toString() => 'YoloException: $message';
}

/// Represents the result of a recording operation.
///
/// This class is used to return both the success status and an error reason
/// when starting a recording session.
class RecordingResult {
  /// Whether the recording operation was successful
  final bool success;

  /// The reason for failure if [success] is false, or null if successful
  final String? reason;

  /// Creates a new recording result
  RecordingResult({required this.success, this.reason});

  /// Creates a successful recording result
  factory RecordingResult.success() => RecordingResult(success: true);

  /// Creates a failed recording result with the given reason
  factory RecordingResult.failure(String reason) =>
      RecordingResult(success: false, reason: reason);
}

/// Exception thrown when a model fails to load.
///
/// This exception is thrown by [YOLO.loadModel] when the model file cannot be found,
/// is in an invalid format, or is otherwise incompatible.
class ModelLoadingException extends YoloException {
  ModelLoadingException(super.message);

  @override
  String toString() => 'ModelLoadingException: $message';
}

/// Exception thrown when attempting to perform inference without loading a model.
///
/// This exception is thrown by [YOLO.predict] when the model has not been loaded
/// or was not loaded successfully.
class ModelNotLoadedException extends YoloException {
  ModelNotLoadedException(super.message);

  @override
  String toString() => 'ModelNotLoadedException: $message';
}

/// Exception thrown when invalid input is provided to YOLO methods.
///
/// This exception is thrown when inputs such as image data are invalid,
/// corrupted, or in an unsupported format.
class InvalidInputException extends YoloException {
  InvalidInputException(super.message);

  @override
  String toString() => 'InvalidInputException: $message';
}

/// Exception thrown when an error occurs during model inference.
///
/// This exception is thrown by [YOLO.predict] when the model encounters an error
/// during the inference process.
class InferenceException extends YoloException {
  InferenceException(super.message);

  @override
  String toString() => 'InferenceException: $message';
}

/// Exception thrown when an error occurs during video recording.
///
/// This exception is thrown by [YOLO.startRecording] or [YOLO.stopRecording]
/// when there's an issue with the recording process, such as missing permissions
/// or hardware problems.
class RecordingException extends YoloException {
  RecordingException(super.message);

  @override
  String toString() => 'RecordingException: $message';
}

/// Exception thrown when a feature is not supported on the current platform.
///
/// This exception is thrown when attempting to use platform-specific features
/// on unsupported platforms. For example, when trying to use video recording
/// on platforms other than Android.
class PlatformNotSupportedException extends YoloException {
  PlatformNotSupportedException(super.message);

  @override
  String toString() => 'PlatformNotSupportedException: $message';
}
