<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue';
import * as THREE from 'three';

const containerRef = ref<HTMLDivElement | null>(null);

let scene: THREE.Scene;
let camera: THREE.PerspectiveCamera;
let renderer: THREE.WebGLRenderer;
let particles: THREE.Points;
let animationId: number;
let mouseX = 0;
let mouseY = 0;
let targetX = 0;
let targetY = 0;

const windowHalfX = window.innerWidth / 2;
const windowHalfY = window.innerHeight / 2;

const initThree = () => {
  if (!containerRef.value) return;

  // 场景
  scene = new THREE.Scene();

  // 相机
  camera = new THREE.PerspectiveCamera(
    75,
    window.innerWidth / window.innerHeight,
    1,
    2000
  );
  camera.position.z = 800;

  // 渲染器
  renderer = new THREE.WebGLRenderer({ 
    alpha: true, 
    antialias: true 
  });
  renderer.setSize(window.innerWidth, window.innerHeight);
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  containerRef.value.appendChild(renderer.domElement);

  // 创建粒子
  createParticles();

  // 开始动画
  animate();
};

const createParticles = () => {
  const particleCount = 1500;
  const geometry = new THREE.BufferGeometry();
  const positions = new Float32Array(particleCount * 3);
  const colors = new Float32Array(particleCount * 3);
  const sizes = new Float32Array(particleCount);

  // 颜色主题 - 青色、蓝绿色、白色
  const colorPalette = [
    new THREE.Color(0x06b6d4), // cyan-500
    new THREE.Color(0x14b8a6), // teal-500
    new THREE.Color(0x22d3ee), // cyan-400
    new THREE.Color(0x5eead4), // teal-300
    new THREE.Color(0xffffff), // white
    new THREE.Color(0xa5f3fc), // cyan-200
  ];

  for (let i = 0; i < particleCount; i++) {
    const i3 = i * 3;
    
    // 位置 - 球形分布
    const radius = 600 + Math.random() * 400;
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(2 * Math.random() - 1);
    
    positions[i3] = radius * Math.sin(phi) * Math.cos(theta);
    positions[i3 + 1] = radius * Math.sin(phi) * Math.sin(theta);
    positions[i3 + 2] = radius * Math.cos(phi) - 400;

    // 随机颜色
    const color = colorPalette[Math.floor(Math.random() * colorPalette.length)];
    colors[i3] = color.r;
    colors[i3 + 1] = color.g;
    colors[i3 + 2] = color.b;

    // 随机大小
    sizes[i] = Math.random() * 4 + 1;
  }

  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
  geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

  // 自定义着色器材质
  const material = new THREE.ShaderMaterial({
    uniforms: {
      time: { value: 0 },
      pixelRatio: { value: renderer.getPixelRatio() }
    },
    vertexShader: `
      attribute float size;
      varying vec3 vColor;
      uniform float time;
      uniform float pixelRatio;
      
      void main() {
        vColor = color;
        
        vec3 pos = position;
        // 添加波动效果
        pos.x += sin(time * 0.5 + position.y * 0.01) * 10.0;
        pos.y += cos(time * 0.3 + position.x * 0.01) * 10.0;
        pos.z += sin(time * 0.4 + position.z * 0.01) * 5.0;
        
        vec4 mvPosition = modelViewMatrix * vec4(pos, 1.0);
        gl_PointSize = size * pixelRatio * (300.0 / -mvPosition.z);
        gl_Position = projectionMatrix * mvPosition;
      }
    `,
    fragmentShader: `
      varying vec3 vColor;
      
      void main() {
        // 创建圆形粒子
        float dist = length(gl_PointCoord - vec2(0.5));
        if (dist > 0.5) discard;
        
        // 添加发光效果
        float glow = 1.0 - dist * 2.0;
        glow = pow(glow, 1.5);
        
        gl_FragColor = vec4(vColor, glow * 0.8);
      }
    `,
    transparent: true,
    vertexColors: true,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });

  particles = new THREE.Points(geometry, material);
  scene.add(particles);
};

const animate = () => {
  animationId = requestAnimationFrame(animate);

  // 平滑跟随鼠标
  targetX = mouseX * 0.001;
  targetY = mouseY * 0.001;

  particles.rotation.x += 0.0005;
  particles.rotation.y += 0.001;

  // 鼠标影响旋转
  particles.rotation.x += (targetY - particles.rotation.x) * 0.02;
  particles.rotation.y += (targetX - particles.rotation.y) * 0.02;

  // 更新时间uniform
  const material = particles.material as THREE.ShaderMaterial;
  material.uniforms.time.value = performance.now() * 0.001;

  renderer.render(scene, camera);
};

const onMouseMove = (event: MouseEvent) => {
  mouseX = event.clientX - windowHalfX;
  mouseY = event.clientY - windowHalfY;
};

const onWindowResize = () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
};

onMounted(() => {
  initThree();
  window.addEventListener('mousemove', onMouseMove);
  window.addEventListener('resize', onWindowResize);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(animationId);
  window.removeEventListener('mousemove', onMouseMove);
  window.removeEventListener('resize', onWindowResize);
  
  if (renderer) {
    renderer.dispose();
    containerRef.value?.removeChild(renderer.domElement);
  }
});
</script>

<template>
  <div 
    ref="containerRef" 
    class="fixed inset-0 z-0 pointer-events-none"
    aria-hidden="true"
  />
</template>

<style scoped>
div {
  opacity: 0.7;
}
</style>
