package com.tanghulu.launcher.core

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.tanghulu.launcher.util.Json
import com.tanghulu.launcher.util.Log
import com.tanghulu.launcher.util.SkinManager
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.LinkedHashMap
import java.util.UUID

/**
 * Local Yggdrasil skin server.
 * Works together with authlib-injector so that offline mode can also show the launcher's custom local skin.
 */
object LocalSkinServer {

    private var server: HttpServer? = null
    private var currentPlayer: String? = null
    private var port = -1

    /** The current API root URL, or null when not started. */
    @JvmStatic
    @Synchronized
    fun apiRoot(): String? {
        if (server == null) return null
        return "http://127.0.0.1:$port"
    }

    /** Whether the skin server has been started for the given player. */
    @JvmStatic
    @Synchronized
    fun isRunningFor(playerName: String?): Boolean =
        server != null && playerName != null && playerName == currentPlayer

    /** Start the skin server for the given player (reuse it if already started for that player). */
    @JvmStatic
    @Synchronized
    fun startFor(playerName: String?): String? {
        if (isRunningFor(playerName)) return apiRoot()
        stop()
        if (playerName.isNullOrBlank()) return null
        return try {
            val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            s.createContext("/") { ex -> handle(ex) }
            s.start()
            server = s
            currentPlayer = playerName
            port = s.address.port
            Log.info("本地皮肤服务器已启动: " + apiRoot() + " (玩家: $playerName)")
            apiRoot()
        } catch (e: IOException) {
            Log.warn("本地皮肤服务器启动失败: " + e.message)
            null
        }
    }

    /** Stop the skin server. */
    @JvmStatic
    @Synchronized
    fun stop() {
        if (server != null) {
            server?.stop(0)
            server = null
            currentPlayer = null
            port = -1
        }
    }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        try {
            when {
                path.isEmpty() || path == "/" -> handleMetadata(ex)
                path.startsWith("/textures/") -> handleTexture(ex)
                path.startsWith("/sessionserver/session/minecraft/profile/") -> handleProfile(ex, path)
                else -> respond(ex, 404, "text/plain", "Not Found".toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            respond(ex, 500, "text/plain", e.toString().toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun handleMetadata(ex: HttpExchange) {
        val json = "{"
            .plus("\"meta\":{")
            .plus("\"serverName\":\"TanghuluLauncher Local Skin Server\",")
            .plus("\"implementationName\":\"TanghuluLauncher\",")
            .plus("\"implementationVersion\":\"1.0.0\",")
            .plus("\"links\":{\"homepage\":\"https://www.minecraft.net\",\"register\":\"\"}")
            .plus("},")
            .plus("\"skinDomains\":[]")
            .plus("}")
        val body = json.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    private fun handleTexture(ex: HttpExchange) {
        val skin = SkinManager.skinFile(currentPlayer)
        if (skin == null) {
            respond(ex, 404, "text/plain", "no skin".toByteArray(StandardCharsets.UTF_8))
            return
        }
        val data = Files.readAllBytes(skin)
        ex.responseHeaders.set("Content-Type", "image/png")
        ex.sendResponseHeaders(200, data.size.toLong())
        ex.responseBody.use { os -> os.write(data) }
    }

    private fun handleProfile(ex: HttpExchange, path: String) {
        var uuid = path.substring("/sessionserver/session/minecraft/profile/".length)
        if (uuid.contains("?")) uuid = uuid.substring(0, uuid.indexOf('?'))
        val skin = SkinManager.skinFile(currentPlayer)
        if (skin == null) {
            respond(ex, 404, "application/json", "{}".toByteArray(StandardCharsets.UTF_8))
            return
        }
        val api = apiRoot()
        val textureUrl = api + "/textures/" + uuid

        val textures = LinkedHashMap<String, Any?>()
        val skinObj = LinkedHashMap<String, Any?>()
        skinObj["url"] = textureUrl
        textures["SKIN"] = skinObj

        val textureRoot = LinkedHashMap<String, Any?>()
        textureRoot["timestamp"] = System.currentTimeMillis()
        textureRoot["profileId"] = uuid
        textureRoot["profileName"] = currentPlayer
        textureRoot["textures"] = textures
        val value = Base64.getEncoder().encodeToString(
            Json.stringify(textureRoot).toByteArray(StandardCharsets.UTF_8)
        )

        val prop = LinkedHashMap<String, Any?>()
        prop["name"] = "textures"
        prop["value"] = value

        val profile = LinkedHashMap<String, Any?>()
        profile["id"] = uuid
        profile["name"] = currentPlayer
        profile["properties"] = listOf(prop)

        val body = Json.stringify(profile).toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "application/json")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    private fun respond(ex: HttpExchange, code: Int, type: String, body: ByteArray) {
        ex.responseHeaders.set("Content-Type", type)
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    /** Generate an offline UUID consistent with MinecraftLauncher (OfflinePlayer:name). */
    @JvmStatic
    fun offlineUuid(playerName: String): String {
        val uuid = UUID.nameUUIDFromBytes(
            ("OfflinePlayer:" + playerName).toByteArray(StandardCharsets.UTF_8)
        )
        return uuid.toString().replace("-", "")
    }
}
