# Contributing to Android_IP_Cam

Thank you for your interest in contributing to Android_IP_Cam! This document provides guidelines and instructions for contributing to the project.

## Development Environment Setup

### Prerequisites

1. **Android Studio**: Ladybug (2024.2.1) or later
2. **Android SDK**: API 24 (minimum) to API 34 (target)
3. **Java Development Kit**: JDK 17 or later (JDK 21 recommended)
4. **Git**: For version control

### Setting Up the Project

1. **Clone the repository**:
   ```bash
   git clone https://github.com/tobi01001/Android_IP_Cam.git
   cd Android_IP_Cam
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory
   - Wait for Gradle sync to complete

3. **Install required SDK components**:
   - Android Studio will prompt you to install missing SDK components
   - Accept and install all required components

## Project Structure

```
Android_IP_Cam/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/ipcam/    # Kotlin source code
│   │   │   ├── CameraService.kt         # Core camera service
│   │   │   ├── IPCamWebServer.kt        # HTTP server
│   │   │   ├── MainActivity.kt          # Main activity
│   │   │   ├── NetworkMonitor.kt        # Network monitoring
│   │   │   ├── StreamingConfig.kt       # Configuration
│   │   │   └── BootReceiver.kt          # Auto-start receiver
│   │   ├── res/                         # Android resources
│   │   └── AndroidManifest.xml          # App manifest
│   └── build.gradle.kts                 # App build configuration
├── build.gradle.kts                     # Project build configuration
├── settings.gradle.kts                  # Gradle settings
├── README.md                            # Main documentation
├── REQUIREMENTS_SPECIFICATION.md        # Technical requirements
└── API_DOCUMENTATION.md                 # API reference

## Core Design Principles

When contributing, ensure your changes align with these five core principles:

### 1. Bandwidth Usage & Performance
- Minimize network bandwidth consumption
- Target ~10 fps for optimal balance
- Use 70-85% JPEG quality
- Implement frame dropping for slow clients

### 2. Single Source of Truth
- CameraService is the ONLY camera manager
- MainActivity receives updates via callbacks
- No direct camera access outside CameraService

### 3. Persistence of Background Processes
- Foreground service with automatic restart
- Watchdog monitoring with exponential backoff
- Wake locks for 24/7 operation
- Settings persistence via SharedPreferences

### 4. Usability
- One-tap controls for common operations
- Real-time status updates
- Clear, actionable error messages
- Responsive web interface

### 5. Standardized Interface
- Standard MJPEG stream format
- Compatible with major NVR/VMS systems
- RESTful API with JSON responses
- Support for 32+ simultaneous connections

## Development Guidelines

### Code Style

#### Kotlin
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and concise

```kotlin
/**
 * Process camera frame and convert to JPEG
 * 
 * @param image ImageProxy from CameraX
 * @return Compressed JPEG byte array or null on error
 */
private fun processFrame(image: ImageProxy): ByteArray? {
    // Implementation
}
```

#### XML Resources
- Use meaningful resource IDs
- Follow Android naming conventions
- Add comments for complex layouts

### Testing

Before submitting a pull request:

1. **Build the project**:
   ```bash
   ./gradlew build
   ```

2. **Test on physical device**:
   - Install on Android device
   - Test camera streaming
   - Verify web interface
   - Check NVR compatibility

3. **Test scenarios**:
   - Start/stop service
   - Camera switching
   - Flashlight toggle
   - Network reconnection
   - App task removal
   - Device reboot (if auto-start enabled)

### Commit Guidelines

- Write clear, descriptive commit messages
- Use present tense ("Add feature" not "Added feature")
- Reference issue numbers when applicable
- Keep commits focused and atomic

```bash
# Good commit messages
git commit -m "Add support for 4K video streaming"
git commit -m "Fix camera not releasing on service stop"
git commit -m "Update README with VLC integration example"

# Bad commit messages
git commit -m "Fixed stuff"
git commit -m "Updates"
git commit -m "WIP"
```

## Types of Contributions

### Bug Reports

When reporting bugs, please include:

1. **Environment**:
   - Android version
   - Device model
   - App version

2. **Steps to reproduce**:
   - Numbered list of steps
   - Expected behavior
   - Actual behavior

3. **Logs**:
   - Relevant logcat output
   - Error messages

### Feature Requests

When requesting features:

1. Describe the feature clearly
2. Explain the use case
3. Consider how it aligns with core design principles
4. Provide examples if applicable

### Pull Requests

#### Before Submitting

1. Fork the repository
2. Create a feature branch:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make your changes
4. Test thoroughly
5. Update documentation if needed
6. Commit with clear messages

#### Submitting

1. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
2. Create a pull request on GitHub
3. Fill out the PR template
4. Link related issues

#### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement

## Testing
Describe how you tested the changes

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tested on physical device
- [ ] No breaking changes
```

## Development Workflow

### Adding a New Feature

1. **Design**:
   - Review requirements specification
   - Consider impact on core principles
   - Plan architecture changes

2. **Implementation**:
   - Create feature branch
   - Write code following style guide
   - Add appropriate logging
   - Handle errors gracefully

3. **Testing**:
   - Test on multiple Android versions
   - Verify performance impact
   - Check memory usage
   - Test edge cases

4. **Documentation**:
   - Update README if needed
   - Update API documentation
   - Add code comments
   - Update REQUIREMENTS_SPECIFICATION if applicable

5. **Review**:
   - Self-review code
   - Check for potential issues
   - Ensure tests pass
   - Submit pull request

### Fixing a Bug

1. **Reproduce**:
   - Confirm bug exists
   - Identify root cause
   - Create test case

2. **Fix**:
   - Implement minimal fix
   - Avoid refactoring unless necessary
   - Add logging if helpful

3. **Verify**:
   - Test fix works
   - Ensure no regressions
   - Test related functionality

4. **Document**:
   - Update changelog
   - Add comments if needed
   - Submit pull request

## Key Areas for Contribution

### High Priority

1. **Testing**: Add unit tests and integration tests
2. **Performance**: Optimize frame processing and network usage
3. **Compatibility**: Test on various Android versions and devices
4. **Documentation**: Improve guides and examples

### Medium Priority

1. **UI Improvements**: Enhance mobile app interface
2. **Web Interface**: Add more controls and features
3. **API Extensions**: Add new endpoints as needed
4. **Error Handling**: Improve error messages and recovery

### Future Enhancements

1. **HLS Support**: Implement H.264 streaming (see REQUIREMENTS_SPECIFICATION.md)
2. **Audio Streaming**: Add audio capture and streaming
3. **Authentication**: Add optional authentication layer
4. **Cloud Integration**: Cloud recording options

## Code Review Process

All contributions go through code review:

1. **Automated checks**:
   - Build success
   - Code style compliance
   - Basic functionality tests

2. **Manual review**:
   - Code quality
   - Adherence to design principles
   - Performance considerations
   - Security implications

3. **Feedback**:
   - Reviewers may request changes
   - Address feedback promptly
   - Push updates to PR branch

4. **Approval**:
   - Once approved, PR will be merged
   - Changes will be included in next release

## Resources

### Documentation
- [REQUIREMENTS_SPECIFICATION.md](REQUIREMENTS_SPECIFICATION.md) - Technical requirements
- [API_DOCUMENTATION.md](API_DOCUMENTATION.md) - API reference
- [README.md](README.md) - User guide

### External Resources
- [CameraX Documentation](https://developer.android.com/training/camerax)
- [NanoHTTPD GitHub](https://github.com/NanoHttpd/nanohttpd)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android Foreground Services](https://developer.android.com/guide/components/foreground-services)

## Getting Help

- **GitHub Issues**: For bugs and feature requests
- **Discussions**: For questions and general discussion
- **Code Review**: Ask questions in pull request comments

## License

By contributing to Android_IP_Cam, you agree that your contributions will be licensed under the MIT License.

## Recognition

Contributors will be acknowledged in:
- GitHub contributors page
- Release notes
- Project documentation

Thank you for contributing to Android_IP_Cam! 🎥📹
