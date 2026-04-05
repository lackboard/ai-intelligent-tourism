<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRouter } from 'vue-router';
import MarkdownIt from 'markdown-it';
import { PlusIcon, PaperAirplaneIcon, HomeIcon, PlayIcon } from '@heroicons/vue/24/outline';
import { useChatStore } from '@/stores/chat';
import { postAgentChat, startSseChat } from '@/services/chatService';
import type { ChatMessage, ChatPayload } from '@/types/chat';
import ItineraryCard from '@/components/ItineraryCard.vue';
import FloatingBadge from '@/components/particles/FloatingBadge.vue';
import TipSidebar from '@/components/panels/TipSidebar.vue';
import MiniWeatherWidget from '@/components/panels/MiniWeatherWidget.vue';
import ParticleBackground from '@/components/particles/ParticleBackground.vue';
import MouseFollowParticles from '@/components/particles/MouseFollowParticles.vue';
import { useRandomBackground } from '@/composables/useRandomBackground';

const { backgroundUrl, isLoading: bgLoading } = useRandomBackground();

const md = new MarkdownIt({ breaks: true, linkify: true });
const chatStore = useChatStore();
const router = useRouter();

const input = ref('');
const isSubmitting = ref(false);
const thinking = ref(false);
let stopSse: (() => void) | null = null;

const messages = computed(() => chatStore.messages);

const modeBadge = computed(() =>
  chatStore.mode === 'sse'
    ? {
        label: '即时咨询',
        classes: 'bg-gradient-to-r from-amber-400 to-orange-400 text-white shadow-lg shadow-amber-300/30',
      }
    : {
        label: '深度规划',
        classes: 'bg-gradient-to-r from-emerald-400 to-teal-400 text-white shadow-lg shadow-emerald-300/30',
      },
);

const genId = () => (crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2));

const goHome = () => {
  stopSse?.();
  router.push({ name: 'home' });
};

const resetChat = () => {
  stopSse?.();
  thinking.value = false;
  isSubmitting.value = false;
  chatStore.resetThread();
};

const renderMarkdown = (text: string) => md.render(text);

const pushUserMessage = (content: string) => {
  const userMessage: ChatMessage = {
    id: genId(),
    sender: 'user',
    payload: { type: 'text', data: content },
  };
  chatStore.addMessage(userMessage);
};

const handleTextStreaming = (messageId: string, chunk: string) => {
  chatStore.updateMessage(messageId, (msg: ChatMessage) => {
    if (msg.payload.type === 'text') {
      msg.payload = { type: 'text', data: (msg.payload.data || '') + chunk };
    }
  });
};

const handleAiPayload = (messageId: string, payload: ChatPayload) => {
  if (payload.type === 'text') {
    handleTextStreaming(messageId, payload.data);
  } else {
    chatStore.updateMessage(messageId, (msg: ChatMessage) => {
      msg.payload = payload;
      msg.streaming = false;
    });
  }
};

const startSseFlow = (content: string) => {
  thinking.value = true;
  isSubmitting.value = true;

  const aiMessageId = genId();
  chatStore.addMessage({
    id: aiMessageId,
    sender: 'ai',
    payload: { type: 'text', data: '' },
    streaming: true,
  });

  stopSse?.();
  stopSse = startSseChat({
    message: content,
    visitorId: chatStore.visitorId,
    threadId: chatStore.threadId,
    onPayload: (payload) => handleAiPayload(aiMessageId, payload),
    onError: (err) => {
      chatStore.updateMessage(aiMessageId, (msg: ChatMessage) => {
        msg.payload = { type: 'error', data: err };
        msg.streaming = false;
      });
      thinking.value = false;
      isSubmitting.value = false;
    },
    onComplete: () => {
      chatStore.updateMessage(aiMessageId, (msg: ChatMessage) => {
        msg.streaming = false;
      });
      thinking.value = false;
      isSubmitting.value = false;
    },
  }).close;
};

const startAgentFlow = async (content: string) => {
  thinking.value = true;
  isSubmitting.value = true;

  const aiMessageId = genId();
  chatStore.addMessage({
    id: aiMessageId,
    sender: 'ai',
    payload: { type: 'text', data: '' },
    streaming: true,
  });

  try {
    const payload = await postAgentChat(content, chatStore.visitorId, chatStore.threadId);
    chatStore.updateMessage(aiMessageId, (msg: ChatMessage) => {
      msg.payload = payload;
      msg.streaming = false;
    });
  } catch (error) {
    chatStore.updateMessage(aiMessageId, (msg: ChatMessage) => {
      msg.payload = { type: 'error', data: '请求失败，请稍后重试' };
      msg.streaming = false;
    });
  } finally {
    thinking.value = false;
    isSubmitting.value = false;
  }
};

const sendMessage = async () => {
  const content = input.value.trim();
  if (!content || isSubmitting.value) return;

  input.value = '';
  pushUserMessage(content);

  if (chatStore.mode === 'sse') {
    startSseFlow(content);
  } else {
    await startAgentFlow(content);
  }
};

onBeforeUnmount(() => {
  stopSse?.();
});
</script>

<template>
  <div class="min-h-screen flex flex-col relative overflow-hidden">
    <!-- Three.js 3D 粒子背景 -->
    <ParticleBackground />
    
    <!-- 跟随鼠标的浮动粒子 -->
    <MouseFollowParticles :particle-count="40" :mouse-influence="120" />

    <!-- 全屏背景图 -->
    <div class="fixed inset-0 z-0 overflow-hidden">
      <!-- 加载占位 -->
      <div 
        v-if="bgLoading" 
        class="absolute inset-0 bg-gradient-to-br from-emerald-600 via-teal-500 to-cyan-600"
      />
      <img
        v-else
        :src="backgroundUrl"
        alt="Travel background"
        class="h-full w-full object-cover animate-kenburns"
      />
      <!-- 轻微暗色遮罩，确保文字可读 -->
      <div class="pointer-events-none absolute inset-0 bg-gradient-to-b from-black/20 via-black/10 to-black/30" />
    </div>

    <!-- 装饰性光效 -->
    <div class="fixed inset-0 z-[1] overflow-hidden pointer-events-none" aria-hidden="true">
      <div class="absolute top-16 left-10 h-52 w-52 rounded-full bg-emerald-300/20 blur-3xl animate-float-slow" />
      <div class="absolute top-32 right-20 h-64 w-64 rounded-full bg-teal-200/20 blur-3xl animate-float-slower" />
      <div class="absolute bottom-24 left-1/4 h-72 w-72 rounded-full bg-cyan-200/20 blur-3xl animate-float-medium" />
      <div class="absolute top-1/2 right-1/4 h-80 w-80 rounded-full bg-amber-200/10 blur-[100px] animate-breathe" />
    </div>

    <header class="fixed top-0 inset-x-0 z-20 backdrop-blur-xl bg-white/10 border-b border-white/20">
      <div class="w-full px-6 lg:px-8 2xl:px-12 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <button
            type="button"
            class="inline-flex items-center justify-center h-9 w-9 rounded-full bg-white/20 backdrop-blur-md border border-white/30 text-white transition hover:bg-white/30 hover:scale-110 shadow-lg"
            title="返回首页"
            @click="goHome"
          >
            <HomeIcon class="h-5 w-5" />
          </button>
          <span class="px-3 py-1 rounded-full text-sm font-medium transition-all duration-300 backdrop-blur-md" :class="modeBadge.classes">
            {{ modeBadge.label }}
          </span>
          <span class="hidden md:inline text-sm text-white/80 drop-shadow">Visitor: {{ chatStore.visitorId.slice(0, 6) }}...</span>
          <span class="hidden sm:inline text-sm text-white/80 drop-shadow">Thread: {{ chatStore.threadId.slice(0, 6) }}...</span>
        </div>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-full border border-white/30 bg-white/15 backdrop-blur-md px-4 py-2 text-sm text-white transition hover:-translate-y-0.5 hover:bg-white/25 shadow-lg"
          @click="resetChat"
        >
          <PlusIcon class="h-4 w-4" /> New Chat
        </button>
      </div>
    </header>

    <main class="relative z-10 flex-1 pt-28 pb-32">
      <div class="w-full px-6 lg:px-8 2xl:px-12 xl:pl-[21rem] xl:pr-[21rem] 2xl:pl-[24rem] 2xl:pr-[24rem]">
        <div class="hidden xl:flex xl:fixed xl:left-6 xl:top-24 2xl:left-10 w-[19rem] 2xl:w-80 h-fit flex-col gap-4">
          <TipSidebar />
          <div class="flex flex-col gap-3">
            <FloatingBadge label="海岛浮潜" icon="🏝️" :delay="0.2" />
            <FloatingBadge label="森林徒步" icon="🌲" :delay="0.6" />
            <FloatingBadge label="当地美食" icon="🍱" :delay="1" />
          </div>
        </div>

        <div class="space-y-6">
            <div
              v-if="messages.length === 0"
              class="mt-4 rounded-3xl px-8 py-12 text-center bg-white/15 backdrop-blur-xl shadow-2xl border border-white/30 animate-fade-in"
            >
              <div class="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-gradient-to-br from-teal-400/80 to-cyan-500/80 backdrop-blur-md text-5xl shadow-xl shadow-cyan-500/30 animate-bounce-slow">
                ✈️
              </div>
              <p class="text-2xl font-bold text-white mb-3 drop-shadow-lg">开始你的旅程问答</p>
              <p class="text-white/80 mb-6 leading-relaxed drop-shadow">输入你的目的地、预算或喜欢的旅行节奏，AI 将为你提供即时咨询或生成行程卡片。</p>
              <div class="flex flex-wrap justify-center gap-3 text-sm">
                <span class="px-4 py-2 rounded-full bg-white/20 backdrop-blur-md text-white border border-white/30 cursor-pointer transition hover:bg-white/30 hover:scale-105 shadow-lg">🌤️ 查天气</span>
                <span class="px-4 py-2 rounded-full bg-white/20 backdrop-blur-md text-white border border-white/30 cursor-pointer transition hover:bg-white/30 hover:scale-105 shadow-lg">📋 做攻略</span>
                <span class="px-4 py-2 rounded-full bg-white/20 backdrop-blur-md text-white border border-white/30 cursor-pointer transition hover:bg-white/30 hover:scale-105 shadow-lg">🎫 问签证</span>
              </div>
            </div>

            <div class="space-y-6 sm:space-y-8">
              <transition-group name="message" tag="div" class="space-y-5 sm:space-y-6">
                <div
                  v-for="message in messages"
                  :key="message.id"
                  class="flex"
                  :class="message.sender === 'user' ? 'justify-end' : 'justify-start'"
                >
                  <div
                    v-if="message.sender === 'user'"
                    class="max-w-[85%] sm:max-w-[70%] bg-gradient-to-br from-emerald-500/80 to-teal-500/80 backdrop-blur-md text-white rounded-3xl rounded-br-none px-4 sm:px-5 py-2.5 sm:py-3 shadow-lg shadow-emerald-500/20 hover:shadow-xl transition-all duration-300 animate-message-in border border-white/20"
                  >
                    <p class="leading-relaxed text-sm sm:text-base">{{ message.payload.type === 'text' ? message.payload.data : '' }}</p>
                  </div>

                  <div v-else class="max-w-[92%] sm:max-w-[72%] space-y-3 animate-message-in">
                    <div v-if="message.payload.type === 'error'" class="rounded-2xl border-2 border-red-300/50 bg-red-500/20 backdrop-blur-md px-3 sm:px-4 py-2.5 sm:py-3 text-white shadow-lg">
                      <p class="font-semibold flex items-center gap-2 text-sm sm:text-base">⚠️ 错误</p>
                      <p class="mt-1 text-xs sm:text-sm text-white/90">{{ message.payload.data }}</p>
                    </div>

                    <div v-else-if="message.payload.type === 'card'" class="w-full">
                      <ItineraryCard :itinerary="message.payload.data" />
                    </div>

                    <div v-else class="bg-white/20 backdrop-blur-xl rounded-3xl shadow-lg px-4 sm:px-5 py-3 sm:py-4 text-white border border-white/30">
                      <div v-if="message.streaming && !message.payload.data" class="flex items-center gap-3 text-white/80">
                        <div class="flex gap-1">
                          <span class="h-2 w-2 rounded-full bg-cyan-400 animate-bounce-delay-0" />
                          <span class="h-2 w-2 rounded-full bg-cyan-400 animate-bounce-delay-1" />
                          <span class="h-2 w-2 rounded-full bg-cyan-400 animate-bounce-delay-2" />
                        </div>
                        <span class="text-sm">AI 正在思考路径...</span>
                      </div>
                      <article v-else class="prose prose-sm sm:prose prose-invert prose-p:text-white/90 prose-headings:text-white prose-strong:text-white prose-a:text-cyan-300 max-w-none" v-html="renderMarkdown(message.payload.data)" />
                    </div>
                  </div>
                </div>
              </transition-group>
            </div>
        </div>

        <div class="hidden xl:flex xl:fixed xl:right-6 xl:top-24 2xl:right-10 w-[19rem] 2xl:w-80 h-fit flex-col gap-4">
          <MiniWeatherWidget city="苏梅岛" :temperature="28" condition="sunny" />
          <div class="bg-white/15 backdrop-blur-xl rounded-3xl border border-white/30 shadow-2xl px-6 py-6 space-y-4">
            <div class="flex items-center gap-3">
              <span class="inline-flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400/80 to-cyan-400/80 text-white shadow-lg">
                <PlayIcon class="h-5 w-5" />
              </span>
              <div>
                <p class="text-sm font-semibold text-white drop-shadow">AI 旅行挑战</p>
                <p class="text-xs text-white/70">选择主题，AI 即刻生成沉浸式路线</p>
              </div>
            </div>
            <div class="flex flex-wrap gap-2 text-xs">
              <span class="px-3 py-1.5 rounded-full bg-emerald-500/30 text-white border border-emerald-400/40">潜店巡礼</span>
              <span class="px-3 py-1.5 rounded-full bg-cyan-500/30 text-white border border-cyan-400/40">亲子乐园</span>
              <span class="px-3 py-1.5 rounded-full bg-teal-500/30 text-white border border-teal-400/40">徒步秘境</span>
            </div>
            <button
              type="button"
              class="w-full inline-flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-emerald-500/80 to-teal-500/80 backdrop-blur-md text-white text-sm font-semibold px-4 py-2 shadow-lg border border-white/20 transition hover:shadow-xl hover:-translate-y-0.5"
            >
              <PlayIcon class="h-4 w-4" /> 立即开启冒险
            </button>
          </div>
        </div>
      </div>
    </main>

    <div class="fixed inset-x-0 bottom-0 z-20 pb-4 sm:pb-6 pointer-events-none">
      <div class="max-w-screen-lg mx-auto px-4 sm:px-6 lg:px-8">
        <div class="relative pointer-events-auto">
          <div class="absolute inset-0 blur-3xl bg-gradient-to-r from-cyan-400/20 via-teal-400/15 to-emerald-400/20 rounded-full animate-pulse-slow" />
          <div class="relative bg-white/15 backdrop-blur-xl rounded-full shadow-2xl border border-white/30 px-4 sm:px-6 py-2.5 sm:py-3 flex items-center gap-2 sm:gap-3 transition-all duration-300 hover:bg-white/20">
            <input
              v-model="input"
              class="flex-1 bg-transparent outline-none text-white placeholder:text-white/50 px-1 sm:px-2 py-1 text-sm min-w-0"
              type="text"
              placeholder="输入问题..."
              @keyup.enter="sendMessage"
            />
            <button
              type="button"
              class="inline-flex items-center gap-1.5 sm:gap-2 rounded-full bg-gradient-to-r from-teal-500/90 to-cyan-500/90 backdrop-blur-md px-3 sm:px-5 py-1.5 sm:py-2 text-white text-xs sm:text-sm font-semibold shadow-lg border border-white/20 transition duration-300 hover:-translate-y-0.5 hover:shadow-xl disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0"
              :disabled="isSubmitting"
              @click="sendMessage"
            >
              <PaperAirplaneIcon class="h-4 w-4" />
              <span class="hidden sm:inline">{{ isSubmitting ? '发送中...' : '发送' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes kenburns {
  0% { transform: scale(1); }
  50% { transform: scale(1.08); }
  100% { transform: scale(1); }
}

@keyframes float-slow {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-40px); }
}

@keyframes float-slower {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-50px); }
}

@keyframes float-medium {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-30px); }
}

@keyframes breathe {
  0%, 100% { opacity: 0.1; transform: scale(1); }
  50% { opacity: 0.2; transform: scale(1.15); }
}

@keyframes bounce-slow {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-12px); }
}

@keyframes bounce-delay-0 {
  0%, 80%, 100% { opacity: 0.4; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-10px); }
}

@keyframes bounce-delay-1 {
  0%, 100% { opacity: 0.4; transform: translateY(0); }
  25%, 55% { opacity: 1; transform: translateY(-10px); }
}

@keyframes bounce-delay-2 {
  0%, 100% { opacity: 0.4; transform: translateY(0); }
  10%, 40% { opacity: 1; transform: translateY(-10px); }
}

@keyframes fade-in {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes pulse-slow {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 0.9; }
}

.animate-kenburns {
  animation: kenburns 25s ease-in-out infinite;
}

.animate-float-slow {
  animation: float-slow 8s ease-in-out infinite;
}

.animate-float-slower {
  animation: float-slower 12s ease-in-out infinite;
}

.animate-float-medium {
  animation: float-medium 10s ease-in-out infinite;
}

.animate-breathe {
  animation: breathe 6s ease-in-out infinite;
}

.animate-bounce-slow {
  animation: bounce-slow 2s ease-in-out infinite;
}

.animate-bounce-delay-0 {
  animation: bounce-delay-0 1.2s ease-in-out infinite;
}

.animate-bounce-delay-1 {
  animation: bounce-delay-1 1.2s ease-in-out infinite;
}

.animate-bounce-delay-2 {
  animation: bounce-delay-2 1.2s ease-in-out infinite;
}

.animate-fade-in {
  animation: fade-in 0.6s ease-out;
}

.animate-message-in {
  animation: message-in 0.4s ease-out;
}

.animate-pulse-slow {
  animation: pulse-slow 3s ease-in-out infinite;
}

.message-enter-active,
.message-leave-active {
  transition: all 0.3s ease;
}

.message-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.message-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
