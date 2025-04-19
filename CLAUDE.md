# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands
- Install dependencies: `flutter pub get`
- Run all tests: `flutter test`
- Run single test: `flutter test test/FILE_PATH.dart`
- Run widget tests: `flutter test test/widget_test.dart`
- Run integration tests: `flutter test integration_test/plugin_integration_test.dart`
- Format code: `flutter format lib test`
- Analyze code: `flutter analyze`

## Code Style Guidelines
- Follow Flutter/Dart style in package:flutter_lints/flutter.yaml
- Import order: dart:*, package:flutter/*, other packages, relative imports
- Use named parameters for constructors with 2+ parameters
- Class Structure: constructors, fields, methods
- Error handling: Use try/catch with specific error types
- Documentation: Add /// dartdoc comments for public APIs
- Naming: camelCase for variables/methods, PascalCase for classes
- Platform-specific code: Keep in android/ios directories
- Method channels: Use consistent channel names across platforms
- Avoid print statements in production code; use proper logging

## Documentation Guidelines
- All documentation, comments, and public API docs must be written in English
- Use dartdoc format for API documentation
- Each public class, method, and property should have documentation comments
- Example usage should be included in documentation where appropriate
- Write clear, concise descriptions for all public APIs
- Include parameters and return value descriptions in method documentation
- For complex functionality, include code examples in documentation