package com.tanghulu.launcher.core.model

import com.tanghulu.launcher.util.Json
import java.util.regex.Pattern

/**
 * A rule in the version JSON, used to filter libraries and arguments by OS / features.
 */
class Rule(
    @JvmField val action: String,
    @JvmField val osName: String?,
    @JvmField val osVersion: Pattern?,
    @JvmField val osArch: String?,
    @JvmField val featureDemoUser: Boolean?
) {
    fun matches(currentOs: String, hasCustomResolution: Boolean, isDemoUser: Boolean): Boolean {
        if (osName != null && osName != currentOs) return false
        if (osVersion != null) {
            val osVer = System.getProperty("os.version", "")
            if (!osVersion.matcher(osVer).find()) return false
        }
        if (osArch != null) {
            val arch = System.getProperty("os.arch", "").lowercase()
            val expected = when (osArch) {
                "x86" -> "x86"
                "amd64" -> "amd64"
                else -> osArch
            }
            if (!arch.contains(expected)) return false
        }
        if (featureDemoUser != null && featureDemoUser != isDemoUser) return false
        return true
    }

    fun allows(): Boolean = action == "allow"

    companion object {
        @JvmStatic
        fun fromJson(json: Map<String, Any?>?): Rule {
            val action = Json.optString(json, "action", "allow") ?: "allow"
            val os = Json.asObject(Json.opt(json, "os"))
            val osName = os?.let { Json.optString(it, "name") }
            val osVersion = os?.let { Json.optString(it, "version") }?.let { Pattern.compile(it) }
            val osArch = os?.let { Json.optString(it, "arch") }
            val features = Json.asObject(Json.opt(json, "features"))
            val demo = features?.let { Json.asBoolean(it["is_demo_user"]) }
            return Rule(action, osName, osVersion, osArch, demo)
        }
    }
}
