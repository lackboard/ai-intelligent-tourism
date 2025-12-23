<script setup lang="ts">
import { computed } from 'vue';
import { SunIcon, CloudIcon, ArrowRightIcon } from '@heroicons/vue/24/outline';

const props = defineProps<{
  city?: string;
  temperature?: number;
  condition?: 'sunny' | 'cloudy' | 'rainy';
}>();

const iconComponent = computed(() => {
  if (props.condition === 'cloudy') return CloudIcon;
  if (props.condition === 'rainy') return ArrowRightIcon;
  return SunIcon;
});

const conditionLabel = computed(() => {
  switch (props.condition) {
    case 'cloudy':
      return '多云';
    case 'rainy':
      return '降雨';
    default:
      return '晴朗';
  }
});
</script>

<template>
  <aside class="weather-widget">
    <div class="widget-header">
      <span class="badge">今日灵感</span>
      <span class="location">{{ props.city ?? '海岛度假村' }}</span>
    </div>

    <div class="widget-body">
      <component :is="iconComponent" class="h-10 w-10 text-amber-400 drop-shadow" />
      <div>
        <p class="temp">{{ props.temperature ?? 26 }}°C</p>
        <p class="condition">{{ conditionLabel }}</p>
      </div>
    </div>

    <div class="widget-footer">
      <span>适合漫步海滩与水上活动</span>
      <button type="button" class="cta">探索当地体验</button>
    </div>
  </aside>
</template>

<style scoped>
.weather-widget {
  @apply bg-white/15 backdrop-blur-xl rounded-3xl border border-white/30 shadow-2xl px-6 py-6 space-y-4;
}
.widget-header {
  @apply flex items-center justify-between text-sm;
}
.badge {
  @apply px-2.5 py-1 rounded-full bg-gradient-to-r from-cyan-500/40 to-teal-500/40 text-white font-medium shadow-lg border border-white/20;
}
.location {
  @apply font-medium text-white drop-shadow;
}
.widget-body {
  @apply flex items-center gap-4;
}
.temp {
  @apply text-3xl font-semibold text-white drop-shadow-lg;
}
.condition {
  @apply text-sm text-white/80;
}
.widget-footer {
  @apply text-xs text-white/70 flex items-center justify-between;
}
.cta {
  @apply inline-flex items-center justify-center px-3 py-1.5 rounded-full bg-gradient-to-r from-emerald-500/80 to-cyan-500/80 text-white text-xs font-semibold shadow-lg border border-white/20 transition hover:shadow-xl;
}
</style>
