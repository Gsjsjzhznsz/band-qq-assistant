package com.example.bandqq.onebot

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

class NapCatDetectorTest {

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
    fun `detect 命中标准 NapCat 端点并回填配置`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"status":"ok","data":{"msg":"NapCat Test"}}""")
        )

        val cfg = NapCatDetector.detect(preferred = server.url("/").toString().trimEnd('/'))

        assertNotNull("应探测到 NapCat", cfg)
        cfg!!
        assertTrue(cfg.httpUrl.startsWith("http://127.0.0.1:"))
        assertTrue(cfg.wsUrl.startsWith("ws://127.0.0.1:"))
    }

    @Test
    fun `detect 对非 NapCat 响应返回 null`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>404</html>"))

        val cfg = NapCatDetector.detect(
            preferred = server.url("/").toString().trimEnd('/'),
            ports = intArrayOf()
        )

        assertNull("不应误认非 JSON 服务", cfg)
    }

    @Test
    fun `非 3000 端口保持 http ws 同端口`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"status":"ok","data":{"msg":"ok"}}""")
        )

        val httpPort = server.port
        val cfg = NapCatDetector.detect(
            preferred = "http://127.0.0.1:$httpPort",
            ports = intArrayOf()
        )

        assertNotNull(cfg)
        assertEquals("http://127.0.0.1:$httpPort", cfg!!.httpUrl)
        assertEquals("ws://127.0.0.1:$httpPort", cfg.wsUrl)
    }
}