import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { createApi } from '../src/common/api.js'

function mockInterconnect() {
  let conn
  const instance = () => {
    if (!conn) {
      conn = {
        handlers: {},
        diagnosisArgs: null,
        sendArgs: null,
        send(opt) { this.sendArgs = opt; opt.success && opt.success({}) },
        diagnosis(opt) { this.diagnosisArgs = opt; opt.success && opt.success({ status: 0 }) }
      }
      conn.onmessage = (fn) => { conn.handlers.onmessage = fn }
      conn.onopen = (fn) => { conn.handlers.onopen = fn }
      conn.onclose = (fn) => { conn.handlers.onclose = fn }
      conn.onerror = (fn) => { conn.handlers.onerror = fn }
    }
    return conn
  }
  return { instance, getConn: () => conn }
}

describe('api', () => {
  it('send 序列化 payload 为 JSON 字符串', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    api.init({})
    await api.send({ type: 'send_message', seq: 1 })
    assert.equal(m.getConn().sendArgs.data, JSON.stringify({ type: 'send_message', seq: 1 }))
  })

  it('connectStatus 对 status=0 返回 true', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    api.init({})
    assert.equal(await api.connectStatus(), true)
  })

  it('onmessage 分发 JSON', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    let got = null
    api.init({ onMessage: (msg) => { got = msg } })
    m.getConn().handlers.onmessage({ data: JSON.stringify({ type: 'push_message' }) })
    assert.deepEqual(got, { type: 'push_message' })
  })
})