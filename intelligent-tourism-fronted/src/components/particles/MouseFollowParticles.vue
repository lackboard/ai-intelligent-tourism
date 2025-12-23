<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue';

interface Props {
  particleCount?: number;
  mouseInfluence?: number;
}

const props = withDefaults(defineProps<Props>(), {
  particleCount: 50,
  mouseInfluence: 100
});

const containerRef = ref<HTMLDivElement | null>(null);
const mouseX = ref(0);
const mouseY = ref(0);

interface Particle {
  id: number;
  x: number;
  y: number;
  baseX: number;
  baseY: number;
  size: number;
  color: string;
  delay: number;
  duration: number;
}

const particles = ref<Particle[]>([]);

const colors = [
  'rgba(6, 182, 212, 0.6)',   // cyan-500
  'rgba(20, 184, 166, 0.5)',  // teal-500
  'rgba(34, 211, 238, 0.4)',  // cyan-400
  'rgba(94, 234, 212, 0.5)',  // teal-300
  'rgba(255, 255, 255, 0.6)', // white
  'rgba(165, 243, 252, 0.5)', // cyan-200
  'rgba(251, 191, 36, 0.4)',  // amber-400
];

const initParticles = () => {
  if (!containerRef.value) return;
  
  const rect = containerRef.value.getBoundingClientRect();
  const newParticles: Particle[] = [];
  
  for (let i = 0; i < props.particleCount; i++) {
    const x = Math.random() * rect.width;
    const y = Math.random() * rect.height;
    newParticles.push({
      id: i,
      x,
      y,
      baseX: x,
      baseY: y,
      size: Math.random() * 8 + 2,
      color: colors[Math.floor(Math.random() * colors.length)],
      delay: Math.random() * 5,
      duration: 3 + Math.random() * 4
    });
  }
  
  particles.value = newParticles;
};

const getParticleStyle = (particle: Particle) => {
  const dx = mouseX.value - particle.baseX;
  const dy = mouseY.value - particle.baseY;
  const distance = Math.sqrt(dx * dx + dy * dy);
  const maxDistance = props.mouseInfluence;
  
  let offsetX = 0;
  let offsetY = 0;
  
  if (distance < maxDistance) {
    const force = (maxDistance - distance) / maxDistance;
    offsetX = -dx * force * 0.3;
    offsetY = -dy * force * 0.3;
  }
  
  return {
    left: `${particle.baseX + offsetX}px`,
    top: `${particle.baseY + offsetY}px`,
    width: `${particle.size}px`,
    height: `${particle.size}px`,
    backgroundColor: particle.color,
    animationDelay: `${particle.delay}s`,
    animationDuration: `${particle.duration}s`,
    transition: 'left 0.3s ease-out, top 0.3s ease-out'
  };
};

const onMouseMove = (event: MouseEvent) => {
  if (!containerRef.value) return;
  const rect = containerRef.value.getBoundingClientRect();
  mouseX.value = event.clientX - rect.left;
  mouseY.value = event.clientY - rect.top;
};

const onResize = () => {
  initParticles();
};

onMounted(() => {
  initParticles();
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize);
});
</script>

<template>
  <div 
    ref="containerRef" 
    class="fixed inset-0 z-[1] overflow-hidden pointer-events-auto"
    @mousemove="onMouseMove"
  >
    <div
      v-for="particle in particles"
      :key="particle.id"
      class="absolute rounded-full animate-float-particle"
      :style="getParticleStyle(particle)"
    />
  </div>
</template>

<style scoped>
@keyframes float-particle {
  0%, 100% {
    transform: translateY(0) scale(1);
    opacity: 0.6;
  }
  50% {
    transform: translateY(-20px) scale(1.1);
    opacity: 1;
  }
}

.animate-float-particle {
  animation: float-particle ease-in-out infinite;
  filter: blur(1px);
  box-shadow: 0 0 10px currentColor;
}
</style>
