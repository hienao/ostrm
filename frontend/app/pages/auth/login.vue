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
  <div class="min-h-screen flex items-center justify-center py-12 px-4">
    <div class="max-w-md w-full space-y-8">
      <!-- 检查用户注册状态 -->
      <div v-if="checkingUser" class="text-center">
        <svg class="loading-spinner mx-auto mb-4 w-8 h-8 text-blue-400" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <p class="text-sm text-white/40">正在检查账户状态...</p>
      </div>

      <!-- 登录页面 -->
      <div v-else-if="!showRegister" class="animate-fade-in">
        <!-- Logo和标题 -->
        <div class="text-center mb-8">
          <img
            src="/logo.svg"
            alt="Ostrm"
            class="w-16 h-16 rounded-2xl mx-auto mb-5 shadow-lg shadow-blue-900/20"
          />
          <h2 class="text-3xl font-bold gradient-text mb-2">欢迎回来</h2>
          <p class="text-white/40">登录到您的 Ostrm 账户</p>
        </div>

        <!-- 登录表单 -->
        <div class="card">
          <form @submit.prevent="handleLogin" class="space-y-5">
            <div class="space-y-4">
              <div>
                <label for="username" class="block text-sm font-medium text-white/70 mb-2">用户名</label>
                <div class="relative">
                  <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <svg class="h-5 w-5 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                    </svg>
                  </div>
                  <input id="username" v-model="form.username" name="username" type="text" required class="input-field pl-10" placeholder="请输入用户名" :disabled="loading" />
                </div>
              </div>

              <div>
                <label for="password" class="block text-sm font-medium text-white/70 mb-2">密码</label>
                <div class="relative">
                  <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <svg class="h-5 w-5 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"></path>
                    </svg>
                  </div>
                  <input id="password" v-model="form.password" name="password" type="password" required class="input-field pl-10" placeholder="请输入密码" :disabled="loading" />
                </div>
              </div>
            </div>

            <div class="flex items-center justify-between">
              <div class="flex items-center">
                <input id="remember-me" v-model="form.rememberMe" name="remember-me" type="checkbox" class="h-4 w-4" />
                <label for="remember-me" class="ml-2 block text-sm text-white/50">记住我</label>
              </div>
            </div>

            <!-- 错误信息 -->
            <div v-if="error" class="p-4 bg-red-500/10 border border-red-500/20 rounded-xl animate-slide-up">
              <div class="flex items-center gap-2 text-red-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                <div>
                  <h3 class="text-sm font-semibold">登录失败</h3>
                  <p class="text-xs mt-0.5 opacity-80">{{ error }}</p>
                </div>
              </div>
            </div>

            <!-- 成功信息 -->
            <div v-if="success" class="p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-xl animate-slide-up">
              <div class="flex items-center gap-2 text-emerald-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                </svg>
                <span class="text-sm">登录成功，正在跳转...</span>
              </div>
            </div>

            <button type="submit" :disabled="loading" class="w-full btn-primary justify-center">
              <svg v-if="loading" class="loading-spinner -ml-1 mr-2 w-5 h-5" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ loading ? '登录中...' : '登录' }}
            </button>
          </form>
        </div>
      </div>

      <!-- 注册页面 -->
      <div v-else class="animate-fade-in">
        <div class="text-center mb-8">
          <div class="w-16 h-16 bg-gradient-to-br from-emerald-500 to-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-5 shadow-lg shadow-emerald-900/20">
            <svg class="w-9 h-9 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
            </svg>
          </div>
          <h2 class="text-3xl font-bold gradient-text mb-2">创建账户</h2>
          <p class="text-white/40">用户不存在，请先注册</p>
        </div>

        <div class="card">
          <form @submit.prevent="handleRegister" class="space-y-5">
            <div class="space-y-4">
              <div>
                <label for="reg-username" class="block text-sm font-medium text-white/70 mb-2">用户名</label>
                <input id="reg-username" v-model="registerForm.username" type="text" required class="input-field" placeholder="请输入用户名" :disabled="registerLoading" />
              </div>

              <div>
                <label for="reg-password" class="block text-sm font-medium text-white/70 mb-2">密码</label>
                <input id="reg-password" v-model="registerForm.password" type="password" required class="input-field" placeholder="请输入密码" :disabled="registerLoading" />
              </div>

              <div>
                <label for="reg-confirm-password" class="block text-sm font-medium text-white/70 mb-2">确认密码</label>
                <input id="reg-confirm-password" v-model="registerForm.confirmPassword" type="password" required class="input-field" placeholder="请再次输入密码" :disabled="registerLoading" />
              </div>
            </div>

            <!-- 注册错误 -->
            <div v-if="registerError" class="p-4 bg-red-500/10 border border-red-500/20 rounded-xl animate-slide-up">
              <div class="flex items-center gap-2 text-red-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                <span class="text-sm">{{ registerError }}</span>
              </div>
            </div>

            <!-- 注册成功 -->
            <div v-if="registerSuccess" class="p-4 bg-emerald-500/10 border border-emerald-500/20 rounded-xl animate-slide-up">
              <div class="flex items-center gap-2 text-emerald-400">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
                </svg>
                <span class="text-sm">注册成功，请使用新账户登录</span>
              </div>
            </div>

            <button type="submit" :disabled="registerLoading" class="w-full btn-success justify-center">
              <svg v-if="registerLoading" class="loading-spinner -ml-1 mr-2 w-5 h-5" fill="none" viewBox="0 0 24 24">
                <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
              {{ registerLoading ? '注册中...' : '注册' }}
            </button>

            <div v-if="userExists" class="text-center">
              <button type="button" @click="showRegister = false; clearForms()" class="text-sm text-white/50 hover:text-white transition-colors">
                返回登录
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted } from 'vue'
import { apiCall } from '~/core/api/client'
import logger from '~/core/utils/logger'
import { useAuthStore } from '~/core/stores/auth'

definePageMeta({ layout: false, middleware: ['guest'] })

const authStore = useAuthStore()
const loading = ref(false)
const error = ref('')
const success = ref(false)
const showRegister = ref(false)
const checkingUser = ref(true)
const userExists = ref(false)
const registerLoading = ref(false)
const registerError = ref('')
const registerSuccess = ref(false)

const form = reactive({ username: '', password: '', rememberMe: false })
const registerForm = reactive({ username: '', password: '', confirmPassword: '' })

const checkUserRegistration = async () => {
  checkingUser.value = true

  try {
    const response = await apiCall('/auth/check-user')
    userExists.value = response.data?.exists === true
    showRegister.value = !userExists.value
  } catch (err) {
    logger.error('检查用户注册状态失败:', err)
    userExists.value = true
    showRegister.value = false
    error.value = '无法检查账户状态，请确认服务已正常启动'
  } finally {
    checkingUser.value = false
  }
}

onMounted(checkUserRegistration)

const handleLogin = async () => {
  if (!form.username || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  error.value = ''
  success.value = false

  try {
    const response = await apiCall('/auth/sign-in', {
      method: 'POST',
      body: { username: form.username, password: form.password }
    })

    if (response.code === 200 && response.data?.token) {
      authStore.clearAuth()
      const token = response.data.token
      const userInfo = response.data.user || { username: form.username }
      authStore.setAuth(token, userInfo, form.rememberMe)
      success.value = true
      await nextTick()

      if (!authStore.isAuthenticated) {
        error.value = '登录状态异常，请重试'
        success.value = false
        return
      }

      const { initTokenRefreshService } = await import('~/core/utils/tokenRefresh.js')
      initTokenRefreshService()
      await navigateTo('/', { replace: true })
    } else {
      success.value = false
      error.value = response.message || '登录失败，请检查用户名和密码'
    }
  } catch (err) {
    logger.error('登录错误:', err)
    success.value = false

    if (err.status === 401) {
      error.value = '用户名或密码错误'
    } else if (err.status === 500) {
      error.value = '服务器错误，请稍后重试'
    } else {
      error.value = err.data?.message || '网络错误，请检查网络连接'
    }
  } finally {
    loading.value = false
  }
}

const validateRegisterForm = () => {
  if (!registerForm.username || !registerForm.password || !registerForm.confirmPassword) {
    registerError.value = '请填写所有必填字段'
    return false
  }
  if (registerForm.username.length < 3) { registerError.value = '用户名至少需要3个字符'; return false }
  if (registerForm.password.length < 6) { registerError.value = '密码至少需要6个字符'; return false }
  if (registerForm.password !== registerForm.confirmPassword) { registerError.value = '两次输入的密码不一致'; return false }
  return true
}

const handleRegister = async () => {
  if (!validateRegisterForm()) return

  registerLoading.value = true
  registerError.value = ''
  registerSuccess.value = false

  try {
    const response = await apiCall('/auth/sign-up', {
      method: 'POST',
      body: { username: registerForm.username, password: registerForm.password }
    })

    if (response.code === 200) {
      registerSuccess.value = true
      setTimeout(() => {
        userExists.value = true
        showRegister.value = false
        form.username = registerForm.username
        clearForms()
      }, 2000)
    } else {
      registerError.value = response.message || '注册失败，请重试'
    }
  } catch (err) {
    logger.error('注册错误:', err)
    if (err.status === 409) registerError.value = '用户名或邮箱已存在'
    else if (err.status === 400) registerError.value = err.data?.message || '请求参数错误'
    else registerError.value = err.data?.message || '注册失败，请重试'
  } finally {
    registerLoading.value = false
  }
}

const clearForms = () => {
  registerForm.username = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerError.value = ''
  registerSuccess.value = false
  error.value = ''
  success.value = false
}

useHead({ title: '用户登录 - Ostrm' })
</script>
