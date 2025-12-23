<script setup lang="ts">
import { InformationCircleIcon, MapPinIcon, SparklesIcon } from '@heroicons/vue/24/outline';

const travelTips = [
  {
    icon: MapPinIcon,
    title: '地标热门时段',
    description: '避开 10:00-14:00 的高峰时段，提前预约或清晨出发，享受专属沉浸感。',
  },
  {
    icon: SparklesIcon,
    title: '推荐体验',
    description: '跟随 AI 提示解锁隐藏玩法，特产市集、夜间灯光秀、山谷热气球等限定体验。',
  },
  {
    icon: InformationCircleIcon,
    title: '避坑提醒',
    description: '实时提醒天气变化与交通中断，提前规划备用路线，不再错过美景。',
  },
];
</script>

<template>
  <aside class="sidebar-panel">
    <div class="panel-header">
      <span class="icon-wrapper">
        <SparklesIcon class="h-5 w-5" />
      </span>
      <h3 class="panel-title">旅途灵感板</h3>
    </div>

    <p class="panel-desc">跟随灵感探索目的地，AI 将根据你的对话实时刷新推荐。</p>

    <ul class="panel-list">
      <li
        v-for="tip in travelTips"
        :key="tip.title"
        class="panel-item"
      >
        <span class="panel-icon">
          <component :is="tip.icon" class="h-5 w-5" />
        </span>
        <div>
          <p class="panel-item-title">{{ tip.title }}</p>
          <p class="panel-item-desc">{{ tip.description }}</p>
        </div>
      </li>
    </ul>
  </aside>
</template>

<style scoped>
.sidebar-panel {
  @apply bg-white/15 backdrop-blur-xl rounded-3xl border border-white/30 shadow-2xl px-6 py-6 space-y-4;
}
.panel-header {
  @apply flex items-center gap-3;
}
.icon-wrapper {
  @apply inline-flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br from-emerald-400/50 to-cyan-400/50 text-white shadow-lg;
}
.panel-title {
  @apply text-lg font-semibold text-white drop-shadow;
}
.panel-desc {
  @apply text-sm text-white/70 leading-relaxed;
}
.panel-list {
  @apply space-y-3;
}
.panel-item {
  @apply flex gap-3 items-start rounded-2xl bg-white/10 border border-white/20 px-4 py-3 transition;
}
.panel-item:hover {
  @apply border-white/40 bg-white/20 shadow-lg;
}
.panel-icon {
  @apply inline-flex h-8 w-8 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-400/40 to-teal-400/40 text-white;
}
.panel-item-title {
  @apply text-sm font-semibold text-white drop-shadow;
}
.panel-item-desc {
  @apply text-xs text-white/70 leading-relaxed;
}
</style>
