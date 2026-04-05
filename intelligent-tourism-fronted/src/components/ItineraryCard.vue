<script setup lang="ts">
import { computed, ref } from 'vue';
import { MapPinIcon, ChevronDownIcon, ClockIcon, LightBulbIcon, BookmarkIcon, ShareIcon, CheckIcon } from '@heroicons/vue/24/outline';
import { BookmarkIcon as BookmarkSolidIcon } from '@heroicons/vue/24/solid';
import type { ItineraryResponse, ActivityItem, ActivityType } from '@/types/chat';
import { useItineraryStore } from '@/stores/itinerary';

const props = defineProps<{ itinerary: ItineraryResponse }>();

const itineraryStore = useItineraryStore();

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

// 是否已保存
const isSaved = computed(() => itineraryStore.isSaved(props.itinerary));

// 保存状态提示
const saveMessage = ref('');
const showSaveMessage = ref(false);

// 分享状态
const shareMessage = ref('');
const showShareMessage = ref(false);

// 保存行程
const handleSave = () => {
  if (isSaved.value) {
    saveMessage.value = '该行程已保存';
  } else {
    itineraryStore.saveItinerary(props.itinerary);
    saveMessage.value = '保存成功！';
  }
  showSaveMessage.value = true;
  setTimeout(() => {
    showSaveMessage.value = false;
  }, 2000);
};

// 分享行程
const handleShare = async () => {
  // 生成分享文本
  const shareText = generateShareText();
  
  try {
    await navigator.clipboard.writeText(shareText);
    shareMessage.value = '已复制到剪贴板';
  } catch {
    shareMessage.value = '复制失败，请手动复制';
  }
  showShareMessage.value = true;
  setTimeout(() => {
    showShareMessage.value = false;
  }, 2000);
};

// 生成分享文本
const generateShareText = () => {
  const { title, totalBudget, days } = props.itinerary;
  let text = `🗺️ ${title}\n`;
  text += `💰 预算: ￥${totalBudget.toLocaleString()}\n\n`;
  
  days.forEach((day) => {
    text += `📅 Day ${day.day} - ${day.city}\n`;
    day.activities.forEach((a) => {
      text += `  • ${a.time} ${a.location}`;
      if (a.cost > 0) text += ` (￥${a.cost})`;
      text += `\n`;
    });
    if (day.note) {
      text += `  💡 ${day.note}\n`;
    }
    text += `\n`;
  });
  
  text += `—— 由 AI 旅游规划助手生成`;
  return text;
};

// 根据活动类型获取图标和颜色配置
const getActivityStyle = (type: ActivityType) => {
  const styles: Record<ActivityType, { icon: string; nodeColor: string; iconBg: string }> = {
    '景点': {
      icon: '⛰️',
      nodeColor: 'bg-emerald-400',
      iconBg: 'bg-emerald-50 text-emerald-500'
    },
    '美食': {
      icon: '🍜',
      nodeColor: 'bg-orange-400',
      iconBg: 'bg-orange-50 text-orange-500'
    },
    '住宿': {
      icon: '🛏️',
      nodeColor: 'bg-violet-400',
      iconBg: 'bg-violet-50 text-violet-500'
    },
    '交通': {
      icon: '🚄',
      nodeColor: 'bg-sky-400',
      iconBg: 'bg-sky-50 text-sky-500'
    },
    '购物': {
      icon: '🛍️',
      nodeColor: 'bg-pink-400',
      iconBg: 'bg-pink-50 text-pink-500'
    },
    '活动': {
      icon: '🎯',
      nodeColor: 'bg-indigo-400',
      iconBg: 'bg-indigo-50 text-indigo-500'
    },
    '娱乐': {
      icon: '🎭',
      nodeColor: 'bg-rose-400',
      iconBg: 'bg-rose-50 text-rose-500'
    },
    '参观': {
      icon: '🏛️',
      nodeColor: 'bg-teal-400',
      iconBg: 'bg-teal-50 text-teal-500'
    },
    '其他': {
      icon: '📍',
      nodeColor: 'bg-lime-500',
      iconBg: 'bg-lime-50 text-lime-500'
    }
  };
  return styles[type] || styles['其他'];
};

// 格式化 Day 数字，补零
const formatDayNumber = (day: number) => {
  return day.toString().padStart(2, '0');
};

// 计算每天的花费
const getDayBudget = (activities: ActivityItem[]) => {
  return activities.reduce((sum, a) => sum + (a.cost || 0), 0);
};
</script>

<template>
  <article class="bg-white/95 backdrop-blur-md text-slate-900 rounded-2xl shadow-xl overflow-hidden w-full max-w-full">
    
    <!-- ========== 1. 杂志封面风格头部 ========== -->
    <header class="relative overflow-hidden">
      <!-- 渐变背景 -->
      <div class="absolute inset-0 bg-gradient-to-r from-emerald-500 to-teal-600"></div>
      
      <!-- 波浪纹理叠加层 -->
      <div class="absolute inset-0 opacity-10">
        <svg class="w-full h-full" viewBox="0 0 1440 320" preserveAspectRatio="none">
          <path fill="white" d="M0,160L48,170.7C96,181,192,203,288,192C384,181,480,139,576,128C672,117,768,139,864,165.3C960,192,1056,224,1152,218.7C1248,213,1344,171,1392,149.3L1440,128L1440,320L1392,320C1344,320,1248,320,1152,320C1056,320,960,320,864,320C768,320,672,320,576,320C480,320,384,320,288,320C192,320,96,320,48,320L0,320Z"></path>
        </svg>
      </div>
      
      <!-- 地图矢量点缀 -->
      <div class="absolute right-0 top-0 w-48 h-48 opacity-5">
        <svg viewBox="0 0 100 100" fill="white">
          <circle cx="20" cy="20" r="3"/>
          <circle cx="50" cy="30" r="2"/>
          <circle cx="80" cy="25" r="3"/>
          <circle cx="35" cy="60" r="2"/>
          <circle cx="65" cy="70" r="3"/>
          <circle cx="25" cy="85" r="2"/>
          <circle cx="75" cy="85" r="2"/>
          <path d="M20,20 L50,30 L80,25 M50,30 L35,60 L65,70 M35,60 L25,85 M65,70 L75,85" stroke="white" stroke-width="0.5" fill="none"/>
        </svg>
      </div>
      
      <!-- 头部内容 -->
      <div class="relative px-5 sm:px-8 py-6 sm:py-8">
        <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <!-- 左侧标题区 -->
          <div class="space-y-1">
            <h3 class="text-2xl sm:text-3xl font-bold text-white leading-tight tracking-tight">
              {{ itinerary.title }}
            </h3>
            <p class="text-sm text-white/60">
              {{ itinerary.days.length }} 天精心规划 · 探索无限精彩
            </p>
          </div>
          
          <!-- 右侧预算胶囊 -->
          <div class="inline-flex items-center gap-2 bg-white/20 backdrop-blur-sm rounded-full px-4 py-2 self-start sm:self-auto">
            <span class="text-xs text-white/80">预计花费</span>
            <span class="text-xl sm:text-2xl font-bold text-white">{{ budgetLabel }}</span>
          </div>
        </div>
        
        <!-- 操作按钮组 -->
        <div class="flex items-center gap-2 mt-4">
          <!-- 保存按钮 -->
          <button
            @click="handleSave"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all duration-300"
            :class="isSaved 
              ? 'bg-white text-emerald-600' 
              : 'bg-white/20 text-white hover:bg-white/30'"
          >
            <BookmarkSolidIcon v-if="isSaved" class="h-4 w-4" />
            <BookmarkIcon v-else class="h-4 w-4" />
            {{ isSaved ? '已保存' : '保存行程' }}
          </button>
          
          <!-- 分享按钮 -->
          <button
            @click="handleShare"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium bg-white/20 text-white hover:bg-white/30 transition-all duration-300"
          >
            <ShareIcon class="h-4 w-4" />
            分享
          </button>
          
          <!-- 保存提示 -->
          <Transition name="fade">
            <span 
              v-if="showSaveMessage" 
              class="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs bg-white text-emerald-600"
            >
              <CheckIcon class="h-3.5 w-3.5" />
              {{ saveMessage }}
            </span>
          </Transition>
          
          <!-- 分享提示 -->
          <Transition name="fade">
            <span 
              v-if="showShareMessage" 
              class="inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs bg-white text-emerald-600"
            >
              <CheckIcon class="h-3.5 w-3.5" />
              {{ shareMessage }}
            </span>
          </Transition>
        </div>
      </div>
    </header>

    <!-- ========== 2. 垂直时光轴主体 ========== -->
    <div class="px-4 sm:px-6 py-6 sm:py-8 space-y-8">
      <div
        v-for="day in itinerary.days"
        :key="day.day"
        class="relative"
      >
        <!-- Day Header: 水印风格大数字 -->
        <div 
          class="flex items-center gap-4 cursor-pointer select-none group"
          @click="toggleDay(day.day)"
        >
          <!-- 巨大水印数字 -->
          <div class="relative flex-shrink-0 w-20 sm:w-24">
            <span class="text-5xl sm:text-6xl font-black text-slate-300 leading-none drop-shadow-sm">
              {{ formatDayNumber(day.day) }}
            </span>
            <span class="absolute top-0 left-0 text-xs font-bold text-emerald-600 uppercase tracking-widest">
              Day
            </span>
          </div>
          
          <!-- 城市信息 -->
          <div class="flex-1 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <MapPinIcon class="h-5 w-5 text-emerald-500" />
              <span class="text-lg font-semibold text-slate-800">{{ day.city }}</span>
              <span class="text-xs text-slate-400 hidden sm:inline">
                · {{ day.activities.length }} 个活动
                · ￥{{ getDayBudget(day.activities).toLocaleString() }}
              </span>
            </div>
            <ChevronDownIcon
              class="h-5 w-5 text-slate-400 transition-transform duration-300"
              :class="{ 'rotate-180': openDays.has(day.day) }"
            />
          </div>
        </div>

        <!-- 时光轴内容区 -->
        <div
          class="overflow-hidden transition-all duration-500 ease-out"
          :class="openDays.has(day.day) ? 'max-h-[3000px] opacity-100 mt-6' : 'max-h-0 opacity-0 mt-0'"
        >
          <!-- 虚线时光轴 -->
          <div class="relative ml-10 sm:ml-12 pl-8 border-l-2 border-dashed border-slate-200">
            <div
              v-for="(activity, idx) in day.activities"
              :key="idx"
              class="relative pb-6 last:pb-0 group/activity"
            >
              <!-- 时光轴节点 -->
              <div 
                class="absolute -left-[25px] top-1 w-4 h-4 rounded-full border-[3px] border-white shadow-md transition-all duration-300 group-hover/activity:scale-125 group-hover/activity:shadow-lg"
                :class="getActivityStyle(activity.type).nodeColor"
              ></div>
              
              <!-- 活动卡片 -->
              <div class="bg-white shadow-sm hover:shadow-md rounded-xl p-4 transition-all duration-300 group-hover/activity:-translate-y-0.5 border border-slate-100">
                <!-- 第一行：时间 + 地点 -->
                <div class="flex items-start justify-between gap-3 mb-2">
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 text-xs text-slate-400 mb-1">
                      <ClockIcon class="h-3.5 w-3.5" />
                      <span>{{ activity.time }}</span>
                    </div>
                    <div class="flex items-center gap-2">
                      <span 
                        class="flex-shrink-0 w-7 h-7 rounded-lg flex items-center justify-center text-sm"
                        :class="getActivityStyle(activity.type).iconBg"
                      >
                        {{ getActivityStyle(activity.type).icon }}
                      </span>
                      <h4 class="text-base font-bold text-slate-800 truncate">
                        {{ activity.location }}
                      </h4>
                    </div>
                  </div>
                  
                  <!-- 价格标签 -->
                  <div v-if="activity.cost > 0" class="flex-shrink-0">
                    <span class="inline-flex items-center px-2 py-1 rounded-md bg-amber-50 text-amber-600 text-xs font-medium">
                      ￥{{ activity.cost }}
                    </span>
                  </div>
                </div>
                
                <!-- 第二行：描述 -->
                <p class="text-sm text-slate-500 leading-relaxed pl-9">
                  {{ activity.description }}
                </p>
              </div>
            </div>
          </div>

          <!-- ========== 3. 便利贴风格贴士 ========== -->
          <div v-if="day.note" class="ml-10 sm:ml-12 mt-4">
            <div class="relative bg-amber-50/80 backdrop-blur-sm rounded-xl px-4 py-3 border border-amber-100/50 shadow-sm">
              <!-- 便利贴折角效果 -->
              <div class="absolute -top-1 -right-1 w-6 h-6 bg-amber-100 rounded-bl-lg shadow-sm transform rotate-6"></div>
              
              <div class="flex items-start gap-3">
                <div class="flex-shrink-0 w-8 h-8 rounded-full bg-amber-100 flex items-center justify-center">
                  <LightBulbIcon class="h-4 w-4 text-amber-600" />
                </div>
                <div class="flex-1 min-w-0">
                  <p class="text-xs font-semibold text-amber-700 mb-0.5">Day {{ day.day }} 小贴士</p>
                  <p class="text-sm text-amber-700/80 leading-relaxed">{{ day.note }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部装饰线 -->
    <div class="h-1 bg-gradient-to-r from-emerald-500 to-teal-600"></div>
  </article>
</template>

<style scoped>
/* 悬停时节点颜色增强 */
.group\/activity:hover .bg-emerald-500 {
  @apply bg-emerald-600 ring-2 ring-emerald-200;
}
.group\/activity:hover .bg-orange-500 {
  @apply bg-orange-600 ring-2 ring-orange-200;
}
.group\/activity:hover .bg-violet-500 {
  @apply bg-violet-600 ring-2 ring-violet-200;
}
.group\/activity:hover .bg-sky-500 {
  @apply bg-sky-600 ring-2 ring-sky-200;
}

/* 提示动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
