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

### Step 5: Build the Project

There are several ways to build:

#### Option A: Build APK (Recommended for Testing)

1. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Wait for the build to complete (2-5 minutes on first build)
3. When complete, you'll see a notification: "APK(s) generated successfully"
4. Click **"locate"** to find the APK file

The APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

#### Option B: Build from Terminal

Open the **Terminal** tab at the bottom of Android Studio and run:

```bash
# For debug build
./gradlew assembleDebug

# For release build (unsigned)
./gradlew assembleRelease
```

The APK will be generated at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

#### Option C: Run on Device/Emulator

1. **Connect Android Device** (or start an emulator):
   - Enable **Developer Options** and **USB Debugging** on your device
   - Connect via USB
   - OR click the **Device Manager** icon and start an emulator

2. **Select Device**:
   - At the top of Android Studio, you'll see a device dropdown
   - Select your connected device or emulator

3. **Click Run**:
   - Click the green **▶ Run** button
   - OR press **Shift + F10** (Windows/Linux) or **Ctrl + R** (Mac)
   - The app will build, install, and launch automatically

   ![Run Configuration](docs/images/run-config.png)

### Step 6: Install APK on Physical Device

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
