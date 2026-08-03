// 惰性获取系统 interconnect，避免在 Node 测试环境中解析 @system.interconnect（该模块仅存在于 Vela 运行时）
let systemInterconnectPromise = null
function getSystemInterconnect() {
  if (!systemInterconnectPromise) {
    systemInterconnectPromise = import('@system.interconnect').then((m) => m.default)
  }
  return systemInterconnectPromise
}

export function createApi(interconnectImpl) {
  const ic = interconnectImpl || null
  // 注入实现（单测）时同步建立连接；默认实现惰性加载
  let conn = ic ? ic.instance() : null
  let connected = false
  let messageHandler = null
  let connPromise = null

  function ensureConn() {
    if (conn) return Promise.resolve(conn)
    if (!connPromise) {
      connPromise = getSystemInterconnect().then((impl) => {
        conn = impl.instance()
        // 未注入实现时，init 可能已在连接就绪前被调用，这里补注册
        return conn
      })
    }
    return connPromise
  }

  function registerOn(c, handlers) {
    messageHandler = handlers.onMessage || null
    // 通过调用系统的 onmessage/onopen/onclose/onerror 方法注册回调
    c.onmessage((res) => {
      try {
        const msg = JSON.parse(res.data)
        if (messageHandler) messageHandler(msg)
      } catch (e) {
        console.error('onmessage parse error', e)
      }
    })
    c.onopen((data) => {
      connected = true
      if (handlers.onOpen) handlers.onOpen(data)
    })
    c.onclose((data) => {
      connected = false
      if (handlers.onClose) handlers.onClose(data)
    })
    c.onerror((data) => {
      connected = false
      if (handlers.onError) handlers.onError(data)
    })
  }

  function init(handlers) {
    if (conn) {
      // 注入场景：同步注册，确保事件处理器立即可用
      registerOn(conn, handlers)
    } else {
      // 默认场景：异步惰性建立连接后注册
      ensureConn().then((c) => registerOn(c, handlers)).catch((e) => console.error('init connect error', e))
    }
  }

  function send(payload) {
    return new Promise((resolve, reject) => {
      ensureConn().then((c) => {
        c.send({
          data: JSON.stringify(payload),
          success: () => resolve(),
          fail: (data, code) => reject({ data, code })
        })
      }).catch(reject)
    })
  }

  function connectStatus() {
    return new Promise((resolve, reject) => {
      ensureConn().then((c) => {
        c.diagnosis({
          success: (data) => resolve(data.status === 0),
          fail: (data, code) => reject({ data, code })
        })
      }).catch(reject)
    })
  }

  function isConnected() {
    return connected
  }

  return { init, send, connectStatus, isConnected }
}

const api = createApi()
export default api