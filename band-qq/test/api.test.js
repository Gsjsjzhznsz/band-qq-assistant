import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { createApi } from '../src/common/api.js'

function mockInterconnect() {
  let conn
  const instance = () => {
    if (!conn) {
      conn = {
        diagnosisArgs: null,
        sendArgs: null,
        onmessage: null,
        onopen: null,
        onclose: null,
        onerror: null,
        send(opt) { this.sendArgs = opt; opt.success && opt.success({}) },
        diagnosis(opt) { this.diagnosisArgs = opt; opt.success && opt.success({ status: 0 }) }
      }
    }
    return conn
  }
  return { instance, getConn: () => conn }
}

describe('api', () => {
  it('send 传对象 payload 作为 data', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    api.init({})
    await api.send({ type: 'send_message', seq: 1 })
    assert.deepEqual(m.getConn().sendArgs.data, { type: 'send_message', seq: 1 })
  })

  it('connectStatus 对 status=0 返回 true', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    api.init({})
    assert.equal(await api.connectStatus(), true)
  })

  it('onmessage 赋值式注册并分发 JSON', async () => {
    const m = mockInterconnect()
    const api = createApi(m)
    let got = null
    api.init({ onMessage: (msg) => { got = msg } })
    assert.equal(typeof m.getConn().onmessage, 'function')
    m.getConn().onmessage({ data: JSON.stringify({ type: 'push_message' }) })
    assert.deepEqual(got, { type: 'push_message' })
  })
})