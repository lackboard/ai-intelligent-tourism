import { defineStore } from 'pinia';
import { ChatMessage, ChatMode } from '@/types/chat';

const genId = () => (crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2));

export const useChatStore = defineStore('chat', {
  state: () => ({
    mode: 'sse' as ChatMode,
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
