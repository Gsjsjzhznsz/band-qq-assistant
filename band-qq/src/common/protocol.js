let seq = 0

export function nextSeq() {
  seq += 1
  return seq
}

export function sendMessage(messageType, targetId, content) {
  return {
    type: 'send_message',
    seq: nextSeq(),
    message_type: messageType,
    target_id: targetId,
    content: content
  }
}

export function getConversations() {
  return { type: 'get_conversations', seq: nextSeq() }
}

export function getVisibleContacts() {
  return { type: 'get_visible_contacts', seq: nextSeq() }
}

export function getConnectState() {
  return { type: 'get_connect_state', seq: nextSeq() }
}

export function getHistory(targetId, limit) {
  return { type: 'get_history', seq: nextSeq(), target_id: targetId, limit: limit || 20 }
}

export function clearAllHistory() {
  return { type: 'clear_all_history', seq: nextSeq() }
}

export function stripEmoji(s) {
  if (typeof s !== 'string') return ''
  return s.replace(/[\uD83C-\uDFFF\u2600-\u27BF\u2B00-\u2BFF\u2B50-\u2B55\uFE0F\u200D\u20E3\u3030\u303D\uA9C2\uA9CE\uA9D0-\uA9FF\uAA00-\uAA5F\u{1F000}-\u{1FAFF}]/gu, '')
}

export function markEmoji(s) {
  if (typeof s !== 'string') return ''
  return s.replace(/[\uD83C-\uDFFF\u2600-\u27BF\u2B00-\u2BFF\u2B50-\u2B55\uFE0F\u200D\u20E3\u3030\u303D\uA9C2\uA9CE\uA9D0-\uA9FF\uAA00-\uAA5F\u{1F000}-\u{1FAFF}]/gu, '[表情]')
}

export function degradeContent(raw) {
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

export function decodePush(raw) {
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
