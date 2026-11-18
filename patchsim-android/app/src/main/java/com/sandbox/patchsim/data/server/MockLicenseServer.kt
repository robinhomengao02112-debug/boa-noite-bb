package com.sandbox.patchsim.data.server

import com.sandbox.patchsim.core.sim.LicensePolicy
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A tiny loopback-only HTTP server used to test how an app reacts to license
 * verification responses, entirely offline. It binds to 127.0.0.1 and serves a
 * single JSON endpoint:
 *
 *   GET /license?package=<pkg>&nonce=<n>  ->  { verdict, allowAccess, reason, nonce }
 *
 * It validates nothing against any real backend; the verdict comes from the
 * local [LicensePolicy]. Loopback binding keeps it unreachable from the network.
 */
class MockLicenseServer(
    private val policy: LicensePolicy,
    private val preferredPort: Int = 8723,
    private val onLog: (String) -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()
    @Volatile var boundPort: Int = -1
        private set

    val isRunning: Boolean get() = running.get()

    val baseUrl: String get() = "http://127.0.0.1:$boundPort"

    @Synchronized
    fun start(): Int {
        if (running.get()) return boundPort
        val loopback = InetAddress.getByName("127.0.0.1")
        val socket = try {
            ServerSocket(preferredPort, 16, loopback)
        } catch (e: Exception) {
            ServerSocket(0, 16, loopback) // fall back to an ephemeral port
        }
        serverSocket = socket
        boundPort = socket.localPort
        running.set(true)
        onLog("Mock license server listening on $baseUrl")
        pool.execute { acceptLoop(socket) }
        return boundPort
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        boundPort = -1
        onLog("Mock license server stopped")
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val client = try {
                socket.accept()
            } catch (e: Exception) {
                break
            }
            pool.execute { handle(client) }
        }
    }

    private fun handle(client: Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            // Drain headers
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(" ")
            val path = parts.getOrElse(1) { "/" }
            val response = route(path)
            writeResponse(sock.getOutputStream(), 200, response)
        }
    }

    private fun route(path: String): String {
        val query = path.substringAfter('?', "")
        val params = query.split('&')
            .filter { it.contains('=') }
            .associate {
                val (k, v) = it.split('=', limit = 2)
                k to java.net.URLDecoder.decode(v, "UTF-8")
            }
        val pkg = params["package"] ?: "unknown.package"
        val nonce = params["nonce"] ?: System.nanoTime().toString()
        val result = policy.evaluate(pkg, nonce)
        onLog("License check pkg=$pkg -> ${result.verdict} (allow=${result.allowAccess})")
        return JSONObject().apply {
            put("package", result.packageName)
            put("verdict", result.verdict.name)
            put("allowAccess", result.allowAccess)
            put("reason", result.reason)
            put("nonce", result.nonce)
        }.toString()
    }

    private fun writeResponse(out: OutputStream, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $status OK\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }
}
