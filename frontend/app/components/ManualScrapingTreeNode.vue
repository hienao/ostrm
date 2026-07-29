<template>
  <div>
    <div
      class="group flex items-center gap-2 rounded-lg border px-2 py-1.5 transition-colors"
      :class="selected
        ? 'border-blue-500/40 bg-blue-500/15'
        : 'border-transparent hover:border-white/10 hover:bg-white/5'"
      :style="{ marginLeft: `${depth * 16}px` }"
    >
      <button
        type="button"
        class="flex min-w-0 flex-1 items-center gap-2 text-left"
        @click="$emit('select', node)"
      >
        <svg
          class="h-3.5 w-3.5 shrink-0 text-white/35 transition-transform"
          :class="{ 'rotate-90': expanded }"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          @click.stop="expanded = !expanded"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
        <svg class="h-4 w-4 shrink-0 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7a2 2 0 012-2h4l2 2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V7z" />
        </svg>
        <span class="truncate text-sm text-white/80">{{ node.name }}</span>
      </button>
      <span
        class="shrink-0 rounded-full px-2 py-0.5 text-[11px]"
        :class="node.videoFileCount ? 'bg-blue-500/15 text-blue-300' : 'bg-white/5 text-white/30'"
      >
        {{ node.videoFileCount }} 个媒体
      </span>
    </div>

    <div v-if="expanded">
      <ManualScrapingTreeNode
        v-for="child in node.children"
        :key="child.path"
        :node="child"
        :depth="depth + 1"
        :selected-path="selectedPath"
        @select="$emit('select', $event)"
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
  },
  selectedPath: {
    type: String,
    default: ''
  }
})

defineEmits(['select'])

const expanded = ref(true)
const selected = computed(() => props.selectedPath === props.node.path)
</script>
