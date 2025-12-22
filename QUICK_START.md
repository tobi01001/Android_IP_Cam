# IP_Cam Quick Start Guide

Get your Android IP Camera up and running in 5 minutes!

## Installation

1. **Download and Install**
   - Download the APK from [Releases](https://github.com/tobi01001/Android_IP_Cam/releases)
   - Install on your Android device (Allow "Install from Unknown Sources" if needed)

2. **Grant Permissions**
   - Camera permission ✓
   - Notification permission (Android 13+) ✓
   - Battery optimization exemption (recommended) ✓

3. **Start Streaming**
   - Tap "Start Streaming" button
   - Note the server URL (e.g., `http://192.168.1.100:8080`)

## Accessing Your Camera

### Web Browser
Open in any browser on the same network:
```
http://YOUR_DEVICE_IP:8080
```

### VLC Media Player
```bash
vlc http://YOUR_DEVICE_IP:8080/stream
```

### Direct Stream URL
```
http://YOUR_DEVICE_IP:8080/stream
```

### Snapshot URL
```
http://YOUR_DEVICE_IP:8080/snapshot
```

## Common Use Cases

### 1. Baby Monitor
- Mount phone with view of crib
- Access stream from browser on any device
- Use snapshot endpoint for periodic checks

### 2. Home Security
- Install in strategic location
- Integrate with surveillance software
- Enable auto-start on boot

### 3. Pet Monitoring
- Place where pets spend time
- Check in remotely via web interface
- Use front camera if wall-mounted

### 4. Multi-Camera Setup
- Install IP_Cam on multiple devices
- Add all to NVR software (ZoneMinder, etc.)
- Monitor all cameras from single interface

## Tips for Best Results

### Placement
- ✓ Ensure stable WiFi connection
- ✓ Use charging cable for continuous power
- ✓ Mount securely at desired angle
- ✓ Test view before final placement

### Settings
- ✓ Enable battery optimization exemption
- ✓ Enable auto-start on boot (if desired)
- ✓ Adjust camera orientation as needed
- ✓ Use back camera for better quality

### Network
- ✓ Use WiFi (not cellular data)
- ✓ Note IP address (may change on reboot)
- ✓ Consider static IP in router settings
- ✓ Use VPN for remote access

## Troubleshooting

### Can't Connect to Stream
1. Verify device and computer on same WiFi network
2. Check IP address in app matches URL you're using
3. Ensure firewall isn't blocking port 8080
4. Try restarting the streaming service

### Stream Is Choppy
1. Move closer to WiFi router
2. Reduce number of simultaneous connections
3. Check network bandwidth
4. Close other apps on device

### Camera Stops Working
1. Check battery optimization settings
2. Ensure wake locks are enabled (automatic)
3. Verify sufficient storage space
4. Restart app if needed

### Can't Switch Cameras
1. Ensure camera is not in use by another app
2. Grant camera permission if prompted
3. Restart service if needed

## Integration with Surveillance Software

### ZoneMinder
```
Monitor Type: Remote
Source Type: HTTP  
Remote Method: Simple
Path: /stream
```

### Blue Iris
```
Network IP
Make: Generic/MJPEG
Path: /stream
```

### Shinobi
```
Connection Type: MJPEG
Input: http://DEVICE_IP:8080/stream
```

### MotionEye
```
Camera Type: Network Camera
URL: http://DEVICE_IP:8080/stream
```

## API Quick Reference

| URL | Purpose |
|-----|---------|
| `/` | Web interface |
| `/stream` | MJPEG video stream |
| `/snapshot` | Single image |
| `/status` | JSON status info |
| `/switch` | Switch front/back camera |
| `/toggleFlashlight` | Toggle flashlight |

## Example Commands

```bash
# Get status
curl http://192.168.1.100:8080/status

# Save snapshot
curl http://192.168.1.100:8080/snapshot -o photo.jpg

# Switch camera
curl http://192.168.1.100:8080/switch

# View stream in VLC
vlc http://192.168.1.100:8080/stream
```

## Power Management

### For 24/7 Operation
1. Keep device plugged in
2. Disable battery optimization
3. Enable "Keep screen on" in settings (optional)
4. Use battery saver mode (if available)

### Battery Considerations
- Streaming uses significant power
- Device will get warm during use
- Ensure adequate ventilation
- Monitor device temperature

## Security Considerations

### Local Network Use (Recommended)
- ✓ No authentication required
- ✓ Fast streaming
- ✓ Simple setup

### Remote Access
- Use VPN for secure remote access
- Consider firewall rules
- Use authentication if exposing to internet
- Change default port if needed

## Performance Tips

### Optimal Settings
- **Resolution**: 1920x1080 (1080p)
- **Frame Rate**: 10 fps
- **JPEG Quality**: 80%
- **Max Connections**: 32

### Reducing Bandwidth
- Lower resolution (1280x720)
- Reduce frame rate (5-10 fps)
- Decrease JPEG quality (70-75%)
- Use snapshot endpoint instead of stream

## Getting Help

- **Documentation**: [README.md](README.md)
- **API Reference**: [API_DOCUMENTATION.md](API_DOCUMENTATION.md)
- **Issues**: [GitHub Issues](https://github.com/tobi01001/Android_IP_Cam/issues)
- **Requirements**: [REQUIREMENTS_SPECIFICATION.md](REQUIREMENTS_SPECIFICATION.md)

## Next Steps

1. **Explore Features**: Try all camera controls
2. **Test Performance**: Check stream quality
3. **Integrate**: Add to surveillance system
4. **Optimize**: Adjust settings for your needs
5. **Automate**: Enable auto-start if desired

---

**Need more help?** Check the full [README.md](README.md) or open an issue on GitHub.
