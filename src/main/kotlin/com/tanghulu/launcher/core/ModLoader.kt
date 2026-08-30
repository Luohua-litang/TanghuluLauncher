package com.tanghulu.launcher.core

/**
 * Mod loader type.
 * The first seven (Fabric/Quilt/NeoForge/Forge/OptiFine/Fabric API/QSL-QFAPI) can be installed directly.
 */
enum class ModLoader(
    @JvmField val displayName: String,
    /** Meta API base URL (used by Fabric/Quilt; null means the maven + installer path is used). */
    @JvmField val metaBase: String?,
    /** Maven repository base URL. */
    @JvmField val mavenBase: String?,
    /** Whether this launcher has implemented the install flow. */
    @JvmField val supported: Boolean
) {
    FABRIC("Fabric", "https://meta.fabricmc.net/v2/versions/loader", "https://maven.fabricmc.net/", true),
    QUILT("Quilt", "https://meta.quiltmc.org/v3/versions/loader", "https://maven.quiltmc.org/repository/release/", true),
    NEOFORGE("NeoForge", null, "https://maven.neoforged.net/releases/", true),
    FORGE("Forge", null, "https://maven.minecraftforge.net/", true),
    OPTIFINE("OptiFine", null, null, true),
    FABRIC_API("Fabric API", null, null, true),
    QSL_QFAPI("QSL/QFAPI", null, null, true);

    /** Whether this is a meta-based loader (generates a version JSON directly, without running the official installer). */
    fun isMeta(): Boolean = metaBase != null

    /** Whether installation requires a standalone Java runtime (used to run the official installer subprocess). */
    fun requiresJava(): Boolean = this == FORGE || this == NEOFORGE

    override fun toString(): String = displayName
}
