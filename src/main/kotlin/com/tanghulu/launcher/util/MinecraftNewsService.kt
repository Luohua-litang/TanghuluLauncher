package com.tanghulu.launcher.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * Minecraft 官方新闻获取服务。
 * 数据源为 Mojang 官方启动器新闻接口（launchercontent.mojang.com）。
 */
object MinecraftNewsService {

    /** 一条新闻。 */
    class NewsItem(
        @JvmField val title: String?,
        /** 发布日期，格式 yyyy-MM-dd。 */
        @JvmField val date: String?,
        /** 分类标签，如 "Java Edition"，可能为空。 */
        @JvmField val tag: String?,
        /** 摘要文本，可能为空。 */
        @JvmField val text: String?,
        @JvmField val link: String?
    )

    private const val NEWS_URL = "https://launchercontent.mojang.com/v2/news.json"
    private const val FALLBACK_LINK = "https://www.minecraft.net/zh-hans/news"

    private val POOL: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mc-news-fetcher").apply { isDaemon = true }
    }

    /**
     * 异步获取官方新闻（按日期倒序）。
     * 回调在后台线程执行，UI 层收到后需自行切回 JavaFX 线程。
     */
    @JvmStatic
    fun fetchNews(limit: Int, onSuccess: Consumer<List<NewsItem>>, onError: Consumer<Exception>) {
        POOL.submit {
            try {
                val json = HttpUtil.getTextOnce(NEWS_URL)
                val items = parse(json, limit)
                if (items.isEmpty()) {
                    throw java.io.IOException("No news entries from $NEWS_URL")
                }
                onSuccess.accept(items)
            } catch (e: Exception) {
                onError.accept(e)
            }
        }
    }

    private fun parse(json: String, limit: Int): List<NewsItem> {
        val items = ArrayList<NewsItem>()
        val root = Json.asObject(Json.parse(json))
        val entries = root?.get("entries")
        if (entries !is List<*>) return items
        for (o in entries) {
            if (items.size >= limit) break
            val m = Json.asObject(o) ?: continue
            val title = Json.asString(m["title"])
            val date = Json.asString(m["date"])
            if (title == null || date == null) continue
            var tag = Json.asString(m["tag"])
            if (tag.isNullOrEmpty()) tag = Json.asString(m["category"])
            var link = Json.asString(m["readMoreLink"])
            if (link.isNullOrEmpty()) link = FALLBACK_LINK
            items.add(NewsItem(title, date, tag, clean(Json.asString(m["text"])), link))
        }
        items.sortByDescending { it.date ?: "" }
        return items
    }

    private val HTML_TAG = Pattern.compile("<[^>]+>")
    private val WHITESPACE = Pattern.compile("\\s+")

    /** 去掉 HTML 标签、压缩空白并截断摘要。 */
    private fun clean(s: String?): String? {
        if (s == null) return null
        val m = HTML_TAG.matcher(s)
        var text = WHITESPACE.matcher(m.replaceAll(" ")).replaceAll(" ").trim()
        if (text.length > 140) {
            text = text.substring(0, 137) + "…"
        }
        return text
    }
}
