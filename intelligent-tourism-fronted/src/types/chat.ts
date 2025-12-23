export type ChatMode = 'sse' | 'agent';

export interface DailyPlan {
  day: number;
  city: string;
  activities: string[];
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
