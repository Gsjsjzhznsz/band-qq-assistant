let seq = 0

function nextSeq() {
  seq += 1
  return seq
}

function sendMessage(messageType, targetId, content) {
  return {
    type: 'send_message',
    seq: nextSeq(),
    message_type: messageType,
    target_id: targetId,
    content: content,
    time: Date.now()
  }
}

function getConversations() {
  return { type: 'get_conversations', seq: nextSeq() }
}

function getVisibleContacts() {
  return { type: 'get_visible_contacts', seq: nextSeq() }
}

function getConnectState() {
  return { type: 'get_connect_state', seq: nextSeq() }
}

function getHistory(targetId, limit) {
  return { type: 'get_history', seq: nextSeq(), target_id: targetId, limit: limit || 20 }
}

function clearAllHistory() {
  return { type: 'clear_all_history', seq: nextSeq() }
}

function isEmojiCode(c) {
  return (c >= 0x2600 && c <= 0x27bf) ||
    (c >= 0x2b00 && c <= 0x2bff) ||
    (c >= 0x2b50 && c <= 0x2b55) ||
    c === 0xfe0f || c === 0x200d || c === 0x20e3 ||
    c === 0x3030 || c === 0x303d ||
    (c >= 0xa9c2 && c <= 0xa9ff) ||
    (c >= 0xaa00 && c <= 0xaa5f)
}

function transformEmoji(s, replace) {
  if (typeof s !== 'string') return ''
  let out = ''
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c >= 0xd83c && c <= 0xd83e) {
      out += replace ? '[表情]' : ''
      i++
      continue
    }
    if (isEmojiCode(c)) {
      out += replace ? '[表情]' : ''
      continue
    }
    out += s[i]
  }
  return out
}

function stripEmoji(s) {
  return transformEmoji(s, false)
}

function markEmoji(s) {
  return transformEmoji(s, true)
}

function degradeContent(raw) {
  if (typeof raw === 'string') return markEmoji(raw)
  if (!Array.isArray(raw)) return ''
  return raw.map((seg) => {
    if (seg.type === 'text') return markEmoji((seg.data && seg.data.text) || '')
    if (seg.type === 'face') return '[表情]'
    if (seg.type === 'image') return '[图片]'
    if (seg.type === 'record' || seg.type === 'voice') return '[语音]'
    if (seg.type === 'video') return '[视频]'
    if (seg.type === 'file') return '[文件]'
    return '[其他]'
  }).join('')
}

function decodePush(raw) {
  if (!raw || raw.type !== 'push_message') return null
  if (typeof raw.target_id !== 'string' || typeof raw.sender_id !== 'string') return null
  return {
    type: 'push_message',
    seq: raw.seq || 0,
    message_type: raw.message_type === 'group' ? 'group' : 'private',
    target_id: raw.target_id,
    sender_id: raw.sender_id,
    sender_name: stripEmoji(raw.sender_name || ''),
    target_name: stripEmoji(raw.target_name || ''),
    content: typeof raw.content === 'string' ? markEmoji(raw.content) : degradeContent(raw.content),
    is_self: raw.is_self === true,
    time: raw.time || Date.now(),
    visible: raw.visible !== false
  }
}

export default {
  nextSeq,
  sendMessage,
  getConversations,
  getVisibleContacts,
  getConnectState,
  getHistory,
  clearAllHistory,
  stripEmoji,
  markEmoji,
  degradeContent,
  decodePush
}