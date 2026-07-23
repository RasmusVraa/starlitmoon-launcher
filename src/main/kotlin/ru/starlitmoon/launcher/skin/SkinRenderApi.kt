package ru.starlitmoon.launcher.skin

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import ru.starlitmoon.launcher.LauncherVersion
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * External 3D skin renders (no local WebGL / JavaFX).
 *
 * - [VZGE](https://vzge.me) — perspective 3D full-body (same family the site uses)
 * - [Starlight](https://starlightskins.lunareclipse.studio) — posed 3D (walking, etc.)
 */
object SkinRenderApi {
    private val client = HttpClient(CIO)
    private val ua =
        "StarlitMoon-Launcher/${LauncherVersion.CURRENT} (+https://github.com/RasmusVraa/starlitmoon-launcher; rasmusvraa@users.noreply.github.com)"

    fun close() = client.close()

    /**
     * VZGE full-body 3D PNG. Prefer public [skinUrl]; else base64 of local PNG (may fail if URL too long).
     */
    suspend fun fetchVzgeFull(
        skinUrl: String?,
        skinPng: ByteArray?,
        slim: Boolean,
        height: Int = 512,
        yaw: Int = 25,
    ): ByteArray? {
        val model = if (slim) "slim" else "wide"
        val urls = buildList {
            val publicUrl = skinUrl?.trim()?.takeIf { it.isNotBlank() }
            if (publicUrl != null) {
                val enc = URLEncoder.encode(publicUrl, StandardCharsets.UTF_8)
                add("https://vzge.me/full/$height/X-Steve.png?skin_url=$enc&$model&y=$yaw")
            }
            if (skinPng != null && skinPng.size in 1..(48 * 1024)) {
                val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(skinPng)
                if (b64.length < 6000) {
                    add("https://vzge.me/full/$height/$b64.png?$model&y=$yaw")
                }
            }
        }
        for (url in urls) {
            fetchBytes(url)?.let { return it }
        }
        return null
    }

    /**
     * Starlight walking pose with custom skin URL (true 3D posed render).
     */
    suspend fun fetchStarlightWalking(
        username: String,
        skinUrl: String,
        capeUrl: String?,
        slim: Boolean,
    ): ByteArray? {
        val nick = username.trim().ifBlank { "Steve" }.let {
            URLEncoder.encode(it, StandardCharsets.UTF_8).replace("+", "%20")
        }
        val base = "https://starlightskins.lunareclipse.studio/render/walking/$nick/full"
        val q = buildString {
            append("?skinUrl=").append(jsonStringParam(skinUrl))
            append("&skinType=").append(jsonStringParam(if (slim) "slim" else "wide"))
            if (!capeUrl.isNullOrBlank()) {
                append("&capeTexture=").append(jsonStringParam(capeUrl))
                append("&capeEnabled=true")
            }
        }
        return fetchBytes(base + q)
    }

    private fun jsonStringParam(value: String): String =
        URLEncoder.encode("\"$value\"", StandardCharsets.UTF_8)

    private suspend fun fetchBytes(url: String): ByteArray? = runCatching {
        val response = client.get(url) {
            header("User-Agent", ua)
            header("Accept", "image/png,image/webp,image/*")
        }
        if (!response.status.isSuccess()) return@runCatching null
        val bytes = response.bodyAsBytes()
        if (bytes.size < 64) return@runCatching null
        // Reject HTML error pages
        if (bytes.size >= 15 && bytes[0] == '<'.code.toByte()) return@runCatching null
        bytes
    }.getOrNull()
}
