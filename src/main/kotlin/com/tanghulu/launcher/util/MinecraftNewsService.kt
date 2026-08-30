package com.tanghulu.launcher.util

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * Minecraft official news fetch service.
 * Data source is the Mojang launcher news endpoint (launchercontent.mojang.com).
 */
object MinecraftNewsService {

    /** A single news item. */
    class NewsItem(
        @JvmField val title: String?,
        /** Publication date in yyyy-MM-dd format. */
        @JvmField val date: String?,
        /** Category tag such as "Java Edition"; may be empty. */
        @JvmField val tag: String?,
        /** Summary text; may be empty. */
        @JvmField val text: String?,
        @JvmField val link: String?
    )

    private const val NEWS_URL = "https://launchercontent.mojang.com/v2/news.json"
    private const val FALLBACK_LINK = "https://www.minecraft.net/zh-hans/news"

    private val POOL: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "mc-news-fetcher").apply { isDaemon = true }
    }

    /**
     * Fetch official news asynchronously (newest first).
     * Callbacks run on a background thread; the UI layer must switch back to the JavaFX thread.
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

    /** Strip HTML tags, collapse whitespace and truncate the summary. */
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
