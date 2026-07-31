<template>
  <div>
    <button
      v-if="isFolder"
      type="button"
      class="group flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm text-white/75 hover:bg-white/5"
      :style="{ paddingLeft: `${depth * 18 + 8}px` }"
      @click="expanded = !expanded"
    >
      <svg
        class="h-3.5 w-3.5 shrink-0 text-white/35 transition-transform"
        :class="{ 'rotate-90': expanded }"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
      >
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
      </svg>
      <svg class="h-4 w-4 shrink-0 text-amber-400/80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" />
      </svg>
      <span class="truncate font-medium">{{ node.name }}</span>
      <span class="ml-auto text-xs text-white/25">{{ node.children?.length || 0 }}</span>
    </button>

    <div
      v-else
      class="flex items-start gap-2 rounded-lg px-2 py-2 text-sm hover:bg-red-500/5"
      :style="{ paddingLeft: `${depth * 18 + 29}px` }"
    >
      <svg class="mt-0.5 h-4 w-4 shrink-0 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6M7 3h7l5 5v13H7V3z" />
      </svg>
      <div class="min-w-0">
        <div class="break-all font-mono text-white/80">{{ node.name }}</div>
        <div class="mt-0.5 text-xs leading-5 text-red-300/80">{{ node.reason }}</div>
      </div>
    </div>

    <div v-if="isFolder && expanded">
      <TaskStructureTreeNode
        v-for="child in node.children"
        :key="`${child.type}:${child.path}`"
        :node="child"
        :depth="depth + 1"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  node: {
    type: Object,
    required: true
  },
  depth: {
    type: Number,
    default: 0
  }
})

const expanded = ref(true)
const isFolder = computed(() => props.node.type === 'folder')
</script>
