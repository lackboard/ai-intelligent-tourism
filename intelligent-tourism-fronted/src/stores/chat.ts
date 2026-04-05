import { defineStore } from 'pinia';
import { ChatMessage, ChatMode } from '@/types/chat';

const genId = () => (crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2));
const VISITOR_ID_STORAGE_KEY = 'tourism-visitor-id';

const getOrCreateVisitorId = () => {
  if (typeof window === 'undefined') {
    return genId();
  }

  const cachedVisitorId = window.localStorage.getItem(VISITOR_ID_STORAGE_KEY);
  if (cachedVisitorId) {
    return cachedVisitorId;
  }

  const visitorId = genId();
  window.localStorage.setItem(VISITOR_ID_STORAGE_KEY, visitorId);
  return visitorId;
};

export const useChatStore = defineStore('chat', {
  state: () => ({
    mode: 'sse' as ChatMode,
    visitorId: getOrCreateVisitorId(),
    threadId: genId(),
    messages: [] as ChatMessage[],
  }),
  actions: {
    setMode(mode: ChatMode) {
      this.mode = mode;
    },
    resetThread() {
      this.threadId = genId();
      this.messages = [];
    },
    addMessage(message: ChatMessage) {
      this.messages.push(message);
    },
    updateMessage(id: string, updater: (msg: ChatMessage) => void) {
      const idx = this.messages.findIndex((m) => m.id === id);
      if (idx !== -1) {
        updater(this.messages[idx]);
      }
    },
  },
});
