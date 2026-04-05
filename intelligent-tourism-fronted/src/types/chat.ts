export type ChatMode = 'sse' | 'agent';

// 活动类型枚举
export type ActivityType = '景点' | '美食' | '住宿' | '交通' | '购物' | '活动' | '其他' | '娱乐' | '参观';

// 单个活动项
export interface ActivityItem {
  time: string;        // e.g. "上午" 或 "09:00 - 11:30"
  location: string;    // e.g. "故宫博物院"
  description: string; // e.g. "建议提前7天官网预约，必看珍宝馆"
  type: ActivityType;  // 用于前端显示不同图标
  cost: number;        // e.g. 60.0 (门票)
}

export interface DailyPlan {
  day: number;
  city: string;
  activities: ActivityItem[];
  note: string;
}

export interface ItineraryResponse {
  title: string;
  totalBudget: number;
  days: DailyPlan[];
}

export type ChatPayload =
  | { type: 'text'; data: string }
  | { type: 'error'; data: string }
  | { type: 'card'; data: ItineraryResponse };

export type Sender = 'user' | 'ai';

export interface ChatMessage {
  id: string;
  sender: Sender;
  payload: ChatPayload;
  streaming?: boolean;
}
