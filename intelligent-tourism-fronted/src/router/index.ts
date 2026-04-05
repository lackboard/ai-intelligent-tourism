import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '@/views/HomeView.vue';
import ChatView from '@/views/ChatView.vue';
import MyItinerariesView from '@/views/MyItinerariesView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/chat',
      name: 'chat',
      component: ChatView,
    },
    {
      path: '/my-itineraries',
      name: 'my-itineraries',
      component: MyItinerariesView,
    },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

export default router;
