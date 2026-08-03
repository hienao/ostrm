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
  <header class="nav-glass">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex justify-between h-16 items-center">
        <!-- 左侧导航 -->
        <div class="flex items-center gap-3">
          <!-- 返回按钮 -->
          <button
            v-if="showBackButton"
            @click="goBack"
            class="btn-icon"
            title="返回"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
            </svg>
          </button>

          <!-- Logo -->
          <NuxtLink to="/" class="flex items-center gap-3 group">
            <img
              src="/logo.svg"
              alt="Ostrm"
              class="w-9 h-9 rounded-xl shadow-lg shadow-blue-900/20 group-hover:shadow-blue-900/40 transition-shadow"
            />
            <span class="text-xl font-bold gradient-text hidden sm:block">Ostrm</span>
          </NuxtLink>

          <!-- GitHub 版本链接 -->
          <a
            href="https://github.com/hienao/ostrm"
            target="_blank"
            rel="noopener noreferrer"
            class="hidden md:flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-white/5 border border-white/6 hover:bg-white/10 hover:border-white/12 transition-all text-xs text-white/50 hover:text-white/80"
            title="查看 GitHub 仓库"
          >
            <svg class="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.30.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            <span class="font-mono">{{ appVersion }}</span>
          </a>

          <!-- 新版本提示 -->
          <div
            v-if="versionStore.getShowUpdateNotice"
            class="hidden lg:flex items-center gap-2 px-3 py-1.5 bg-gradient-to-r from-amber-500/20 to-orange-500/20 border border-amber-500/30 rounded-lg cursor-pointer hover:from-amber-500/30 hover:to-orange-500/30 transition-all"
            @click="handleUpdateClick"
            title="点击查看新版本"
          >
            <svg class="w-3.5 h-3.5 text-amber-400" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"/>
            </svg>
            <span class="text-xs font-medium text-amber-300">v{{ versionStore.latestVersion }}</span>
            <button
              @click.stop="ignoreThisVersion"
              class="ml-1 p-0.5 hover:bg-white/10 rounded transition-colors"
              title="忽略此版本"
            >
              <svg class="w-3 h-3 text-amber-400/70" fill="currentColor" viewBox="0 0 24 24">
                <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 右侧操作按钮 -->
        <div class="hidden lg:flex items-center gap-2">
          <!-- 日志按钮 -->
          <button
            @click="openLogs"
            class="btn-icon"
            title="系统日志"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
          </button>

          <!-- 设置按钮 -->
          <button
            @click="openSettings"
            class="btn-icon"
            title="系统设置"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
            </svg>
          </button>

          <!-- 修改密码按钮 -->
          <button
            @click="changePassword"
            class="btn-icon"
            title="修改密码"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path>
            </svg>
          </button>

          <!-- 用户信息 -->
          <div class="flex items-center gap-2 pl-2 ml-1 border-l border-white/10">
            <div class="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
              <span class="text-white text-sm font-semibold">
                {{ (displayUserInfo?.username || '用户').charAt(0).toUpperCase() }}
              </span>
            </div>
            <span class="text-sm font-medium text-white/80 max-w-[100px] truncate hidden xl:block">
              {{ displayUserInfo?.username || '用户' }}
            </span>
          </div>

          <!-- 退出登录 -->
          <button
            @click="logout"
            class="btn-icon text-red-400 hover:text-red-300 hover:bg-red-500/10"
            title="退出登录"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
            </svg>
          </button>
        </div>

        <!-- 移动端菜单按钮 -->
        <div class="lg:hidden flex items-center gap-2">
          <!-- 移动端用户头像 -->
          <div class="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
            <span class="text-white text-sm font-semibold">
              {{ (displayUserInfo?.username || 'U').charAt(0).toUpperCase() }}
            </span>
          </div>

          <button
            @click="toggleMobileMenu"
            class="btn-icon"
            :class="{ 'bg-white/10': showMobileMenu }"
          >
            <span class="sr-only">打开菜单</span>
            <svg v-if="!showMobileMenu" class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"></path>
            </svg>
            <svg v-else class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
            </svg>
          </button>
        </div>
      </div>

      <!-- 移动端下拉菜单 -->
      <div v-if="showMobileMenu" class="lg:hidden py-4 border-t border-white/6 mt-2 animate-slide-down">
        <div class="space-y-1">
          <!-- 日志 -->
          <button
            @click="handleMobileAction(openLogs)"
            class="w-full flex items-center gap-3 px-4 py-3 rounded-xl hover:bg-white/5 transition-colors text-left"
          >
            <svg class="w-5 h-5 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
            <span class="text-white/80">系统日志</span>
          </button>

          <!-- 设置 -->
          <button
            @click="handleMobileAction(openSettings)"
            class="w-full flex items-center gap-3 px-4 py-3 rounded-xl hover:bg-white/5 transition-colors text-left"
          >
            <svg class="w-5 h-5 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path>
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
            </svg>
            <span class="text-white/80">系统设置</span>
          </button>

          <!-- 修改密码 -->
          <button
            @click="handleMobileAction(changePassword)"
            class="w-full flex items-center gap-3 px-4 py-3 rounded-xl hover:bg-white/5 transition-colors text-left"
          >
            <svg class="w-5 h-5 text-white/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 7a2 2 0 012 2m4 0a6 6 0 01-7.743 5.743L11 17H9v2H7v2H4a1 1 0 01-1-1v-2.586a1 1 0 01.293-.707l5.964-5.964A6 6 0 1121 9z"></path>
            </svg>
            <span class="text-white/80">修改密码</span>
          </button>

          <div class="border-t border-white/6 my-2"></div>

          <!-- 退出登录 -->
          <button
            @click="handleMobileAction(logout)"
            class="w-full flex items-center gap-3 px-4 py-3 rounded-xl hover:bg-red-500/10 transition-colors text-left"
          >
            <svg class="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"></path>
            </svg>
            <span class="text-red-400">退出登录</span>
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useRuntimeConfig } from '#app'
import { useAuthStore } from '~/core/stores/auth'
import { useVersionStore } from '~/core/stores/version.js'

const config = useRuntimeConfig()
const appVersion = computed(() => config.public.appVersion || 'dev')
const authStore = useAuthStore()
const versionStore = useVersionStore()
const router = useRouter()

const showMobileMenu = ref(false)

const props = defineProps({
  title: {
    type: String,
    default: 'Ostrm'
  },
  showBackButton: {
    type: Boolean,
    default: false
  },
  userInfo: {
    type: Object,
    default: () => ({})
  }
})

const displayUserInfo = computed(() => {
  if (props.userInfo && Object.keys(props.userInfo).length > 0) {
    return props.userInfo
  }
  const storeUserInfo = authStore.getUserInfo
  if (storeUserInfo && storeUserInfo.username) {
    return storeUserInfo
  }
  return { username: '用户' }
})

onMounted(() => {
  authStore.restoreAuth()
  versionStore.restoreFromStorage()
  setTimeout(() => {
    versionStore.checkVersion()
  }, 2000)
})

const emit = defineEmits(['logout', 'changePassword', 'goBack', 'openSettings', 'openLogs'])

const handleUpdateClick = () => {
  if (versionStore.updateInfo?.releaseUrl) {
    window.open(versionStore.updateInfo.releaseUrl, '_blank')
  }
}

const ignoreThisVersion = () => {
  if (versionStore.latestVersion) {
    versionStore.ignoreVersion(versionStore.latestVersion)
  }
}

const toggleMobileMenu = () => {
  showMobileMenu.value = !showMobileMenu.value
}

const handleMobileAction = (action) => {
  showMobileMenu.value = false
  action()
}

const goBack = () => emit('goBack')
const openSettings = () => emit('openSettings')
const openLogs = () => emit('openLogs')
const changePassword = () => emit('changePassword')
const logout = () => emit('logout')
</script>

<style scoped>
.gradient-text {
  background: linear-gradient(to right, #60A5FA, #A78BFA);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
</style>
