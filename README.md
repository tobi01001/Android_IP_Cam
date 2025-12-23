# Android_IP_Cam

Android_IP_Cam is an Android application that transforms Android devices into fully-functional IP cameras with HTTP streaming capabilities. The application is designed for 24/7 surveillance operations, repurposing older Android devices into reliable, network-accessible camera systems compatible with professional surveillance software.

## Quick Links

📖 **[BUILD_GUIDE.md](BUILD_GUIDE.md)** - Step-by-step guide for building with Android Studio  
🏗️ **[ARCHITECTURE.md](ARCHITECTURE.md)** - Detailed code structure and architecture explanation  
🚀 **[ANDROID_STUDIO_SETUP.md](ANDROID_STUDIO_SETUP.md)** - Quick reference for Android Studio run/debug setup  
⚡ **[QUICK_START.md](QUICK_START.md)** - Get up and running in 5 minutes  
📡 **[API_DOCUMENTATION.md](API_DOCUMENTATION.md)** - Complete API reference  
🤝 **[CONTRIBUTING.md](CONTRIBUTING.md)** - Development guidelines

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
- **Persistent Service**: Foreground service with automatic restart and battery optimization
- **Network Monitoring**: Automatically restarts server on network changes
- **Settings Persistence**: All settings saved and restored across app restarts
- **REST API**: Simple API for integration with other systems
- **Low Latency**: Optimized for fast streaming with JPEG compression

## Requirements

### Minimum Requirements
- **Android Version**: Android 7.0 (API 24) or higher
- **Target Version**: Android 14 (API 34)
- **Hardware**: Device with camera (front or back)
- **Network**: WiFi connection for streaming

### Recommended Requirements
- **Android Version**: Android 12 (API 31) or higher for best compatibility
- **Hardware**: Device with at least 2GB RAM
- **Network**: Stable WiFi connection

## Installation

### Option 1: Build from Source

#### Prerequisites
- Android Studio Ladybug (2024.2.1) or later
- Android SDK with API 34
- Gradle 8.11 or later
- JDK 17 or later (JDK 21 recommended for future-proofing)

#### Build Steps

**For detailed instructions with screenshots, see [BUILD_GUIDE.md](BUILD_GUIDE.md)**

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tobi01001/Android_IP_Cam.git
   cd Android_IP_Cam
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned repository folder
   - Click "OK" and wait for Gradle sync

3. **Build the APK**:
   ```bash
   ./gradlew assembleRelease
   ```
   The APK will be generated at: `app/build/outputs/apk/release/app-release-unsigned.apk`

4. **Install on device**:
   ```bash
   adb install app/build/outputs/apk/release/app-release-unsigned.apk
   ```

### Option 2: Install Pre-built APK

Download the latest APK from the [Releases](https://github.com/tobi01001/Android_IP_Cam/releases) page and install it on your Android device.

## Usage

### Getting Started

1. **Launch the app** on your Android device
2. **Grant permissions**:
   - Camera permission (required)
   - Notification permission (Android 13+)
   - Battery optimization exemption (recommended for 24/7 operation)
3. **Start streaming** by tapping the "Start Streaming" button
4. **Note the server URL** displayed in the app (e.g., `http://192.168.1.100:8080`)

### Accessing the Stream

#### Via Web Browser
Open the server URL in any web browser:
```
http://DEVICE_IP:8080
```

You'll see a live stream with controls for:
- Camera switching
- Flashlight toggle
- Taking snapshots
- Real-time connection monitoring

#### Via Surveillance Software

Configure your NVR/VMS software with:
- **Stream URL**: `http://DEVICE_IP:8080/stream`
- **Snapshot URL**: `http://DEVICE_IP:8080/snapshot`
- **Format**: MJPEG

#### Via VLC Media Player
```bash
vlc http://DEVICE_IP:8080/stream
```

## API Endpoints

### Essential Endpoints (NVR Compatible)

| Endpoint | Method | Description | Response Type |
|----------|--------|-------------|---------------|
| `/stream` | GET | MJPEG video stream | multipart/x-mixed-replace |
| `/snapshot` | GET | Single JPEG image | image/jpeg |
| `/status` | GET | System status | application/json |

### Control Endpoints

| Endpoint | Method | Description | Response Type |
|----------|--------|-------------|---------------|
| `/` | GET | Web interface | text/html |
| `/switch` | GET | Switch camera | application/json |
| `/toggleFlashlight` | GET | Toggle flashlight | application/json |
| `/setRotation?value=X` | GET | Set rotation (0/90/180/270/auto) | application/json |
| `/setFormat?value=WxH` | GET | Set resolution | application/json |
| `/events` | GET | Server-Sent Events stream | text/event-stream |

### Example API Calls

```bash
# Get system status
curl http://192.168.1.100:8080/status

# Take a snapshot
curl http://192.168.1.100:8080/snapshot -o snapshot.jpg

# Switch camera
curl http://192.168.1.100:8080/switch

# Toggle flashlight
curl http://192.168.1.100:8080/toggleFlashlight

# Set rotation to 90 degrees
curl "http://192.168.1.100:8080/setRotation?value=90"
```

## NVR/VMS Integration

### ZoneMinder
```
Source Type: Remote
Remote Method: HTTP
Remote Host Path: DEVICE_IP:8080/stream
```

### Shinobi
```
Input Type: H.264/MJPEG
Connection URL: http://DEVICE_IP:8080/stream
```

### Blue Iris
```
Network IP Camera
Make: Generic MJPEG
Path: /stream
```

### MotionEye
```
Camera Type: Network Camera
URL: http://DEVICE_IP:8080/stream
```

## Configuration

### Settings Persistence

All settings are automatically saved and restored:
- Camera type (front/back)
- Rotation angle
- Resolution
- JPEG quality
- Server port
- Flashlight state
- Auto-start on boot

### Advanced Configuration

Edit `StreamingConfig.kt` to customize:
- **JPEG Quality**: Default 80% (range: 70-85%)
- **Target FPS**: Default 10 fps
- **Server Port**: Default 8080
- **Max Connections**: Default 32

## Architecture

### Core Components

1. **CameraService**: Foreground service managing camera lifecycle
2. **IPCamWebServer**: NanoHTTPD-based HTTP server
3. **MainActivity**: Main UI with camera preview and controls
4. **NetworkMonitor**: WiFi connectivity monitoring
5. **StreamingConfig**: Settings persistence manager

### Design Principles

1. **Single Source of Truth**: CameraService manages ONE camera instance for all consumers
2. **24/7 Reliability**: Foreground service with wake locks and watchdog monitoring
3. **Bandwidth Optimization**: ~10 fps, 80% JPEG quality for optimal network usage
4. **NVR Compatibility**: Standard MJPEG stream format

## Troubleshooting

### Camera Not Starting
- Check camera permissions
- Ensure no other app is using the camera
- Restart the device

### Stream Not Accessible
- Verify device and client are on same network
- Check firewall settings
- Ensure server is running (check notification)

### Connection Drops
- Enable battery optimization exemption
- Check WiFi stability
- Verify device is not entering deep sleep

### Poor Performance
- Reduce JPEG quality (70-80%)
- Lower target FPS (5-10 fps)
- Check network bandwidth

## Development

### Project Structure
```
Android_IP_Cam/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ipcam/
│   │   │   ├── CameraService.kt          # Core camera management
│   │   │   ├── IPCamWebServer.kt         # HTTP server
│   │   │   ├── MainActivity.kt           # Main UI
│   │   │   ├── NetworkMonitor.kt         # Network monitoring
│   │   │   ├── StreamingConfig.kt        # Settings management
│   │   │   └── BootReceiver.kt           # Auto-start on boot
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml     # Main activity layout
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       ├── colors.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── REQUIREMENTS_SPECIFICATION.md
└── README.md
```

### Key Dependencies
- **CameraX**: 1.3.1 - Modern camera API
- **NanoHTTPD**: 2.3.1 - Embedded HTTP server
- **Kotlin Coroutines**: 1.7.3 - Async operations
- **Material Components**: 1.11.0 - UI components

### Building
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Install on connected device
./gradlew installDebug
```

## Documentation

### For Developers
- **[BUILD_GUIDE.md](BUILD_GUIDE.md)** - Step-by-step Android Studio build instructions (with troubleshooting)
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete code structure and architecture explanation
- **[CONTRIBUTING.md](CONTRIBUTING.md)** - Development workflow and guidelines

### For Users
- **[QUICK_START.md](QUICK_START.md)** - Get started in 5 minutes
- **[API_DOCUMENTATION.md](API_DOCUMENTATION.md)** - Complete API reference with examples

### Technical Specifications
- **[REQUIREMENTS_SPECIFICATION.md](REQUIREMENTS_SPECIFICATION.md)** - Complete technical requirements (125+ requirements)
- **[REQUIREMENTS_SUMMARY.md](REQUIREMENTS_SUMMARY.md)** - Quick reference guide
- **[IMPLEMENTATION_VALIDATION.md](IMPLEMENTATION_VALIDATION.md)** - Compliance verification report

### Additional Resources
- **[CHANGELOG.md](CHANGELOG.md)** - Version history

## Target Device

Developed and tested for Samsung Galaxy S10+ (but should work on any Android device with camera and Android 7.0+)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues, questions, or suggestions, please open an issue on GitHub.

## Acknowledgments

- Built with CameraX for modern camera management
- Powered by NanoHTTPD for embedded HTTP server
- Compatible with industry-standard surveillance software

---

- **[Design Principles](DESIGN_PRINCIPLES.md)** - Comprehensive guide to the five core design principles guiding all implementation decisions
- **[Requirements Specification](REQUIREMENTS_SPECIFICATION.md)** - Complete technical requirements for implementing IP_Cam from scratch
- **[Requirements Summary](REQUIREMENTS_SUMMARY.md)** - Quick reference guide to the requirements specification
