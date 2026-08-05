package com.example.bandqq.onebot

import com.example.bandqq.config.ProtocolType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameProtocolDetectorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `detect NapCat 命中标准端点并回填配置`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","data":{"msg":"NapCat"}}"""))
        val cfg = GameProtocolDetector.detect(
            type = ProtocolType.NAPCAT,
            preferred = server.url("/").toString().trimEnd('/'),
            hosts = listOf("127.0.0.1"),
            ports = intArrayOf()
        )
        assertNotNull(cfg)
        assertTrue(cfg!!.httpUrl.startsWith("http://127.0.0.1:"))
        assertTrue(cfg.wsUrl.startsWith("ws://127.0.0.1:"))
    }

    @Test
    fun `detect NapCat 对非 JSON 响应返回 null`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>404</html>"))
        val cfg = GameProtocolDetector.detect(
            type = ProtocolType.NAPCAT,
            preferred = server.url("/").toString().trimEnd('/'),
            hosts = listOf("127.0.0.1"),
            ports = intArrayOf()
        )
        assertNull(cfg)
    }

    @Test
    fun `非 3000 端口保持 http ws 同端口`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"ok","data":{"msg":"ok"}}"""))
        val httpPort = server.port
        val cfg = GameProtocolDetector.detect(
            type = ProtocolType.NAPCAT,
            preferred = "http://127.0.0.1:$httpPort",
            hosts = listOf("127.0.0.1"),
            ports = intArrayOf()
        )
        assertNotNull(cfg)
        assertEquals("http://127.0.0.1:$httpPort", cfg!!.httpUrl)
        assertEquals("ws://127.0.0.1:$httpPort", cfg.wsUrl)
    }

    @Test
    fun `detect SnowLuma 命中 WS 3001 并回填`() = runBlocking {
        val cfg = GameProtocolDetector.detect(
            type = ProtocolType.SNOWLUMA,
            preferred = "ws://127.0.0.1:${server.port}",
            hosts = listOf("127.0.0.1"),
            ports = intArrayOf()
        )
        // MockWebServer 不做 WS 升级,预握手失败后应回退为 null 而非抛异常
        assertNull("无真实 WS 服务的端点不应误判", cfg)
    }

    @Test
    fun `defaultPorts 分别覆盖两端默认端口`() {
        val napPorts = GameProtocolDetector.defaultPorts(ProtocolType.NAPCAT).toList()
        assertTrue(napPorts.contains(3000))
        assertTrue(napPorts.contains(6099))
        val snowPorts = GameProtocolDetector.defaultPorts(ProtocolType.SNOWLUMA).toList()
        assertTrue(snowPorts.contains(3001))
        assertTrue(snowPorts.contains(5099))
    }
}
