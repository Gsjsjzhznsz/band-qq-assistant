import { degradeContent } from './protocol.js'

const CONV_KEY = 'conv_list'
const MSG_PREFIX = 'msg_'
const QUICK_KEY = 'quick_replies'
const MAX_CONVERSATIONS = 50
const MAX_MESSAGES = 100

let systemStoragePromise = null

function getSystemStorage() {
  // 惰性加载系统 storage，避免在 Node 测试环境中解析 @system.storage（该模块仅存在于 Vela 运行时）
  if (!systemStoragePromise) {
    systemStoragePromise = import('@system.storage').then((m) => m.default)
  }
  return systemStoragePromise
}

export function createStore(storageImpl) {
  const st = storageImpl || null
  let conversations = []
  let quickReplies = []

  async function pGet(key, def) {
    const impl = st || await getSystemStorage()
    return new Promise((resolve) => {
      impl.get({ key: key, default: def, success: (v) => resolve(v), fail: () => resolve(def) })
    })
  }
  async function pSet(key, value) {
    const impl = st || await getSystemStorage()
    return new Promise((resolve) => {
      impl.set({ key: key, value: value, success: () => resolve(), fail: () => resolve() })
    })
  }

  return {
    async init() {
      const convRaw = await pGet(CONV_KEY, '[]')
      const quickRaw = await pGet(QUICK_KEY, '')
      try { conversations = JSON.parse(convRaw) } catch (e) { conversations = [] }
      if (quickRaw) { try { quickReplies = JSON.parse(quickRaw) } catch (e) {} }
    },
    async getConversations() {
      return conversations
    },
    async getMessages(targetId) {
      const raw = await pGet(MSG_PREFIX + targetId, '[]')
      try { return JSON.parse(raw) } catch (e) { return [] }
    },
    async upsertMessage(msg) {
      const content = typeof msg.content === 'string' ? msg.content : degradeContent(msg.content)
      const key = msg.target_id
      const messages = await this.getMessages(key)
      messages.push({
        message_type: msg.message_type,
        sender_id: msg.sender_id,
        sender_name: msg.sender_name || '',
        content: content,
        time: msg.time || Date.now()
      })
      while (messages.length > MAX_MESSAGES) messages.shift()
      await pSet(MSG_PREFIX + key, JSON.stringify(messages))

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
      await pSet(CONV_KEY, JSON.stringify(conversations))
    },
    async getQuickReplies() {
      return quickReplies
    },
    async addQuickReply(text) {
      if (!text) return
      quickReplies.push(text)
      await pSet(QUICK_KEY, JSON.stringify(quickReplies))
    },
    async removeQuickReply(index) {
      if (index < 0 || index >= quickReplies.length) return
      quickReplies.splice(index, 1)
      await pSet(QUICK_KEY, JSON.stringify(quickReplies))
    }
  }
}

const store = createStore()
export default store
