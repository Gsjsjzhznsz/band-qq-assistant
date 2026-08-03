import { degradeContent } from './protocol.js'

const MAX_CONVERSATIONS = 50
const MAX_MESSAGES = 100
const CACHE_CONVERSATIONS = 10
const CACHE_MESSAGES = 30
const CONV_KEY = 'conv_cache'
const MSG_PREFIX = 'msg_cache_'
const QUICK_KEY = 'quick_replies'

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

let systemStoragePromise = null
function resolveSystemStorage() {
  if (!systemStoragePromise) {
    systemStoragePromise = import('@system.storage').then((m) => m.default).catch(() => null)
  }
  return systemStoragePromise
}

export function createStore(storageImpl) {
  const cache = createStorageAdapter(storageImpl)
  let conversations = []
  let quickReplies = []
  const messagesByTarget = {}

  return {
    async init() {
      const convRaw = await cache.get(CONV_KEY, '[]')
      try { conversations = JSON.parse(convRaw) } catch (e) { conversations = [] }
      const quickRaw = await cache.get(QUICK_KEY, '[]')
      try { quickReplies = JSON.parse(quickRaw) } catch (e) { quickReplies = [] }
    },
    async setConversations(list) {
      conversations = Array.isArray(list) ? list : []
      const slice = conversations.slice(0, CACHE_CONVERSATIONS)
      await cache.set(CONV_KEY, JSON.stringify(slice))
    },
    async getConversations() {
      return conversations
    },
    async getMessages(targetId) {
      const msgs = messagesByTarget[targetId]
      if (msgs) return msgs
      const raw = await cache.get(MSG_PREFIX + targetId, '[]')
      try {
        messagesByTarget[targetId] = JSON.parse(raw)
      } catch (e) {
        messagesByTarget[targetId] = []
      }
      return messagesByTarget[targetId]
    },
    async setMessages(targetId, list) {
      const sliced = Array.isArray(list) ? list.slice(0, MAX_MESSAGES) : []
      messagesByTarget[targetId] = sliced
      await cache.set(MSG_PREFIX + targetId, JSON.stringify(sliced.slice(0, CACHE_MESSAGES)))
    },
    async upsertMessage(msg) {
      const content = typeof msg.content === 'string' ? msg.content : degradeContent(msg.content)
      const key = msg.target_id
      const messages = messagesByTarget[key] || []
      messages.push({
        message_type: msg.message_type,
        sender_id: msg.sender_id,
        sender_name: msg.sender_name || '',
        content: content,
        time: msg.time || Date.now()
      })
      while (messages.length > MAX_MESSAGES) messages.shift()
      messagesByTarget[key] = messages
      await cache.set(MSG_PREFIX + key, JSON.stringify(messages.slice(0, CACHE_MESSAGES)))

      const idx = conversations.findIndex((c) => c.id === key)
      const conv = {
        id: key,
        type: msg.message_type,
        name: msg.sender_name || key,
        last_msg: content,
        time: msg.time || Date.now()
      }
      if (idx >= 0) conversations.splice(idx, 1)
      conversations.unshift(conv)
      while (conversations.length > MAX_CONVERSATIONS) conversations.pop()
      await cache.set(CONV_KEY, JSON.stringify(conversations.slice(0, CACHE_CONVERSATIONS)))
    },
    getDefaultQuickReplies() {
      return ['好的', '收到', '稍等', '马上到', '嗯嗯', '哈哈哈']
    },
    async getQuickReplies() {
      return quickReplies
    },
    async addQuickReply(text) {
      if (!text) return
      quickReplies.push(text)
      await cache.set(QUICK_KEY, JSON.stringify(quickReplies))
    },
    async removeQuickReply(index) {
      if (index < 0 || index >= quickReplies.length) return
      quickReplies.splice(index, 1)
      await cache.set(QUICK_KEY, JSON.stringify(quickReplies))
    }
  }
}

const store = createStore()
export default store
