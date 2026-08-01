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
  <div class="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <!-- 页面标题和统计 -->
    <div class="mb-8 animate-fade-in">
      <div class="text-center mb-8">
        <h1 class="text-4xl font-bold gradient-text mb-4">OpenList 配置管理</h1>
        <p class="text-white/50 text-lg">管理您的 OpenList 配置，轻松生成 STRM 文件</p>
      </div>

      <!-- 统计卡片 -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div class="stat-card">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 bg-blue-500/10 rounded-xl flex items-center justify-center">
              <svg class="w-6 h-6 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
              </svg>
            </div>
            <div>
              <p class="text-3xl font-bold text-white tabular-nums">{{ configs.length }}</p>
              <p class="text-sm text-white/40">总配置数</p>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 bg-emerald-500/10 rounded-xl flex items-center justify-center">
              <svg class="w-6 h-6 text-emerald-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
              </svg>
            </div>
            <div>
              <p class="text-3xl font-bold text-white tabular-nums">{{ configs.filter(c => c.isActive).length }}</p>
              <p class="text-sm text-white/40">启用配置</p>
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="flex items-center gap-4">
            <div class="w-12 h-12 bg-purple-500/10 rounded-xl flex items-center justify-center">
              <svg class="w-6 h-6 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
              </svg>
            </div>
            <div>
              <p class="text-3xl font-bold text-white tabular-nums">{{ configs.filter(c => !c.isActive).length }}</p>
              <p class="text-sm text-white/40">禁用配置</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 配置列表 -->
    <div class="animate-slide-up">
      <!-- 加载状态 -->
      <div v-if="loading" class="flex justify-center items-center py-20">
        <div class="text-center">
          <div class="inline-block loading-spinner w-12 h-12 border-4 border-blue-500 border-t-transparent"></div>
          <p class="mt-4 text-white/50">加载配置中...</p>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else-if="configs.length === 0" class="empty-state">
        <div class="card max-w-md mx-auto">
          <div class="empty-state-icon">
            <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"></path>
            </svg>
          </div>
          <h3 class="empty-state-title">暂无配置</h3>
          <p class="empty-state-description">点击下方按钮添加您的第一个 OpenList 配置</p>
          <button @click="showAddModal = true" class="btn-primary">
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
            </svg>
            添加配置
          </button>
        </div>
      </div>

      <!-- 配置卡片网格 -->
      <div v-else class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        <div v-for="config in configs" :key="config.id"
             class="card card-hover"
             @click="goToTaskManagement(config)">
          <!-- 卡片头部 -->
          <div class="flex items-center justify-between mb-4">
            <div class="flex items-center gap-3">
              <div class="w-11 h-11 bg-gradient-to-br from-blue-500 to-purple-600 rounded-xl flex items-center justify-center">
                <span class="text-white font-bold text-lg">
                  {{ config.username.charAt(0).toUpperCase() }}
                </span>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-white">{{ config.username }}</h3>
                <p class="text-xs text-white/40">{{ formatDate(config.createdAt) }}</p>
              </div>
            </div>
            <span :class="config.isActive ? 'badge-success' : 'badge-neutral'">
              {{ config.isActive ? '启用' : '禁用' }}
            </span>
          </div>

          <!-- 配置信息 -->
          <div class="space-y-3 mb-4">
            <div class="bg-white/5 rounded-lg p-3 border border-white/6">
              <label class="text-xs font-medium text-white/40 uppercase tracking-wider">Base URL</label>
              <p class="mt-1 text-sm text-white/80 break-all font-mono">{{ config.baseUrl }}</p>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="bg-white/5 rounded-lg p-3 border border-white/6">
                <label class="text-xs font-medium text-white/40 uppercase tracking-wider">Base Path</label>
                <p class="mt-1 text-sm text-white/80 font-mono">{{ config.basePath || '/' }}</p>
              </div>

              <div class="bg-white/5 rounded-lg p-3 border border-white/6">
                <label class="text-xs font-medium text-white/40 uppercase tracking-wider">URL编码</label>
                <div class="mt-1 flex items-center gap-1">
                  <span :class="config.enableUrlEncoding !== false ? 'text-emerald-400' : 'text-red-400'" class="text-sm font-medium">
                    {{ config.enableUrlEncoding !== false ? '启用' : '禁用' }}
                  </span>
                  <svg v-if="config.enableUrlEncoding !== false" class="w-4 h-4 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                  </svg>
                  <svg v-else class="w-4 h-4 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636m12.728 12.728L18.364 5.636M5.636 18.364l12.728-12.728"></path>
                  </svg>
                </div>
              </div>
            </div>

            <div class="bg-white/5 rounded-lg p-3 border border-white/6">
              <label class="text-xs font-medium text-white/40 uppercase tracking-wider">文件 API 限速</label>
              <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-white/80">
                <span>QPS：{{ config.fsApiQpsLimit > 0 ? `${config.fsApiQpsLimit} 次/秒` : '不限制' }}</span>
                <span>QPM：{{ config.fsApiQpmLimit > 0 ? `${config.fsApiQpmLimit} 次/分钟` : '不限制' }}</span>
              </div>
            </div>

            <div v-if="config.strmBaseUrl" class="bg-blue-500/5 rounded-lg p-3 border border-blue-500/10">
              <label class="text-xs font-medium text-blue-400 uppercase tracking-wider">STRM Base URL</label>
              <p class="mt-1 text-sm text-white/80 break-all font-mono">{{ config.strmBaseUrl }}</p>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex justify-between items-center pt-4 border-t border-white/6">
            <div class="flex gap-1">
              <button @click.stop="editConfig(config)" class="btn-icon" title="编辑配置">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                </svg>
              </button>

              <button @click.stop="toggleConfigStatus(config)"
                      :class="config.isActive ? 'text-amber-400 hover:bg-amber-500/10' : 'text-emerald-400 hover:bg-emerald-500/10'"
                      class="btn-icon"
                      :title="config.isActive ? '禁用配置' : '启用配置'">
                <svg v-if="config.isActive" class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728L5.636 5.636m12.728 12.728L18.364 5.636M5.636 18.364l12.728-12.728"></path>
                </svg>
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                </svg>
              </button>

              <button @click.stop="deleteConfig(config)" class="btn-icon text-red-400 hover:bg-red-500/10" title="删除配置">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                </svg>
              </button>
            </div>

            <div class="flex items-center text-white/40 group-hover:text-blue-400 transition-colors">
              <span class="text-sm mr-2">管理任务</span>
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
              </svg>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 浮动添加按钮 -->
    <button @click="showAddModal = true" class="fab" title="添加配置">
      <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
      </svg>
    </button>

    <!-- 添加配置模态框 -->
    <Teleport to="body">
      <div v-if="showAddModal" class="modal-overlay animate-fade-in">
        <div class="flex items-center justify-center min-h-screen p-4">
          <div
            class="modal-content animate-scale-in mx-0 flex max-h-[calc(100dvh-2rem)] w-full max-w-lg flex-col overflow-hidden p-0"
            @click.stop
          >
            <div class="flex shrink-0 items-center justify-between border-b border-white/10 px-4 py-4 sm:px-6">
              <h3 class="text-xl font-semibold text-white">添加 OpenList 配置</h3>
              <button @click="closeAddModal" class="btn-icon">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <form @submit.prevent="addConfig" class="flex min-h-0 flex-1 flex-col">
              <div class="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-5 sm:px-6">
              <div>
                <label for="baseUrl" class="block text-sm font-medium text-white/70 mb-2">Base URL</label>
                <input id="baseUrl" v-model="configForm.baseUrl" type="url" required class="input-field" placeholder="https://openlist.example.com" :disabled="formLoading" />
              </div>

              <div>
                <label for="token" class="block text-sm font-medium text-white/70 mb-2">Token</label>
                <input id="token" v-model="configForm.token" type="password" required class="input-field" placeholder="您的 OpenList Token" :disabled="formLoading" />
              </div>

              <div>
                <label for="strmBaseUrl" class="block text-sm font-medium text-white/70 mb-2">STRM Base URL（可选）</label>
                <input id="strmBaseUrl" v-model="configForm.strmBaseUrl" type="url" class="input-field" placeholder="https://your-media-server.com/path" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">用于STRM文件生成时替换原始URL的baseUrl，留空则不进行替换</p>
              </div>

              <div>
                <label for="fsApiQpmLimit" class="block text-sm font-medium text-white/70 mb-2">文件 API 每分钟最大调用次数</label>
                <input id="fsApiQpmLimit" v-model.number="configForm.fsApiQpmLimit" type="number" min="0" max="6000" required class="input-field" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">控制持续调用总量，0 表示不限制</p>
              </div>

              <div>
                <label for="fsApiQpsLimit" class="block text-sm font-medium text-white/70 mb-2">文件 API 每秒最大调用次数</label>
                <input id="fsApiQpsLimit" v-model.number="configForm.fsApiQpsLimit" type="number" min="0" max="1000" required class="input-field" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">控制短时间突发速度，0 表示不限制；QPS 和 QPM 会同时生效</p>
              </div>

              <div class="flex items-start gap-3">
                <input id="enableUrlEncoding" v-model="configForm.enableUrlEncoding" type="checkbox" class="mt-1" :disabled="formLoading" />
                <label for="enableUrlEncoding" class="text-sm text-white/70">
                  <span class="font-medium">启用URL编码</span>
                  <p class="text-xs text-white/40 mt-0.5">对STRM文件中的链接进行URL编码，确保中文和特殊字符正确显示（推荐启用）</p>
                </label>
              </div>

              <div v-if="formError" class="p-4 bg-red-500/10 border border-red-500/20 rounded-xl">
                <div class="flex items-center gap-2 text-red-400">
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                  <p class="text-sm font-medium">{{ formError }}</p>
                </div>
              </div>
              </div>

              <div class="flex shrink-0 justify-end gap-3 border-t border-white/10 bg-[#0A0A0F] px-4 py-4 sm:px-6">
                <button type="button" @click="closeAddModal" class="btn-secondary" :disabled="formLoading">取消</button>
                <button type="submit" class="btn-primary" :disabled="formLoading">
                  <svg v-if="formLoading" class="loading-spinner -ml-1 mr-2 w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ formLoading ? '验证中...' : '添加' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 编辑配置模态框 -->
    <Teleport to="body">
      <div v-if="showEditModal" class="modal-overlay animate-fade-in">
        <div class="flex items-center justify-center min-h-screen p-4">
          <div
            class="modal-content animate-scale-in mx-0 flex max-h-[calc(100dvh-2rem)] w-full max-w-lg flex-col overflow-hidden p-0"
            @click.stop
          >
            <div class="flex shrink-0 items-center justify-between border-b border-white/10 px-4 py-4 sm:px-6">
              <h3 class="text-xl font-semibold text-white">编辑 OpenList 配置</h3>
              <button @click="closeEditModal" class="btn-icon">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <form @submit.prevent="updateConfig" class="flex min-h-0 flex-1 flex-col">
              <div class="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-5 sm:px-6">
              <div>
                <label for="editBaseUrl" class="block text-sm font-medium text-white/70 mb-2">Base URL</label>
                <input id="editBaseUrl" v-model="configForm.baseUrl" type="url" required class="input-field" placeholder="https://openlist.example.com" :disabled="formLoading" />
              </div>

              <div>
                <label for="editToken" class="block text-sm font-medium text-white/70 mb-2">Token</label>
                <input id="editToken" v-model="configForm.token" type="password" required class="input-field" placeholder="您的 OpenList Token" :disabled="formLoading" />
              </div>

              <div>
                <label for="editStrmBaseUrl" class="block text-sm font-medium text-white/70 mb-2">STRM Base URL（可选）</label>
                <input id="editStrmBaseUrl" v-model="configForm.strmBaseUrl" type="url" class="input-field" placeholder="https://your-media-server.com/path" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">用于STRM文件生成时替换原始URL的baseUrl，留空则不进行替换</p>
              </div>

              <div>
                <label for="editFsApiQpmLimit" class="block text-sm font-medium text-white/70 mb-2">文件 API 每分钟最大调用次数</label>
                <input id="editFsApiQpmLimit" v-model.number="configForm.fsApiQpmLimit" type="number" min="0" max="6000" required class="input-field" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">控制持续调用总量，0 表示不限制</p>
              </div>

              <div>
                <label for="editFsApiQpsLimit" class="block text-sm font-medium text-white/70 mb-2">文件 API 每秒最大调用次数</label>
                <input id="editFsApiQpsLimit" v-model.number="configForm.fsApiQpsLimit" type="number" min="0" max="1000" required class="input-field" :disabled="formLoading" />
                <p class="mt-1 text-xs text-white/30">控制短时间突发速度，0 表示不限制；QPS 和 QPM 会同时生效</p>
              </div>

              <div class="flex items-start gap-3">
                <input id="editEnableUrlEncoding" v-model="configForm.enableUrlEncoding" type="checkbox" class="mt-1" :disabled="formLoading" />
                <label for="editEnableUrlEncoding" class="text-sm text-white/70">
                  <span class="font-medium">启用URL编码</span>
                  <p class="text-xs text-white/40 mt-0.5">对STRM文件中的链接进行URL编码，确保中文和特殊字符正确显示（推荐启用）</p>
                </label>
              </div>

              <div v-if="formError" class="p-4 bg-red-500/10 border border-red-500/20 rounded-xl">
                <div class="flex items-center gap-2 text-red-400">
                  <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                  <p class="text-sm font-medium">{{ formError }}</p>
                </div>
              </div>
              </div>

              <div class="flex shrink-0 justify-end gap-3 border-t border-white/10 bg-[#0A0A0F] px-4 py-4 sm:px-6">
                <button type="button" @click="closeEditModal" class="btn-secondary" :disabled="formLoading">取消</button>
                <button type="submit" class="btn-primary" :disabled="formLoading">
                  <svg v-if="formLoading" class="loading-spinner -ml-1 mr-2 w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ formLoading ? '验证中...' : '更新' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { apiCall, authenticatedApiCall } from '~/core/api/client'
import { useAuthStore } from '~/core/stores/auth'
import logger from '~/core/utils/logger'

definePageMeta({ middleware: 'auth' })

const authStore = useAuthStore()
const configs = ref([])
const loading = ref(false)
const showAddModal = ref(false)
const showEditModal = ref(false)
const currentConfig = ref(null)
const emptyConfigForm = () => ({
  baseUrl: '',
  token: '',
  strmBaseUrl: '',
  enableUrlEncoding: true,
  fsApiQpmLimit: 0,
  fsApiQpsLimit: 0
})
const configForm = ref(emptyConfigForm())
const formLoading = ref(false)
const formError = ref('')

const logout = async () => {
  try {
    await authenticatedApiCall('/auth/sign-out', { method: 'POST' })
  } catch (error) {
    logger.error('登出失败:', error)
  } finally {
    const { stopTokenRefreshService } = await import('~/core/utils/tokenRefresh.js')
    stopTokenRefreshService()
    authStore.clearAuth()
    await navigateTo('/auth/login')
  }
}

const changePassword = () => navigateTo('/auth/change-password')
const openSettings = () => navigateTo('/settings')
const openLogs = () => navigateTo('/logs')

const getConfigs = async () => {
  loading.value = true
  try {
    const response = await authenticatedApiCall('/openlist-config', { method: 'GET' })
    if (response.code === 200) configs.value = response.data || []
  } catch (error) {
    logger.error('获取配置列表错误:', error)
  } finally {
    loading.value = false
  }
}

const validateOpenListConfig = async (baseUrl, token) => {
  const response = await authenticatedApiCall('/openlist-config/validate', {
    method: 'POST',
    body: { baseUrl, token }
  })
  if (response.code === 200 && response.data) {
    return { username: response.data.username, basePath: response.data.basePath || '/' }
  }
  throw new Error(response.message || '验证失败')
}

const addConfig = async () => {
  formLoading.value = true
  formError.value = ''
  try {
    const validationResult = await validateOpenListConfig(configForm.value.baseUrl, configForm.value.token)
    const response = await authenticatedApiCall('/openlist-config', {
      method: 'POST',
      body: { ...configForm.value, ...validationResult }
    })
    if (response.code === 200) {
      await getConfigs()
      closeAddModal()
    } else formError.value = response.message || '添加配置失败'
  } catch (error) {
    logger.error('添加配置错误:', error)
    formError.value = error.message || '添加配置失败'
  } finally {
    formLoading.value = false
  }
}

const editConfig = (config) => {
  currentConfig.value = config
  configForm.value = {
    baseUrl: config.baseUrl,
    token: config.token,
    strmBaseUrl: config.strmBaseUrl || '',
    enableUrlEncoding: config.enableUrlEncoding !== false,
    fsApiQpmLimit: config.fsApiQpmLimit || 0,
    fsApiQpsLimit: config.fsApiQpsLimit || 0
  }
  showEditModal.value = true
}

const updateConfig = async () => {
  formLoading.value = true
  formError.value = ''
  try {
    const validationResult = await validateOpenListConfig(configForm.value.baseUrl, configForm.value.token)
    const response = await authenticatedApiCall(`/openlist-config/${currentConfig.value.id}`, {
      method: 'PUT',
      body: { ...configForm.value, ...validationResult }
    })
    if (response.code === 200) {
      await getConfigs()
      closeEditModal()
    } else formError.value = response.message || '更新配置失败'
  } catch (error) {
    logger.error('更新配置错误:', error)
    formError.value = error.message || '更新配置失败'
  } finally {
    formLoading.value = false
  }
}

const deleteConfig = async (config) => {
  if (!confirm(`确定要删除用户 "${config.username}" 的配置吗？`)) return
  try {
    const response = await authenticatedApiCall(`/openlist-config/${config.id}`, { method: 'DELETE' })
    if (response.code === 200) await getConfigs()
    else alert('删除失败: ' + (response.message || '未知错误'))
  } catch (error) {
    logger.error('删除配置错误:', error)
    alert('删除失败: ' + (error.message || '网络错误'))
  }
}

const toggleConfigStatus = async (config) => {
  const action = config.isActive ? '禁用' : '启用'
  if (!confirm(`确定要${action}用户 "${config.username}" 的配置吗？`)) return
  try {
    const response = await authenticatedApiCall(`/openlist-config/${config.id}/status`, {
      method: 'PATCH',
      body: { isActive: !config.isActive }
    })
    if (response.code === 200) await getConfigs()
    else alert(`${action}失败: ` + (response.message || '未知错误'))
  } catch (error) {
    logger.error('切换配置状态错误:', error)
    alert(`${action}失败: ` + (error.message || '网络错误'))
  }
}

const closeAddModal = () => {
  showAddModal.value = false
  configForm.value = emptyConfigForm()
  formError.value = ''
  formLoading.value = false
}

const closeEditModal = () => {
  showEditModal.value = false
  currentConfig.value = null
  configForm.value = emptyConfigForm()
  formError.value = ''
  formLoading.value = false
}

const formatDate = (dateString) => !dateString ? '-' : new Date(dateString).toLocaleString('zh-CN')
const goToTaskManagement = (config) => navigateTo(`/task-management/${config.id}`)

onMounted(async () => {
  authStore.restoreAuth()
  await new Promise(r => setTimeout(r, 100))
  await getConfigs()
})
</script>
