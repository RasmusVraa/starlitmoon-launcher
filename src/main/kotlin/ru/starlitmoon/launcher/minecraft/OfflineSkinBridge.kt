package ru.starlitmoon.launcher.minecraft

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Local Yggdrasil-compatible skin API + authlib-injector agent.
 * Uses a plain ServerSocket (no jdk.httpserver module required in jpackage runtime).
 */
class OfflineSkinBridge private constructor(
    val agentArg: String,
    private val server: ServerSocket,
    private val running: AtomicBoolean,
) : AutoCloseable {
    override fun close() {
        running.set(false)
        runCatching { server.close() }
    }

    companion object {
        private const val INJECTOR_VERSION = "1.2.5"
        private const val INJECTOR_URL =
            "https://github.com/yushijinhun/authlib-injector/releases/download/" +
                "v$INJECTOR_VERSION/authlib-injector-$INJECTOR_VERSION.jar"

        fun startIfNeeded(
            cacheDir: Path,
            username: String,
            uuidDashed: String,
            skinFile: Path?,
        ): OfflineSkinBridge? {
            if (skinFile == null || !skinFile.exists()) return null
            val skinBytes = Files.readAllBytes(skinFile)
            if (skinBytes.isEmpty()) return null

            val injector = ensureInjector(cacheDir)
            val uuidFlat = uuidDashed.replace("-", "").lowercase()
            val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            val port = server.localPort
            val base = "http://127.0.0.1:$port"
            val skinUrl = "$base/textures/skin.png"
            val texturesJson =
                """{"timestamp":${System.currentTimeMillis()},"profileId":"$uuidFlat","profileName":${jsonString(username)},"textures":{"SKIN":{"url":${jsonString(skinUrl)},"metadata":{"model":"classic"}}}}"""
            val texturesB64 = Base64.getEncoder().encodeToString(texturesJson.toByteArray(StandardCharsets.UTF_8))
            val profileJson =
                """{"id":"$uuidFlat","name":${jsonString(username)},"properties":[{"name":"textures","value":${jsonString(texturesB64)}}]}"""
            val rootJson =
                """{"meta":{"serverName":"StarlitMoon","implementationName":"StarlitMoonLauncher","implementationVersion":"1.0","feature.non_email_login":true},"skinDomains":["127.0.0.1","localhost"]}"""
            val authJson =
                """{"accessToken":"0","clientToken":"0","selectedProfile":{"id":"$uuidFlat","name":${jsonString(username)}},"availableProfiles":[{"id":"$uuidFlat","name":${jsonString(username)}}]}"""

            val running = AtomicBoolean(true)
            thread(name = "starlit-skin-bridge", isDaemon = true) {
                while (running.get()) {
                    val socket = try {
                        server.accept()
                    } catch (_: Exception) {
                        break
                    }
                    thread(isDaemon = true) {
                        handleClient(socket, rootJson, profileJson, authJson, skinBytes)
                    }
                }
            }

            val agent = "-javaagent:${injector.toAbsolutePath().normalize()}=$base"
            return OfflineSkinBridge(agent, server, running)
        }

        private fun handleClient(
            socket: Socket,
            rootJson: String,
            profileJson: String,
            authJson: String,
            skinBytes: ByteArray,
        ) {
            socket.use { sock ->
                val input = BufferedInputStream(sock.getInputStream())
                val request = readHttpRequest(input) ?: return
                val path = request.path.substringBefore('?')
                when {
                    request.method == "GET" && (path == "/" || path.isEmpty()) ->
                        writeResponse(sock, 200, "application/json; charset=utf-8", rootJson.toByteArray(StandardCharsets.UTF_8))
                    request.method == "GET" && path.contains("/sessionserver/session/minecraft/profile/") ->
                        writeResponse(sock, 200, "application/json; charset=utf-8", profileJson.toByteArray(StandardCharsets.UTF_8))
                    request.method == "GET" && path.endsWith("/textures/skin.png") ->
                        writeResponse(sock, 200, "image/png", skinBytes)
                    request.method == "POST" && path.contains("/authserver/") ->
                        writeResponse(sock, 200, "application/json; charset=utf-8", authJson.toByteArray(StandardCharsets.UTF_8))
                    else ->
                        writeResponse(sock, 404, "application/json; charset=utf-8", """{"error":"Not Found"}""".toByteArray())
                }
            }
        }

        private data class HttpRequest(val method: String, val path: String)

        private fun readHttpRequest(input: BufferedInputStream): HttpRequest? {
            val lineBytes = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) break
                if (b == '\n'.code) break
                if (b != '\r'.code) lineBytes.write(b)
            }
            val line = lineBytes.toString(StandardCharsets.UTF_8)
            if (line.isBlank()) return null
            val parts = line.split(' ')
            if (parts.size < 2) return null
            // Drain headers
            var prevCr = false
            var empty = 0
            while (empty < 2) {
                val b = input.read()
                if (b < 0) break
                when (b) {
                    '\n'.code -> {
                        if (prevCr) empty++ else empty = 0
                        prevCr = false
                    }
                    '\r'.code -> prevCr = true
                    else -> {
                        empty = 0
                        prevCr = false
                    }
                }
            }
            return HttpRequest(parts[0].uppercase(), parts[1])
        }

        private fun writeResponse(socket: Socket, code: Int, contentType: String, body: ByteArray) {
            val status = when (code) {
                200 -> "OK"
                404 -> "Not Found"
                else -> "Error"
            }
            val header = buildString {
                append("HTTP/1.1 $code $status\r\n")
                append("Content-Type: $contentType\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Connection: close\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("\r\n")
            }.toByteArray(StandardCharsets.US_ASCII)
            BufferedOutputStream(socket.getOutputStream()).use { out ->
                out.write(header)
                out.write(body)
                out.flush()
            }
        }

        private fun ensureInjector(cacheDir: Path): Path {
            cacheDir.createDirectories()
            val jar = cacheDir.resolve("authlib-injector-$INJECTOR_VERSION.jar")
            if (jar.exists() && Files.size(jar) > 10_000) return jar
            val tmp = jar.resolveSibling("${jar.fileName}.part")
            val conn = (URI(INJECTOR_URL).toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "StarlitMoonLauncher")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    error("Не удалось скачать authlib-injector (HTTP ${conn.responseCode})")
                }
                conn.inputStream.use { input -> tmp.writeBytes(input.readBytes()) }
                Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                conn.disconnect()
            }
            return jar
        }

        private fun jsonString(s: String): String =
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
