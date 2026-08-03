import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { nextSeq, sendMessage, getConversations, degradeContent, decodePush } from '../common/protocol.js'

describe('protocol', () => {
  it('seq 自增', () => {
    assert.equal(nextSeq(), 1)
    assert.equal(nextSeq(), 2)
  })

  it('构造 send_message 帧', () => {
    const msg = sendMessage('group', '123', '收到')
    assert.equal(msg.type, 'send_message')
    assert.equal(msg.message_type, 'group')
    assert.equal(msg.target_id, '123')
    assert.equal(msg.content, '收到')
  })

  it('构造 get_conversations 帧', () => {
    const msg = getConversations()
    assert.equal(msg.type, 'get_conversations')
  })

  it('降级非文本段', () => {
    assert.equal(degradeContent([{ type: 'text', data: { text: 'hi' } }, { type: 'image' }]), 'hi[图片]')
  })

  it('解析 push_message 并规范化', () => {
    const raw = { type: 'push_message', seq: 1, message_type: 'group', target_id: '9', sender_id: '8', sender_name: '张三', content: '你好', time: 1700000000 }
    const msg = decodePush(raw)
    assert.equal(msg.sender_id, '8')
    assert.equal(msg.content, '你好')
  })

  it('push_message 缺字段返回 null', () => {
    assert.equal(decodePush({ type: 'push_message' }), null)
  })
})
