# Android_IP_Cam API Documentation

This document provides detailed information about the RESTful API endpoints available in the Android_IP_Cam application.

## Base URL

```
http://DEVICE_IP:8080
```

Replace `DEVICE_IP` with your Android device's IP address on the local network.

## API Endpoints

### 1. Web Interface

**Endpoint**: `/`  
**Method**: GET  
**Description**: Serves the HTML web interface with live stream view  
**Response Type**: `text/html`

**Response**: HTML page with:
- Live MJPEG stream display
- Camera control buttons
- Real-time status monitoring
- API endpoint documentation

**Example**:
```bash
curl http://192.168.1.100:8080/
```

---

### 2. MJPEG Stream

**Endpoint**: `/stream`  
**Method**: GET  
**Description**: Primary streaming endpoint providing continuous MJPEG video stream  
**Response Type**: `multipart/x-mixed-replace; boundary=--jpgboundary`

**Features**:
- Supports 32+ simultaneous connections
- Automatic frame distribution
- ~10 fps frame rate
- 80% JPEG quality by default
- Low latency streaming

**Example**:
```bash
# View in VLC
vlc http://192.168.1.100:8080/stream

# Save to file
curl http://192.168.1.100:8080/stream > stream.mjpeg
```

**NVR Integration**: Use this endpoint in ZoneMinder, Shinobi, Blue Iris, or MotionEye.

---

### 3. Snapshot

**Endpoint**: `/snapshot`  
**Method**: GET  
**Description**: Returns a single JPEG image from the camera  
**Response Type**: `image/jpeg`

**Response Headers**:
- `Access-Control-Allow-Origin: *`
- `Cache-Control: no-cache`

**Example**:
```bash
# Download snapshot
curl http://192.168.1.100:8080/snapshot -o snapshot.jpg

# View in browser
open http://192.168.1.100:8080/snapshot
```

**Use Cases**:
- Motion detection triggers
- Periodic snapshots for timelapse
- Thumbnail generation
- Testing camera functionality

---

### 4. System Status

**Endpoint**: `/status`  
**Method**: GET  
**Description**: Returns JSON object with current system status  
**Response Type**: `application/json`

**Response Body**:
```json
{
  "status": "running",
  "camera": "back",
  "flashlight": false,
  "connections": 3,
  "port": 8080,
  "jpegQuality": 80,
  "targetFps": 10,
  "serverUrl": "http://192.168.1.100:8080"
}
```

**Fields**:
- `status` (string): Server status ("running" or "stopped")
- `camera` (string): Active camera ("back" or "front")
- `flashlight` (boolean): Flashlight state (true/false)
- `connections` (integer): Number of active streaming connections
- `port` (integer): HTTP server port
- `jpegQuality` (integer): JPEG compression quality (70-85)
- `targetFps` (integer): Target frame rate
- `serverUrl` (string): Full server URL

**Example**:
```bash
curl http://192.168.1.100:8080/status | jq .
```

---

### 5. Switch Camera

**Endpoint**: `/switch`  
**Method**: GET  
**Description**: Switches between front and back camera  
**Response Type**: `application/json`

**Response Body**:
```json
{
  "success": true,
  "camera": "front",
  "message": "Camera switched to front"
}
```

**Example**:
```bash
curl http://192.168.1.100:8080/switch
```

**Note**: Camera switch takes ~500ms. Active streams will automatically reconnect.

---

### 6. Toggle Flashlight

**Endpoint**: `/toggleFlashlight`  
**Method**: GET  
**Description**: Toggles flashlight on/off (back camera only)  
**Response Type**: `application/json`

**Response Body**:
```json
{
  "success": true,
  "flashlight": true,
  "message": "Flashlight enabled"
}
```

**Example**:
```bash
curl http://192.168.1.100:8080/toggleFlashlight
```

**Note**: Flashlight is only available when using the back camera.

---

### 7. Set Rotation

**Endpoint**: `/setRotation`  
**Method**: GET  
**Description**: Sets camera rotation angle  
**Response Type**: `application/json`

**Query Parameters**:
- `value` (required): Rotation value
  - `0` - No rotation
  - `90` - Rotate 90° clockwise
  - `180` - Rotate 180°
  - `270` - Rotate 270° clockwise
  - `auto` - Automatic based on device orientation

**Response Body**:
```json
{
  "success": true,
  "rotation": "90"
}
```

**Example**:
```bash
curl "http://192.168.1.100:8080/setRotation?value=90"
```

---

### 8. Set Format (Resolution)

**Endpoint**: `/setFormat`  
**Method**: GET  
**Description**: Sets camera resolution  
**Response Type**: `application/json`

**Query Parameters**:
- `value` (optional): Resolution in format `WIDTHxHEIGHT`
  - Examples: `1920x1080`, `1280x720`, `640x480`
  - Omit for automatic resolution

**Response Body**:
```json
{
  "success": true,
  "format": "1920x1080"
}
```

**Example**:
```bash
curl "http://192.168.1.100:8080/setFormat?value=1920x1080"
```

---

### 9. Server-Sent Events

**Endpoint**: `/events`  
**Method**: GET  
**Description**: Provides real-time status updates via Server-Sent Events  
**Response Type**: `text/event-stream`

**Event Data Format**:
```
data: {"connections":3,"camera":"back","flashlight":false}

data: {"connections":4,"camera":"back","flashlight":false}
```

**Update Frequency**: Every 2 seconds

**Example (JavaScript)**:
```javascript
const eventSource = new EventSource('http://192.168.1.100:8080/events');
eventSource.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Connections:', data.connections);
  console.log('Camera:', data.camera);
  console.log('Flashlight:', data.flashlight);
};
```

---

## Error Responses

### HTTP Status Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | OK | Request successful |
| 404 | Not Found | Endpoint not found |
| 500 | Internal Server Error | Server error occurred |
| 503 | Service Unavailable | Camera not available |

### Error Response Format

```json
{
  "error": "Error message",
  "status": 500
}
```

---

## CORS Support

All API endpoints include CORS headers:
```
Access-Control-Allow-Origin: *
```

This allows web applications from any origin to access the API.

---

## Rate Limiting

- **No rate limiting** is applied
- Designed to support 32+ simultaneous connections
- Frame rate throttling prevents server overload

---

## Authentication

- **No authentication** is required
- Intended for use on trusted local networks
- For secure remote access, use VPN or reverse proxy with authentication

---

## Best Practices

### Streaming
- Use `/stream` for continuous video
- Use `/snapshot` for periodic images (less bandwidth)
- Monitor `/status` for connection count

### Integration
- Check `/status` before starting stream
- Handle reconnection on network interruption
- Use Server-Sent Events for real-time monitoring

### Performance
- Limit concurrent connections to reasonable number
- Use snapshot endpoint for motion detection
- Consider network bandwidth limitations

---

## Example Integrations

### Python Script
```python
import requests
import cv2
import numpy as np

# Get snapshot
response = requests.get('http://192.168.1.100:8080/snapshot')
img_array = np.frombuffer(response.content, np.uint8)
img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
cv2.imshow('IP Cam Snapshot', img)
cv2.waitKey(0)
```

### JavaScript Web App
```javascript
// Display stream
const img = document.createElement('img');
img.src = 'http://192.168.1.100:8080/stream';
document.body.appendChild(img);

// Monitor status
setInterval(async () => {
  const response = await fetch('http://192.168.1.100:8080/status');
  const status = await response.json();
  console.log('Active connections:', status.connections);
}, 2000);
```

### Bash Script
```bash
#!/bin/bash

# Check status
STATUS=$(curl -s http://192.168.1.100:8080/status)
echo "Status: $STATUS"

# Take snapshot every minute
while true; do
  curl -s http://192.168.1.100:8080/snapshot -o "snapshot_$(date +%s).jpg"
  sleep 60
done
```

---

## Surveillance Software Configuration

### ZoneMinder
```
Monitor Type: Remote
Source Type: HTTP
Remote Method: Simple
Remote Host Path: 192.168.1.100:8080/stream
```

### Shinobi
```
Connection Type: MJPEG
Input: http://192.168.1.100:8080/stream
```

### Blue Iris
```
Network IP
Make: Generic/MJPEG
HTTP://
Path: /stream
```

### MotionEye
```
Camera Type: Network Camera
Camera URL: http://192.168.1.100:8080/stream
Snapshot URL: http://192.168.1.100:8080/snapshot
```

---

## Troubleshooting

### No Response from Endpoints
- Verify device IP address
- Check firewall settings
- Ensure app is running (check notification)
- Verify same WiFi network

### Stream Buffering
- Check network bandwidth
- Reduce JPEG quality
- Lower target FPS
- Check active connections count

### Connection Refused
- Verify correct port (default 8080)
- Check Android firewall
- Ensure app has camera permission

---

## Support

For issues or questions about the API, please open an issue on GitHub:
https://github.com/tobi01001/Android_IP_Cam/issues

---

**Version**: 1.0  
**Last Updated**: 2025-12-22
