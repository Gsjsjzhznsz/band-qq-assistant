package com.example.bandqq.onebot

import android.util.Log
import com.example.bandqq.config.EndpointConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import com.example.bandqq.sync.MessageSender
import java.util.concurrent.TimeUnit

interface OneBotListener {
    fun onEvent(message: OneBotMessage)
    fun onState(connected: Boolean)
}

class OneBotClient(private val parser: OneBotParser) : MessageSender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var ws: WebSocket? = null
    private var config: EndpointConfig = EndpointConfig("ws://127.0.0.1:3001", "", "http://127.0.0.1:3000", "")
    private var listener: OneBotListener? = null
    @Volatile private var connected = false

    fun start(config: EndpointConfig, listener: OneBotListener) {
        this.config = config
        this.listener = listener
        reconnect()
    }

    fun startWithListener(listener: OneBotListener) {
        this.listener = listener
    }

    fun stop() {
        reconnectJob?.cancel()
        scope.cancel()
        ws?.close(1000, "stopped")
        ws = null
        connected = false
    }

    fun isConnected(): Boolean = connected

    private fun reconnect() {
        reconnectJob = scope.launch {
            while (isActive) {
                if (!connected) {
                    try {
                        connectOnce()
                    } catch (e: Exception) {
                        Log.w("OneBotClient", "connect failed", e)
                    }
                    delay(5000)
                } else {
                    delay(1000)
                }
            }
        }
    }

    private fun connectOnce() {
        val builder = Request.Builder().url(config.wsUrl)
        if (config.wsToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.wsToken}")
        }
        val req = builder.build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
                listener?.onState(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = parser.parseMessageEvent(text)
                if (msg != null) listener?.onEvent(msg)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                listener?.onState(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                listener?.onState(false)
            }
        })
    }

    override fun sendMessage(
        messageType: String,
        targetId: String,
        content: String,
        httpUrlOverride: String?,
        callback: (Boolean) -> Unit
    ) {
        val baseUrl = httpUrlOverride ?: config.httpUrl
        val body = parser.buildSendRequest(messageType, targetId, content)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/send_msg")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { if (config.httpToken.isNotBlank()) header("Authorization", "Bearer ${config.httpToken}") }
            .build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("OneBotClient", "send failed", e)
                callback(false)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.use {
                    callback(it.isSuccessful)
                }
            }
        })
    }

    /**
     * 调用 OneBot HTTP 通用接口（如 get_friend_list/get_group_list）。
     * 路径优先 {httpUrl}/api/{action}，失败时回退 {httpUrl}/{action}。
     * 成功回传原始响应体，失败回传 null。
     */
    fun requestApi(action: String, baseUrl: String = config.httpUrl, callback: (String?) -> Unit) {
        fun doRequest(url: String, onFail: () -> Unit) {
            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .apply { if (config.httpToken.isNotBlank()) header("Authorization", "Bearer ${config.httpToken}") }
                .build()
            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    onFail()
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    response.use {
                        val body = it.body?.string() ?: ""
                        if (it.isSuccessful) callback(body) else onFail()
                    }
                }
            })
        }
        val root = baseUrl.trimEnd('/')
        doRequest("$root/api/$action") {
            doRequest("$root/$action") {
                callback(null)
            }
        }
    }
}
