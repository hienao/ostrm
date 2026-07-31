<!--
  OStrm - Stream Management System
  Copyright (C) 2024 OStrm Project

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.
-->
<template>
  <main class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
    <div class="px-4 py-6 sm:px-0 space-y-4">
      <div class="card">
        <div class="px-4 sm:px-6 py-4 space-y-4">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 class="text-lg font-medium text-white">系统日志</h2>
              <p class="text-sm text-white/40 mt-1">增量加载最新日志，搜索和筛选仅作用于当前加载窗口</p>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <button class="btn-secondary text-sm py-2" :disabled="loading" @click="loadLogs">
                {{ loading ? '加载中...' : '重新加载' }}
              </button>
              <button class="btn-secondary text-sm py-2" :disabled="downloading" @click="downloadLogs">
                {{ downloading ? '下载中...' : '下载完整日志' }}
              </button>
              <button class="btn-danger text-sm py-2" :disabled="clearing" @click="showClearConfirm = true">
                {{ clearing ? '清空中...' : '清空日志' }}
              </button>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
            <label class="space-y-1">
              <span class="text-xs text-white/50">日志类型</span>
              <v-select
                v-model="selectedLogType"
                :options="logTypeOptions"
                :reduce="(option: SelectOption) => option.value"
                :clearable="false"
                class="vue-select-py"
                @update:model-value="switchLogType"
              />
            </label>
            <label class="space-y-1">
              <span class="text-xs text-white/50">日志级别（精确）</span>
              <v-select
                v-model="selectedLogLevel"
                :options="logLevelOptions"
                :reduce="(option: SelectOption) => option.value"
                :clearable="false"
                class="vue-select-py"
              />
            </label>
            <label class="space-y-1">
              <span class="text-xs text-white/50">加载窗口</span>
              <v-select
                v-model="lineLimit"
                :options="lineLimitOptions"
                :reduce="(option: NumberSelectOption) => option.value"
                :clearable="false"
                class="vue-select-py"
                @update:model-value="loadLogs"
              />
            </label>
            <label class="space-y-1 md:col-span-2">
              <span class="text-xs text-white/50">关键词搜索</span>
              <div class="relative">
                <input
                  v-model.trim="keyword"
                  class="input-field w-full pr-9"
                  type="search"
                  placeholder="搜索消息、线程或类名"
                >
                <button
                  v-if="keyword"
                  class="absolute right-3 top-1/2 -translate-y-1/2 text-white/40 hover:text-white"
                  aria-label="清除搜索"
                  @click="keyword = ''"
                >
                  ×
                </button>
              </div>
            </label>
          </div>

          <div class="flex flex-wrap items-center gap-x-5 gap-y-2 text-sm text-white/60">
            <label class="inline-flex items-center gap-2">
              <input v-model="autoRefresh" type="checkbox" class="rounded">
              自动刷新（2秒）
            </label>
            <label class="inline-flex items-center gap-2">
              <input v-model="followTail" type="checkbox" class="rounded">
              跟随最新日志
            </label>
            <label class="inline-flex items-center gap-2">
              <input v-model="wrapLines" type="checkbox" class="rounded">
              自动换行
            </label>
            <span :class="polling ? 'text-emerald-400' : 'text-white/35'">
              {{ polling ? '正在检查新日志…' : `最后更新：${lastUpdateTime || '暂无'}` }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="errorMessage" class="rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm text-red-300">
        <div class="flex items-center justify-between gap-4">
          <span>{{ errorMessage }}</span>
          <button class="underline" @click="loadLogs">重试</button>
        </div>
      </div>

      <div class="card p-0 overflow-hidden">
        <div class="px-4 py-3 bg-[#1A1A24] border-b border-white/6">
          <div class="flex flex-wrap justify-between items-center gap-2">
            <h3 class="text-sm font-medium text-white">
              {{ selectedLogType === 'backend' ? '后端日志' : '前端日志' }}
              <span class="text-white/40">
                （显示 {{ displayedLogs.length }} 条，已加载 {{ loadedLineCount }} 行）
              </span>
            </h3>
            <button
              v-if="pendingLogCount > 0"
              class="text-xs text-emerald-400 hover:text-emerald-300"
              @click="scrollToLatest"
            >
              {{ pendingLogCount }} 条新日志，点击查看
            </button>
          </div>
        </div>

        <div class="relative bg-[#0A0A0F] text-emerald-400 font-mono text-sm h-[60vh] min-h-[420px]">
          <div v-if="loading" class="flex justify-center items-center h-full">
            <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-emerald-400" />
            <p class="ml-3">加载日志中...</p>
          </div>
          <div v-else-if="displayedLogs.length === 0" class="flex justify-center items-center h-full">
            <p class="text-white/30">{{ keyword || selectedLogLevel !== 'all' ? '没有符合条件的日志' : '暂无日志数据' }}</p>
          </div>
          <DynamicScroller
            v-else
            ref="scroller"
            class="h-full scrollbar-thin"
            :items="displayedLogs"
            :min-item-size="28"
            key-field="id"
            @update="onScrollerUpdate"
          >
            <template #default="{ item, index, active }">
              <DynamicScrollerItem
                :item="item"
                :active="active"
                :size-dependencies="[item.text, wrapLines]"
                :data-index="index"
              >
                <div
                  class="px-4 py-1 hover:bg-white/5 transition-colors flex group"
                  :class="[item.cssClass, wrapLines ? 'whitespace-pre-wrap break-words' : 'whitespace-pre']"
                >
                  <span class="text-white/25 mr-3 select-none w-12 text-right flex-shrink-0">{{ item.lineNum }}</span>
                  <span class="min-w-0 flex-1">{{ item.text }}</span>
                  <button
                    class="ml-3 opacity-0 group-hover:opacity-100 text-white/40 hover:text-white flex-shrink-0"
                    title="复制这条日志"
                    @click="copyLog(item.text)"
                  >
                    复制
                  </button>
                </div>
              </DynamicScrollerItem>
            </template>
          </DynamicScroller>
        </div>
      </div>

      <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
        <div class="stat-card">
          <p class="text-sm text-white/40">已加载事件</p>
          <p class="text-2xl font-bold text-white tabular-nums">{{ originalLogs.length }}</p>
        </div>
        <div class="stat-card">
          <p class="text-sm text-white/40">错误</p>
          <p class="text-2xl font-bold text-red-400 tabular-nums">{{ logStats.error }}</p>
        </div>
        <div class="stat-card">
          <p class="text-sm text-white/40">警告</p>
          <p class="text-2xl font-bold text-amber-400 tabular-nums">{{ logStats.warn }}</p>
        </div>
        <div class="stat-card">
          <p class="text-sm text-white/40">信息</p>
          <p class="text-2xl font-bold text-blue-400 tabular-nums">{{ logStats.info }}</p>
        </div>
      </div>

      <Teleport to="body">
        <div v-if="showClearConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
          <div class="bg-[#1A1A24] rounded-lg p-6 max-w-md mx-4 border border-white/10">
            <h3 class="text-lg font-medium text-white mb-4">确认清空</h3>
            <p class="text-white/70 mb-6">
              确定要清空{{ selectedLogType === 'backend' ? '后端' : '前端' }}日志吗？日志文件会保留，新日志可继续正常写入。
            </p>
            <div class="flex justify-end gap-3">
              <button class="btn-secondary text-sm py-2" @click="showClearConfirm = false">取消</button>
              <button class="btn-danger text-sm py-2" @click="clearLogs">确认清空</button>
            </div>
          </div>
        </div>
      </Teleport>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import { apiCall } from '~/core/api/client'
import logger from '~/core/utils/logger'

definePageMeta({
  layout: 'default',
  pageTitle: '系统日志',
  middleware: 'auth'
})

type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error'
type SelectOption = { label: string, value: string }
type NumberSelectOption = { label: string, value: number }
type LogEntry = {
  id: number
  text: string
  level: LogLevel
  cssClass: string
  lineNum: string
  rawLineCount: number
}
type LogTailResponse = {
  lines: string[]
  cursor: number
  fileKey: string | null
  reset: boolean
  hasMore: boolean
}
type ApiResponse<T> = { code: number, message?: string, data?: T }

const selectedLogType = ref('backend')
const selectedLogLevel = ref('all')
const lineLimit = ref(1000)
const keyword = ref('')
const autoRefresh = ref(true)
const followTail = ref(true)
const wrapLines = ref(true)
const loading = ref(false)
const polling = ref(false)
const downloading = ref(false)
const clearing = ref(false)
const errorMessage = ref('')
const lastUpdateTime = ref('')
const originalLogs = ref<LogEntry[]>([])
const cursor = ref<number | null>(null)
const fileKey = ref<string | null>(null)
const scroller = ref<{ scrollToBottom?: () => void } | null>(null)
const showClearConfirm = ref(false)
const isAtBottom = ref(true)
const pendingLogCount = ref(0)

const logTypeOptions: SelectOption[] = [
  { label: '后端日志', value: 'backend' },
  { label: '前端日志', value: 'frontend' }
]
const logLevelOptions: SelectOption[] = [
  { label: '全部', value: 'all' },
  { label: 'Error', value: 'error' },
  { label: 'Warn', value: 'warn' },
  { label: 'Info', value: 'info' },
  { label: 'Debug', value: 'debug' },
  { label: 'Trace', value: 'trace' }
]
const lineLimitOptions: NumberSelectOption[] = [
  { label: '最近 200 行', value: 200 },
  { label: '最近 1000 行', value: 1000 },
  { label: '最近 5000 行', value: 5000 }
]

const levelClass: Record<LogLevel, string> = {
  error: 'text-red-400',
  warn: 'text-yellow-400',
  info: 'text-blue-300',
  debug: 'text-gray-400',
  trace: 'text-gray-500'
}

let entryId = 0
let pollTimer: ReturnType<typeof setInterval> | null = null
let requestVersion = 0

const parseLevel = (line: string): LogLevel | null => {
  const backendMatch = line.match(
    /^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+(TRACE|DEBUG|INFO|WARN|ERROR)\b/i
  )
  const frontendMatch = line.match(
    /^\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+\[FRONTEND]\s+(TRACE|DEBUG|INFO|WARN|ERROR)\b/i
  )
  return (backendMatch?.[1] || frontendMatch?.[1])?.toLowerCase() as LogLevel | null
}

const appendLines = (lines: string[], reset = false) => {
  if (reset) {
    originalLogs.value = []
    entryId = 0
  }

  const wasAtBottom = isAtBottom.value
  let addedEvents = 0
  for (const rawLine of lines) {
    const level = parseLevel(rawLine)
    const previous = originalLogs.value.at(-1)
    if (!level && previous) {
      previous.text += `\n${rawLine}`
      previous.rawLineCount++
      continue
    }

    const resolvedLevel = level || 'info'
    entryId++
    originalLogs.value.push({
      id: entryId,
      text: rawLine,
      level: resolvedLevel,
      cssClass: levelClass[resolvedLevel],
      lineNum: String(entryId).padStart(5, '0'),
      rawLineCount: 1
    })
    addedEvents++
  }

  trimLoadedWindow()
  if (addedEvents > 0 && (!wasAtBottom || !followTail.value)) {
    pendingLogCount.value += addedEvents
  }
  if (followTail.value && wasAtBottom) {
    scrollToLatest()
  }
}

const trimLoadedWindow = () => {
  let count = originalLogs.value.reduce((sum, item) => sum + item.rawLineCount, 0)
  while (count > lineLimit.value && originalLogs.value.length > 1) {
    const removed = originalLogs.value.shift()
    count -= removed?.rawLineCount || 0
  }
}

const displayedLogs = computed(() => {
  const query = keyword.value.toLocaleLowerCase()
  return originalLogs.value.filter((entry) => {
    const levelMatches = selectedLogLevel.value === 'all' || entry.level === selectedLogLevel.value
    const keywordMatches = !query || entry.text.toLocaleLowerCase().includes(query)
    return levelMatches && keywordMatches
  })
})

const loadedLineCount = computed(() =>
  originalLogs.value.reduce((sum, entry) => sum + entry.rawLineCount, 0)
)

const logStats = computed(() => {
  const stats = { error: 0, warn: 0, info: 0, debug: 0, trace: 0 }
  for (const entry of originalLogs.value) stats[entry.level]++
  return stats
})

const buildTailUrl = (initial: boolean) => {
  const params = new URLSearchParams({ lines: String(lineLimit.value) })
  if (!initial && cursor.value !== null) params.set('cursor', String(cursor.value))
  if (!initial && fileKey.value) params.set('fileKey', fileKey.value)
  return `/logs/${selectedLogType.value}/tail?${params.toString()}`
}

const fetchTail = async (initial: boolean) => {
  const version = requestVersion
  const response = await apiCall<ApiResponse<LogTailResponse>>(buildTailUrl(initial), { method: 'GET' })
  if (version !== requestVersion) return
  if (response.code !== 200 || !response.data) {
    throw new Error(response.message || '获取日志失败')
  }

  const data = response.data
  appendLines(data.lines || [], initial || data.reset)
  cursor.value = data.cursor
  fileKey.value = data.fileKey
  lastUpdateTime.value = new Date().toLocaleTimeString('zh-CN')
  errorMessage.value = ''

  if (data.hasMore && !initial) {
    await fetchTail(false)
  }
}

const loadLogs = async () => {
  requestVersion++
  const version = requestVersion
  loading.value = true
  errorMessage.value = ''
  cursor.value = null
  fileKey.value = null
  originalLogs.value = []
  pendingLogCount.value = 0
  isAtBottom.value = true
  try {
    await fetchTail(true)
    if (version === requestVersion) await scrollToLatest()
  } catch (error) {
    if (version === requestVersion) {
      errorMessage.value = error instanceof Error ? error.message : '获取日志失败'
      logger.error('获取日志失败', { error })
    }
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

const pollLogs = async () => {
  if (!autoRefresh.value || loading.value || polling.value || document.hidden) return
  polling.value = true
  try {
    await fetchTail(false)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '刷新日志失败'
  } finally {
    polling.value = false
  }
}

const switchLogType = () => {
  selectedLogLevel.value = 'all'
  keyword.value = ''
  loadLogs()
}

const onScrollerUpdate = (
  _startIndex: number,
  _endIndex: number,
  _visibleStartIndex: number,
  visibleEndIndex: number
) => {
  isAtBottom.value = visibleEndIndex >= displayedLogs.value.length - 2
  if (isAtBottom.value) pendingLogCount.value = 0
}

const scrollToLatest = async () => {
  await nextTick()
  scroller.value?.scrollToBottom?.()
  isAtBottom.value = true
  pendingLogCount.value = 0
}

const copyLog = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    errorMessage.value = '复制失败，请检查浏览器剪贴板权限'
  }
}

const downloadLogs = async () => {
  downloading.value = true
  try {
    const response = await fetch(`/api/logs/${selectedLogType.value}/download`)
    if (!response.ok) throw new Error('下载失败')
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = `${selectedLogType.value}-${new Date().toISOString().slice(0, 10)}.log`
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '下载失败'
  } finally {
    downloading.value = false
  }
}

const clearLogs = async () => {
  clearing.value = true
  showClearConfirm.value = false
  try {
    const response = await apiCall<ApiResponse<string>>(`/logs/${selectedLogType.value}`, {
      method: 'DELETE'
    })
    if (response.code !== 200) throw new Error(response.message || '清空失败')
    await loadLogs()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '清空失败'
  } finally {
    clearing.value = false
  }
}

watch(autoRefresh, (enabled) => {
  if (enabled) pollLogs()
})

onMounted(() => {
  loadLogs()
  pollTimer = setInterval(pollLogs, 2000)
})

onUnmounted(() => {
  requestVersion++
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
:deep(.vue-recycle-scroller__item-wrapper) {
  box-sizing: border-box;
}

:deep(.vue-recycle-scroller.ready .vue-recycle-scroller__item-view) {
  will-change: transform;
}

.vue-select-py {
  width: 100%;
}

.vue-select-py :deep(.vs__dropdown-toggle) {
  min-height: 40px;
}
</style>
