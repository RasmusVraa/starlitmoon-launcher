package ru.starlitmoon.launcher.minecraft

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.concurrent.Executors
import com.sun.net.httpserver.HttpServer
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Local Yggdrasil-compatible skin API + authlib-injector agent, so the offline
 * client loads the launcher skin in singleplayer and multiplayer.
 */
class OfflineSkinBridge private constructor(
    val agentArg: String,
    private val server: HttpServer,
) : AutoCloseable {
    override fun close() {
        runCatching { server.stop(0) }
    }

    companion object {
        private const val INJECTOR_VERSION = "1.2.5"
        private const val INJECTOR_URL =
            "https://github.com/yushijinhun/authlib-injector/releases/download/" +
                "v$INJECTOR_VERSION/authlib-injector-$INJECTOR_VERSION.jar"

        /**
         * @return bridge to keep alive for the game process, or null if no local skin.
         */
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
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val port = server.address.port
            val base = "http://127.0.0.1:$port"
            val skinUrl = "$base/textures/skin.png"
            val texturesJson = """
                {
                  "timestamp": ${System.currentTimeMillis()},
                  "profileId": "$uuidFlat",
                  "profileName": ${jsonString(username)},
                  "textures": {
                    "SKIN": {
                      "url": ${jsonString(skinUrl)},
                      "metadata": { "model": "classic" }
                    }
                  }
                }
            """.trimIndent().replace(Regex("\\s+"), "")
            val texturesB64 = Base64.getEncoder().encodeToString(
                texturesJson.toByteArray(StandardCharsets.UTF_8),
            )
            val profileJson = """
                {
                  "id": "$uuidFlat",
                  "name": ${jsonString(username)},
                  "properties": [
                    { "name": "textures", "value": ${jsonString(texturesB64)} }
                  ]
                }
            """.trimIndent()
            val rootJson = """
                {
                  "meta": {
                    "serverName": "StarlitMoon",
                    "implementationName": "StarlitMoonLauncher",
                    "implementationVersion": "1.0",
                    "feature.non_email_login": true
                  },
                  "skinDomains": ["127.0.0.1", "localhost"],
                  "signaturePublickey": ""
                }
            """.trimIndent()

            server.createContext("/") { exchange ->
                when {
                    exchange.requestMethod == "GET" && exchange.requestURI.path == "/" ->
                        writeJson(exchange, 200, rootJson)
                    exchange.requestMethod == "GET" &&
                        exchange.requestURI.path.startsWith("/sessionserver/session/minecraft/profile/") ->
                        writeJson(exchange, 200, profileJson)
                    exchange.requestMethod == "GET" && exchange.requestURI.path == "/textures/skin.png" ->
                        writeBytes(exchange, 200, "image/png", skinBytes)
                    exchange.requestMethod == "POST" &&
                        exchange.requestURI.path.startsWith("/authserver/") ->
                        writeJson(
                            exchange,
                            200,
                            """{"accessToken":"0","clientToken":"0","selectedProfile":{"id":"$uuidFlat","name":${jsonString(username)}},"availableProfiles":[{"id":"$uuidFlat","name":${jsonString(username)}}]}""",
                        )
                    else -> writeJson(exchange, 404, """{"error":"Not Found"}""")
                }
            }
            server.executor = Executors.newCachedThreadPool()
            server.start()
            val agent = "-javaagent:${injector.toAbsolutePath()}=$base"
            return OfflineSkinBridge(agent, server)
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

        private fun writeJson(
            exchange: com.sun.net.httpserver.HttpExchange,
            code: Int,
            body: String,
        ) {
            writeBytes(exchange, code, "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
        }

        private fun writeBytes(
            exchange: com.sun.net.httpserver.HttpExchange,
            code: Int,
            contentType: String,
            body: ByteArray,
        ) {
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
