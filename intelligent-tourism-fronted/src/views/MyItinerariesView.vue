<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { 
  TrashIcon, 
  BookmarkIcon,
  ShareIcon,
  MagnifyingGlassIcon,
  CalendarDaysIcon,
  MapPinIcon,
  CurrencyYenIcon,
  HomeIcon
} from '@heroicons/vue/24/outline';
import { useItineraryStore } from '@/stores/itinerary';
import ItineraryCard from '@/components/ItineraryCard.vue';
import ParticleBackground from '@/components/particles/ParticleBackground.vue';
import MouseFollowParticles from '@/components/particles/MouseFollowParticles.vue';
import { useRandomBackground } from '@/composables/useRandomBackground';
import type { SavedItinerary } from '@/stores/itinerary';

const router = useRouter();
const itineraryStore = useItineraryStore();
const { backgroundUrl, isLoading: bgLoading } = useRandomBackground();

// 搜索关键词
const searchQuery = ref('');

// 当前选中查看的行程
const selectedItinerary = ref<SavedItinerary | null>(null);

// 过滤后的行程列表
const filteredItineraries = computed(() => {
  const query = searchQuery.value.toLowerCase().trim();
  if (!query) return itineraryStore.sortedItineraries;
  
  return itineraryStore.sortedItineraries.filter((item) => {
    const { title, days } = item.itinerary;
    // 搜索标题
    if (title.toLowerCase().includes(query)) return true;
    // 搜索城市
    if (days.some((d) => d.city.toLowerCase().includes(query))) return true;
    // 搜索活动地点
    if (days.some((d) => d.activities.some((a) => a.location.toLowerCase().includes(query)))) return true;
    return false;
  });
});

// 格式化日期
const formatDate = (timestamp: number) => {
  const date = new Date(timestamp);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
};

// 获取行程涉及的城市
const getCities = (itinerary: SavedItinerary) => {
  const cities = [...new Set(itinerary.itinerary.days.map((d) => d.city))];
  return cities.slice(0, 3).join(' → ') + (cities.length > 3 ? '...' : '');
};

// 删除行程
const handleDelete = (id: string, event: Event) => {
  event.stopPropagation();
  if (confirm('确定要删除这个行程吗？')) {
    itineraryStore.removeItinerary(id);
    if (selectedItinerary.value?.id === id) {
      selectedItinerary.value = null;
    }
  }
};

// 分享行程
const handleShare = async (item: SavedItinerary, event: Event) => {
  event.stopPropagation();
  const { title, totalBudget, days } = item.itinerary;
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
  
  try {
    await navigator.clipboard.writeText(text);
    alert('行程已复制到剪贴板！');
  } catch {
    alert('复制失败，请手动复制');
  }
};

// 返回首页
const goBack = () => {
  router.push('/');
};
</script>

<template>
  <div class="relative min-h-screen">
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
    </div>

    <!-- 顶部导航栏 -->
    <header class="fixed top-0 inset-x-0 z-50 backdrop-blur-xl bg-white/10 border-b border-white/20">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-4">
        <div class="flex items-center justify-between gap-4">
          <!-- 返回按钮 + 标题 -->
          <div class="flex items-center gap-3">
            <button
              @click="goBack"
              class="inline-flex items-center justify-center h-9 w-9 rounded-full bg-white/20 backdrop-blur-md border border-white/30 text-white transition hover:bg-white/30 hover:scale-110 shadow-lg"
              title="返回首页"
            >
              <HomeIcon class="h-5 w-5" />
            </button>
            <div>
              <h1 class="text-xl sm:text-2xl font-bold text-white flex items-center gap-2 drop-shadow-lg">
                <BookmarkIcon class="h-6 w-6 text-emerald-300" />
                我的行程
              </h1>
              <p class="text-xs text-white/70 mt-0.5">
                共 {{ itineraryStore.savedItineraries.length }} 个已保存行程
              </p>
            </div>
          </div>
          
          <!-- 搜索框 -->
          <div class="relative flex-1 max-w-md hidden sm:block">
            <MagnifyingGlassIcon class="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-white/60" />
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索行程、城市或景点..."
              class="w-full pl-10 pr-4 py-2.5 rounded-xl border border-white/30 bg-white/15 backdrop-blur-md text-white text-sm placeholder:text-white/50 focus:outline-none focus:ring-2 focus:ring-white/30 focus:border-white/50 transition-all"
            />
          </div>
        </div>
        
        <!-- 移动端搜索框 -->
        <div class="relative mt-3 sm:hidden">
          <MagnifyingGlassIcon class="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-white/60" />
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索..."
            class="w-full pl-10 pr-4 py-2.5 rounded-xl border border-white/30 bg-white/15 backdrop-blur-md text-white text-sm placeholder:text-white/50 focus:outline-none focus:ring-2 focus:ring-white/30 focus:border-white/50 transition-all"
          />
        </div>
      </div>
    </header>

    <main class="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 pt-40 sm:pt-32 lg:pt-32">
      <!-- 空状态 -->
      <div 
        v-if="itineraryStore.savedItineraries.length === 0" 
        class="flex flex-col items-center justify-center py-20 text-center"
      >
        <div class="w-24 h-24 rounded-full bg-white/20 backdrop-blur-md flex items-center justify-center mb-6">
          <BookmarkIcon class="h-12 w-12 text-white" />
        </div>
        <h2 class="text-xl font-semibold text-white mb-2 drop-shadow-lg">还没有保存的行程</h2>
        <p class="text-white/80 mb-6 max-w-sm">
          在聊天中生成行程后，点击「保存行程」按钮即可收藏到这里
        </p>
        <button
          @click="router.push('/chat')"
          class="px-6 py-3 rounded-xl bg-white/20 backdrop-blur-md border border-white/30 text-white font-medium shadow-lg hover:bg-white/30 hover:-translate-y-0.5 transition-all"
        >
          开始规划行程
        </button>
      </div>

      <!-- 搜索无结果 -->
      <div 
        v-else-if="filteredItineraries.length === 0 && searchQuery" 
        class="flex flex-col items-center justify-center py-20 text-center"
      >
        <MagnifyingGlassIcon class="h-16 w-16 text-white/50 mb-4" />
        <h2 class="text-lg font-medium text-white mb-2 drop-shadow">未找到相关行程</h2>
        <p class="text-white/70">试试其他关键词？</p>
      </div>

      <!-- 行程列表 + 详情双栏布局 -->
      <div v-else class="flex flex-col lg:flex-row gap-6">
        <!-- 左侧列表 - PC端固定 -->
        <div class="w-full lg:w-2/5 lg:sticky lg:top-32 lg:self-start lg:max-h-[calc(100vh-8rem)] lg:overflow-y-auto space-y-4 scrollbar-thin pr-1">
          <div
            v-for="item in filteredItineraries"
            :key="item.id"
            @click="selectedItinerary = item"
            class="bg-white/90 backdrop-blur-md rounded-2xl border border-white/30 p-4 sm:p-5 cursor-pointer transition-all duration-300 hover:shadow-xl hover:-translate-y-1 hover:bg-white shadow-md"
            :class="{ 'ring-2 ring-emerald-400 shadow-xl bg-white': selectedItinerary?.id === item.id }"
          >
            <div class="flex items-start justify-between gap-3 mb-3">
              <h3 class="text-lg font-bold text-slate-800 leading-snug line-clamp-2">
                {{ item.itinerary.title }}
              </h3>
              <div class="flex-shrink-0 flex items-center gap-1">
                <button
                  @click="handleShare(item, $event)"
                  class="p-1.5 rounded-lg hover:bg-emerald-50 text-slate-400 hover:text-emerald-500 transition-colors"
                  title="分享"
                >
                  <ShareIcon class="h-4 w-4" />
                </button>
                <button
                  @click="handleDelete(item.id, $event)"
                  class="p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-500 transition-colors"
                  title="删除"
                >
                  <TrashIcon class="h-4 w-4" />
                </button>
              </div>
            </div>
            
            <div class="flex flex-wrap items-center gap-3 text-xs text-slate-500 mb-3">
              <span class="inline-flex items-center gap-1">
                <CalendarDaysIcon class="h-3.5 w-3.5" />
                {{ item.itinerary.days.length }} 天
              </span>
              <span class="inline-flex items-center gap-1">
                <MapPinIcon class="h-3.5 w-3.5" />
                {{ getCities(item) }}
              </span>
              <span class="inline-flex items-center gap-1 text-amber-600 font-medium">
                <CurrencyYenIcon class="h-3.5 w-3.5" />
                ￥{{ item.itinerary.totalBudget.toLocaleString() }}
              </span>
            </div>
            
            <div class="flex items-center justify-between text-xs">
              <span class="text-slate-400">
                保存于 {{ formatDate(item.savedAt) }}
              </span>
              <span v-if="item.note" class="text-emerald-600 bg-emerald-50 px-2 py-0.5 rounded-full">
                {{ item.note }}
              </span>
            </div>
          </div>
        </div>

        <!-- 右侧详情预览 -->
        <div class="lg:w-3/5">
          <div v-if="selectedItinerary" class="relative">
            <!-- 关闭按钮 -->
            <button
              @click="selectedItinerary = null"
              class="absolute -top-2 -right-2 z-10 w-8 h-8 rounded-full bg-white/90 backdrop-blur-md shadow-lg border border-white/50 flex items-center justify-center text-slate-500 hover:text-slate-700 hover:bg-white transition-all hover:scale-110"
              title="关闭"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
              </svg>
            </button>
            <ItineraryCard :itinerary="selectedItinerary.itinerary" />
          </div>
          <div 
            v-else 
            class="hidden lg:flex flex-col items-center justify-center h-96 bg-white/20 backdrop-blur-md rounded-2xl border-2 border-dashed border-white/30"
          >
            <MapPinIcon class="h-16 w-16 text-white/50 mb-4" />
            <p class="text-white/70 drop-shadow">点击左侧行程卡片查看详情</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
/* Ken Burns 缩放动画 */
@keyframes kenburns {
  0% {
    transform: scale(1) translate(0, 0);
  }
  50% {
    transform: scale(1.1) translate(-2%, -1%);
  }
  100% {
    transform: scale(1) translate(0, 0);
  }
}

.animate-kenburns {
  animation: kenburns 30s ease-in-out infinite;
}

/* 浮动光效动画 */
@keyframes float-slow {
  0%, 100% { transform: translateY(0) translateX(0); }
  50% { transform: translateY(-20px) translateX(10px); }
}
@keyframes float-slower {
  0%, 100% { transform: translateY(0) translateX(0); }
  50% { transform: translateY(15px) translateX(-15px); }
}
@keyframes float-medium {
  0%, 100% { transform: translateY(0) translateX(0); }
  50% { transform: translateY(-15px) translateX(-10px); }
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

/* 自定义滚动条 */
.scrollbar-thin::-webkit-scrollbar {
  width: 6px;
}
.scrollbar-thin::-webkit-scrollbar-track {
  background: transparent;
}
.scrollbar-thin::-webkit-scrollbar-thumb {
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}
.scrollbar-thin::-webkit-scrollbar-thumb:hover {
  background-color: rgba(255, 255, 255, 0.5);
}
</style>
