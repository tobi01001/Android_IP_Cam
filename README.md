# Android_IP_Cam
IP_Cam is an Android application that transforms Android devices into fully-functional IP cameras with HTTP streaming capabilities. The application is designed for 24/7 surveillance operations, repurposing older Android devices into reliable, network-accessible camera systems compatible with professional surveillance software.


## Features
- **Live Camera Preview**: View what the camera sees directly in the app
- **HTTP Web Server**: Access the camera through any web browser
- **MJPEG Streaming**: Real-time video streaming compatible with surveillance systems
- **Multiple Concurrent Connections**: Supports 32+ simultaneous clients (streams, status checks, snapshots)
- **Real-time Updates**: Server-Sent Events (SSE) for live connection monitoring
- **Camera Selection**: Switch between front and back cameras
- **Flashlight/Torch Control**: Toggle flashlight for back camera (in-app and via HTTP API)
- **Configurable Formats**: Choose supported resolutions from the web UI
- **Orientation Control**: Independent camera orientation (landscape/portrait) and rotation (0°, 90°, 180°, 270°)
- **Resolution Debugging**: Optional overlay showing actual bitmap dimensions
- **Persistent Service**: Foreground service with automatic restart and battery optimization
- **Network Monitoring**: Automatically restarts server on network changes
- **Settings Persistence**: All settings saved and restored across app restarts
- **Overlay & Reliability**: Battery/time overlay with auto-reconnect stream handling
- **REST API**: Simple API for integration with other systems
- **Low Latency**: Optimized for fast streaming with JPEG compression

## Target Device
Developed and tested for Samsung Galaxy S10+ (but should work on any Android device with camera and Android 7.0+)

## Documentation

- **[Design Principles](DESIGN_PRINCIPLES.md)** - Comprehensive guide to the five core design principles guiding all implementation decisions
- **[Requirements Specification](REQUIREMENTS_SPECIFICATION.md)** - Complete technical requirements for implementing IP_Cam from scratch
- **[Requirements Summary](REQUIREMENTS_SUMMARY.md)** - Quick reference guide to the requirements specification