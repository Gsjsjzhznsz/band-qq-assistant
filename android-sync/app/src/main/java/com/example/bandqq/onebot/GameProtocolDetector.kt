package com.example.bandqq.onebot

import com.example.bandqq.config.EndpointConfig
import com.example.bandqq.config.ProtocolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * 通用协议端探测：
 *  - 扫描 loopback + 局域网子网中配置的默认端口
 *  - 按协议类型生成候选配置（NapCat: HTTP 3000/6099/...; SnowLuma: WS 3001/5099）
 *  - 判定条件：HTTP `GET /api/get_version` 返回 JSON（NapCat 与 SnowLuma 均实现 OB11 协议）；
 *    SnowLuma 额外尝试 WebSocket 握手（避免部分部署无 HTTP 端点时漏检）。
 */
object GameProtocolDetector {

    private val client = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    fun defaultPorts(type: ProtocolType): IntArray = when (type) {
        ProtocolType.NAPCAT -> intArrayOf(3000, 6099, 3001, 5700, 8080, 3002)
        ProtocolType.SNOWLUMA -> intArrayOf(3001, 5099)
    }

    suspend fun detect(
        type: ProtocolType,
        preferred: String? = null,
        hosts: List<String> = detectHosts(),
        ports: IntArray = defaultPorts(type)
    ): EndpointConfig? {
        val preferredUrl = preferred?.trim()?.ifBlank { null }
        val candidates = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        if (preferredUrl != null && seen.add(preferredUrl)) candidates.add(preferredUrl)

        for (host in hosts) {
            for (port in ports) {
                val url = if (host.startsWith("http")) host else "http://$host:$port"
                if (seen.add(url)) candidates.add(url)
            }
        }

        return coroutineScope {
            val sem = Semaphore(24)
            val results = candidates.map { url ->
                async(Dispatchers.IO) {
                    sem.withPermit { if (isProtocol(type, url)) url else null }
                }
            }.awaitAll().filterNotNull()

            if (preferredUrl != null && results.contains(preferredUrl)) {
                toConfig(type, preferredUrl)
            } else {
                results.firstOrNull()?.let { toConfig(type, it) }
            }
        }
    }

    private fun isProtocol(type: ProtocolType, base: String): Boolean {
        if (base.startsWith("ws://") || base.startsWith("wss://")) {
            return probeWs(base)
        }
        if (httpProbe(base)) return true
        // SnowLuma 无 HTTP 端点时,尝试把 http 换成 ws 握手
        if (type == ProtocolType.SNOWLUMA) {
            val wsUrl = base.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
            if (wsUrl != base && probeWs(wsUrl)) return true
        }
        return false
    }

    private fun httpProbe(httpBase: String): Boolean = try {
        val resp = client.newCall(
            Request.Builder().url(httpBase.trimEnd('/') + "/api/get_version").get().build()
        ).execute()
        resp.use { r ->
            if (!r.isSuccessful) return@use false
            val body = r.body?.string().orEmpty()
            body.isNotBlank() && body.trimStart().startsWith("{")
        }
    } catch (e: IOException) {
        false
    } catch (e: Exception) {
        false
    }

    /** WebSocket 握手探测：onOpen 触发即判成功（连接建立后关闭）。 */
    private fun probeWs(wsUrl: String): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        val result = BooleanArray(1) { false }
        var wsRef: WebSocket? = null
        try {
            val ws: WebSocket = wsClient.newWebSocket(
                Request.Builder().url(wsUrl).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        result[0] = true
                        latch.countDown()
                        webSocket.close(1000, "probe done")
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {}
                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        latch.countDown()
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        latch.countDown()
                    }
                }
            )
            wsRef = ws
        } catch (e: Exception) {
            return false
        }
        latch.await(1, TimeUnit.SECONDS)
        wsRef?.cancel()
        return result[0]
    }

    private fun toConfig(type: ProtocolType, base: String): EndpointConfig {
        if (base.startsWith("ws://") || base.startsWith("wss://")) {
            return EndpointConfig(wsUrl = base, httpUrl = base, token = "")
        }
        val trimmed = base.trim()
        return try {
            val isHttps = trimmed.lowercase().startsWith("https://")
            val scheme = if (isHttps) "wss" else "ws"
            val m = Regex("""^https?://([^:/]+)(?::(\d+))?""").find(trimmed)
            if (m == null) return EndpointConfig(wsUrl = trimmed, httpUrl = base, token = "")
            val host = m.groupValues[1]
            val httpPort = m.groupValues[2].ifBlank { if (isHttps) "443" else "80" }.toInt()
            val wsPort = when {
                type == ProtocolType.NAPCAT && httpPort == 3000 -> 3001
                else -> httpPort
            }
            EndpointConfig(wsUrl = "$scheme://$host:$wsPort", httpUrl = base, token = "")
        } catch (e: Exception) {
            EndpointConfig(wsUrl = trimmed, httpUrl = base, token = "")
        }
    }

    /** 探测目标主机：loopback + 本机所在子网。取不到网络信息时仅 loopback。 */
    fun detectHosts(): List<String> {
        val hosts = mutableListOf("127.0.0.1")
        return try {
            val nics = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nic in nics) {
                val addrs = java.util.Collections.list(nic.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val prefix = ip.substringBeforeLast('.')
                        hosts.addAll((1..254).map { "$prefix.$it" })
                    }
                }
            }
            hosts
        } catch (e: Exception) {
            hosts
        }
    }
}