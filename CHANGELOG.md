# Changelog

All notable changes to the Android_IP_Cam project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial implementation of Android_IP_Cam application
- Complete Android project structure with Gradle build system
- CameraService as single source of truth for camera management
- MJPEG streaming via HTTP server (NanoHTTPD)
- RESTful API endpoints:
  - `/stream` - MJPEG video stream
  - `/snapshot` - Single JPEG image
  - `/status` - JSON system status
  - `/switch` - Switch camera
  - `/toggleFlashlight` - Toggle flashlight
  - `/setRotation` - Set rotation angle
  - `/setFormat` - Set resolution
  - `/events` - Server-Sent Events for real-time updates
- Responsive web interface with live stream view
- MainActivity with camera preview and controls
- Settings persistence via SharedPreferences
- Network monitoring with automatic server restart
- Watchdog monitoring with exponential backoff recovery
- Wake locks for 24/7 operation (CPU + WiFi)
- Battery optimization exemption request
- Foreground service with persistent notification
- Auto-start on device boot (BootReceiver)
- Support for 32+ simultaneous streaming connections
- Camera switching (front/back)
- Flashlight control for back camera
- Configurable JPEG quality (70-85%)
- Target frame rate of 10 fps
- Frame dropping for slow clients

### Documentation
- Comprehensive README with build instructions, usage guide, and troubleshooting
- Complete REQUIREMENTS_SPECIFICATION.md with 125+ requirements
- Detailed API_DOCUMENTATION.md with endpoint specifications
- CONTRIBUTING.md with development guidelines
- NVR/VMS integration guides for ZoneMinder, Shinobi, Blue Iris, MotionEye

### Technical Details
- Minimum Android version: 7.0 (API 24)
- Target Android version: 14 (API 34)
- CameraX 1.3.1 for camera management
- NanoHTTPD 2.3.1 for HTTP server
- Kotlin Coroutines 1.7.3 for async operations
- Material Components 1.11.0 for UI

## [1.0.0] - TBD

### Summary
First official release of Android_IP_Cam with complete MJPEG streaming functionality, RESTful API, web interface, and compatibility with major NVR/VMS systems.

---

## Version History

- **[Unreleased]**: Initial development and implementation
- **[1.0.0]**: First stable release (planned)

## Notes

### Versioning Scheme
- **Major**: Significant changes, potential breaking changes
- **Minor**: New features, backwards compatible
- **Patch**: Bug fixes, minor improvements

### Change Categories
- **Added**: New features
- **Changed**: Changes to existing functionality
- **Deprecated**: Soon-to-be removed features
- **Removed**: Removed features
- **Fixed**: Bug fixes
- **Security**: Security-related changes
