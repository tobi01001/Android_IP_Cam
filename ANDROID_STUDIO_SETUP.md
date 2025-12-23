# Android Studio Setup - Quick Reference

This guide helps you quickly set up Android Studio to launch and debug the Android_IP_Cam application.

## Prerequisites

- Android Studio Ladybug (2024.2.1) or later installed
- JDK 17 or later
- Project already cloned and opened in Android Studio

## Quick Start (5 Minutes)

### 1. Open Project
```bash
# Clone if you haven't
git clone https://github.com/tobi01001/Android_IP_Cam.git
cd Android_IP_Cam
```

Open in Android Studio: **File → Open** → Select `Android_IP_Cam` folder

### 2. Sync Gradle
- Wait for automatic Gradle sync (1-2 minutes first time)
- OR manually: **File → Sync Project with Gradle Files**
- Status bar shows: "Gradle sync finished"

### 3. Verify Run Configuration
- Look at top toolbar for dropdown showing **"app"**
- Should see: `[app] [Device] ▶ Run 🐛 Debug`
- ✅ If visible → Ready to run!
- ❌ If missing → See "Manual Configuration" below

### 4. Connect Device or Start Emulator

**Physical Device:**
```
Settings → About Phone → Tap "Build Number" 7 times
Settings → Developer Options → Enable "USB Debugging"
Connect via USB → Accept debugging prompt
```

**Emulator:**
```
Device Manager (phone icon) → Create Device → Pixel 6
Select System Image → API 33 (Tiramisu)
Finish → Click ▶ to start emulator
```

### 5. Run the App
1. Select device from dropdown (next to "app")
2. Click green **▶ Run** button (or press `Shift + F10`)
3. Watch Logcat at bottom for app logs
4. App launches automatically!

## Manual Run Configuration Setup

If "app" configuration is missing:

1. **Run → Edit Configurations...**
2. Click **+** button (top left)
3. Select **Android App**
4. Configure:
   - **Name**: `app`
   - **Module**: `Android_IP_Cam.app.main`
   - **Launch**: Default Activity
5. **Apply** → **OK**

## Debugging

### Set Breakpoints
- Click left margin next to code line
- Red dot = breakpoint active
- Try: `CameraService.kt` line 200

### Debug Controls
- **▶ Run** (Shift+F10): Run without debugger
- **🐛 Debug** (Shift+F9): Run with debugger
- **F8**: Step over
- **F7**: Step into
- **F9**: Resume
- **Ctrl+F2**: Stop

### Logcat Filtering
- Type in filter: `CameraService` or `AndroidIPCam`
- Log levels: Verbose, Debug, Info, Warn, Error, Assert
- Search: `Ctrl+F` in Logcat

## Common Issues & Quick Fixes

### ❌ No run configuration available
**Fix**: Close and reopen project, or manually create (see above)

### ❌ Device not showing
**Fix**: 
- Physical: Check USB debugging, try different cable
- Emulator: Wait 60s for full boot, restart if needed
- Check: `adb devices` in terminal

### ❌ Build fails with "compileJava" error
**Fix**: Don't use `compileJava` task. Use:
```bash
./gradlew assembleDebug  # For Android projects
```

### ❌ App crashes on launch
**Fix**:
1. Check Logcat for errors
2. Grant camera permission: **Settings → Apps → Android_IP_Cam → Permissions**
3. Close other camera apps

### ❌ Cannot find SDK
**Fix**: **File → Project Structure → SDK Location** → Set Android SDK path

## Testing the App

Once running:

1. **Grant Permissions**: Allow Camera access when prompted
2. **Start Streaming**: Tap "Start Streaming" button
3. **View URL**: Note the server URL (e.g., `http://192.168.1.100:8080`)
4. **Test Stream**: Open URL in browser on same network
5. **Check Logs**: Watch Logcat for "Stream started" messages

## Build Variants

Switch between build types:

- **View → Tool Windows → Build Variants**
- Select `debug` or `release`
- Rebuild if needed

## Keyboard Shortcuts

| Action | Windows/Linux | Mac |
|--------|---------------|-----|
| Run | Shift+F10 | Ctrl+R |
| Debug | Shift+F9 | Ctrl+D |
| Stop | Ctrl+F2 | Cmd+F2 |
| Build | Ctrl+F9 | Cmd+F9 |
| Sync Gradle | Ctrl+Shift+O | Cmd+Shift+O |
| Find | Ctrl+F | Cmd+F |

## Project Structure

```
Android_IP_Cam/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ipcam/
│   │   │   ├── MainActivity.kt          # Entry point
│   │   │   ├── CameraService.kt         # Core service
│   │   │   ├── IPCamWebServer.kt        # HTTP server
│   │   │   └── ...
│   │   ├── res/                         # Resources
│   │   └── AndroidManifest.xml          # App config
│   └── build.gradle.kts                 # App dependencies
├── .idea/
│   └── runConfigurations/
│       └── app.xml                      # Run config
└── build.gradle.kts                     # Project config
```

## Next Steps

1. **Read Code**: Start with `MainActivity.kt` and `CameraService.kt`
2. **Architecture**: See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed code structure
3. **API Reference**: See [API_DOCUMENTATION.md](API_DOCUMENTATION.md) for endpoints
4. **Full Guide**: See [BUILD_GUIDE.md](BUILD_GUIDE.md) for comprehensive instructions

## Getting Help

- **Build issues**: See [BUILD_GUIDE.md](BUILD_GUIDE.md) Troubleshooting section
- **Code questions**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **GitHub Issues**: https://github.com/tobi01001/Android_IP_Cam/issues

---

**Quick Tip**: Keep Logcat visible while developing - it shows real-time app activity and is essential for debugging!
