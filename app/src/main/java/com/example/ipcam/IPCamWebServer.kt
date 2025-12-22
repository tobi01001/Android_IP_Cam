package com.example.ipcam

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * IPCamWebServer - HTTP Server for IP Camera Streaming
 * 
 * Implements the standardized interface for surveillance software:
 * - /stream - MJPEG video stream
 * - /snapshot - Single JPEG image
 * - /status - JSON system status
 * - /switch - Switch camera
 * - /toggleFlashlight - Toggle flashlight
 * - / - Web interface
 * 
 * Supports 32+ simultaneous connections with dedicated thread pools.
 */
class IPCamWebServer(
    port: Int,
    private val cameraService: CameraService
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "IPCamWebServer"
        private const val BOUNDARY = "jpgboundary"
        private const val MJPEG_MIME = "multipart/x-mixed-replace; boundary=--$BOUNDARY"
    }

    private val streamingExecutor = Executors.newCachedThreadPool()
    private val activeStreams = ConcurrentHashMap<Long, Boolean>()
    private val streamIdGenerator = AtomicLong(0)
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        Log.d(TAG, "Request: ${session.method} $uri from ${session.remoteIpAddress}")
        
        return try {
            when {
                uri == "/" -> serveWebInterface()
                uri == "/stream" -> serveMJPEGStream()
                uri == "/snapshot" -> serveSnapshot()
                uri == "/status" -> serveStatus()
                uri == "/switch" -> handleSwitchCamera()
                uri == "/toggleFlashlight" -> handleToggleFlashlight()
                uri == "/setRotation" -> handleSetRotation(session)
                uri == "/setFormat" -> handleSetFormat(session)
                uri.startsWith("/events") -> serveServerSentEvents()
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "404 Not Found"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Internal Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve web interface HTML
     */
    private fun serveWebInterface(): Response {
        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IP_Cam - Live Stream</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 0;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        h1 {
            color: #333;
            margin-top: 0;
        }
        .stream-container {
            width: 100%;
            max-width: 100%;
            margin: 20px 0;
            text-align: center;
            background: #000;
            border-radius: 4px;
            overflow: hidden;
        }
        #stream {
            width: 100%;
            height: auto;
            display: block;
        }
        .controls {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 10px;
            margin: 20px 0;
        }
        button {
            padding: 12px 24px;
            font-size: 16px;
            background-color: #6200ee;
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            transition: background-color 0.3s;
        }
        button:hover {
            background-color: #3700b3;
        }
        button:active {
            background-color: #30009c;
        }
        .status {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin: 20px 0;
        }
        .status-item {
            background: #f5f5f5;
            padding: 15px;
            border-radius: 4px;
        }
        .status-label {
            font-weight: bold;
            color: #666;
            font-size: 14px;
            margin-bottom: 5px;
        }
        .status-value {
            font-size: 18px;
            color: #333;
        }
        .footer {
            margin-top: 30px;
            padding-top: 20px;
            border-top: 1px solid #ddd;
            color: #666;
            font-size: 14px;
        }
        @media (max-width: 768px) {
            .container {
                padding: 10px;
            }
            .controls {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📹 IP_Cam - Live Stream</h1>
        
        <div class="stream-container">
            <img id="stream" src="/stream" alt="Loading stream...">
        </div>
        
        <div class="controls">
            <button onclick="switchCamera()">Switch Camera</button>
            <button onclick="toggleFlashlight()">Toggle Flashlight</button>
            <button onclick="takeSnapshot()">Take Snapshot</button>
            <button onclick="location.reload()">Refresh Page</button>
        </div>
        
        <div class="status">
            <div class="status-item">
                <div class="status-label">Server Status</div>
                <div class="status-value" id="serverStatus">Running</div>
            </div>
            <div class="status-item">
                <div class="status-label">Active Connections</div>
                <div class="status-value" id="connections">-</div>
            </div>
            <div class="status-item">
                <div class="status-label">Camera</div>
                <div class="status-value" id="camera">-</div>
            </div>
            <div class="status-item">
                <div class="status-label">Flashlight</div>
                <div class="status-value" id="flashlight">-</div>
            </div>
        </div>
        
        <div class="footer">
            <p><strong>API Endpoints:</strong></p>
            <ul>
                <li><code>GET /stream</code> - MJPEG video stream</li>
                <li><code>GET /snapshot</code> - Single JPEG image</li>
                <li><code>GET /status</code> - JSON system status</li>
                <li><code>GET /switch</code> - Switch camera</li>
                <li><code>GET /toggleFlashlight</code> - Toggle flashlight</li>
            </ul>
            <p>Compatible with ZoneMinder, Shinobi, Blue Iris, MotionEye, and VLC</p>
        </div>
    </div>
    
    <script>
        // Auto-refresh status every 2 seconds
        setInterval(updateStatus, 2000);
        updateStatus();
        
        function updateStatus() {
            fetch('/status')
                .then(response => response.json())
                .then(data => {
                    document.getElementById('connections').textContent = data.connections || 0;
                    document.getElementById('camera').textContent = data.camera || 'Unknown';
                    document.getElementById('flashlight').textContent = data.flashlight ? 'ON' : 'OFF';
                })
                .catch(err => console.error('Status update failed:', err));
        }
        
        function switchCamera() {
            fetch('/switch')
                .then(response => response.json())
                .then(data => {
                    alert('Camera switched to: ' + data.camera);
                    setTimeout(() => location.reload(), 500);
                })
                .catch(err => alert('Failed to switch camera: ' + err));
        }
        
        function toggleFlashlight() {
            fetch('/toggleFlashlight')
                .then(response => response.json())
                .then(data => {
                    updateStatus();
                    alert('Flashlight: ' + (data.flashlight ? 'ON' : 'OFF'));
                })
                .catch(err => alert('Failed to toggle flashlight: ' + err));
        }
        
        function takeSnapshot() {
            window.open('/snapshot', '_blank');
        }
        
        // Handle stream errors
        document.getElementById('stream').onerror = function() {
            console.error('Stream error - attempting reconnect in 3s');
            setTimeout(() => {
                this.src = '/stream?t=' + Date.now();
            }, 3000);
        };
    </script>
</body>
</html>
        """.trimIndent()
        
        return newFixedLengthResponse(Response.Status.OK, "text/html", html).apply {
            addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            addHeader("Pragma", "no-cache")
            addHeader("Expires", "0")
        }
    }

    /**
     * Serve MJPEG stream - primary streaming endpoint
     */
    private fun serveMJPEGStream(): Response {
        val streamId = streamIdGenerator.incrementAndGet()
        activeStreams[streamId] = true
        cameraService.incrementConnections()
        
        Log.d(TAG, "Starting MJPEG stream #$streamId, total connections: ${cameraService.getActiveConnections()}")
        
        return newChunkedResponse(Response.Status.OK, MJPEG_MIME) { output ->
            streamingExecutor.execute {
                try {
                    streamMJPEG(output, streamId)
                } catch (e: Exception) {
                    Log.e(TAG, "Stream #$streamId error", e)
                } finally {
                    activeStreams.remove(streamId)
                    cameraService.decrementConnections()
                    Log.d(TAG, "Stream #$streamId ended, remaining: ${cameraService.getActiveConnections()}")
                }
            }
        }
    }

    /**
     * Stream MJPEG frames to client
     */
    private fun streamMJPEG(output: OutputStream, streamId: Long) {
        val frameListener = object : CameraService.FrameListener {
            override fun onFrameAvailable(frame: ByteArray) {
                try {
                    if (!activeStreams.containsKey(streamId)) {
                        return
                    }
                    
                    // Write multipart boundary
                    output.write("--$BOUNDARY\r\n".toByteArray())
                    output.write("Content-Type: image/jpeg\r\n".toByteArray())
                    output.write("Content-Length: ${frame.size}\r\n".toByteArray())
                    output.write("\r\n".toByteArray())
                    
                    // Write JPEG data
                    output.write(frame)
                    output.write("\r\n".toByteArray())
                    output.flush()
                } catch (e: IOException) {
                    Log.d(TAG, "Stream #$streamId client disconnected")
                    activeStreams.remove(streamId)
                }
            }
        }
        
        cameraService.addFrameListener(frameListener)
        
        try {
            // Keep stream alive
            while (activeStreams.containsKey(streamId)) {
                Thread.sleep(100)
            }
        } catch (e: InterruptedException) {
            Log.d(TAG, "Stream #$streamId interrupted")
        } finally {
            cameraService.removeFrameListener(frameListener)
        }
    }

    /**
     * Serve single snapshot image
     */
    private fun serveSnapshot(): Response {
        val frame = cameraService.getLatestFrame()
        
        return if (frame != null) {
            newFixedLengthResponse(
                Response.Status.OK,
                "image/jpeg",
                ByteArrayInputStream(frame),
                frame.size.toLong()
            ).apply {
                addHeader("Access-Control-Allow-Origin", "*")
                addHeader("Cache-Control", "no-cache")
            }
        } else {
            newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                MIME_PLAINTEXT,
                "No frame available"
            )
        }
    }

    /**
     * Serve JSON status
     */
    private fun serveStatus(): Response {
        val config = cameraService.getConfig()
        
        val json = JSONObject().apply {
            put("status", "running")
            put("camera", config.cameraType)
            put("flashlight", config.flashlightEnabled)
            put("connections", cameraService.getActiveConnections())
            put("port", config.serverPort)
            put("jpegQuality", config.jpegQuality)
            put("targetFps", config.targetFps)
            put("serverUrl", cameraService.getServerUrl())
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Handle camera switch request
     */
    private fun handleSwitchCamera(): Response {
        cameraService.switchCamera()
        
        val json = JSONObject().apply {
            put("success", true)
            put("camera", cameraService.getConfig().cameraType)
            put("message", "Camera switched to ${cameraService.getConfig().cameraType}")
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Handle flashlight toggle request
     */
    private fun handleToggleFlashlight(): Response {
        cameraService.toggleFlashlight()
        
        val json = JSONObject().apply {
            put("success", true)
            put("flashlight", cameraService.getConfig().flashlightEnabled)
            put("message", "Flashlight ${if (cameraService.getConfig().flashlightEnabled) "enabled" else "disabled"}")
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Handle set rotation request
     */
    private fun handleSetRotation(session: IHTTPSession): Response {
        val params = session.parms
        val rotation = params["value"] ?: "auto"
        
        // Save rotation setting
        val config = cameraService.getConfig()
        val newConfig = config.copy(rotation = rotation)
        StreamingConfig.save(cameraService, newConfig)
        
        val json = JSONObject().apply {
            put("success", true)
            put("rotation", rotation)
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Handle set format request
     */
    private fun handleSetFormat(session: IHTTPSession): Response {
        val params = session.parms
        val format = params["value"]
        
        // Save format setting
        val config = cameraService.getConfig()
        val newConfig = config.copy(resolution = format)
        StreamingConfig.save(cameraService, newConfig)
        
        val json = JSONObject().apply {
            put("success", true)
            put("format", format ?: "auto")
        }
        
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            json.toString()
        ).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Serve Server-Sent Events for real-time status updates
     */
    private fun serveServerSentEvents(): Response {
        return newChunkedResponse(Response.Status.OK, "text/event-stream") { output ->
            serverScope.launch {
                try {
                    while (isActive) {
                        val status = JSONObject().apply {
                            put("connections", cameraService.getActiveConnections())
                            put("camera", cameraService.getConfig().cameraType)
                            put("flashlight", cameraService.getConfig().flashlightEnabled)
                        }
                        
                        output.write("data: ${status}\n\n".toByteArray())
                        output.flush()
                        
                        delay(2000) // Update every 2 seconds
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "SSE connection closed")
                }
            }
        }
    }

    override fun stop() {
        super.stop()
        streamingExecutor.shutdown()
        activeStreams.clear()
        Log.d(TAG, "Web server stopped")
    }
}
