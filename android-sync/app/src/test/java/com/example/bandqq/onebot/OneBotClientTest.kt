package com.example.bandqq.onebot

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OneBotClientTest {

    private lateinit var server: MockWebServer
    private val parser = OneBotParser()

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `sendMessage 发送 HTTP 请求`() = runBlocking {
        val url = server.url("/").toString()
        server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
        val client = OneBotClient(parser)
        val latch = CountDownLatch(1)
        var ok = false
        client.sendMessage("group", "123", "收到", url) { ok = it; latch.countDown() }
        latch.await(3, TimeUnit.SECONDS)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("send_group_msg"))
        assertTrue(ok)
    }
}
