<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { BoltIcon, CubeTransparentIcon } from '@heroicons/vue/24/outline';
import { useChatStore } from '@/stores/chat';
import type { ChatMode } from '@/types/chat';
import ParticleBackground from '@/components/particles/ParticleBackground.vue';
import MouseFollowParticles from '@/components/particles/MouseFollowParticles.vue';
import { useRandomBackground } from '@/composables/useRandomBackground';

const router = useRouter();
const chatStore = useChatStore();
const { backgroundUrl, isLoading: bgLoading } = useRandomBackground();

// 卡片 3D 倾斜效果
const card1Ref = ref<HTMLButtonElement | null>(null);
const card2Ref = ref<HTMLButtonElement | null>(null);
const card1Style = ref({});
const card2Style = ref({});

const handleMouseMove = (event: MouseEvent, cardRef: HTMLButtonElement | null, styleRef: typeof card1Style) => {
  if (!cardRef) return;
  
  const rect = cardRef.getBoundingClientRect();
  const x = event.clientX - rect.left;
  const y = event.clientY - rect.top;
  const centerX = rect.width / 2;
  const centerY = rect.height / 2;
  
  const rotateX = (y - centerY) / 10;
  const rotateY = (centerX - x) / 10;
  
  styleRef.value = {
    transform: `perspective(1000px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateZ(10px)`,
    transition: 'transform 0.1s ease-out'
  };
};

const handleMouseLeave = (styleRef: typeof card1Style) => {
  styleRef.value = {
    transform: 'perspective(1000px) rotateX(0deg) rotateY(0deg) translateZ(0px)',
    transition: 'transform 0.5s ease-out'
  };
};

const handleSelect = (mode: ChatMode) => {
  chatStore.setMode(mode);
  chatStore.resetThread();
  router.push({ name: 'chat', query: { mode } });
};
</script>

<template>
  <main class="relative min-h-screen overflow-hidden">
    <!-- Three.js 3D 粒子背景 -->
    <ParticleBackground />
    
    <!-- 跟随鼠标的浮动粒子 -->
    <MouseFollowParticles :particle-count="60" :mouse-influence="150" />

    <!-- 全屏背景图，增加 Ken Burns 缓慢缩放 -->
    <div class="absolute inset-0 z-0 overflow-hidden">
      <!-- 加载占位 -->
      <div 
        v-if="bgLoading" 
        class="absolute inset-0 bg-gradient-to-br from-emerald-600 via-teal-500 to-cyan-600"
      />
      <img
        v-else
        :src="backgroundUrl"
        alt="Beach resort"
        class="h-full w-full object-cover animate-kenburns"
      />
      <!-- 局部渐变仅用于文字区域轻微遮罩，避免压暗全局色彩 -->
      <div class="pointer-events-none absolute inset-x-0 top-0 h-1/2 bg-gradient-to-b from-black/25 via-black/10 to-transparent" />
    </div>

    <!-- 装饰性光效（保持轻盈） -->
    <div class="absolute inset-0 z-0 overflow-hidden pointer-events-none" aria-hidden="true">
      <div class="absolute -top-32 -right-24 h-[420px] w-[420px] rounded-full bg-cyan-200/25 blur-[110px] animate-pulse-glow" />
      <div class="absolute -bottom-10 -left-10 h-[360px] w-[360px] rounded-full bg-emerald-200/25 blur-[90px] animate-pulse-glow-delayed" />
      <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[500px] w-[500px] rounded-full bg-amber-200/10 blur-[120px] animate-breathe" />
    </div>

    <!-- 主内容区 -->
    <section class="relative z-10 flex min-h-screen flex-col items-center justify-center px-6 py-16">
      <div class="max-w-6xl w-full text-center space-y-8 animate-fade-in-up">
        <!-- 胶囊标签 -->
        <div class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/60 backdrop-blur-md border border-white/80 text-sm text-slate-800 shadow-lg shadow-black/10">
          <span class="relative flex h-2 w-2">
            <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-cyan-400 opacity-70"></span>
            <span class="relative inline-flex h-2 w-2 rounded-full bg-cyan-500"></span>
          </span>
          阳光正好，选择你的对话方式
        </div>

        <!-- 主标题 -->
        <h1 class="text-5xl sm:text-6xl lg:text-7xl font-bold tracking-tight leading-[1.05] text-white drop-shadow-[0_10px_30px_rgba(0,0,0,0.35)]">
          AI 旅游规划助手
        </h1>

        <!-- 副标题 -->
        <p class="text-xl sm:text-2xl text-white/90 font-light tracking-wide max-w-3xl mx-auto drop-shadow-[0_6px_20px_rgba(0,0,0,0.35)]">
          探索世界，从未如此简单
        </p>

        <!-- 描述文字 -->
        <p class="text-base sm:text-lg text-white/85 max-w-2xl mx-auto leading-relaxed drop-shadow-[0_6px_18px_rgba(0,0,0,0.3)]">
          两种智能体验，满足你的不同旅行需求。极速查询，或深度规划。
        </p>
      </div>

      <!-- 双模式选择卡片 -->
      <div class="relative z-10 mt-14 w-full max-w-5xl px-4">
        <div class="grid grid-cols-1 md:grid-cols-2 gap-6 lg:gap-8">
          <!-- 即时咨询卡片 -->
          <button
            ref="card1Ref"
            type="button"
            class="group relative rounded-3xl bg-white/20 backdrop-blur-lg border border-white/40 p-8 text-left transition-all duration-600 ease-out shadow-xl shadow-black/10 hover:border-cyan-400 hover:shadow-2xl hover:shadow-cyan-300/30 animate-slide-in-left card-3d"
            :style="card1Style"
            @mousemove="handleMouseMove($event, card1Ref, card1Style)"
            @mouseleave="handleMouseLeave(card1Style)"
            @click="handleSelect('sse')"
          >
            <!-- 光泽反射效果 -->
            <div class="absolute inset-0 rounded-3xl overflow-hidden pointer-events-none">
              <div class="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 bg-gradient-to-br from-white/30 via-transparent to-transparent" />
              <div class="glare-effect absolute inset-0 opacity-0 group-hover:opacity-30" />
            </div>
            
            <!-- 卡片内微弱流光 -->
            <div class="absolute inset-0 rounded-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700 bg-[radial-gradient(circle_at_20%_20%,rgba(255,255,255,0.35),transparent_45%),radial-gradient(circle_at_80%_0%,rgba(59,203,255,0.25),transparent_40%)]" />
            
            <div class="relative z-10">
              <!-- 图标带光晕 + 晃动 -->
              <div class="relative inline-flex mb-6">
                <div class="absolute inset-0 rounded-2xl bg-orange-300/50 blur-xl scale-150 group-hover:bg-orange-300/70 transition-all duration-500" />
                <span class="relative flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-amber-300 to-orange-400 text-slate-900 shadow-xl shadow-orange-300/50 group-hover:animate-wiggle">
                  <BoltIcon class="h-8 w-8" />
                </span>
              </div>

              <!-- 标题 -->
              <p class="text-xs uppercase tracking-[0.25em] text-cyan-700/80 mb-1">Instant Chat</p>
              <h3 class="text-2xl font-bold text-slate-900 mb-4">即时咨询</h3>
              
              <!-- 描述 -->
              <p class="text-slate-800/80 leading-relaxed mb-6">
                基于 SSE 流式技术，毫秒级响应。适合快速查询天气、签证、交通与景点信息。
              </p>

              <!-- 标签 -->
              <div class="flex flex-wrap gap-2">
                <span class="px-3 py-1 rounded-full bg-cyan-500/15 text-cyan-700 text-xs border border-cyan-400/40">⚡ 极速响应</span>
                <span class="px-3 py-1 rounded-full bg-cyan-500/15 text-cyan-700 text-xs border border-cyan-400/40">📡 流式推送</span>
              </div>
            </div>
          </button>

          <!-- 深度规划卡片 -->
          <button
            ref="card2Ref"
            type="button"
            class="group relative rounded-3xl bg-white/20 backdrop-blur-lg border border-white/40 p-8 text-left transition-all duration-600 ease-out shadow-xl shadow-black/10 hover:border-cyan-400 hover:shadow-2xl hover:shadow-cyan-300/30 animate-slide-in-right card-3d"
            :style="card2Style"
            @mousemove="handleMouseMove($event, card2Ref, card2Style)"
            @mouseleave="handleMouseLeave(card2Style)"
            @click="handleSelect('agent')"
          >
            <!-- 光泽反射效果 -->
            <div class="absolute inset-0 rounded-3xl overflow-hidden pointer-events-none">
              <div class="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 bg-gradient-to-br from-white/30 via-transparent to-transparent" />
              <div class="glare-effect absolute inset-0 opacity-0 group-hover:opacity-30" />
            </div>
            
            <!-- 卡片内微弱流光 -->
            <div class="absolute inset-0 rounded-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700 bg-[radial-gradient(circle_at_20%_20%,rgba(255,255,255,0.35),transparent_45%),radial-gradient(circle_at_80%_0%,rgba(59,203,255,0.25),transparent_40%)]" />
            
            <div class="relative z-10">
              <!-- 图标带光晕 + 慢速旋转 -->
              <div class="relative inline-flex mb-6">
                <div class="absolute inset-0 rounded-2xl bg-cyan-300/50 blur-xl scale-150 group-hover:bg-cyan-300/70 transition-all duration-500" />
                <span class="relative flex h-16 w-16 items-center justify-center rounded-2xl bg-gradient-to-br from-cyan-300 to-emerald-400 text-slate-900 shadow-xl shadow-cyan-300/50 group-hover:animate-spin-slow">
                  <CubeTransparentIcon class="h-8 w-8" />
                </span>
              </div>

              <!-- 标题 -->
              <p class="text-xs uppercase tracking-[0.25em] text-cyan-700/80 mb-1">Deep Planning</p>
              <h3 class="text-2xl font-bold text-slate-900 mb-4">深度规划</h3>
              
              <!-- 描述 -->
              <p class="text-slate-800/80 leading-relaxed mb-6">
                AI Agent 多步推理与上下文记忆，为您生成完整可执行的行程方案。
              </p>

              <!-- 标签 -->
              <div class="flex flex-wrap gap-2">
                <span class="px-3 py-1 rounded-full bg-cyan-500/15 text-cyan-700 text-xs border border-cyan-400/40">🧠 智能推理</span>
                <span class="px-3 py-1 rounded-full bg-cyan-500/15 text-cyan-700 text-xs border border-cyan-400/40">📋 行程卡片</span>
              </div>
            </div>
          </button>
        </div>
      </div>

      <!-- 底部装饰线 -->
      <div class="mt-16 w-24 h-[1px] bg-gradient-to-r from-transparent via-white/60 to-transparent animate-fade-in-up" />
    </section>

    <!-- Footer -->
    <footer class="absolute bottom-0 inset-x-0 z-10 py-6 text-center">
      <p class="text-xs text-white/80 tracking-wider drop-shadow-[0_4px_12px_rgba(0,0,0,0.35)]">
        © 2025 AI Travel Planner · Sun-kissed journeys await
      </p>
    </footer>
  </main>
</template>

<style scoped>
@keyframes kenburns {
  0% { transform: scale(1); }
  50% { transform: scale(1.08); }
  100% { transform: scale(1); }
}

@keyframes fadeInUp {
  0% { opacity: 0; transform: translateY(18px); }
  100% { opacity: 1; transform: translateY(0); }
}

@keyframes slideInLeft {
  0% { opacity: 0; transform: translateX(-30px); }
  100% { opacity: 1; transform: translateX(0); }
}

@keyframes slideInRight {
  0% { opacity: 0; transform: translateX(30px); }
  100% { opacity: 1; transform: translateX(0); }
}

@keyframes wiggle {
  0%, 100% { transform: rotate(0deg); }
  25% { transform: rotate(-6deg); }
  50% { transform: rotate(6deg); }
  75% { transform: rotate(-3deg); }
}

@keyframes pulse-glow {
  0%, 100% { opacity: 0.25; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(1.1); }
}

@keyframes pulse-glow-delayed {
  0%, 100% { opacity: 0.25; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(1.05); }
}

@keyframes breathe {
  0%, 100% { opacity: 0.1; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 0.2; transform: translate(-50%, -50%) scale(1.15); }
}

@keyframes glare-move {
  0% { transform: translateX(-100%) rotate(45deg); }
  100% { transform: translateX(200%) rotate(45deg); }
}

.animate-kenburns {
  animation: kenburns 20s ease-in-out infinite;
}

.animate-fade-in-up {
  animation: fadeInUp 0.9s ease-out 0.2s both;
}

.animate-slide-in-left {
  animation: slideInLeft 0.9s ease-out 0.2s both;
}

.animate-slide-in-right {
  animation: slideInRight 0.9s ease-out 0.3s both;
}

.animate-wiggle {
  animation: wiggle 0.6s ease-in-out;
}

.animate-spin-slow {
  animation: spin 14s linear infinite;
}

.animate-pulse-glow {
  animation: pulse-glow 4s ease-in-out infinite;
}

.animate-pulse-glow-delayed {
  animation: pulse-glow-delayed 5s ease-in-out infinite 1s;
}

.animate-breathe {
  animation: breathe 6s ease-in-out infinite;
}

.card-3d {
  transform-style: preserve-3d;
  will-change: transform;
}

.glare-effect {
  background: linear-gradient(
    105deg,
    transparent 40%,
    rgba(255, 255, 255, 0.5) 45%,
    rgba(255, 255, 255, 0.8) 50%,
    rgba(255, 255, 255, 0.5) 55%,
    transparent 60%
  );
  animation: glare-move 3s ease-in-out infinite;
}
</style>
