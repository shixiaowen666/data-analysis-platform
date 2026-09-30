/**
 * 连接思考过程 WebSocket
 * @param {Object} options
 * @param {string} options.chatSessionId
 * @param {string} options.chatId
 * @param {Function} [options.onMessage]
 * @param {Function} [options.onClose]
 * @param {Function} [options.onError]
 * @returns {{ close: Function }}
 */

let timeout; // 用于存储setTimeout的ID
const timeoutTime = 2*60*1000;


export function connectChatWebSocket({chatSessionId, chatId, host, onMessage, onClose, onError}) {

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';

  let wsBase = ''
  if(process.env.VUE_APP_PRODUCTION === 'true'){
    wsBase = `${protocol}//${location.host}`
  }

  const path = `/api/websocket/${chatSessionId}%40${chatId}`;
  const url = `${wsBase}${path}?host=${host}`;

  const ws = new WebSocket(url);
  let closed = false;

  const close = () => {
    if (closed) return;
    closed = true;
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
      ws.close();
    }
  };

  ws.onopen = () => {
    console.log('WebSocket 连接成功');
     clearTimeout(timeout); // 清除之前的定时器
    //startHeartbeat();
  };

  ws.onmessage = event => {

    /*resetHeartbeatTimeout();
    // 处理心跳响应
    if (event.data === 'pong') {
      console.log('收到心跳响应');
      return;
    }*/

    clearTimeout(timeout); // 清除定时器，因为收到了消息
    timeout = setTimeout(function() {
        console.error('WebSocket timed out');
        ws.onerror(new Event('WebSocket timed out'));
        ws.close()
    }, timeoutTime);


    let data;
    try {
      data = JSON.parse(event.data);
    } catch {
      data = {raw: event.data};
    }

    onMessage && onMessage(data);

    if (data.metadata && data.metadata.close) {
      close();
      onClose && onClose(data);
    }
  };

  ws.onerror = event => {
    clearTimeout(timeout); // 清除定时器
    onError && onError(event);
  };

  ws.onclose = () => {
    //stopHeartbeat();
    clearTimeout(timeout); // 清除定时器

    if (!closed) {
      closed = true;
      onClose && onClose();
    }
  };

  return {close};
}
