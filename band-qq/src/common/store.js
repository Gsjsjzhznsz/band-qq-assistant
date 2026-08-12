import protocol from './protocol.js'
const { degradeContent, stripEmoji } = protocol

const MAX_CONVERSATIONS = 50
const MAX_MESSAGES = 100
const CACHE_CONVERSATIONS = 10
const CACHE_MESSAGES = 30
const CONV_KEY = 'conv_cache'
const MSG_PREFIX = 'msg_cache_'
const VISIBLE_KEY = 'visible_contacts'

function createStorageAdapter(storageImpl) {
  const st = storageImpl || null
  return {
    get(key, def) {
      return new Promise((resolve) => {
        if (st) { st.get({ key: key, default: def, success: (v) => resolve(v), fail: () => resolve(def) }); return }
        resolveSystemStorage().then((sys) => {
          sys.get({ key: key, default: def, success: (v) => resolve(v), fail: () => resolve(def) })
        }).catch(() => resolve(def))
      })
    },
    set(key, value) {
      return new Promise((resolve) => {
        if (st) { st.set({ key: key, value: value, success: () => resolve(), fail: () => resolve() }); return }
        resolveSystemStorage().then((sys) => {
          sys.set({ key: key, value: value, success: () => resolve(), fail: () => resolve() })
        }).catch(() => resolve())
      })
    }
  }
}

let systemStorage = null
function resolveSystemStorage() {
  if (!systemStorage) {
    try {
      systemStorage = require('@system.storage')
    } catch (e) {
      systemStorage = null
    }
  }
  return Promise.resolve(systemStorage)
}

export function createStore(storageImpl) {
  const cache = createStorageAdapter(storageImpl)
  let conversations = []
  let visibleContacts = []
  let connectState = null
  const messagesByTarget = {}
  let initPromise = null

  return {
    lastTarget: null,
    setLastTarget(t) {
      this.lastTarget = t || null
    },
    getLastTarget() {
      return this.lastTarget
    },
    sendStatus: { text: '', ts: 0 },
    setSendStatus(text) {
      this.sendStatus = { text, ts: Date.now() }
    },
    getSendStatus() {
      return this.sendStatus
    },
    async init() {
      if (initPromise) return initPromise
      initPromise = (async () => {
        const convRaw = await cache.get(CONV_KEY, '[]')
        try {
          conversations = JSON.parse(convRaw).filter((c) => c && c.id !== '' && c.id !== null && c.id !== undefined && c.id !== 'undefined')
        } catch (e) {
          conversations = []
        }
        const visibleRaw = await cache.get(VISIBLE_KEY, '[]')
        try {
          visibleContacts = JSON.parse(visibleRaw).filter((c) => c && c.id !== '' && c.id !== null && c.id !== undefined && c.id !== 'undefined')
        } catch (e) {
          visibleContacts = []
        }
      })()
      return initPromise
    },
    // 确保任何读取前缓存行初始化完成，避免冷启动时拿到空列表并被清空
    async ensureInit() {
      await this.init()
    },
    async setConversations(list) {
      await this.ensureInit()
      let incoming = Array.isArray(list) ? list.map((c) => Object.assign({}, c, { name: stripEmoji(c.name || '') })) : []
      incoming = incoming.filter((c) => c.id !== '' && c.id !== null && c.id !== undefined && c.id !== 'undefined')
      let merged = incoming.slice()
      const byId = new Map(merged.map((c) => [c.id, c]))
      for (const vc of visibleContacts) {
        if (vc.id && vc.id !== '' && !byId.has(vc.id)) {
          merged.push({ id: vc.id, type: vc.type, name: vc.name, last_msg: '', time: 0, is_temporary: false })
        }
      }
      conversations = merged
      conversations.sort((a, b) => (b.time || 0) - (a.time || 0))
      const slice = conversations.slice(0, CACHE_CONVERSATIONS)
      await cache.set(CONV_KEY, JSON.stringify(slice))
    },
    async getConversations() {
      await this.ensureInit()
      const hasUnmatched = visibleContacts.some((vc) => vc.id && vc.id !== '' && !conversations.some((c) => c.id === vc.id))
      let result = hasUnmatched
        ? conversations.concat(
            visibleContacts
              .filter((vc) => vc.id && vc.id !== '' && !conversations.some((c) => c.id === vc.id))
              .map((vc) => ({ id: vc.id, type: vc.type, name: vc.name, last_msg: '', time: 0, is_temporary: false }))
          )
        : conversations.slice()
      // 按最新消息时间降序排序，最新会话置顶
      result.sort((a, b) => (b.time || 0) - (a.time || 0))
      return result
    },
    async getMessages(targetId) {
      await this.ensureInit()
      const msgs = messagesByTarget[targetId]
      if (msgs) {
        // 读取时也按 time 升序兜底，兼容早期缓存里未排序的数据
        if (msgs.length > 1) msgs.sort((a, b) => (a.time || 0) - (b.time || 0))
        return msgs
      }
      const raw = await cache.get(MSG_PREFIX + targetId, '[]')
      try {
        messagesByTarget[targetId] = JSON.parse(raw)
        if (messagesByTarget[targetId].length > 1) {
          messagesByTarget[targetId].sort((a, b) => (a.time || 0) - (b.time || 0))
        }
      } catch (e) {
        messagesByTarget[targetId] = []
      }
      return messagesByTarget[targetId]
    },
    async setMessages(targetId, list) {
      await this.ensureInit()
      // 合并而非覆盖：history_list 可能晚于 push_message 到达，
      // 直接用历史覆盖会丢失刚到的新消息。按 time+content 去重合并，保留全部。
      const existing = messagesByTarget[targetId] || []
      const seen = {}
      const merged = []
      for (const m of existing) {
        const k = (m && m.time) + '|' + (m && m.content !== undefined ? m.content : '')
        if (!seen[k]) { seen[k] = true; merged.push(m) }
      }
      if (Array.isArray(list)) {
        for (const m of list) {
          const k = (m && m.time) + '|' + (m && m.content !== undefined ? m.content : '')
          if (!seen[k]) { seen[k] = true; merged.push(m) }
        }
      }
      merged.sort((a, b) => (a.time || 0) - (b.time || 0))
      const sliced = merged.slice(-MAX_MESSAGES)
      messagesByTarget[targetId] = sliced
      await cache.set(MSG_PREFIX + targetId, JSON.stringify(sliced.slice(-CACHE_MESSAGES)))
    },
    async upsertMessage(msg) {
      await this.ensureInit()
      const content = typeof msg.content === 'string' ? msg.content : degradeContent(msg.content)
      const senderName = stripEmoji(msg.sender_name || '')
      const targetName = stripEmoji(msg.target_name || '')
      const key = msg.target_id
      if (!key || key === '' || key === 'undefined') return
      const isTemp = !(msg.visible !== false && this.isVisible(msg.target_id))
      const messages = messagesByTarget[key] || []
      // push 重复时去重（手机端可能因监听器叠加重复推送同一消息）
      const dk = (msg.time || Date.now()) + '|' + content
      if (!messages.some((m) => (m.time || '') + '|' + m.content === dk)) {
        messages.push({
          message_type: msg.message_type,
          sender_id: msg.sender_id,
          sender_name: senderName,
          content: content,
          is_self: msg.is_self === true,
          time: msg.time || Date.now()
        })
        // 按时间升序排列，保证消息顺序不乱（秒/毫秒混用也统一比较）
        messages.sort((a, b) => (a.time || 0) - (b.time || 0))
        while (messages.length > MAX_MESSAGES) messages.shift()
        messagesByTarget[key] = messages
        await cache.set(MSG_PREFIX + key, JSON.stringify(messages.slice(-CACHE_MESSAGES)))
      }

      const idx = conversations.findIndex((c) => c.id === key)
      const conv = {
        id: key,
        type: msg.message_type,
        name: targetName || (msg.is_self && !targetName ? key : senderName) || key,
        last_msg: content,
        time: msg.time || Date.now(),
        is_temporary: isTemp
      }
      if (idx >= 0) conversations.splice(idx, 1)
      conversations.unshift(conv)
      while (conversations.length > MAX_CONVERSATIONS) conversations.pop()
      await cache.set(CONV_KEY, JSON.stringify(conversations.slice(0, CACHE_CONVERSATIONS)))
    },
    async setVisibleContacts(list) {
      await this.ensureInit()
      let incoming = Array.isArray(list)
        ? list.map((c) => Object.assign({}, c, { name: stripEmoji(c.name || '') }))
        : []
      incoming = incoming.filter((c) => c.id !== '' && c.id !== null && c.id !== undefined && c.id !== 'undefined')
      // 同步到空列表时不覆盖已存在的联系人骨架，避免未连接/时序问题导致本地联系人被清空
      if (incoming.length === 0 && visibleContacts.length > 0) return
      visibleContacts = incoming
      const visibleIds = new Set(visibleContacts.map((c) => c.id))
      const remaining = conversations.filter((c) => !c.is_temporary)
      conversations = remaining.filter((c) => visibleIds.has(c.id))
      for (const c of visibleContacts) {
        if (!conversations.some((x) => x.id === c.id)) {
          conversations.push({ id: c.id, type: c.type, name: c.name, last_msg: '', time: 0, is_temporary: false })
        }
      }
      const msgKeys = Object.keys(messagesByTarget)
      msgKeys.forEach((k) => {
        if (!visibleIds.has(k)) {
          delete messagesByTarget[k]
          cache.set(MSG_PREFIX + k, JSON.stringify([]))
        }
      })
      await cache.set(VISIBLE_KEY, JSON.stringify(visibleContacts))
      await cache.set(CONV_KEY, JSON.stringify(conversations.slice(0, CACHE_CONVERSATIONS)))
    },
    async getVisibleContacts() {
      await this.ensureInit()
      return visibleContacts
    },
    setConnectState(state) {
      connectState = state || null
    },
    getConnectState() {
      return connectState
    },
    isVisible(id) {
      return visibleContacts.some((c) => c.id === id)
    },
    async clearAllMessages() {
      const keys = Object.keys(messagesByTarget)
      keys.forEach((k) => { delete messagesByTarget[k] })
      conversations = []
      await cache.set(CONV_KEY, JSON.stringify([]))
      for (const k of keys) await cache.set(MSG_PREFIX + k, JSON.stringify([]))
    }
  }
}

const store = createStore()
export default store
