<script setup lang="ts">
const props = defineProps<{
  label: string;
  icon?: string;
  delay?: number;
  href?: string;
}>();
</script>

<template>
  <component
    :is="props.href ? 'a' : 'div'"
    :href="props.href"
    class="floating-badge"
    :style="{
      animationDelay: `${props.delay ?? 0}s`,
    }"
    target="_blank"
    rel="noopener"
  >
    <span v-if="props.icon" class="mr-2 text-lg">{{ props.icon }}</span>
    <span class="text-xs font-medium tracking-wider uppercase">{{ props.label }}</span>
  </component>
</template>

<style scoped>
@keyframes floatBadge {
  0%, 100% {
    transform: translateY(0px) scale(1);
    opacity: 0.85;
  }
  50% {
    transform: translateY(-12px) scale(1.03);
    opacity: 1;
  }
}

.floating-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 9999px;
  padding: 0.5rem 1rem;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.08));
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: white;
  backdrop-filter: blur(16px);
  animation: floatBadge 5s ease-in-out infinite;
  box-shadow: 0 20px 35px rgba(0, 0, 0, 0.15);
  text-decoration: none;
  transition: transform 0.4s ease, box-shadow 0.4s ease;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.floating-badge:hover {
  transform: translateY(-6px) scale(1.05);
  box-shadow: 0 25px 40px rgba(0, 0, 0, 0.2);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.25), rgba(255, 255, 255, 0.12));
}
</style>
