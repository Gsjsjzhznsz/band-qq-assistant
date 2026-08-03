package com.example.bandqq.onebot

import com.example.bandqq.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 自动探测本机 NapCat。
 *
 * NapCat 与同步器跑在同一台手机的 loopback（127.0.0.1）上，端口可能被用户改过，
 * 也在 3000/6099 等默认端口。这里并发扫描多个候选 HTTP 地址，并向 OneBot 标准端点
 * `GET /api/get_version` 发送探测请求，只有返回 JSON（可解析出存在性）才判定为 NapCat，
 * 避免把别的 HTTP 服务误认成 NapCat。
 */
object NapCatDetector {

    private val client = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    /** 常见 NapCat 端口：HTTP OneBot、WebSocket、WebUI、以及其他常见端口 */
    private val candidatePorts = intArrayOf(3000, 6099, 3001, 5700, 8080, 3002)

    /**
     * 先探测用户已填的 [preferred]，失败则扫描候选端口。
     * 返回探测成功的最优 [AppConfig]；找不到返回 null。
     */
    suspend fun detect(preferred: String? = null, ports: IntArray = candidatePorts.copyOf()): AppConfig? {
        val preferredUrl = preferred?.trim()?.ifBlank { null }
        val candidates = mutableListOf<String>()
        if (preferredUrl != null) candidates.add(preferredUrl)

        // 用 wy key 去重，保留用户填的优先
        val seen = mutableSetOf<String>()
        if (preferredUrl != null) seen.add(preferredUrl)

        for (port in ports) {
            val url = "http://127.0.0.1:$port"
            if (seen.add(url)) candidates.add(url)
        }

        return coroutineScope {
            // 并发探测所有候选（含用户填的）
            val results = candidates.map { url ->
                async(Dispatchers.IO) { if (isNapCat(url)) url else null }
            }.awaitAll().filterNotNull()

            // 用户填的优先返回
            if (preferredUrl != null && results.contains(preferredUrl)) {
                toConfig(preferredUrl)
            } else if (results.isNotEmpty()) {
                toConfig(results.first())
            } else {
                null
            }
        }
    }

    private fun isNapCat(httpBase: String): Boolean = try {
        val resp = client.newCall(
            Request.Builder()
                .url(httpBase.trimEnd('/') + "/api/get_version")
                .get()
                .build()
        ).execute()
        resp.use { r ->
            if (!r.isSuccessful) return@use false
            val body = r.body?.string().orEmpty()
            // NapCat 某此版本返回 {"status":"ok","data":...}；只要 JSON 且非空即认为可识别
            body.isNotBlank() && body.trimStart().startsWith("{")
        }
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        false
    }

    private fun toConfig(httpBase: String): AppConfig {
        // 从 httpBase 推导 ws 地址：NapCat 默认 https 3000 → ws 3001，其余保持同端口
        val trimmed = httpBase.trim()
        return try {
            val isHttps = trimmed.lowercase().startsWith("https://")
            val scheme = if (isHttps) "wss" else "ws"
            val m = Regex("""^https?://([^:/]+)(?::(\d+))?""").find(trimmed)
            if (m == null) return AppConfig(httpUrl = httpBase)
            val host = m.groupValues[1]
            val httpPort = m.groupValues[2].ifBlank {
                if (isHttps) "443" else "80"
            }.toInt()
            val wsPort = if (httpPort == 3000) 3001 else httpPort
            AppConfig(wsUrl = "$scheme://$host:$wsPort", httpUrl = httpBase)
        } catch (e: Exception) {
            AppConfig(httpUrl = httpBase)
        }
    }
}