# Building IP_Cam with Android Studio

This guide provides step-by-step instructions for building the IP_Cam Android application using Android Studio.

## Prerequisites

Before you begin, ensure you have the following installed:

1. **Android Studio Ladybug (2024.2.1) or later**
   - Download from: https://developer.android.com/studio
   
2. **JDK 17 or later**
   - Android Studio typically includes a bundled JDK 17
   - JDK 21 recommended for future-proofing
   - Or download from: https://adoptium.net/
   
3. **Git** (for cloning the repository)
   - Download from: https://git-scm.com/

## Step-by-Step Build Instructions

### Step 1: Clone the Repository

Open a terminal/command prompt and run:

```bash
git clone https://github.com/tobi01001/Android_IP_Cam.git
cd Android_IP_Cam
```

### Step 2: Open Project in Android Studio

1. **Launch Android Studio**

2. **Open the Project**:
   - Click on **"Open"** from the welcome screen
   - OR go to **File → Open** if Android Studio is already running
   - Navigate to the cloned `Android_IP_Cam` folder
   - Click **"OK"**

   ![Open Project](docs/images/open-project.png)

3. **Wait for Gradle Sync**:
   - Android Studio will automatically start syncing the project
   - This may take 1-5 minutes on first run
   - You'll see a progress bar at the bottom: "Gradle Sync"
   - Wait until you see "Gradle sync finished" in the status bar

   ![Gradle Sync](docs/images/gradle-sync.png)

### Step 3: Install Required SDK Components

If you see any prompts about missing SDK components:

1. **SDK Platforms**:
   - Click on the blue links in any error messages
   - OR go to **File → Settings → Appearance & Behavior → System Settings → Android SDK**
   - Ensure **Android 14.0 (API 34)** is installed
   - Click **"Apply"** to download

2. **SDK Build Tools**:
   - In the same SDK settings window
   - Go to **"SDK Tools"** tab
   - Ensure these are checked:
     - Android SDK Build-Tools 34
     - Android SDK Platform-Tools
     - Android Emulator (if you want to test without a physical device)
   - Click **"Apply"** to install

   ![SDK Manager](docs/images/sdk-manager.png)

### Step 4: Configure JDK (if needed)

Android Studio should use JDK 17 automatically. To verify:

1. Go to **File → Settings** (or **Android Studio → Preferences** on Mac)
2. Navigate to **Build, Execution, Deployment → Build Tools → Gradle**
3. Under **"Gradle JDK"**, ensure it's set to **"jbr-17"** (JetBrains Runtime 17) or **"Java 17"** or higher
4. Click **"OK"**

![Gradle JDK](docs/images/gradle-jdk.png)

### Step 5: Configure Run/Debug Configuration

Android Studio needs a run configuration to launch and debug the app. This project includes a pre-configured setup.

#### Automatic Configuration (Recommended)

1. **Open the project** in Android Studio (if not already open)
2. **Sync Gradle** if prompted (or go to **File → Sync Project with Gradle Files**)
3. Look at the **top toolbar** - you should see a dropdown that says **"app"** next to a green **▶ Run** button
4. If you see the "app" configuration, you're ready to go! Skip to **Step 6** below.

#### Manual Configuration (If Needed)

If the run configuration doesn't appear automatically:

1. Go to **Run → Edit Configurations...**
2. Click the **+** button (top left)
3. Select **Android App**
4. Configure as follows:
   - **Name**: `app`
   - **Module**: Select `IP_Cam.app.main` from dropdown
   - **Deploy**: Check "Default Activity" or select "Launch: default Activity"
   - **Installation Options**: Keep defaults
5. Click **Apply** then **OK**

   ![Run Configuration Setup](docs/images/run-config-setup.png)

#### Verify Configuration

The run configuration toolbar should show:
```
[app] [Connected Device/Emulator] ▶ Run 🐛 Debug
```

### Step 6: Run on Device/Emulator

Now you can launch and debug the app directly from Android Studio!

#### Option A: Using Android Studio Run Button (Recommended for Development)

1. **Prepare Device**:
   
   **For Physical Device**:
   - Enable **Developer Options** on your Android device:
     - Go to **Settings → About Phone**
     - Tap **Build Number** 7 times
     - Go back to **Settings → Developer Options**
     - Enable **USB Debugging**
   - Connect device via USB
   - Accept the USB debugging prompt on your device
   
   **For Emulator**:
   - Click **Device Manager** icon (phone icon in right toolbar)
   - Click **Create Device** if no emulator exists
   - Select a device (e.g., "Pixel 6")
   - Select a system image (e.g., "Tiramisu" / API 33)
   - Click **Finish**
   - Click the **▶ Play** button next to your emulator to start it

2. **Select Target Device**:
   - At the top of Android Studio, click the device dropdown (next to "app")
   - Select your connected device or running emulator
   - You should see device name (e.g., "Pixel 6 API 33" or "Samsung Galaxy S10+")

3. **Run the App**:
   - Click the green **▶ Run** button (or press **Shift + F10**)
   - OR click the **🐛 Debug** button to run with debugger attached
   - Android Studio will:
     - Build the APK
     - Install it on the device
     - Launch the app automatically
   - The app should open on your device showing the IP_Cam interface

4. **View Logs** (Logcat):
   - The **Logcat** panel at the bottom shows app logs in real-time
   - Filter by "IP_Cam" or "CameraService" to see app-specific logs
   - Useful for debugging and monitoring streaming activity

#### Option B: Build APK and Install Manually

If you prefer to build and install separately:

1. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   Output: `app/build/outputs/apk/debug/app-debug.apk`

2. **Install via ADB**:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Launch manually** on the device from app drawer

### Step 7: Debugging in Android Studio

Once the app is running with the debugger attached:

1. **Set Breakpoints**:
   - Click in the left margin of any code line to set a breakpoint
   - Red dot appears indicating breakpoint is set
   - Good locations: `CameraService.kt` line 200+ (camera initialization)

2. **Debug Controls**:
   - **Step Over** (F8): Execute current line
   - **Step Into** (F7): Enter method calls
   - **Resume** (F9): Continue execution
   - **Stop** (Ctrl+F2): Stop debugging session

3. **Inspect Variables**:
   - When execution pauses at breakpoint, view **Variables** panel
   - Hover over variables in code to see values
   - Evaluate expressions in **Watches** panel

4. **View Logs**:
   - Logcat shows real-time application logs
   - Filter by log level (Verbose, Debug, Info, Warn, Error)
   - Search for specific tags like "CameraService" or "IPCamWebServer"

### Step 8: Install APK on Physical Device (Alternative Method)

Once you have the APK file:

#### Via Android Studio:

1. Connect your device via USB
2. Open **Terminal** in Android Studio
3. Run:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

#### Via File Transfer:

1. Copy the APK file to your device
2. On your device, open the file manager
3. Tap the APK file
4. Allow installation from unknown sources if prompted
5. Tap **"Install"**

## Troubleshooting

### Problem: "No run configuration available" or "app configuration missing"

**Solution:**
The project includes a pre-configured run configuration in `.idea/runConfigurations/app.xml`. If it doesn't appear:

1. **Close and reopen the project**:
   - **File → Close Project**
   - Reopen from recent projects
   
2. **Manual configuration**:
   - **Run → Edit Configurations...**
   - Click **+** → **Android App**
   - Set **Name**: `app`
   - Set **Module**: `IP_Cam.app.main`
   - Set **Launch**: Default Activity
   - Click **Apply** → **OK**

3. **Verify the configuration file**:
   - Ensure `.idea/runConfigurations/app.xml` exists
   - If missing, sync Gradle: **File → Sync Project with Gradle Files**

### Problem: "Cannot locate tasks that match ':app:compileJava'"

**Solution:**
This error occurs when trying to run Java-specific tasks on an Android project. Use Android-specific build commands instead:
```bash
# CORRECT - Android build commands
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK  
./gradlew build              # Build all variants

# INCORRECT - Don't use these for Android
./gradlew compileJava        # ❌ Not for Android projects
./gradlew jar                # ❌ Not for Android projects
```

### Problem: "Gradle sync failed"

**Solution:**
1. Check your internet connection (Gradle downloads dependencies)
2. Go to **File → Invalidate Caches / Restart**
3. Try again after restart

### Problem: "SDK not found" or "SDK location not found"

**Solution:**
1. Go to **File → Project Structure**
2. Under **SDK Location**, verify the Android SDK path is set
3. If blank, set it to your Android SDK location (usually `~/Android/Sdk` on Linux/Mac or `C:\Users\YourName\AppData\Local\Android\Sdk` on Windows)

### Problem: "JDK version incompatible"

**Solution:**
1. Ensure you're using JDK 17 or later
2. Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
3. Set **Gradle JDK** to JDK 17 or higher
4. Click **"OK"** and sync again

### Problem: Device not showing in dropdown

**Solution:**
1. **For Physical Device**:
   - Verify USB debugging is enabled
   - Try a different USB cable or port
   - On device, revoke and re-authorize USB debugging:
     - **Settings → Developer Options → Revoke USB debugging authorizations**
     - Reconnect and accept prompt
   - Check ADB connection: `adb devices` (should list your device)

2. **For Emulator**:
   - Ensure emulator is fully started (not just starting up)
   - Wait 30-60 seconds for emulator to boot completely
   - Restart emulator from Device Manager
   - If still not appearing, invalidate caches: **File → Invalidate Caches / Restart**

### Problem: "Installation failed" or "INSTALL_FAILED_INSUFFICIENT_STORAGE"

**Solution:**
1. Free up space on your device/emulator
2. Uninstall old version: `adb uninstall com.example.ipcam`
3. Try installing again

### Problem: App crashes immediately on launch

**Solution:**
1. Check Logcat for error messages (filter by "AndroidRuntime" or "FATAL")
2. Verify camera permissions:
   - Go to device **Settings → Apps → IP_Cam → Permissions**
   - Ensure **Camera** permission is granted
3. Common issues:
   - Camera already in use by another app
   - Device doesn't have a camera (use physical device or emulator with camera support)
   - Missing permissions in manifest (should be already configured)

### Problem: Build is very slow

**Solution:**
1. Increase Gradle memory: Edit `gradle.properties` and increase:
   ```
   org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
   ```
2. Enable parallel builds in `gradle.properties`:
   ```
   org.gradle.parallel=true
   ```

### Problem: "Cannot resolve symbol" errors in code

**Solution:**
1. Go to **File → Invalidate Caches / Restart**
2. After restart, go to **Build → Clean Project**
3. Then **Build → Rebuild Project**

## Understanding Build Output

After a successful build, you'll find:

```
app/build/
├── outputs/
│   └── apk/
│       ├── debug/
│       │   └── app-debug.apk          # Debug APK for testing
│       └── release/
│           └── app-release-unsigned.apk  # Release APK (needs signing)
├── intermediates/                      # Temporary build files
└── generated/                          # Auto-generated files
```

## Build Variants

The project has two build variants:

1. **Debug** (default):
   - Includes debugging information
   - Not optimized
   - Larger file size
   - Easier to debug

2. **Release**:
   - Optimized code
   - Smaller file size
   - Needs signing for distribution
   - No debugging information

To switch variants:
1. Go to **Build → Select Build Variant**
2. Choose **debug** or **release**

## Signing the Release APK (for Distribution)

For production release, you need to sign the APK:

1. **Generate Keystore** (first time only):
   - Go to **Build → Generate Signed Bundle / APK**
   - Select **APK**
   - Click **"Create new..."** under Key store path
   - Fill in the details and remember the passwords

2. **Sign the APK**:
   - Go to **Build → Generate Signed Bundle / APK**
   - Select **APK**
   - Choose your keystore file
   - Enter passwords
   - Select **release** build variant
   - Click **"Finish"**

The signed APK will be at:
```
app/release/app-release.apk
```

## Running Tests (Optional)

To run unit tests:

```bash
./gradlew test
```

To run instrumented tests (requires device/emulator):

```bash
./gradlew connectedAndroidTest
```

## Next Steps After Building

Once you have successfully built the APK:

1. **Install on Device**: Follow Step 6 above
2. **Test the App**: Follow instructions in [QUICK_START.md](QUICK_START.md)
3. **Read Documentation**: See [README.md](README.md) for usage guide

## Additional Resources

- **Android Studio User Guide**: https://developer.android.com/studio/intro
- **Gradle Build Guide**: https://developer.android.com/studio/build
- **Project Documentation**: See [ARCHITECTURE.md](ARCHITECTURE.md) for code structure

## Getting Help

If you encounter issues not covered here:

1. Check the [CONTRIBUTING.md](CONTRIBUTING.md) guide
2. Open an issue on GitHub: https://github.com/tobi01001/Android_IP_Cam/issues
3. Include:
   - Android Studio version
   - JDK version
   - Error messages
   - Build output logs

---

**Last Updated**: 2025-12-22  
**Android Studio Version**: Ladybug 2024.2.1+  
**Gradle Version**: 8.11.1  
**JDK Version**: 17+ (21 recommended)
