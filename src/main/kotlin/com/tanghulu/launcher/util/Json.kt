package com.tanghulu.launcher.util

/**
 * 轻量 JSON 解析器，零外部依赖。
 *
 * [parse] 返回的对象结构：
 * - JSON 对象 -> `Map<String, Any?>`
 * - JSON 数组 -> `List<Any?>`
 * - JSON 字符串 -> `String`
 * - JSON 数字 -> `Double`
 * - JSON 布尔 -> `Boolean`
 * - JSON null -> `null`
 */
object Json {

    class JsonException(msg: String) : RuntimeException(msg)

    @JvmStatic
    fun parse(text: String?): Any? {
        if (text == null) throw JsonException("Null input")
        return Parser(text).parseDocument()
    }

    // ============ 便捷访问 ============

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun asObject(v: Any?): Map<String, Any?>? =
        if (v is Map<*, *>) v as Map<String, Any?> else null

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun asArray(v: Any?): List<Any?>? = v as? List<*>

    @JvmStatic
    fun asString(v: Any?): String? = v as? String

    @JvmStatic
    fun asNumber(v: Any?): Double? = (v as? Number)?.toDouble()

    @JvmStatic
    fun asBoolean(v: Any?): Boolean? = v as? Boolean

    @JvmStatic
    fun opt(obj: Map<String, Any?>?, key: String): Any? = obj?.get(key)

    @JvmStatic
    fun optString(obj: Map<String, Any?>?, key: String, def: String?): String? {
        val v = opt(obj, key)
        return v as? String ?: def
    }

    @JvmStatic
    fun optString(obj: Map<String, Any?>?, key: String): String? = optString(obj, key, null)

    @JvmStatic
    fun optBool(obj: Map<String, Any?>?, key: String, def: Boolean): Boolean {
        val v = opt(obj, key)
        return v as? Boolean ?: def
    }

    @JvmStatic
    fun optInt(obj: Map<String, Any?>?, key: String, def: Int): Int {
        val v = opt(obj, key)
        return (v as? Number)?.toInt() ?: def
    }

    @JvmStatic
    fun optLong(obj: Map<String, Any?>?, key: String, def: Long): Long {
        val v = opt(obj, key)
        return (v as? Number)?.toLong() ?: def
    }

    // ============ 序列化 ============

    /** 把 Map/List/String/Number/Boolean/null 序列化为 JSON 文本（紧凑格式）。 */
    @JvmStatic
    fun stringify(v: Any?): String {
        val sb = StringBuilder()
        write(sb, v)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, v: Any?) {
        when {
            v == null -> sb.append("null")
            v is String -> writeString(sb, v)
            v is Boolean -> sb.append(v)
            v is Number -> writeNumber(sb, v)
            v is Map<*, *> -> writeObject(sb, v)
            v is List<*> -> writeArray(sb, v)
            else -> writeString(sb, v.toString())
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
    }

    private fun writeNumber(sb: StringBuilder, n: Number) {
        if (n is Double || n is Float) {
            val d = n.toDouble()
            if (!d.isFinite()) {
                // NaN / Infinity 不是合法 JSON，输出 null 以保证结果可被解析
                sb.append("null")
                return
            }
            // 整数值输出为整数形式（1.0 -> 1），避免污染 JSON 语义
            if (d == Math.floor(d) && Math.abs(d) < 1e15) {
                sb.append(d.toLong())
                return
            }
        }
        sb.append(n)
    }

    private fun writeObject(sb: StringBuilder, map: Map<*, *>) {
        sb.append('{')
        var first = true
        for ((k, value) in map) {
            if (!first) sb.append(',')
            first = false
            writeString(sb, k.toString())
            sb.append(':')
            write(sb, value)
        }
        sb.append('}')
    }

    private fun writeArray(sb: StringBuilder, list: List<*>) {
        sb.append('[')
        var first = true
        for (o in list) {
            if (!first) sb.append(',')
            first = false
            write(sb, o)
        }
        sb.append(']')
    }

    // ============ 解析器 ============

    private class Parser(private val s: String) {
        private var i = 0

        fun parseDocument(): Any? {
            skipWs()
            val v = parseValue()
            skipWs()
            if (i < s.length) {
                throw JsonException("Unexpected trailing content at index $i")
            }
            return v
        }

        private fun skipWs() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++ else break
            }
        }

        private fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) throw JsonException("Unexpected end of JSON")
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { expect("true"); true }
                'f' -> { expect("false"); false }
                'n' -> { expect("null"); null }
                else -> {
                    if (c == '-' || (c in '0'..'9')) {
                        parseNumber()
                    } else {
                        throw JsonException("Unexpected character '$c' at index $i")
                    }
                }
            }
        }

        private fun expect(word: String) {
            if (!s.startsWith(word, i)) throw JsonException("Invalid token at index $i")
            i += word.length
        }

        private fun parseObject(): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            i++ // consume '{'
            skipWs()
            if (i < s.length && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') {
                    throw JsonException("Expected string key at index $i")
                }
                val key = parseString()
                skipWs()
                if (i >= s.length || s[i] != ':') {
                    throw JsonException("Expected ':' at index $i")
                }
                i++
                map[key] = parseValue()
                skipWs()
                if (i >= s.length) throw JsonException("Unexpected end of JSON object")
                when (val c = s[i]) {
                    ',' -> { i++; continue }
                    '}' -> { i++; return map }
                    else -> throw JsonException("Expected ',' or '}' at index $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            val list = ArrayList<Any?>()
            i++ // consume '['
            skipWs()
            if (i < s.length && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                list.add(parseValue())
                skipWs()
                if (i >= s.length) throw JsonException("Unexpected end of JSON array")
                when (val c = s[i]) {
                    ',' -> { i++; continue }
                    ']' -> { i++; return list }
                    else -> throw JsonException("Expected ',' or ']' at index $i")
                }
            }
        }

        private fun parseString(): String {
            if (i >= s.length || s[i] != '"') {
                throw JsonException("Expected string at index $i")
            }
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (i >= s.length) throw JsonException("Unterminated escape sequence")
                    when (val e = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) throw JsonException("Invalid unicode escape")
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> throw JsonException("Invalid escape '\\$e'")
                    }
                } else if (c < ' ') {
                    throw JsonException("Unescaped control character in string")
                } else {
                    sb.append(c)
                }
            }
            throw JsonException("Unterminated string")
        }

        private fun parseNumber(): Double {
            val start = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && s[i].isDigit()) i++
            if (i < s.length && s[i] == '.') {
                i++
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                while (i < s.length && s[i].isDigit()) i++
            }
            val num = s.substring(start, i)
            if (num.isEmpty() || num == "-") throw JsonException("Invalid number")
            return try {
                num.toDouble()
            } catch (e: NumberFormatException) {
                throw JsonException("Invalid number '$num'")
            }
        }
    }
}
