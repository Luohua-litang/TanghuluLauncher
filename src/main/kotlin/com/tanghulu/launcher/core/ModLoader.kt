package com.tanghulu.launcher.core

/**
 * Mod 加载器类型。
 * 前七个（Fabric/Quilt/NeoForge/Forge/OptiFine/Fabric API/QSL-QFAPI）均可直接安装。
 */
enum class ModLoader(
    @JvmField val displayName: String,
    /** meta API 基础地址（Fabric/Quilt 使用；null 表示走 maven + installer 方式）。 */
    @JvmField val metaBase: String?,
    /** maven 仓库基础地址。 */
    @JvmField val mavenBase: String?,
    /** 当前启动器是否已实现安装流程。 */
    @JvmField val supported: Boolean
) {
    FABRIC("Fabric", "https://meta.fabricmc.net/v2/versions/loader", "https://maven.fabricmc.net/", true),
    QUILT("Quilt", "https://meta.quiltmc.org/v3/versions/loader", "https://maven.quiltmc.org/repository/release/", true),
    NEOFORGE("NeoForge", null, "https://maven.neoforged.net/releases/", true),
    FORGE("Forge", null, "https://maven.minecraftforge.net/", true),
    OPTIFINE("OptiFine", null, null, true),
    FABRIC_API("Fabric API", null, null, true),
    QSL_QFAPI("QSL/QFAPI", null, null, true);

    /** 是否为 meta 型加载器（直接生成版本 JSON，无需运行官方 installer）。 */
    fun isMeta(): Boolean = metaBase != null

    /** 安装是否需要独立 Java 运行时（用于运行官方 installer 子进程）。 */
    fun requiresJava(): Boolean = this == FORGE || this == NEOFORGE

    override fun toString(): String = displayName
}
