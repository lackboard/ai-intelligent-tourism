<script setup lang="ts">
import { computed, ref } from 'vue';
import { MapPinIcon, ChevronDownIcon } from '@heroicons/vue/24/outline';
import type { ItineraryResponse } from '@/types/chat';

const props = defineProps<{ itinerary: ItineraryResponse }>();

const openDays = ref(new Set<number>(props.itinerary.days.map((d) => d.day)));
const toggleDay = (day: number) => {
  const next = new Set(openDays.value);
  if (next.has(day)) {
    next.delete(day);
  } else {
    next.add(day);
  }
  openDays.value = next;
};

const budgetLabel = computed(() => `￥${props.itinerary.totalBudget.toLocaleString()}`);

// 根据活动内容智能匹配图标
const getActivityIcon = (activity: string): string => {
  const lower = activity.toLowerCase();
  if (lower.includes('早') || lower.includes('上午')) return '🌅';
  if (lower.includes('下午')) return '☀️';
  if (lower.includes('晚上')) return '🌙';
  if (lower.includes('餐') || lower.includes('吃') || lower.includes('小吃') || lower.includes('美食')) return '🍽️';
  if (lower.includes('博物') || lower.includes('展览')) return '🏛️';
  if (lower.includes('寺') || lower.includes('庙') || lower.includes('佛')) return '🛕';
  if (lower.includes('山') || lower.includes('峰') || lower.includes('登')) return '⛰️';
  if (lower.includes('古城') || lower.includes('古镇') || lower.includes('老城')) return '🏯';
  if (lower.includes('公园') || lower.includes('花') || lower.includes('园')) return '🌸';
  if (lower.includes('石窟') || lower.includes('遗址') || lower.includes('遗产')) return '🏛️';
  return '📍';
};
</script>

<template>
  <article class="bg-white/95 backdrop-blur-md text-slate-900 rounded-2xl shadow-xl overflow-hidden w-full max-w-full">
    <header class="bg-gradient-to-r from-teal-500 to-emerald-500 px-4 sm:px-6 py-4 sm:py-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 text-white">
      <div>
        <p class="text-xs sm:text-sm uppercase tracking-wide text-white/80">行程方案</p>
        <h3 class="text-xl sm:text-2xl font-semibold leading-tight">{{ itinerary.title }}</h3>
      </div>
      <div class="sm:text-right">
        <p class="text-xs text-white/80">总预算</p>
        <p class="text-xl sm:text-2xl font-bold">{{ budgetLabel }}</p>
      </div>
    </header>

    <div class="px-4 sm:px-6 py-4 sm:py-6 space-y-4 sm:space-y-6">
      <div class="relative pl-4 sm:pl-6 border-l-2 border-slate-200 space-y-4">
        <div
          v-for="day in itinerary.days"
          :key="day.day"
          class="relative"
        >
          <div class="absolute -left-[9px] sm:-left-[11px] top-2 h-4 w-4 sm:h-5 sm:w-5 rounded-full bg-white border-2 border-emerald-500 shadow-sm" />

          <button
            type="button"
            class="w-full flex items-center justify-between rounded-xl bg-slate-100 px-3 sm:px-4 py-2 sm:py-3 text-left transition hover:bg-slate-200"
            @click="toggleDay(day.day)"
          >
            <div class="flex items-center gap-2 sm:gap-3 flex-wrap">
              <span class="text-xs sm:text-sm font-semibold text-emerald-600">Day {{ day.day }}</span>
              <span class="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 sm:py-1 text-xs text-emerald-700">
                <MapPinIcon class="h-3 w-3 sm:h-4 sm:w-4" />
                {{ day.city }}
              </span>
            </div>
            <ChevronDownIcon
              class="h-4 w-4 sm:h-5 sm:w-5 text-slate-500 transition-transform flex-shrink-0"
              :class="{ 'rotate-180': openDays.has(day.day) }"
            />
          </button>

          <div
            class="overflow-hidden transition-all duration-300 ease-in-out"
            :class="openDays.has(day.day) ? 'max-h-[1200px] opacity-100 py-3 sm:py-4' : 'max-h-0 opacity-0'"
          >
            <ul class="space-y-2 sm:space-y-3">
              <li
                v-for="(activity, idx) in day.activities"
                :key="idx"
                class="flex items-start gap-2 sm:gap-3 rounded-lg bg-white border border-slate-100 px-3 sm:px-4 py-2 sm:py-3 shadow-sm"
              >
                <span class="text-base sm:text-lg flex-shrink-0">{{ getActivityIcon(activity) }}</span>
                <p class="text-sm sm:text-base text-slate-700 leading-relaxed break-words">{{ activity }}</p>
              </li>
            </ul>

            <!-- 每天的备注 -->
            <div v-if="day.note" class="mt-3 sm:mt-4 bg-amber-50 text-amber-800 border-l-4 border-amber-400 rounded-lg px-3 sm:px-4 py-2 sm:py-3">
              <p class="text-xs font-semibold flex items-center gap-1">
                <span>💡</span> Day {{ day.day }} 小贴士
              </p>
              <p class="text-xs sm:text-sm leading-relaxed mt-1 break-words">{{ day.note }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </article>
</template>
