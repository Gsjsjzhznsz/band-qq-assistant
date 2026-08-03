import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { createStore } from '../common/store.js'

function mockStorage(initial) {
  const map = new Map(Object.entries(initial))
  return {
    get: (o) => { o.success(map.get(o.key) ?? '') },
    set: (o) => { map.set(o.key, o.value); o.success({}) }
  }
}

let store
beforeEach(async () => {
  store = createStore(mockStorage({}))
  await store.init()
})

describe('store', () => {
  it('初始化空列表', async () => {
    assert.deepEqual(await store.getConversations(), [])
    assert.deepEqual(await store.getMessages('100'), [])
  })

  it('写入消息后更新会话', async () => {
    const msg = { type: 'push_message', message_type: 'group', target_id: '100', sender_id: '1', sender_name: 'A', content: 'hi', time: 1700000000 }
    await store.upsertMessage(msg)
    const convs = await store.getConversations()
    assert.equal(convs.length, 1)
    assert.equal(convs[0].id, '100')
    assert.equal(convs[0].last_msg, 'hi')
    const msgs = await store.getMessages('100')
    assert.equal(msgs.length, 1)
    assert.equal(msgs[0].sender_name, 'A')
  })

  it('setMessages 覆盖历史', async () => {
    const list = [{ message_type: 'group', sender_id: '2', sender_name: 'B', content: '旧', time: 1700000000 }]
    await store.setMessages('200', list)
    assert.equal((await store.getMessages('200')).length, 1)
    await store.setMessages('200', [])
    assert.deepEqual(await store.getMessages('200'), [])
  })

  it('setConversations 覆盖列表', async () => {
    const convs = [{ id: '300', type: 'group', name: '群', last_msg: 'x', time: 1700000000 }]
    await store.setConversations(convs)
    assert.equal((await store.getConversations()).length, 1)
  })

  it('兜底缓存重载', async () => {
    const storage = mockStorage({})
    const s1 = createStore(storage)
    await s1.init()
    await s1.setConversations([{ id: '9', type: 'group', name: '群', last_msg: 'x', time: 1 }])
    const s2 = createStore(storage)
    await s2.init()
    assert.equal((await s2.getConversations()).length, 1)
    assert.equal((await s2.getConversations())[0].id, '9')
  })

  it('快捷回复增删', async () => {
    await store.addQuickReply('好的')
    await store.addQuickReply('收到')
    assert.deepEqual(await store.getQuickReplies(), ['好的', '收到'])
    await store.removeQuickReply(0)
    assert.deepEqual(await store.getQuickReplies(), ['收到'])
  })

  it('默认快捷词非空', () => {
    assert.ok(store.getDefaultQuickReplies().length > 0)
  })
})
