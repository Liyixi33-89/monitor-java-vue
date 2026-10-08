/** WebSocket 实时推送封装：断线指数退避重连，最大 30s；心跳保活 */
export function useRealtimeSocket(onMessage) {
  let socket = null
  let retries = 0
  let closed = false
  let heartbeatTimer = null

  function connect() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    socket = new WebSocket(`${proto}://${location.host}/ws/realtime`)

    socket.onopen = () => {
      retries = 0
      heartbeatTimer = setInterval(() => {
        if (socket && socket.readyState === WebSocket.OPEN) socket.send('ping')
      }, 30000)
    }

    socket.onmessage = (evt) => {
      if (evt.data === 'pong') return
      try {
        onMessage(JSON.parse(evt.data))
      } catch (e) { /* 非 JSON 消息忽略 */ }
    }

    socket.onclose = () => {
      clearInterval(heartbeatTimer)
      if (!closed) {
        const delay = Math.min(30, 2 ** retries)
        retries++
        setTimeout(connect, delay * 1000)
      }
    }

    socket.onerror = () => socket.close()
  }

  connect()

  return {
    close() {
      closed = true
      clearInterval(heartbeatTimer)
      if (socket) socket.close()
    }
  }
}
