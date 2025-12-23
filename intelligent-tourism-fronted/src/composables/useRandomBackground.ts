import { ref, onMounted, computed } from 'vue';

// PC端背景图列表
const pcBackgrounds = [
  () => import('@/assets/images/pcbackground/1.jpg'),
  () => import('@/assets/images/pcbackground/2.jpg'),
  () => import('@/assets/images/pcbackground/3.jpg'),
  () => import('@/assets/images/pcbackground/4.jpg'),
  () => import('@/assets/images/pcbackground/5.jpg'),
  () => import('@/assets/images/pcbackground/6.jpg'),
  () => import('@/assets/images/pcbackground/7.jpg'),
  () => import('@/assets/images/pcbackground/8.jpg'),
];

// 移动端背景图列表（只包含常见格式，HEIC 在网页中兼容性差）
const mobileBackgrounds = [
  () => import('@/assets/images/mobilebackground/1.jpg'),
  () => import('@/assets/images/mobilebackground/2.jpg'),
  () => import('@/assets/images/mobilebackground/3.jpg'),
  () => import('@/assets/images/mobilebackground/4.jpg'),
  () => import('@/assets/images/mobilebackground/5.jpeg'),
  () => import('@/assets/images/mobilebackground/7.png'),
  () => import('@/assets/images/mobilebackground/8.jpg'),
];

// 检测是否为移动设备
const isMobileDevice = (): boolean => {
  if (typeof window === 'undefined') return false;
  
  // 检测触摸设备和屏幕宽度
  const isTouchDevice = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
  const isNarrowScreen = window.innerWidth < 768;
  
  // 检测 User Agent
  const mobileRegex = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i;
  const isMobileUA = mobileRegex.test(navigator.userAgent);
  
  return (isTouchDevice && isNarrowScreen) || isMobileUA;
};

// 随机选择一个索引
const getRandomIndex = (max: number): number => {
  return Math.floor(Math.random() * max);
};

export function useRandomBackground() {
  const backgroundUrl = ref<string>('');
  const isMobile = ref(false);
  const isLoading = ref(true);

  const loadBackground = async () => {
    isLoading.value = true;
    isMobile.value = isMobileDevice();
    
    const backgrounds = isMobile.value ? mobileBackgrounds : pcBackgrounds;
    const randomIndex = getRandomIndex(backgrounds.length);
    
    try {
      const module = await backgrounds[randomIndex]();
      backgroundUrl.value = module.default;
    } catch (error) {
      console.error('Failed to load background image:', error);
      // 加载失败时使用默认背景
      try {
        const fallback = await pcBackgrounds[0]();
        backgroundUrl.value = fallback.default;
      } catch {
        backgroundUrl.value = '';
      }
    } finally {
      isLoading.value = false;
    }
  };

  onMounted(() => {
    loadBackground();
    
    // 监听窗口大小变化，可能需要切换背景
    let resizeTimeout: ReturnType<typeof setTimeout>;
    const handleResize = () => {
      clearTimeout(resizeTimeout);
      resizeTimeout = setTimeout(() => {
        const newIsMobile = isMobileDevice();
        // 只有当设备类型发生变化时才重新加载
        if (newIsMobile !== isMobile.value) {
          loadBackground();
        }
      }, 300);
    };
    
    window.addEventListener('resize', handleResize);
    
    // 清理
    return () => {
      window.removeEventListener('resize', handleResize);
      clearTimeout(resizeTimeout);
    };
  });

  return {
    backgroundUrl,
    isMobile,
    isLoading,
    refreshBackground: loadBackground
  };
}
