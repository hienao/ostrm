<!--
  Ostrm - Stream Management System
  Copyright (C) 2024 Ostrm Project

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <https://www.gnu.org/licenses/>.
-->

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- 导航栏 -->
    <AppHeader 
      title="系统日志"
      :show-back-button="true"
      :user-info="userInfo"
      @logout="logout"
      @change-password="changePassword"
      @open-settings="openSettings"
      @go-back="goBack"
      @open-logs="openLogs"
    />

    <!-- 主要内容 -->
    <main class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
      <div class="px-4 py-6 sm:px-0">
        <!-- 日志控制面板 -->
        <div class="bg-white rounded-lg shadow mb-6">
          <div class="px-4 sm:px-6 py-4 border-b border-gray-200">
            <!-- 标题 -->
            <div class="mb-4 sm:mb-0">
              <h2 class="text-lg font-medium text-gray-900">日志控制面板</h2>
            </div>

            <!-- 控制项 - 移动端垂直布局，桌面端水平布局 -->
            <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between space-y-4 sm:space-y-0 sm:space-x-4">
              <!-- 左侧控制组 -->
              <div class="flex flex-col sm:flex-row sm:items-center space-y-3 sm:space-y-0 sm:space-x-4">
                <!-- 日志类型选择 -->
                <div class="flex items-center space-x-2">
                  <label class="text-sm font-medium text-gray-700 whitespace-nowrap">日志类型:</label>
                  <select
                    v-model="selectedLogType"
                    @change="switchLogType"
                    class="border border-gray-300 rounded-md px-3 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 min-w-0 flex-1 sm:flex-initial"
                  >
                    <option value="backend">后端日志</option>
                    <option value="frontend">前端日志</option>
                  </select>
                </div>

                <!-- 日志级别筛选 -->
                <div class="flex items-center space-x-2">
                  <label class="text-sm font-medium text-gray-700 whitespace-nowrap">日志级别:</label>
                  <select
                    v-model="selectedLogLevel"
                    @change="filterLogsByLevel"
                    class="border border-gray-300 rounded-md px-3 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 min-w-0 flex-1 sm:flex-initial"
                  >
                    <option value="all">全部</option>
                    <option value="error">Error</option>
                    <option value="warn">Warn</option>
                    <option value="info">Info</option>
                    <option value="debug">Debug</option>
                    <option value="trace">Trace</option>
                  </select>
                </div>

                <!-- 自动滚动开关 -->
                <div class="flex items-center space-x-2">
                  <label class="text-sm font-medium text-gray-700 whitespace-nowrap">自动滚动:</label>
                  <button
                    @click="toggleAutoScroll"
                    :class="autoScroll ? 'bg-green-500 hover:bg-green-600' : 'bg-gray-400 hover:bg-gray-500'"
                    class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
                  >
                    <span
                      :class="autoScroll ? 'translate-x-6' : 'translate-x-1'"
                      class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform"
                    />
                  </button>
                </div>

                <!-- 实时连接状态 -->
                <div class="flex items-center space-x-2">
                  <span class="text-sm font-medium text-gray-700 whitespace-nowrap">连接状态:</span>
                  <span
                    :class="wsConnected ? 'text-green-600' : 'text-red-600'"
                    class="text-sm font-medium flex items-center"
                  >
                    <span
                      :class="wsConnected ? 'bg-green-500' : 'bg-red-500'"
                      class="w-2 h-2 rounded-full mr-1"
                    ></span>
                    {{ wsConnected ? '已连接' : '未连接' }}
                  </span>
                </div>
              </div>

              <!-- 右侧操作按钮 -->
              <div class="flex flex-col sm:flex-row items-stretch sm:items-center space-y-2 sm:space-y-0 sm:space-x-2">
                <button
                  @click="clearLogs"
                  class="bg-yellow-500 hover:bg-yellow-600 text-white px-3 py-2 rounded-md text-sm font-medium transition-colors text-center"
                >
                  清空显示
                </button>
                <button
                  @click="downloadLogs"
                  :disabled="downloading"
                  class="bg-blue-500 hover:bg-blue-600 disabled:bg-gray-400 text-white px-3 py-2 rounded-md text-sm font-medium transition-colors flex items-center justify-center"
                >
                  <svg v-if="downloading" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <svg v-else class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                  </svg>
                  {{ downloading ? '下载中...' : '下载日志' }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 日志显示区域 -->
        <div class="bg-black rounded-lg shadow overflow-hidden">
          <div class="px-4 py-3 bg-gray-800 border-b border-gray-700">
            <div class="flex justify-between items-center">
              <h3 class="text-sm font-medium text-white">
                {{ selectedLogType === 'backend' ? '后端日志' : '前端日志' }} 
                <span class="text-gray-400">({{ logLines.length }} 行)</span>
              </h3>
              <div class="text-xs text-gray-400">
                最后更新: {{ lastUpdateTime || '暂无数据' }}
              </div>
            </div>
          </div>
          
          <!-- 日志内容 (Virtual Scroll) -->
          <div 
            class="bg-black text-green-400 font-mono text-sm"
            style="height: 500px;"
          >
            <div v-if="loading" class="flex justify-center items-center h-full">
              <div class="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-green-400"></div>
              <p class="ml-3 text-green-400">加载日志中...</p>
            </div>
            
            <div v-else-if="logLines.length === 0" class="flex justify-center items-center h-full">
              <p class="text-gray-500">暂无日志数据</p>
            </div>
            
            <DynamicScroller
              v-else
              ref="scroller"
              class="h-full custom-scrollbar"
              :items="logLines"
              :min-item-size="28" 
              key-field="id"
              @update="onScrollerUpdate"
            >
              <template v-slot="{ item, index, active }">
                <DynamicScrollerItem
                  :item="item"
                  :active="active"
                  :size-dependencies="[
                    item.text,
                  ]"
                  :data-index="index"
                >
                  <div 
                    class="whitespace-pre-wrap break-words px-4 py-1 hover:bg-gray-900 transition-colors flex"
                    :class="item.class"
                  >
                    <span class="text-gray-500 mr-2 select-none w-10 text-right flex-shrink-0">{{ item.lineNum }}</span>
                    <span>{{ item.text }}</span>
                  </div>
                </DynamicScrollerItem>
              </template>
            </DynamicScroller>
          </div>
        </div>
        
        <!-- 日志统计信息 -->
        <div class="mt-6 grid grid-cols-1 md:grid-cols-4 gap-4">
          <div class="bg-white rounded-lg shadow p-4">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                  <svg class="w-4 h-4 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
                  </svg>
                </div>
              </div>
              <div class="ml-4">
                <p class="text-sm font-medium text-gray-500">总行数</p>
                <p class="text-2xl font-semibold text-gray-900">{{ logLines.length }}</p>
              </div>
            </div>
          </div>
          
          <div class="bg-white rounded-lg shadow p-4">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="w-8 h-8 bg-red-100 rounded-full flex items-center justify-center">
                  <svg class="w-4 h-4 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                </div>
              </div>
              <div class="ml-4">
                <p class="text-sm font-medium text-gray-500">错误日志</p>
                <p class="text-2xl font-semibold text-gray-900">{{ logStats.error }}</p>
              </div>
            </div>
          </div>
          
          <div class="bg-white rounded-lg shadow p-4">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="w-8 h-8 bg-yellow-100 rounded-full flex items-center justify-center">
                  <svg class="w-4 h-4 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L3.732 16.5c-.77.833.192 2.5 1.732 2.5z"></path>
                  </svg>
                </div>
              </div>
              <div class="ml-4">
                <p class="text-sm font-medium text-gray-500">警告日志</p>
                <p class="text-2xl font-semibold text-gray-900">{{ logStats.warn }}</p>
              </div>
            </div>
          </div>
          
          <div class="bg-white rounded-lg shadow p-4">
            <div class="flex items-center">
              <div class="flex-shrink-0">
                <div class="w-8 h-8 bg-green-100 rounded-full flex items-center justify-center">
                  <svg class="w-4 h-4 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                  </svg>
                </div>
              </div>
              <div class="ml-4">
                <p class="text-sm font-medium text-gray-500">连接状态</p>
                <p class="text-2xl font-semibold text-gray-900">{{ wsConnected ? '正常' : '断开' }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, computed, reactive } from 'vue'
import AppHeader from '~/components/AppHeader.vue'
import { apiCall } from '~/utils/api.js'
import { useAuthStore } from '~/stores/auth.js'
import logger from '~/utils/logger.js'

// 引入 vue-virtual-scroller
import { DynamicScroller, DynamicScrollerItem } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'

// 页面元数据
definePageMeta({
})

// 获取认证store
const authStore = useAuthStore()

// 响应式数据
const userInfo = computed(() => {
  const storeUserInfo = authStore.getUserInfo
  return storeUserInfo && storeUserInfo.username ? storeUserInfo : { username: '用户' }
})
const selectedLogType = ref('backend')
const selectedLogLevel = ref('all')
const logLines = ref([]) // 显示的日志行
const originalLogLines = ref([]) // 所有日志行
const loading = ref(false)
const autoScroll = ref(true)
const wsConnected = ref(false)
const downloading = ref(false)
const lastUpdateTime = ref('')
const scroller = ref(null)

// 标记是否有待处理的滚动请求
const pendingScrollToBottom = ref(false)

// 增量统计
const logStats = reactive({
  error: 0,
  warn: 0,
  info: 0,
  debug: 0,
  trace: 0
})

// 全局计数器，确保唯一ID
let logIdCounter = 0

// WebSocket 连接
let ws = null

// 定义日志级别优先级
const LOG_LEVEL_PRIORITY = {
  'trace': 0,
  'debug': 1,
  'info': 2,
  'warn': 3,
  'error': 4
}

// 返回上一页
const goBack = () => {
  navigateTo('/')
}

// 退出登录
const logout = async () => {
  try {
    const token = useCookie('token')
    const userInfoCookie = useCookie('userInfo')
    token.value = null
    userInfoCookie.value = null
    await navigateTo('/login')
  } catch (error) {
    logger.error('登出失败:', error)
    const token = useCookie('token')
    const userInfoCookie = useCookie('userInfo')
    token.value = null
    userInfoCookie.value = null
    await navigateTo('/login')
  }
}

// 修改密码
const changePassword = () => {
  navigateTo('/change-password')
}

// 打开设置页面
const openSettings = () => {
  navigateTo('/settings')
}

// 打开日志页面
const openLogs = () => {
}

// 解析日志级别和样式
const parseLogLine = (line) => {
  const lineStr = String(line)
  const lowerLine = lineStr.toLowerCase()
  let level = 'info'
  let cssClass = 'text-green-400'
  
  if (lowerLine.includes('error') || lowerLine.includes('exception') || lowerLine.includes('failed')) {
    level = 'error'
    cssClass = 'text-red-400'
  } else if (lowerLine.includes('warn') || lowerLine.includes('warning')) {
    level = 'warn'
    cssClass = 'text-yellow-400'
  } else if (lowerLine.includes('info')) {
    level = 'info'
    cssClass = 'text-blue-400'
  } else if (lowerLine.includes('debug')) {
    level = 'debug'
    cssClass = 'text-gray-400'
  } else if (lowerLine.includes('trace')) {
    level = 'trace'
    cssClass = 'text-gray-500'
  }
  
  return {
    id: logIdCounter++,
    text: lineStr,
    level: level,
    class: cssClass,
    lineNum: String(logIdCounter).padStart(4, '0') // 实际上应该是基于当前列表的索引，但为了性能这里先用ID
  }
}

// 重置统计
const resetStats = () => {
  logStats.error = 0
  logStats.warn = 0
  logStats.info = 0
  logStats.debug = 0
  logStats.trace = 0
  logIdCounter = 0
}

// 更新统计
const updateStats = (level) => {
  if (logStats[level] !== undefined) {
    logStats[level]++
  }
}

// 批量添加日志
const batchAddLogs = (lines) => {
  const newEntries = []
  
  for (const line of lines) {
    const entry = parseLogLine(line)
    newEntries.push(entry)
    updateStats(entry.level)
  }
  
  // 更新原始数据
  originalLogLines.value.push(...newEntries)
  
  // 更新显示数据
  if (selectedLogLevel.value === 'all') {
    logLines.value.push(...newEntries)
  } else {
    const selectedPriority = LOG_LEVEL_PRIORITY[selectedLogLevel.value]
    const filteredEntries = newEntries.filter(entry => 
      LOG_LEVEL_PRIORITY[entry.level] >= selectedPriority
    )
    logLines.value.push(...filteredEntries)
  }
  
  // 修正行号显示 (在虚拟列表中，每一行需要知道它在当前视图的序号)
  // 注意：为了极致性能，这里我们在 render 时不做计算，而是简单使用原始序号或者不显示序号
  // 上面的 parseLogLine 使用了全局计数器作为行号，这在过滤时会有跳号，
  // 但对于日志查看来说通常是可以接受的，或者我们可以仅在全量显示时保证连续。
  // 如果需要严格连续行号，可以在 filterLogsByLevel 中重新生成。
  
  lastUpdateTime.value = new Date().toLocaleString('zh-CN')
  
  // 标记需要滚动到底部
  if (autoScroll.value) {
    pendingScrollToBottom.value = true
  }
}

// 节流函数
const throttle = (func, limit) => {
  let inThrottle
  return function() {
    const args = arguments
    const context = this
    if (!inThrottle) {
      func.apply(context, args)
      inThrottle = true
      setTimeout(() => inThrottle = false, limit)
    }
  }
}

// 节流滚动
const throttledScrollToBottom = throttle(() => {
  if (autoScroll.value && scroller.value && logLines.value.length > 0) {
    scroller.value.scrollToBottom()
  }
}, 100)

// DynamicScroller 更新事件处理
const onScrollerUpdate = () => {
  if (pendingScrollToBottom.value && autoScroll.value && scroller.value) {
    scroller.value.scrollToBottom()
    pendingScrollToBottom.value = false
  }
}

// 简单的滚动到底部（非节流，用于手动触发）
const scrollToBottom = () => {
  if (autoScroll.value && scroller.value) {
    nextTick(() => {
      scroller.value.scrollToBottom()
    })
  }
}

// 按日志级别筛选
const filterLogsByLevel = () => {
  if (selectedLogLevel.value === 'all') {
    logLines.value = [...originalLogLines.value]
  } else {
    const selectedPriority = LOG_LEVEL_PRIORITY[selectedLogLevel.value]
    logLines.value = originalLogLines.value.filter(entry => 
      LOG_LEVEL_PRIORITY[entry.level] >= selectedPriority
    )
  }
  scrollToBottom()
}

// 切换日志类型
const switchLogType = () => {
  selectedLogLevel.value = 'all'
  loadLogs()
  connectWebSocket()
}

// 切换自动滚动
const toggleAutoScroll = () => {
  autoScroll.value = !autoScroll.value
  if (autoScroll.value) {
    scrollToBottom()
  }
}

// 清空日志显示
const clearLogs = () => {
  logLines.value = []
  originalLogLines.value = []
  lastUpdateTime.value = ''
  resetStats()
}

// 加载日志
const loadLogs = async () => {
  loading.value = true
  clearLogs() // 加载前清空
  
  try {
    const response = await apiCall(`/logs/${selectedLogType.value}`, {
      method: 'GET'
    })

    if (response.code === 200) {
      if (response.data && Array.isArray(response.data)) {
        batchAddLogs(response.data)
      }
    } else {
      logger.error('获取日志失败:', response.message)
    }
  } catch (error) {
    logger.error('获取日志错误:', error)
  } finally {
    loading.value = false
  }
}

// 下载日志
const downloadLogs = async () => {
  downloading.value = true
  try {
    const config = useRuntimeConfig()
    const baseURL = config.public.apiBase || 'http://localhost:8080'
    const downloadUrl = `${baseURL}/logs/${selectedLogType.value}/download`

    const response = await fetch(downloadUrl, { method: 'GET' })

    if (response.ok) {
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${selectedLogType.value}-${new Date().toISOString().split('T')[0]}.log`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    } else {
      const errorText = await response.text()
      alert('下载失败: ' + (errorText || '未知错误'))
    }
  } catch (error) {
    logger.error('下载日志错误:', error)
    alert('下载失败: ' + (error.message || '网络错误'))
  } finally {
    downloading.value = false
  }
}

// 连接WebSocket
const connectWebSocket = () => {
  if (ws) {
    ws.close()
  }

  try {
    const config = useRuntimeConfig()
    const apiBase = config.public.apiBase
    let wsUrl

    if (apiBase && apiBase.startsWith('http')) {
      const apiUrl = new URL(apiBase)
      const wsProtocol = apiUrl.protocol === 'https:' ? 'wss:' : 'ws:'
      wsUrl = `${wsProtocol}//${apiUrl.host}/ws/logs/${selectedLogType.value}`
    } else {
      const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      wsUrl = `${wsProtocol}//${window.location.host}/ws/logs/${selectedLogType.value}`
    }

    logger.info('连接WebSocket:', wsUrl)
    ws = new WebSocket(wsUrl)

    // WebSocket 消息缓冲区
    let messageBuffer = []
    let rafId = null

    // 批量处理函数
    const processBuffer = () => {
      if (messageBuffer.length > 0) {
        batchAddLogs(messageBuffer)
        messageBuffer = []
      }
      rafId = null
    }

    ws.onopen = () => {
      wsConnected.value = true
      logger.info('WebSocket连接已建立')
    }

    ws.onmessage = (event) => {
      // console.log('WS Received:', event.data.length, 'bytes'); // Debug log
      // 将消息加入缓冲区
      messageBuffer.push(event.data)
      
      // 如果没有正在进行的 RAF，则启动一个
      if (!rafId) {
        rafId = requestAnimationFrame(processBuffer)
      }
    }

    ws.onclose = () => {
      wsConnected.value = false
      logger.info('WebSocket连接已关闭')
      if (rafId) {
        cancelAnimationFrame(rafId)
        rafId = null
      }
      setTimeout(() => {
        if (!wsConnected.value) {
          connectWebSocket()
        }
      }, 5000)
    }

    ws.onerror = (error) => {
      logger.error('WebSocket错误:', error)
      wsConnected.value = false
    }
  } catch (error) {
    logger.error('WebSocket连接失败:', error)
    wsConnected.value = false
  }
}

onMounted(() => {
  authStore.restoreAuth()
  loadLogs()
  connectWebSocket()
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
})
</script>

<style scoped>
/* 自定义滚动条样式 - 用于非虚拟滚动区域 */
.custom-scrollbar::-webkit-scrollbar {
  width: 8px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: #1f2937;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #4b5563;
  border-radius: 4px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #6b7280;
}

/* 覆盖 vue-virtual-scroller 默认样式以适配我们的黑色主题 */
:deep(.vue-recycle-scroller__item-wrapper) {
  box-sizing: border-box;
}

:deep(.vue-recycle-scroller.ready .vue-recycle-scroller__item-view) {
  will-change: transform; 
}
</style>
