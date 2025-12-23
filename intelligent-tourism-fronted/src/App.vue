<template>
  <div class="min-h-screen text-slate-50">
    <div class="fixed inset-0 -z-10 bg-gradient-to-br from-slate-950 via-slate-900 to-slate-950" />
    <div class="fixed inset-0 -z-10 opacity-40" aria-hidden="true">
      <div class="h-full w-full bg-grid-glow bg-[size:32px_32px]" />
    </div>
    <RouterView />
  </div>
</template>
