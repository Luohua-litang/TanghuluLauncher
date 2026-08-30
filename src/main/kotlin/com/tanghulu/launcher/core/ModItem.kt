package com.tanghulu.launcher.core

/**
 * Mod display model.
 */
class ModItem(
    @JvmField val id: String,
    @JvmField val title: String,
    @JvmField val description: String?,
    @JvmField val iconUrl: String?,
    @JvmField val downloads: Long
) {
    override fun toString(): String = title
}
