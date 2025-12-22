# IP_Cam Application Architecture

This document explains the code structure and architecture of the IP_Cam Android application.

## Table of Contents

1. [High-Level Overview](#high-level-overview)
2. [Project Structure](#project-structure)
3. [Core Components](#core-components)
4. [Data Flow](#data-flow)
5. [Threading Model](#threading-model)
6. [Key Design Patterns](#key-design-patterns)
7. [Component Interactions](#component-interactions)

---

## High-Level Overview

IP_Cam is structured around the **Single Source of Truth** principle, where `CameraService` is the sole manager of the camera resource and distributes frames to all consumers.

```
┌─────────────────────────────────────────────────────────────┐
│                        Android System                        │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
    ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
    │  MainActivity │  │ CameraService │  │ BootReceiver │
    │   (UI Layer) │  │(Core Service) │  │ (Auto-start) │
    └──────────────┘  └──────────────┘  └──────────────┘
              │               │
              │    ┌──────────┼──────────┐
              │    │          │          │
              ▼    ▼          ▼          ▼
         ┌─────────────┐  ┌──────────────────┐
         │   CameraX   │  │ IPCamWebServer   │
         │  (Camera)   │  │  (HTTP Server)   │
         └─────────────┘  └──────────────────┘
              │                    │
              │                    ▼
              │            ┌──────────────┐
              │            │ Web Clients  │
              │            │ (Browsers,   │
              ▼            │  NVR, VLC)   │
         ┌─────────────┐  └──────────────┘
         │PreviewView  │
         │(App UI)     │
         └─────────────┘
```

---

## Project Structure

```
Android_IP_Cam/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ipcam/     # Kotlin source code
│   │   │   ├── CameraService.kt         # ⭐ Core service (660 lines)
│   │   │   ├── IPCamWebServer.kt        # ⭐ HTTP server (535 lines)
│   │   │   ├── MainActivity.kt          # Main UI (335 lines)
│   │   │   ├── NetworkMonitor.kt        # Network monitoring (102 lines)
│   │   │   ├── StreamingConfig.kt       # Configuration (74 lines)
│   │   │   └── BootReceiver.kt          # Auto-start (51 lines)
│   │   ├── res/                         # Android resources
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml    # Main UI layout
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       ├── colors.xml
│   │   │       └── themes.xml
│   │   └── AndroidManifest.xml          # App configuration
│   └── build.gradle.kts                 # App dependencies
├── build.gradle.kts                     # Root build config
├── settings.gradle.kts                  # Gradle settings
├── gradle.properties                    # Gradle properties
└── Documentation/                       # See README.md, etc.
```

---

## Core Components

### 1. CameraService.kt (660 lines)

**Purpose**: The heart of the application - manages camera lifecycle and frame distribution.

**Key Responsibilities**:
- Initializes and manages CameraX
- Captures frames at target FPS (10 fps)
- Converts YUV frames to JPEG
- Distributes frames to all listeners
- Manages foreground service
- Handles wake locks (CPU + WiFi)
- Implements watchdog monitoring
- Manages HTTP server lifecycle

**Key Classes/Interfaces**:
```kotlin
class CameraService : LifecycleService() {
    interface FrameListener {
        fun onFrameAvailable(frame: ByteArray)
    }
    
    // Public API
    fun startStreaming()
    fun stopStreaming()
    fun switchCamera()
    fun toggleFlashlight()
    fun getServerUrl(): String
    fun addFrameListener(listener: FrameListener)
}
```

**Why it's important**: Single source of truth - prevents resource conflicts by ensuring only one camera instance exists.

---

### 2. IPCamWebServer.kt (535 lines)

**Purpose**: HTTP server that serves MJPEG streams and RESTful API.

**Key Responsibilities**:
- Handles HTTP requests
- Serves MJPEG video stream (`/stream`)
- Provides snapshot endpoint (`/snapshot`)
- Provides status API (`/status`)
- Serves web interface (`/`)
- Manages thread pools for streaming
- Tracks active connections

**Key Methods**:
```kotlin
class IPCamWebServer(port: Int, cameraService: CameraService) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response
    
    private fun serveMJPEGStream(): Response
    private fun serveSnapshot(): Response
    private fun serveStatus(): Response
    private fun serveWebInterface(): Response
}
```

**How it works**:
1. Receives HTTP requests
2. Routes to appropriate handler
3. For `/stream`: Creates frame listener and streams JPEG frames
4. For `/snapshot`: Returns latest frame
5. For `/status`: Returns JSON with system info

---

### 3. MainActivity.kt (335 lines)

**Purpose**: User interface for the mobile app.

**Key Responsibilities**:
- Displays camera preview
- Provides start/stop controls
- Shows server URL and status
- Handles permission requests
- Binds to CameraService
- Updates UI with real-time status

**UI Components**:
- **PreviewView**: Shows camera feed
- **Start/Stop Button**: Controls streaming
- **Switch Camera Button**: Toggles front/back
- **Flashlight Button**: Toggles flashlight
- **Status Card**: Shows server URL, connections, camera type

**Key Methods**:
```kotlin
class MainActivity : AppCompatActivity() {
    private fun startCameraService()
    private fun stopCameraService()
    private fun updateUI()
    private fun checkAndRequestPermissions()
}
```

---

### 4. NetworkMonitor.kt (102 lines)

**Purpose**: Monitors WiFi connectivity and triggers server restart on reconnection.

**Key Responsibilities**:
- Detects network availability
- Triggers callback when WiFi reconnects
- Handles both old and new Android APIs

**How it works**:
```kotlin
class NetworkMonitor(context: Context, onNetworkAvailable: () -> Unit) {
    fun start()  // Start monitoring
    fun stop()   // Stop monitoring
}
```

When WiFi reconnects → Callback fired → CameraService restarts web server

---

### 5. StreamingConfig.kt (74 lines)

**Purpose**: Configuration management with persistence.

**Key Responsibilities**:
- Stores all app settings
- Saves to SharedPreferences
- Loads from SharedPreferences
- Provides default values

**Configuration Fields**:
```kotlin
data class StreamingConfig(
    val cameraType: String = "back",           // front/back
    val rotation: String = "auto",             // 0/90/180/270/auto
    val resolution: String? = null,            // WIDTHxHEIGHT
    val serverPort: Int = 8080,                // HTTP port
    val jpegQuality: Int = 80,                 // 70-85%
    val targetFps: Int = 10,                   // frames per second
    val flashlightEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val autoStart: Boolean = false,
    val maxConnections: Int = 32
)
```

---

### 6. BootReceiver.kt (51 lines)

**Purpose**: Starts CameraService automatically on device boot (if enabled).

**Key Responsibilities**:
- Receives `BOOT_COMPLETED` broadcast
- Checks if auto-start is enabled
- Starts CameraService as foreground service

**How it works**:
```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val config = StreamingConfig.load(context)
            if (config.autoStart) {
                // Start CameraService
            }
        }
    }
}
```

---

## Data Flow

### Camera Frame Flow

```
1. CameraX captures frame (YUV format)
              ↓
2. CameraService.processFrame() converts YUV → JPEG
              ↓
3. Frame stored in latestFrame variable
              ↓
4. Frame distributed to all FrameListeners
              ↓
      ┌───────┴───────┐
      ↓               ↓
5. MainActivity    IPCamWebServer
   (Preview)       (Streaming)
                        ↓
                   6. Web Clients
                      (Browser, NVR)
```

### HTTP Request Flow

```
1. Client sends HTTP request (e.g., GET /stream)
              ↓
2. IPCamWebServer.serve() receives request
              ↓
3. Route to handler (e.g., serveMJPEGStream())
              ↓
4. Handler creates FrameListener
              ↓
5. Handler registers listener with CameraService
              ↓
6. CameraService calls listener.onFrameAvailable() with each frame
              ↓
7. Handler writes frame to HTTP response
              ↓
8. Client receives frame
              ↓
9. Repeat steps 6-8 for continuous streaming
```

### Settings Change Flow

```
1. User changes setting (e.g., switches camera)
              ↓
2. CameraService updates config
              ↓
3. StreamingConfig.save() persists to SharedPreferences
              ↓
4. CameraService applies change (e.g., rebinds camera)
              ↓
5. All consumers (app + web) see new camera feed
```

---

## Threading Model

The application uses multiple threads to avoid blocking:

### Main Thread (UI)
- **Purpose**: UI updates only
- **Used by**: MainActivity
- **DO NOT**: Perform heavy operations here

### Camera Thread
- **Purpose**: Frame capture and processing
- **Created by**: CameraX executor (single thread)
- **Operations**: YUV → JPEG conversion, frame distribution

### HTTP Thread Pool
- **Purpose**: Handle HTTP requests
- **Size**: Bounded pool (default NanoHTTPD configuration)
- **Operations**: Serve static content, handle API calls

### Streaming Executor
- **Purpose**: MJPEG streaming to multiple clients
- **Type**: CachedThreadPool (unbounded, creates threads as needed)
- **Operations**: Stream frames to web clients

### Background Coroutines
- **Purpose**: Async operations
- **Scope**: serviceScope (SupervisorJob + Dispatchers.Default)
- **Operations**: Watchdog, network monitoring, delayed operations

**Thread Safety**:
- `frameListeners`: Uses `CopyOnWriteArrayList` (thread-safe)
- `activeConnections`: Uses `AtomicInteger` (thread-safe)
- `latestFrame`: Simple reference, read-mostly pattern

---

## Key Design Patterns

### 1. Service Pattern
- **CameraService** runs as a foreground service
- **Benefits**: Continues running even when app is in background
- **Implementation**: `LifecycleService` with `START_STICKY`

### 2. Observer Pattern (FrameListener)
- **CameraService** notifies listeners when frames are available
- **Benefits**: Decouples frame producers from consumers
- **Implementation**: Interface with callback method

```kotlin
interface FrameListener {
    fun onFrameAvailable(frame: ByteArray)
}
```

### 3. Singleton Pattern (Service)
- **CameraService** is a singleton managed by Android
- **Benefits**: One camera instance across entire app
- **Access**: Via service binding

### 4. Strategy Pattern (Frame Processing)
- Different strategies for different clients:
  - App preview: Direct CameraX Preview
  - Web clients: JPEG frame distribution
- **Benefits**: Optimizes for each use case

### 5. Watchdog Pattern
- **CameraService** monitors itself for failures
- **Benefits**: Automatic recovery from crashes
- **Implementation**: Coroutine that checks frame production

---

## Component Interactions

### Startup Sequence

```
1. User opens app → MainActivity.onCreate()
        ↓
2. MainActivity requests permissions
        ↓
3. MainActivity binds to CameraService
        ↓
4. User taps "Start Streaming"
        ↓
5. MainActivity sends ACTION_START_SERVICE
        ↓
6. CameraService.startStreaming()
        ↓
7. CameraService:
   - Acquires wake locks
   - Initializes camera
   - Starts web server
   - Starts watchdog
   - Starts network monitoring
        ↓
8. System is now streaming
```

### Camera Switching Sequence

```
1. User taps "Switch Camera" (app or web)
        ↓
2. CameraService.switchCamera()
        ↓
3. Update config (back ↔ front)
        ↓
4. Save to SharedPreferences
        ↓
5. CameraService.releaseCamera()
        ↓
6. CameraService.initializeCamera()
        ↓
7. All clients automatically receive frames from new camera
```

### Streaming to Web Client

```
1. Client requests http://DEVICE_IP:8080/stream
        ↓
2. IPCamWebServer.serveMJPEGStream()
        ↓
3. Create FrameListener implementation
        ↓
4. Register listener with CameraService
        ↓
5. CameraService calls listener.onFrameAvailable()
        ↓
6. Listener writes frame to HTTP stream:
   --jpgboundary
   Content-Type: image/jpeg
   Content-Length: XXX
   
   [JPEG data]
        ↓
7. Repeat step 5-6 for each frame
```

---

## Critical Code Paths

### Path 1: Frame Capture → Distribution

**File**: `CameraService.kt`  
**Method**: `processFrame(image: ImageProxy)`

```kotlin
private fun processFrame(image: ImageProxy) {
    // 1. Throttle to target FPS
    val currentTime = System.currentTimeMillis()
    val targetInterval = 1000L / config.targetFps
    if (currentTime - lastFrameTime < targetInterval) {
        image.close()
        return
    }
    
    // 2. Convert YUV → JPEG
    val jpegData = yuvToJpeg(image, config.jpegQuality)
    
    // 3. Store as latest frame
    latestFrame = jpegData
    
    // 4. Distribute to all listeners
    frameListeners.forEach { listener ->
        listener.onFrameAvailable(jpegData)
    }
    
    image.close()
}
```

### Path 2: HTTP Request → Response

**File**: `IPCamWebServer.kt`  
**Method**: `serve(session: IHTTPSession)`

```kotlin
override fun serve(session: IHTTPSession): Response {
    val uri = session.uri
    
    return when {
        uri == "/stream" -> serveMJPEGStream()
        uri == "/snapshot" -> serveSnapshot()
        uri == "/status" -> serveStatus()
        uri == "/switch" -> handleSwitchCamera()
        // ... other endpoints
        else -> newFixedLengthResponse(Status.NOT_FOUND, ...)
    }
}
```

### Path 3: Service Recovery (Watchdog)

**File**: `CameraService.kt`  
**Method**: `startWatchdog()`

```kotlin
private fun startWatchdog() {
    watchdogJob = serviceScope.launch {
        var failureCount = 0
        
        while (isRunning) {
            delay(5000) // Check every 5 seconds
            
            val timeSinceLastFrame = currentTime - lastFrameTime
            
            if (timeSinceLastFrame > 10000) {
                // No frames for 10 seconds!
                failureCount++
                
                // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s max
                val backoffDelay = minOf(1000L * (1 shl failureCount), 30000L)
                delay(backoffDelay)
                
                // Attempt recovery
                releaseCamera()
                initializeCamera()
            }
        }
    }
}
```

---

## Configuration & Persistence

### Where Settings are Stored

**Location**: Android SharedPreferences  
**File**: `StreamingConfig.kt`  
**Key**: `"ipcam_settings"`

### When Settings are Saved

- Immediately when changed (e.g., camera switch)
- Before service stops
- On configuration change

### When Settings are Loaded

- Service `onCreate()`
- After reboot (if auto-start enabled)
- On app restart

---

## Networking & HTTP

### Server Lifecycle

```
CameraService.startStreaming()
    ↓
startWebServer()
    ↓
IPCamWebServer(port, cameraService)
    ↓
webServer.start()
    ↓
Server listens on 0.0.0.0:8080
```

### Connection Tracking

- Each `/stream` request increments `activeConnections`
- When client disconnects, decrements `activeConnections`
- Displayed in app UI and `/status` endpoint

---

## Error Handling

### Camera Errors
- **Try-catch** around camera operations
- **Watchdog** detects frame starvation
- **Exponential backoff** for recovery attempts

### Network Errors
- **NetworkMonitor** detects WiFi loss
- **Automatic restart** when WiFi reconnects
- **Graceful degradation** if server fails to start

### Client Disconnections
- **IOException** caught when client closes connection
- **Listener removed** from list
- **Connection count** updated

---

## Performance Optimizations

### Frame Rate Control
- **Target 10 fps** to balance quality and bandwidth
- **Drop frames** if behind schedule
- **KEEP_ONLY_LATEST** strategy in CameraX

### Memory Management
- **Reuse frame buffers** where possible
- **Close ImageProxy** immediately after processing
- **No frame caching** (only latest frame kept)

### Threading
- **Separate executors** for different workloads
- **Non-blocking I/O** for network operations
- **Async coroutines** for background tasks

---

## Security Considerations

### Current Implementation
- **No authentication** (designed for local networks)
- **No encryption** (HTTP, not HTTPS)
- **CORS enabled** (`Access-Control-Allow-Origin: *`)

### Recommendations for Production
- Add authentication (basic auth, tokens)
- Use HTTPS (reverse proxy with nginx/Apache)
- Restrict CORS to specific domains
- Implement rate limiting

---

## Testing the Application

### Manual Testing Checklist

1. **Start Service**: Tap "Start Streaming" in app
2. **Check Status**: Verify server URL shows in app
3. **Test Stream**: Open browser to `http://DEVICE_IP:8080`
4. **Test Snapshot**: Navigate to `/snapshot`
5. **Switch Camera**: Use app button or `/switch` endpoint
6. **Toggle Flashlight**: Use app button or `/toggleFlashlight`
7. **Test Persistence**: Kill app, verify service continues
8. **Test Recovery**: Turn off WiFi, turn back on, verify server restarts
9. **Test Multi-Client**: Open stream in 5+ browsers simultaneously

### VLC Testing

```bash
vlc http://DEVICE_IP:8080/stream
```

### NVR Integration Testing

Configure ZoneMinder/Shinobi/Blue Iris with:
- URL: `http://DEVICE_IP:8080/stream`
- Type: MJPEG
- Verify continuous streaming

---

## Common Questions

### Q: Why LifecycleService instead of regular Service?
**A**: LifecycleService provides lifecycle awareness for CameraX, which needs a LifecycleOwner to bind use cases.

### Q: Why CopyOnWriteArrayList for frame listeners?
**A**: It's thread-safe for concurrent reads (common) and writes (rare), perfect for our use case.

### Q: Why not use Camera2 API directly?
**A**: CameraX simplifies camera operations, handles lifecycle, and provides better compatibility across devices.

### Q: Can I use this on Android TV or tablets?
**A**: Yes! The code is device-agnostic. Just ensure the device has a camera.

### Q: How do I add authentication?
**A**: Modify `IPCamWebServer.serve()` to check for credentials before serving content.

---

## Extending the Application

### Adding a New API Endpoint

1. **Add route** in `IPCamWebServer.serve()`:
   ```kotlin
   uri == "/myendpoint" -> handleMyEndpoint()
   ```

2. **Implement handler**:
   ```kotlin
   private fun handleMyEndpoint(): Response {
       // Your logic here
       return newFixedLengthResponse(Status.OK, "application/json", jsonResponse)
   }
   ```

### Adding a New Setting

1. **Add field** to `StreamingConfig`:
   ```kotlin
   val mySetting: Boolean = false
   ```

2. **Add persistence** in companion object:
   ```kotlin
   private const val KEY_MY_SETTING = "my_setting"
   putBoolean(KEY_MY_SETTING, config.mySetting)
   ```

3. **Use setting** in `CameraService`

---

## Summary

The IP_Cam application follows a clean, modular architecture:

- **CameraService** = Core logic (camera + streaming)
- **IPCamWebServer** = HTTP interface (web + API)
- **MainActivity** = User interface (mobile app)
- **Supporting classes** = Configuration, networking, boot

**Key Principle**: Single Source of Truth - one camera managed by one service serving all consumers.

For more information, see:
- [BUILD_GUIDE.md](BUILD_GUIDE.md) - How to build with Android Studio
- [README.md](README.md) - User guide and features
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - API reference

---

**Document Version**: 1.0  
**Last Updated**: 2025-12-22  
**Maintainer**: Development Team
