import { describe, it, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { createStore } from '../common/store.js'

function mockStorage(initial) {
  const map = new Map(Object.entries(initial))
  return {
    get: (o) => { o.success(map.get(o.key) ?? '') },
    set: (o) => { map.set(o.key, o.value); o.success({}) },
    clear: (o) => { map.clear(); o.success({}) }
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

  it('快捷回复增删', async () => {
    await store.addQuickReply('好的')
    await store.addQuickReply('收到')
    assert.deepEqual(await store.getQuickReplies(), ['好的', '收到'])
    await store.removeQuickReply(0)
    assert.deepEqual(await store.getQuickReplies(), ['收到'])
  })
})
