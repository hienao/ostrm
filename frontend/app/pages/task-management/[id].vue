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
  <div class="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
    <!-- 加载状态 -->
    <div v-if="loading" class="flex justify-center items-center py-20">
      <div class="text-center">
        <div class="inline-block animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
        <p class="mt-4 text-white/50 text-lg">加载中...</p>
      </div>
    </div>

    <template v-else>
      <!-- 配置信息卡片 -->
      <div class="card mb-6 animate-fade-in">
        <div class="flex items-center justify-between mb-4">
          <div>
            <h3 class="text-lg leading-6 font-medium text-white">配置信息</h3>
            <p class="mt-1 max-w-2xl text-sm text-white/40">当前 OpenList 配置详情</p>
          </div>
          <span :class="configInfo?.isActive ? 'badge-success' : 'badge-neutral'">
            {{ configInfo?.isActive ? '启用' : '禁用' }}
          </span>
        </div>

        <div class="mt-5 border-t border-white/6 pt-5" v-if="configInfo">
          <dl class="grid grid-cols-1 gap-x-4 gap-y-6 sm:grid-cols-2">
            <div>
              <dt class="text-sm text-white/40">用户名</dt>
              <dd class="mt-1 text-sm text-white">{{ configInfo.username }}</dd>
            </div>
            <div>
              <dt class="text-sm text-white/40">Base URL</dt>
              <dd class="mt-1 text-sm text-white break-all font-mono">{{ configInfo.baseUrl }}</dd>
            </div>
            <div>
              <dt class="text-sm text-white/40">Base Path</dt>
              <dd class="mt-1 text-sm text-white">{{ configInfo.basePath || '/' }}</dd>
            </div>
            <div>
              <dt class="text-sm text-white/40">创建时间</dt>
              <dd class="mt-1 text-sm text-white">{{ formatDate(configInfo.createdAt) }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- 任务管理区域 -->
      <div class="card animate-fade-in" style="animation-delay: 0.1s">
        <div class="flex items-center justify-between mb-6">
          <div>
            <h3 class="text-lg leading-6 font-medium text-white">任务管理</h3>
            <p class="mt-1 text-sm text-white/40">管理您的 STRM 生成任务</p>
          </div>
          <button type="button" class="btn-primary" @click="showCreateTaskModal = true">
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
            </svg>
            创建任务
          </button>
        </div>

        <!-- 任务列表 -->
        <div class="space-y-4" v-if="tasks.length > 0">
          <div class="card" v-for="task in tasks" :key="task.id">
            <div class="flex flex-col sm:flex-row sm:items-center justify-between mb-4 gap-3">
              <h4 class="text-lg font-medium text-white">{{ task.taskName }}</h4>
              <div class="flex items-center space-x-2 flex-wrap gap-y-2">
                <span :class="task.isActive ? 'badge-success' : 'badge-neutral'" class="text-xs">
                  {{ task.isActive ? '启用' : '禁用' }}
                </span>
                <button
                  class="btn-icon text-amber-400 hover:text-amber-300"
                  @click="checkTaskStructure(task)"
                  :disabled="checkingStructureTaskId === task.id"
                  title="检查目录结构"
                >
                  <svg v-if="checkingStructureTaskId === task.id" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7h6l2 2h10v10H3V7zm5 6h8m-4-4v8"></path>
                  </svg>
                </button>
                <button
                  class="btn-icon text-purple-400 hover:text-purple-300"
                  @click="openManualScraping(task)"
                  title="手动刮削"
                >
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v3m0 12v3M3 12h3m12 0h3M5.64 5.64l2.12 2.12m8.48 8.48 2.12 2.12m0-12.72-2.12 2.12m-8.48 8.48-2.12 2.12M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                </button>
                <button class="btn-icon" @click="editTask(task)" title="编辑">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                  </svg>
                </button>
                <button class="btn-icon text-emerald-400 hover:text-emerald-300" @click="showExecuteModal(task.id)" :disabled="generatingStrm[task.id]" title="立即执行">
                  <svg v-if="generatingStrm[task.id]" class="animate-spin w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-3.197-2.132A1 1 0 0010 9.87v4.263a1 1 0 001.555.832l3.197-2.132a1 1 0 000-1.664z"></path>
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                  </svg>
                </button>
                <button class="btn-icon text-red-400 hover:text-red-300" @click="deleteTask(task.id)" title="删除">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path>
                  </svg>
                </button>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-x-4 gap-y-3 sm:grid-cols-2">
              <div>
                <dt class="text-sm text-white/40">路径</dt>
                <dd class="mt-1 text-sm text-white/80 break-all font-mono">{{ task.path }}</dd>
              </div>
              <div>
                <dt class="text-sm text-white/40">媒体库类型</dt>
                <dd class="mt-1 text-sm text-white/80">{{ libraryTypeLabel(task.libraryType) }}</dd>
              </div>
              <div>
                <dt class="text-sm text-white/40">STRM路径</dt>
                <dd class="mt-1 text-sm text-white/80 break-all font-mono">{{ task.strmPath }}</dd>
              </div>
              <div>
                <dt class="text-sm text-white/40">定时任务</dt>
                <dd class="mt-1 text-sm text-white/80">{{ task.cron || '未设置' }}</dd>
              </div>
              <div>
                <dt class="text-sm text-white/40">上次执行</dt>
                <dd class="mt-1 text-sm text-white/80">{{ formatDate(task.lastExecTime) }}</dd>
              </div>
              <div>
                <dt class="text-sm text-white/40">媒体库刷新</dt>
                <dd class="mt-1 text-sm text-white/80">{{ taskRefreshLabel(task) }}</dd>
              </div>
            </div>

            <div class="mt-3 flex items-center space-x-4 flex-wrap gap-y-2">
              <label class="flex items-center text-sm text-white/60">
                <input type="checkbox" :checked="task.needScrap" disabled class="mr-2 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                需要刮削
              </label>
              <label class="flex items-center text-sm text-white/60">
                <input type="checkbox" :checked="task.autoRenameMedia" disabled class="mr-2 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                自动重命名媒体
              </label>
              <label class="flex items-center text-sm text-white/60">
                <input type="checkbox" :checked="task.isIncrement" disabled class="mr-2 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                增量更新
              </label>
              <label class="flex items-center text-sm text-white/60">
                <input type="checkbox" :checked="task.skipInvalidStructure" disabled class="mr-2 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                跳过异常目录
              </label>
            </div>

            <div class="mt-3" v-if="task.renameRegex">
              <dt class="text-sm text-white/40">STRM 文件名正则表达式</dt>
              <dd class="mt-1 text-sm text-white/80 font-mono bg-white/5 px-3 py-2 rounded break-all">{{ task.renameRegex }}</dd>
            </div>

            <div class="mt-3 text-xs text-white/30">
              创建时间: {{ formatDate(task.createdAt) }}
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <div class="text-center py-12" v-else>
          <div class="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg class="w-8 h-8 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v10a2 2 0 002 2h8a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"></path>
            </svg>
          </div>
          <h3 class="text-lg font-medium text-white mb-2">暂无任务配置</h3>
          <p class="text-white/40 mb-6">创建您的第一个任务配置</p>
          <button type="button" class="btn-primary" @click="showCreateTaskModal = true">
            <svg class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
            </svg>
            创建第一个任务
          </button>
        </div>
      </div>
    </template>

    <!-- 创建/编辑任务模态框 -->
    <Teleport to="body">
      <div v-if="showCreateTaskModal || showEditTaskModal" class="modal-overlay animate-fade-in">
        <div class="flex items-center justify-center min-h-screen p-4">
          <div
            class="modal-content animate-scale-in mx-0 flex max-h-[calc(100dvh-2rem)] w-full max-w-lg flex-col overflow-hidden p-0"
            @click.stop
          >
            <div class="flex shrink-0 items-center justify-between border-b border-white/10 px-4 py-4 sm:px-6">
              <h3 class="text-xl font-semibold text-white">
                {{ showCreateTaskModal ? '创建任务' : '编辑任务' }}
              </h3>
              <button @click="closeModal" class="btn-icon">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <form @submit.prevent="submitTask" class="flex min-h-0 flex-1 flex-col">
              <div class="min-h-0 flex-1 space-y-5 overflow-y-auto px-4 py-5 sm:px-6">
              <div>
                <label class="block text-sm text-white/70 mb-2">任务名称 *</label>
                <input v-model="taskForm.taskName" type="text" required class="input-field" placeholder="请输入任务名称">
              </div>

              <div>
                <label class="block text-sm text-white/70 mb-2">任务路径 *</label>
                <input v-model="taskForm.path" type="text" required class="input-field" placeholder="请输入OpenList中的媒体路径">
              </div>

              <div>
                <label class="block text-sm text-white/70 mb-2">媒体库类型 *</label>
                <select v-model="taskForm.libraryType" required class="input-field">
                  <option disabled value="">请选择当前任务目录的媒体类型</option>
                  <option value="movie">电影</option>
                  <option value="tv">电视剧</option>
                  <option value="anime">动画（按电视剧刮削）</option>
                  <option value="auto">自动识别（兼容旧任务）</option>
                </select>
                <p class="mt-1 text-xs text-white/30">
                  系统会按所选类型解释目录层级，并约束 TMDB 和 AI 的媒体类型
                </p>
              </div>

              <div>
                <label class="block text-sm text-white/70 mb-2">STRM路径</label>
                <div class="flex">
                  <span class="inline-flex items-center px-3 rounded-l-xl border border-r-0 border-white/10 bg-white/5 text-white/50 text-sm">
                    /app/backend/strm/
                  </span>
                  <input v-model="strmSubPath" type="text" placeholder="子路径（可选）" class="input-field rounded-l-none">
                </div>
                <p class="mt-1 text-xs text-white/30">前缀 /app/backend/strm/ 固定不可修改</p>
              </div>

              <div>
                <label class="block text-sm text-white/70 mb-2">定时任务表达式</label>
                <input v-model="taskForm.cron" type="text" placeholder="例如: 0 15 10 ? * *" class="input-field">
                <p class="mt-1 text-xs text-white/30">Cron表达式格式，留空表示不启用定时任务</p>
              </div>

              <div>
                <div class="flex items-center justify-between mb-2">
                  <label class="block text-sm text-white/70">STRM 文件名正则表达式</label>
                  <button type="button" @click="showRenameRegexHelp = !showRenameRegexHelp" class="text-white/40 hover:text-blue-400 transition-colors">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                  </button>
                </div>
                <input v-model="taskForm.renameRegex" type="text" placeholder="留空表示不需要重命名" class="input-field">
                <p class="mt-1 text-xs text-white/30">仅修改本地生成的 STRM 文件名，不修改 OpenList 源文件</p>

                <div v-if="showRenameRegexHelp" class="mt-3 p-4 bg-blue-500/10 border border-blue-500/20 rounded-xl">
                  <h4 class="text-sm font-medium text-blue-400 mb-2">使用说明</h4>
                  <div class="text-xs text-white/70 space-y-2">
                    <p><strong>格式：</strong>原始模式|替换内容</p>
                    <p><strong>示例：</strong></p>
                    <ul class="list-disc list-inside ml-2 space-y-1">
                      <li>移除方括号：<code class="bg-white/10 px-1 rounded">[\[\]()]|</code></li>
                      <li>空格转下划线：<code class="bg-white/10 px-1 rounded">\s+|_</code></li>
                      <li>添加前缀：<code class="bg-white/10 px-1 rounded">^|Movie_</code></li>
                    </ul>
                  </div>
                </div>
              </div>

              <div class="rounded-xl border border-white/10 bg-white/[0.03] p-4 space-y-4">
                <div>
                  <label class="block text-sm text-white/70 mb-2">任务完成后刷新媒体库</label>
                  <select v-model="taskForm.mediaRefreshScope" class="input-field" @change="onRefreshScopeChange">
                    <option value="NONE">不刷新（默认）</option>
                    <option value="ALL">刷新全部媒体库</option>
                    <option value="LIBRARY">刷新指定媒体库</option>
                  </select>
                  <p class="mt-1 text-xs text-white/30">全量任务始终提交刷新；增量任务仅在媒体有变化时提交</p>
                </div>

                <div v-if="taskForm.mediaRefreshScope !== 'NONE'">
                  <label class="block text-sm text-white/70 mb-2">媒体服务器 *</label>
                  <select v-model="taskForm.mediaServerConfigId" required class="input-field" @change="onMediaServerChange">
                    <option :value="null" disabled>请选择 Emby 或 Jellyfin</option>
                    <option v-for="server in mediaServers" :key="server.id" :value="server.id">
                      {{ server.name }}（{{ server.serverType }}{{ server.active ? '' : '，已停用' }}）
                    </option>
                  </select>
                  <p v-if="selectedMediaServer && !selectedMediaServer.active" class="mt-2 text-xs text-red-300">
                    ⚠️ 当前服务器已停用，请重新选择、启用配置或关闭刷新后再保存
                  </p>
                </div>

                <div v-if="taskForm.mediaRefreshScope === 'LIBRARY'">
                  <label class="block text-sm text-white/70 mb-2">目标媒体库 *</label>
                  <select
                    v-model="taskForm.mediaLibraryId"
                    required
                    class="input-field"
                    :disabled="libraryLoadState === 'loading' || !taskForm.mediaServerConfigId"
                    @change="onMediaLibraryChange"
                  >
                    <option value="" disabled>{{ libraryLoadState === 'loading' ? '正在读取媒体库...' : '请选择媒体库' }}</option>
                    <option v-if="libraryConfirmedStale" :value="taskForm.mediaLibraryId" disabled>
                      ⚠️ 已失效：{{ taskForm.mediaLibraryName || taskForm.mediaLibraryId }}
                    </option>
                    <option
                      v-else-if="libraryLoadState === 'error' && taskForm.mediaLibraryId"
                      :value="taskForm.mediaLibraryId"
                      disabled
                    >
                      暂时无法验证：{{ taskForm.mediaLibraryName || taskForm.mediaLibraryId }}
                    </option>
                    <option v-for="library in mediaLibraries" :key="library.id" :value="library.id">
                      {{ library.name }}
                    </option>
                  </select>
                  <p v-if="libraryConfirmedStale" class="mt-2 text-xs text-red-300">
                    ⚠️ 已保存的媒体库已不存在。系统不会按名称匹配，也不会回退为全部刷新，请重新选择后再保存。
                  </p>
                  <p v-else-if="libraryLoadState === 'error'" class="mt-2 text-xs text-amber-300">
                    暂时无法验证媒体库：{{ libraryLoadError }}。已保留原配置，可继续保存其他修改。
                  </p>
                </div>
              </div>

              <div class="space-y-3">
                <label class="flex items-start cursor-pointer">
                  <input v-model="taskForm.needScrap" type="checkbox" class="mt-1 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                  <span class="ml-2 text-sm text-white/70">
                    需要刮削
                    <span class="block text-xs text-white/40 mt-0.5">启用TMDB刮削功能，生成NFO和封面</span>
                  </span>
                </label>

                <label
                  class="flex items-start"
                  :class="!taskForm.needScrap || taskForm.libraryType === 'auto' || !taskForm.libraryType ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'"
                >
                  <input
                    v-model="taskForm.autoRenameMedia"
                    type="checkbox"
                    :disabled="!taskForm.needScrap || taskForm.libraryType === 'auto' || !taskForm.libraryType"
                    class="mt-1 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500"
                  >
                  <span class="ml-2 text-sm text-white/70">
                    普通任务自动重命名媒体
                    <span class="block text-xs text-white/40 mt-0.5">
                      执行任务时根据 TMDB 结果重命名 OpenList 媒体目录和文件，默认关闭；需要 OpenList 写入权限
                    </span>
                  </span>
                </label>

                <label
                  class="flex items-start"
                  :class="taskForm.libraryType === 'auto' || !taskForm.libraryType ? 'cursor-not-allowed opacity-50' : 'cursor-pointer'"
                >
                  <input
                    v-model="taskForm.skipInvalidStructure"
                    type="checkbox"
                    :disabled="taskForm.libraryType === 'auto' || !taskForm.libraryType"
                    class="mt-1 h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500"
                  >
                  <span class="ml-2 text-sm text-white/70">
                    跳过目录结构不符合的视频
                    <span class="block text-xs text-white/40 mt-0.5">
                      执行时不生成 STRM、也不刮削；增量任务会清理此前生成的异常文件
                    </span>
                  </span>
                </label>

                <label class="flex items-center cursor-pointer">
                  <input v-model="taskForm.isIncrement" type="checkbox" class="h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                  <span class="ml-2 text-sm text-white/70">增量更新</span>
                </label>

                <label class="flex items-center cursor-pointer">
                  <input v-model="taskForm.isActive" type="checkbox" class="h-4 w-4 rounded border-white/20 bg-white/5 text-blue-500">
                  <span class="ml-2 text-sm text-white/70">启用任务</span>
                </label>
              </div>
              </div>

              <div class="flex shrink-0 justify-end gap-3 border-t border-white/10 bg-[#0A0A0F] px-4 py-4 sm:px-6">
                <button type="button" @click="closeModal" class="btn-secondary">取消</button>
                <button type="submit" :disabled="submitting" class="btn-primary">
                  <svg v-if="submitting" class="loading-spinner -ml-1 mr-2 w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ submitting ? '保存中...' : (showCreateTaskModal ? '创建' : '保存') }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 执行模式选择模态框 -->
    <Teleport to="body">
      <div v-if="showExecuteTaskModal" class="modal-overlay animate-fade-in">
        <div class="flex items-center justify-center min-h-screen p-4">
          <div class="modal-content animate-scale-in w-full max-w-md" @click.stop>
            <div class="flex items-center justify-between mb-6">
              <h3 class="text-xl font-semibold text-white">选择执行模式</h3>
              <button @click="closeExecuteModal" class="btn-icon">
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <div class="space-y-4">
              <p class="text-sm text-white/50">请选择任务执行模式：</p>

              <button @click="executeTask(currentTaskId, false)" class="w-full flex items-center justify-between p-4 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl transition-all cursor-pointer">
                <div class="text-left">
                  <div class="font-medium text-white">全量执行</div>
                  <div class="text-sm text-white/40">清空STRM目录，重新生成所有文件</div>
                </div>
                <svg class="w-5 h-5 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                </svg>
              </button>

              <button @click="executeTask(currentTaskId, true)" class="w-full flex items-center justify-between p-4 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl transition-all cursor-pointer">
                <div class="text-left">
                  <div class="font-medium text-white">增量执行</div>
                  <div class="text-sm text-white/40">只处理变化的文件，清理孤立文件</div>
                </div>
                <svg class="w-5 h-5 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 目录结构检查模态框 -->
    <Teleport to="body">
      <div v-if="showStructureCheckModal" class="modal-overlay animate-fade-in">
        <div class="flex min-h-screen items-center justify-center p-4">
          <div class="modal-content animate-scale-in flex max-h-[85vh] w-full max-w-3xl flex-col" @click.stop>
            <div class="mb-5 flex items-start justify-between gap-4">
              <div>
                <h3 class="text-xl font-semibold text-white">目录结构检查</h3>
                <p class="mt-1 text-sm text-white/40">
                  {{ structureCheckTask?.taskName }}
                  <span v-if="structureCheckTask">· {{ libraryTypeLabel(structureCheckTask.libraryType) }}</span>
                </p>
              </div>
              <button
                @click="closeStructureCheckModal"
                class="btn-icon"
                :disabled="Boolean(checkingStructureTaskId || checkingStructureDirectoryPath)"
              >
                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
                </svg>
              </button>
            </div>

            <div v-if="checkingStructureTaskId" class="flex flex-1 flex-col items-center justify-center py-16">
              <div class="h-10 w-10 animate-spin rounded-full border-4 border-amber-400 border-t-transparent"></div>
              <p class="mt-4 text-sm text-white/50">正在读取任务根目录...</p>
            </div>

            <div v-else-if="structureCheckError" class="rounded-xl border border-red-500/20 bg-red-500/10 p-4 text-sm text-red-300">
              {{ structureCheckError }}
            </div>

            <div v-else-if="structureCheckOverview" class="min-h-0 flex-1 overflow-y-auto pr-1">
              <div class="mb-4 rounded-xl border border-white/10 bg-white/5 p-4">
                <div class="text-xs text-white/40">期望目录结构</div>
                <div class="mt-1 font-mono text-sm text-amber-300">{{ structureCheckOverview.expectedStructure }}</div>
                <div class="mt-2 break-all text-xs text-white/35">任务根目录：{{ structureCheckOverview.rootPath }}</div>
              </div>

              <div v-if="!structureCheckOverview.supported" class="rounded-xl border border-amber-500/20 bg-amber-500/10 p-4 text-sm text-amber-200">
                {{ structureCheckOverview.message }}
              </div>

              <template v-else>
                <div
                  v-if="structureCheckOverview.rootFilesResult?.invalidFileCount"
                  class="mb-4 rounded-xl border border-red-500/20 bg-red-500/10 p-4"
                >
                  <div class="flex items-center justify-between gap-3">
                    <div>
                      <div class="font-medium text-red-200">任务根目录存在异常媒体文件</div>
                      <div class="mt-1 text-xs text-red-200/60">
                        根目录下的视频文件不属于任何第一层媒体目录
                      </div>
                    </div>
                    <span class="rounded-full bg-red-500/15 px-2.5 py-1 text-xs text-red-300">
                      {{ structureCheckOverview.rootFilesResult.invalidFileCount }} 个异常
                    </span>
                  </div>
                  <div class="mt-3 rounded-lg border border-white/10 bg-black/20 p-2">
                    <TaskStructureTreeNode :node="structureCheckOverview.rootFilesResult.tree" />
                  </div>
                </div>

                <div class="mb-3 flex items-center justify-between">
                  <div>
                    <h4 class="font-medium text-white">第一层媒体目录</h4>
                    <p class="mt-1 text-xs text-white/35">点击检查后，只递归扫描对应目录</p>
                  </div>
                  <span class="text-xs text-white/35">{{ structureCheckOverview.directories.length }} 个目录</span>
                </div>

                <div v-if="structureCheckOverview.directories.length" class="space-y-3">
                  <div
                    v-for="directory in structureCheckOverview.directories"
                    :key="directory.path"
                    class="rounded-xl border border-white/10 bg-white/[0.03] p-4"
                  >
                    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div class="min-w-0">
                        <div class="truncate font-medium text-white/85">{{ directory.name }}</div>
                        <div class="mt-1 truncate font-mono text-xs text-white/30">{{ directory.path }}</div>
                      </div>
                      <button
                        type="button"
                        class="btn-secondary shrink-0 justify-center"
                        :disabled="Boolean(checkingStructureDirectoryPath)"
                        @click="checkStructureDirectory(directory)"
                      >
                        <svg
                          v-if="checkingStructureDirectoryPath === directory.path"
                          class="mr-2 h-4 w-4 animate-spin"
                          fill="none"
                          viewBox="0 0 24 24"
                        >
                          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
                          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                        </svg>
                        {{ checkingStructureDirectoryPath === directory.path
                          ? '检查中...'
                          : structureDirectoryStates[directory.path]?.result
                            ? '重新检查'
                            : '检查' }}
                      </button>
                    </div>

                    <div
                      v-if="structureDirectoryStates[directory.path]?.error"
                      class="mt-3 rounded-lg bg-red-500/10 p-3 text-sm text-red-300"
                    >
                      {{ structureDirectoryStates[directory.path].error }}
                    </div>

                    <div v-if="structureDirectoryStates[directory.path]?.result" class="mt-3">
                      <div class="flex flex-wrap gap-2 text-xs">
                        <span class="rounded-md bg-white/5 px-2 py-1 text-white/45">
                          扫描 {{ structureDirectoryStates[directory.path].result.scannedEntryCount }} 项
                        </span>
                        <span class="rounded-md bg-blue-500/10 px-2 py-1 text-blue-300">
                          视频 {{ structureDirectoryStates[directory.path].result.videoFileCount }}
                        </span>
                        <span
                          class="rounded-md px-2 py-1"
                          :class="structureDirectoryStates[directory.path].result.invalidFileCount
                            ? 'bg-red-500/10 text-red-300'
                            : 'bg-emerald-500/10 text-emerald-300'"
                        >
                          {{ structureDirectoryStates[directory.path].result.invalidFileCount
                            ? `异常 ${structureDirectoryStates[directory.path].result.invalidFileCount}`
                            : '检查通过' }}
                        </span>
                      </div>
                      <div
                        v-if="structureDirectoryStates[directory.path].result.invalidFileCount"
                        class="mt-3 rounded-lg border border-white/10 bg-black/20 p-2"
                      >
                        <TaskStructureTreeNode :node="structureDirectoryStates[directory.path].result.tree" />
                      </div>
                    </div>
                  </div>
                </div>

                <div v-else class="rounded-xl border border-white/10 bg-white/5 p-5 text-center text-sm text-white/45">
                  {{ structureCheckOverview.message }}
                </div>
              </template>
            </div>

            <div class="mt-5 flex justify-end">
              <button
                type="button"
                class="btn-secondary"
                :disabled="Boolean(checkingStructureTaskId || checkingStructureDirectoryPath)"
                @click="closeStructureCheckModal"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import TaskStructureTreeNode from '~/components/TaskStructureTreeNode.vue'
import logger from '~/core/utils/logger'
import { useRoute, useRouter } from 'vue-router'
import { apiCall, authenticatedApiCall } from '~/core/api/client'

const route = useRoute()
const router = useRouter()
const configId = route.params.id

const configInfo = ref(null)
const tasks = ref([])
const mediaServers = ref([])
const mediaLibraries = ref([])
const libraryLoadState = ref('idle')
const libraryLoadError = ref('')
const loading = ref(true)
const showCreateTaskModal = ref(false)
const showEditTaskModal = ref(false)
const showExecuteTaskModal = ref(false)
const showStructureCheckModal = ref(false)
const submitting = ref(false)
const editingTaskId = ref(null)
const currentTaskId = ref(null)
const generatingStrm = ref({})
const checkingStructureTaskId = ref(null)
const checkingStructureDirectoryPath = ref('')
const structureCheckTask = ref(null)
const structureCheckOverview = ref(null)
const structureDirectoryStates = ref({})
const structureCheckError = ref('')
const taskForm = ref({
  taskName: '',
  path: '',
  libraryType: '',
  strmPath: '/app/backend/strm',
  cron: '',
  needScrap: false,
  autoRenameMedia: false,
  skipInvalidStructure: false,
  renameRegex: '',
  mediaServerConfigId: null,
  mediaRefreshScope: 'NONE',
  mediaLibraryId: '',
  mediaLibraryName: '',
  isIncrement: true,
  isActive: true
})
const strmSubPath = ref('')
const showRenameRegexHelp = ref(false)
const selectedMediaServer = computed(() => mediaServers.value.find(server => server.id === taskForm.value.mediaServerConfigId))
const libraryConfirmedStale = computed(() =>
  taskForm.value.mediaRefreshScope === 'LIBRARY' &&
  libraryLoadState.value === 'success' &&
  Boolean(taskForm.value.mediaLibraryId) &&
  !mediaLibraries.value.some(library => library.id === taskForm.value.mediaLibraryId)
)

watch(
  [() => taskForm.value.needScrap, () => taskForm.value.libraryType],
  ([needScrap, libraryType]) => {
    if (!needScrap || !libraryType || libraryType === 'auto') {
      taskForm.value.autoRenameMedia = false
    }
  }
)

const getConfigInfo = async () => {
  try {
    loading.value = true
    const response = await authenticatedApiCall(`/openlist-config/${configId}`, { method: 'GET' })
    if (response.code === 200) {
      configInfo.value = response.data
    } else {
      logger.error('获取配置信息失败:', response.message)
      await navigateTo('/')
    }
  } catch (error) {
    logger.error('获取配置信息时发生错误:', error)
    await navigateTo('/')
  } finally {
    loading.value = false
  }
}

const fetchTasks = async () => {
  try {
    const response = await authenticatedApiCall('/task-config', { method: 'GET' })
    if (response.code === 200) {
      tasks.value = response.data.filter(task => task.openlistConfigId == configId)
    }
  } catch (error) {
    logger.error('获取任务列表失败:', error)
  }
}

const fetchMediaServers = async () => {
  try {
    const response = await authenticatedApiCall('/media-servers', { method: 'GET' })
    if (response.code === 200) mediaServers.value = response.data || []
  } catch (error) {
    logger.error('获取媒体服务器列表失败:', error)
  }
}

const loadMediaLibraries = async () => {
  mediaLibraries.value = []
  libraryLoadError.value = ''
  if (!taskForm.value.mediaServerConfigId) {
    libraryLoadState.value = 'idle'
    return
  }
  libraryLoadState.value = 'loading'
  try {
    const response = await authenticatedApiCall(`/media-servers/${taskForm.value.mediaServerConfigId}/libraries`, { method: 'GET' })
    if (response.code !== 200) throw new Error(response.message || '媒体库读取失败')
    mediaLibraries.value = response.data || []
    libraryLoadState.value = 'success'
  } catch (error) {
    libraryLoadState.value = 'error'
    libraryLoadError.value = error.message || '媒体库读取失败'
  }
}

const onRefreshScopeChange = () => {
  if (taskForm.value.mediaRefreshScope === 'NONE') {
    taskForm.value.mediaServerConfigId = null
    taskForm.value.mediaLibraryId = ''
    taskForm.value.mediaLibraryName = ''
    mediaLibraries.value = []
    libraryLoadState.value = 'idle'
  } else if (taskForm.value.mediaRefreshScope === 'ALL') {
    taskForm.value.mediaLibraryId = ''
    taskForm.value.mediaLibraryName = ''
  } else if (taskForm.value.mediaServerConfigId) {
    loadMediaLibraries()
  }
}

const onMediaServerChange = () => {
  taskForm.value.mediaLibraryId = ''
  taskForm.value.mediaLibraryName = ''
  loadMediaLibraries()
}

const onMediaLibraryChange = () => {
  const selected = mediaLibraries.value.find(library => library.id === taskForm.value.mediaLibraryId)
  taskForm.value.mediaLibraryName = selected?.name || ''
}

const resetTaskForm = () => {
  taskForm.value = {
    taskName: '', path: '', strmPath: '/app/backend/strm', cron: '',
    libraryType: '', needScrap: false, autoRenameMedia: false, skipInvalidStructure: false,
    renameRegex: '', mediaServerConfigId: null, mediaRefreshScope: 'NONE', mediaLibraryId: '',
    mediaLibraryName: '', isIncrement: true, isActive: true
  }
  strmSubPath.value = ''
  showRenameRegexHelp.value = false
}

const editTask = (task) => {
  editingTaskId.value = task.id
  taskForm.value = {
    taskName: task.taskName, path: task.path, strmPath: task.strmPath,
    libraryType: task.libraryType || 'auto', cron: task.cron || '', needScrap: task.needScrap || false,
    autoRenameMedia: task.autoRenameMedia || false,
    skipInvalidStructure: task.libraryType && task.libraryType !== 'auto' ? task.skipInvalidStructure || false : false,
    renameRegex: task.renameRegex || '', mediaServerConfigId: task.mediaServerConfigId || null,
    mediaRefreshScope: task.mediaRefreshScope || 'NONE', mediaLibraryId: task.mediaLibraryId || '',
    mediaLibraryName: task.mediaLibraryName || '', isIncrement: task.isIncrement, isActive: task.isActive
  }
  const prefix = '/app/backend/strm/'
  strmSubPath.value = task.strmPath?.startsWith(prefix) ? task.strmPath.substring(prefix.length) : ''
  showEditTaskModal.value = true
  if (taskForm.value.mediaRefreshScope === 'LIBRARY' && taskForm.value.mediaServerConfigId) loadMediaLibraries()
}

const validateTaskPath = async (taskPath) => {
  try {
    if (!configInfo.value) throw new Error('配置信息未加载')
    const response = await authenticatedApiCall('/openlist-config/validate-path', {
      method: 'POST',
      body: { openlistConfigId: parseInt(configId), taskPath }
    })
    if (response.code !== 200) throw new Error(response.message || '路径验证失败')
  } catch (error) {
    throw new Error(error.message || '路径验证失败，请检查路径是否正确')
  }
}

const openManualScraping = (task) => {
  router.push(`/manual-scraping/${task.id}`)
}

const submitTask = async () => {
  try {
    submitting.value = true
    if (selectedMediaServer.value && !selectedMediaServer.value.active) {
      throw new Error('选择的媒体服务器已停用，请重新选择或关闭刷新')
    }
    if (libraryConfirmedStale.value) {
      throw new Error('已保存的媒体库已失效，请重新选择媒体库后再保存')
    }
    if (taskForm.value.path) await validateTaskPath(taskForm.value.path)
    const fullStrmPath = '/app/backend/strm/' + (strmSubPath.value || '')
    const taskData = {
      ...taskForm.value,
      autoRenameMedia: taskForm.value.needScrap && taskForm.value.libraryType !== 'auto'
        ? taskForm.value.autoRenameMedia
        : false,
      skipInvalidStructure: taskForm.value.libraryType === 'auto' ? false : taskForm.value.skipInvalidStructure,
      strmPath: fullStrmPath,
      openlistConfigId: parseInt(configId)
    }
    let response
    if (showCreateTaskModal.value) {
      response = await authenticatedApiCall('/task-config', { method: 'POST', body: taskData })
    } else {
      response = await authenticatedApiCall(`/task-config/${editingTaskId.value}`, { method: 'PUT', body: taskData })
    }
    if (response.code === 200) {
      await fetchTasks()
      closeModal()
    } else {
      throw new Error(response.message || '操作失败')
    }
  } catch (error) {
    logger.error('提交任务失败:', error)
    alert(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

const deleteTask = async (taskId) => {
  if (!confirm('确定要删除这个任务吗？')) return
  try {
    const response = await authenticatedApiCall(`/task-config/${taskId}`, { method: 'DELETE' })
    if (response.code === 200) await fetchTasks()
    else throw new Error(response.message || '删除失败')
  } catch (error) {
    logger.error('删除任务失败:', error)
  }
}

const closeModal = () => {
  showCreateTaskModal.value = false
  showEditTaskModal.value = false
  editingTaskId.value = null
  resetTaskForm()
}

const formatDate = (timestamp) => !timestamp || timestamp === 0 ? '未执行' : new Date(timestamp).toLocaleString('zh-CN')

const libraryTypeLabel = (libraryType) => ({
  movie: '电影',
  tv: '电视剧',
  anime: '动画',
  auto: '自动识别'
}[libraryType || 'auto'] || '自动识别')

const taskRefreshLabel = (task) => {
  if (!task.mediaRefreshScope || task.mediaRefreshScope === 'NONE') return '不刷新'
  const server = mediaServers.value.find(item => item.id === task.mediaServerConfigId)
  const serverName = server?.name || `配置 #${task.mediaServerConfigId}`
  return task.mediaRefreshScope === 'ALL'
    ? `${serverName} · 全部媒体库`
    : `${serverName} · ${task.mediaLibraryName || '指定媒体库'}`
}

const showExecuteModal = (taskId) => {
  currentTaskId.value = taskId
  showExecuteTaskModal.value = true
}

const closeExecuteModal = () => {
  showExecuteTaskModal.value = false
  currentTaskId.value = null
}

const executeTask = async (taskId, isIncremental) => {
  try {
    generatingStrm.value[taskId] = true
    closeExecuteModal()
    const response = await authenticatedApiCall(`/task-config/${taskId}/submit`, {
      method: 'POST',
      body: { isIncremental }
    })
    if (response.code === 200) {
      const modeText = isIncremental ? '增量' : '全量'
      alert(`任务已提交，正在后台执行${modeText}生成STRM文件...`)
    } else {
      throw new Error(response.message || '提交任务失败')
    }
  } catch (error) {
    logger.error('提交任务失败:', error)
    alert(error.message || '提交任务失败，请稍后重试')
  } finally {
    generatingStrm.value[taskId] = false
  }
}

const checkTaskStructure = async (task) => {
  showStructureCheckModal.value = true
  structureCheckTask.value = task
  structureCheckOverview.value = null
  structureDirectoryStates.value = {}
  structureCheckError.value = ''
  checkingStructureTaskId.value = task.id
  try {
    const response = await authenticatedApiCall(`/task-config/${task.id}/structure-check/directories`, {
      method: 'GET'
    })
    if (response.code === 200) {
      structureCheckOverview.value = response.data
    } else {
      throw new Error(response.message || '任务根目录读取失败')
    }
  } catch (error) {
    logger.error('读取待检查目录失败:', error)
    structureCheckError.value = error.message || '任务根目录读取失败，请稍后重试'
  } finally {
    checkingStructureTaskId.value = null
  }
}

const checkStructureDirectory = async (directory) => {
  if (!structureCheckTask.value || checkingStructureDirectoryPath.value) return
  checkingStructureDirectoryPath.value = directory.path
  structureDirectoryStates.value = {
    ...structureDirectoryStates.value,
    [directory.path]: { error: '', result: null }
  }
  try {
    const response = await authenticatedApiCall(
      `/task-config/${structureCheckTask.value.id}/structure-check/directory`,
      {
        method: 'POST',
        body: { directoryPath: directory.path }
      }
    )
    if (response.code !== 200) throw new Error(response.message || '目录检查失败')
    structureDirectoryStates.value = {
      ...structureDirectoryStates.value,
      [directory.path]: { error: '', result: response.data }
    }
  } catch (error) {
    logger.error('检查第一层目录失败:', error)
    structureDirectoryStates.value = {
      ...structureDirectoryStates.value,
      [directory.path]: { error: error.message || '目录检查失败，请稍后重试', result: null }
    }
  } finally {
    checkingStructureDirectoryPath.value = ''
  }
}

const closeStructureCheckModal = () => {
  if (checkingStructureTaskId.value || checkingStructureDirectoryPath.value) return
  showStructureCheckModal.value = false
  structureCheckTask.value = null
  structureCheckOverview.value = null
  structureDirectoryStates.value = {}
  checkingStructureDirectoryPath.value = ''
  structureCheckError.value = ''
}

onMounted(() => {
  getConfigInfo()
  fetchTasks()
  fetchMediaServers()
})
</script>
