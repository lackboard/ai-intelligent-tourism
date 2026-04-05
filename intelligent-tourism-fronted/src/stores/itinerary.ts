import { defineStore } from 'pinia';
import type { ItineraryResponse } from '@/types/chat';

export interface SavedItinerary {
  id: string;
  itinerary: ItineraryResponse;
  savedAt: number; // 时间戳
  note?: string;   // 用户备注
}

const STORAGE_KEY = 'saved_itineraries';

// 从 localStorage 读取
const loadFromStorage = (): SavedItinerary[] => {
  try {
    const data = localStorage.getItem(STORAGE_KEY);
    return data ? JSON.parse(data) : [];
  } catch {
    return [];
  }
};

// 保存到 localStorage
const saveToStorage = (itineraries: SavedItinerary[]) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(itineraries));
};

export const useItineraryStore = defineStore('itinerary', {
  state: () => ({
    savedItineraries: loadFromStorage() as SavedItinerary[],
  }),

  getters: {
    // 按保存时间倒序
    sortedItineraries: (state) => {
      return [...state.savedItineraries].sort((a, b) => b.savedAt - a.savedAt);
    },
    // 检查是否已保存
    isSaved: (state) => (itinerary: ItineraryResponse) => {
      return state.savedItineraries.some(
        (s) => s.itinerary.title === itinerary.title && s.itinerary.totalBudget === itinerary.totalBudget
      );
    },
  },

  actions: {
    // 保存行程
    saveItinerary(itinerary: ItineraryResponse, note?: string) {
      const id = crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2);
      const saved: SavedItinerary = {
        id,
        itinerary,
        savedAt: Date.now(),
        note,
      };
      this.savedItineraries.push(saved);
      saveToStorage(this.savedItineraries);
      return id;
    },

    // 删除行程
    removeItinerary(id: string) {
      const idx = this.savedItineraries.findIndex((s) => s.id === id);
      if (idx !== -1) {
        this.savedItineraries.splice(idx, 1);
        saveToStorage(this.savedItineraries);
      }
    },

    // 更新备注
    updateNote(id: string, note: string) {
      const item = this.savedItineraries.find((s) => s.id === id);
      if (item) {
        item.note = note;
        saveToStorage(this.savedItineraries);
      }
    },

    // 生成分享数据（Base64编码）
    generateShareData(id: string): string | null {
      const item = this.savedItineraries.find((s) => s.id === id);
      if (!item) return null;
      const json = JSON.stringify(item.itinerary);
      return btoa(encodeURIComponent(json));
    },

    // 从分享数据导入
    importFromShareData(shareData: string): string | null {
      try {
        const json = decodeURIComponent(atob(shareData));
        const itinerary: ItineraryResponse = JSON.parse(json);
        // 检查是否已存在
        if (this.isSaved(itinerary)) {
          return null;
        }
        return this.saveItinerary(itinerary, '从分享链接导入');
      } catch {
        return null;
      }
    },
  },
});
