package com.example.bandqq.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigTest {

    @Test
    fun `默认配置为 NapCat 活动端,指向 loopback`() {
        val cfg = AppConfig()
        assertEquals(ProtocolType.NAPCAT, cfg.activeType)
        assertEquals("ws://127.0.0.1:3001", cfg.getActiveEndpoint().wsUrl)
        assertEquals("http://127.0.0.1:3000", cfg.getActiveEndpoint().httpUrl)
    }

    @Test
    fun `getActiveEndpoint 按 activeType 返回对应端点`() {
        val cfg = AppConfig(
            napcat = EndpointConfig("ws://127.0.0.1:3001", "http://127.0.0.1:3000", "t1"),
            snowluma = EndpointConfig("ws://192.168.1.5:3001", "http://192.168.1.5:3001", "t2"),
            activeType = ProtocolType.SNOWLUMA
        )
        assertEquals("ws://192.168.1.5:3001", cfg.getActiveEndpoint().wsUrl)
        assertEquals("t2", cfg.getActiveEndpoint().token)
    }

    @Test
    fun `SnowLuma 默认端点指向 loopback 3001`() {
        val snow = AppConfig().snowluma
        assertEquals("ws://127.0.0.1:3001", snow.wsUrl)
        assertEquals("http://127.0.0.1:3001", snow.httpUrl)
    }
}