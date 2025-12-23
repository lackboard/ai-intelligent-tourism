import axios from 'axios';
import { ChatPayload } from '@/types/chat';

const SSE_URL = 'http://localhost:8123/api/ai/tourism_app/chat/sse_emitter';
const AGENT_URL = 'http://localhost:8123/api/ai/tourism_app/chat/manus';

export interface StartSseOptions {
  message: string;
  threadId: string;
  onPayload: (payload: ChatPayload) => void;
  onError?: (message: string) => void;
  onComplete?: () => void;
}

export const startSseChat = ({ message, threadId, onPayload, onError, onComplete }: StartSseOptions) => {
  const url = `${SSE_URL}?message=${encodeURIComponent(message)}&chatId=${encodeURIComponent(threadId)}`;
  const eventSource = new EventSource(url);
  let hasReceivedData = false;

  eventSource.onmessage = (event) => {
    hasReceivedData = true;
    try {
      // 处理 SSE data 字段，可能是纯文本或 JSON
      const rawData = event.data;
      
      // 尝试解析为 JSON
      try {
        const payload = JSON.parse(rawData) as ChatPayload;
        onPayload(payload);
      } catch {
        // 如果不是 JSON，当作纯文本处理
        if (rawData && rawData.trim()) {
          onPayload({ type: 'text', data: rawData });
        }
      }
    } catch (error) {
      console.error('SSE parse error:', error);
    }
  };

  eventSource.onerror = (e) => {
    eventSource.close();
    
    // EventSource.CLOSED = 2, 表示连接已关闭
    // 如果已经接收过数据，说明是正常结束；否则才是真正的连接错误
    if (eventSource.readyState === EventSource.CLOSED && hasReceivedData) {
      // 正常结束，不报错
      onComplete?.();
    } else if (!hasReceivedData) {
      // 从未收到数据就断开，说明连接失败
      onError?.('无法连接到服务器，请检查后端是否启动');
      onComplete?.();
    } else {
      // 其他情况也视为正常结束
      onComplete?.();
    }
  };

  eventSource.onopen = () => {
    // connection opened
  };

  return {
    close: () => {
      eventSource.close();
      onComplete?.();
    },
  };
};

export const postAgentChat = async (message: string, threadId: string) => {
  const { data } = await axios.post<ChatPayload>(AGENT_URL, {
    message,
    threadId,
  });
  return data;
};
