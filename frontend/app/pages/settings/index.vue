<template>
  <div class="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
    <div class="animate-fade-in">
      <!-- 页面标题 -->
      <div class="text-center mb-8">
        <div class="w-14 h-14 bg-gradient-to-br from-purple-500 to-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-4">
          <svg class="w-7 h-7 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"></path>
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"></path>
          </svg>
        </div>
        <h1 class="text-3xl font-bold gradient-text mb-2">系统设置</h1>
        <p class="text-white/40">配置系统参数和功能选项</p>
      </div>

      <div class="space-y-6">
        <!-- 媒体文件后缀设置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"></path>
            </svg>
            媒体文件设置
          </h3>
          <div class="space-y-4">
            <label class="block text-sm text-white/70">生成 STRM 媒体文件后缀</label>
            <div class="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-2">
              <label v-for="extension in availableExtensions" :key="extension" class="flex items-center gap-2 cursor-pointer p-2 rounded-lg hover:bg-white/5 transition-colors">
                <input :id="extension" v-model="selectedExtensions" :value="extension" type="checkbox" class="h-4 w-4 rounded" />
                <span class="text-sm text-white/80">{{ extension }}</span>
              </label>
            </div>
          </div>
        </div>

        <!-- TMDB API 配置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-purple-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"></path>
            </svg>
            TMDB API 配置
          </h3>
          <div class="space-y-5">
            <div>
              <label for="tmdbApiKey" class="block text-sm text-white/70 mb-2">TMDB API Key</label>
              <div class="flex">
                <input :id="tmdbApiKey" v-model="tmdbConfig.apiKey" :type="showApiKey ? 'text' : 'password'" class="input-field rounded-r-none" placeholder="请输入 TMDB API Key" />
                <button type="button" @click="toggleApiKeyVisibility" class="px-4 bg-white/5 border border-l-0 border-white/10 rounded-r-xl hover:bg-white/10 transition-colors text-sm text-white/60">
                  {{ showApiKey ? '隐藏' : '显示' }}
                </button>
              </div>
              <p class="mt-1 text-xs text-white/30">
                请在 <a href="https://www.themoviedb.org/settings/api" target="_blank" class="text-blue-400 hover:text-blue-300">TMDB 官网</a> 申请 API Key
              </p>
            </div>

            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label for="tmdbLanguage" class="block text-sm text-white/70 mb-2">语言设置</label>
                <v-select
                  id="tmdbLanguage"
                  v-model="tmdbConfig.language"
                  :options="tmdbLanguageOptions"
                  :reduce="(opt: any) => opt.value"
                  :clearable="false"
                  class="vue-select-md"
                />
              </div>
              <div>
                <label for="tmdbRegion" class="block text-sm text-white/70 mb-2">地区设置</label>
                <v-select
                  id="tmdbRegion"
                  v-model="tmdbConfig.region"
                  :options="tmdbRegionOptions"
                  :reduce="(opt: any) => opt.value"
                  :clearable="false"
                  class="vue-select-md"
                />
              </div>
            </div>

            <!-- HTTP 代理配置 -->
            <div class="p-4 bg-white/5 rounded-xl border border-white/6">
              <h4 class="text-sm font-medium text-white/80 mb-3">HTTP 代理配置</h4>
              <p class="text-xs text-white/40 mb-4">如果需要通过代理访问 TMDB API，请配置以下选项（可选）</p>
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label for="tmdbProxyHost" class="block text-xs text-white/50 mb-1">代理主机地址</label>
                  <input id="tmdbProxyHost" v-model="tmdbConfig.proxyHost" type="text" class="input-field" placeholder="例如: 127.0.0.1" />
                </div>
                <div>
                  <label for="tmdbProxyPort" class="block text-xs text-white/50 mb-1">代理端口</label>
                  <input id="tmdbProxyPort" v-model="tmdbConfig.proxyPort" type="text" class="input-field" placeholder="例如: 7890" />
                </div>
              </div>
            </div>

            <!-- TMDB API 域名配置 -->
            <div class="p-4 bg-white/5 rounded-xl border border-white/6">
              <h4 class="text-sm font-medium text-white/80 mb-3">API 域名配置</h4>
              <p class="text-xs text-white/40 mb-4">选择 TMDB API 域名</p>
              <div class="space-y-4">
                <!-- API 域名 -->
                <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label for="tmdbApiDomain" class="block text-xs text-white/50 mb-1">API 域名</label>
                    <v-select
                      id="tmdbApiDomain"
                      v-model="tmdbApiDomain"
                      :options="tmdbApiDomainOptions"
                      :reduce="(opt: any) => opt.value"
                      :clearable="false"
                      class="vue-select-md"
                      @update:modelValue="handleApiDomainChange"
                    />
                  </div>
                  <div v-if="tmdbApiDomain === 'custom'">
                    <label for="tmdbApiUrl" class="block text-xs text-white/50 mb-1">自定义 API 域名</label>
                    <input id="tmdbApiUrl" v-model="tmdbConfig.baseUrl" type="text" class="input-field" placeholder="https://api.example.com" />
                  </div>
                </div>
                <div class="text-xs text-blue-400/80 bg-blue-500/10 p-2 rounded">
                  当前 API URL：<code class="text-blue-300">{{ tmdbConfig.baseUrl.replace('/3', '') }}</code>
                </div>

                <!-- 图片域名 -->
                <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label for="tmdbImageDomain" class="block text-xs text-white/50 mb-1">图片域名</label>
                    <v-select
                      id="tmdbImageDomain"
                      v-model="tmdbImageDomain"
                      :options="tmdbImageDomainOptions"
                      :reduce="(opt: any) => opt.value"
                      :clearable="false"
                      class="vue-select-md"
                      @update:modelValue="handleImageDomainChange"
                    />
                  </div>
                  <div v-if="tmdbImageDomain === 'custom'">
                    <label for="tmdbImageUrl" class="block text-xs text-white/50 mb-1">自定义图片域名</label>
                    <input id="tmdbImageUrl" v-model="tmdbConfig.imageBaseUrl" type="text" class="input-field" placeholder="https://image.example.com" />
                  </div>
                </div>
                <div class="text-xs text-blue-400/80 bg-blue-500/10 p-2 rounded">
                  当前图片 URL：<code class="text-blue-300">{{ tmdbConfig.imageBaseUrl.replace('/t/p', '') }}</code>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 刮削设置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-amber-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
            刮削设置
          </h3>
          <div class="space-y-4">
            <label class="flex items-center gap-3 p-4 bg-white/5 rounded-xl cursor-pointer">
              <input id="scrapingEnabled" v-model="scrapingConfig.enabled" type="checkbox" class="h-5 w-5 rounded" />
              <div>
                <span class="text-sm font-medium text-white">启用刮削功能</span>
              </div>
            </label>

            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <label class="flex items-start gap-3 p-4 bg-white/5 rounded-xl cursor-pointer">
                <input id="keepSubtitleFiles" v-model="scrapingConfig.keepSubtitleFiles" type="checkbox" class="h-5 w-5 rounded mt-0.5" />
                <div>
                  <span class="text-sm font-medium text-white">保留字幕文件</span>
                  <p class="text-xs text-white/40 mt-1">复制媒体文件同级目录的.srt、.ass字幕文件到STRM目录</p>
                </div>
              </label>

              <label class="flex items-start gap-3 p-4 bg-white/5 rounded-xl cursor-pointer">
                <input id="useExistingScrapingInfo" v-model="scrapingConfig.useExistingScrapingInfo" type="checkbox" class="h-5 w-5 rounded mt-0.5" />
                <div>
                  <span class="text-sm font-medium text-white">优先使用已存在的刮削信息</span>
                  <p class="text-xs text-white/40 mt-1">无论是否启用刮削功能，都会尝试复制媒体文件同级目录的NFO文件和刮削图片</p>
                </div>
              </label>
            </div>
          </div>
        </div>

        <!-- AI 识别设置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-cyan-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"></path>
            </svg>
            AI 文件名识别设置
          </h3>
          <div class="space-y-5">
            <label class="flex items-center gap-3 p-4 bg-cyan-500/5 rounded-xl border border-cyan-500/10 cursor-pointer">
              <input id="aiEnabled" v-model="aiConfig.enabled" type="checkbox" class="h-5 w-5 rounded" />
              <div>
                <span class="text-sm font-medium text-white">启用 AI 文件名识别</span>
                <p class="text-xs text-white/40 mt-0.5">（提高 TMDB 刮削准确性）</p>
              </div>
            </label>

            <div v-if="aiConfig.enabled" class="pl-4 border-l-2 border-cyan-500/20 space-y-5">
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label for="aiBaseUrl" class="block text-sm text-white/70 mb-2">API 基础 URL</label>
                  <input id="aiBaseUrl" v-model="aiConfig.baseUrl" type="url" class="input-field" placeholder="https://api.openai.com/v1" />
                </div>
                <div>
                  <label for="aiApiKey" class="block text-sm text-white/70 mb-2">API Key</label>
                  <input id="aiApiKey" v-model="aiConfig.apiKey" type="password" class="input-field" placeholder="sk-..." />
                </div>
                <div>
                  <label for="aiModel" class="block text-sm text-white/70 mb-2">模型名称</label>
                  <input id="aiModel" v-model="aiConfig.model" type="text" class="input-field" placeholder="gpt-3.5-turbo" />
                </div>
                <div>
                  <label for="aiQpmLimit" class="block text-sm text-white/70 mb-2">QPM 限制</label>
                  <input id="aiQpmLimit" v-model.number="aiConfig.qpmLimit" type="number" min="1" max="1000" class="input-field" placeholder="60" />
                  <p class="mt-1 text-xs text-white/30">每分钟最大请求数</p>
                </div>
              </div>

              <div class="flex items-center gap-3">
                <button type="button" @click="testAiConfig" class="btn-success" :disabled="testingAi">
                  <svg v-if="testingAi" class="loading-spinner -ml-1 mr-2 w-4 h-4" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  {{ testingAi ? '测试中...' : '测试配置' }}
                </button>
                <span v-if="aiTestResult" :class="aiTestResult.success ? 'text-emerald-400' : 'text-red-400'" class="text-sm font-medium">
                  {{ aiTestResult.message }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 媒体服务器 -->
        <div class="card">
          <div class="flex items-center justify-between gap-4 mb-4">
            <div>
              <h3 class="text-lg font-semibold text-white">Emby / Jellyfin</h3>
              <p class="mt-1 text-xs text-white/40">供任务在 STRM 生成完成后刷新全部或指定媒体库</p>
            </div>
            <button type="button" class="btn-primary" @click="openMediaServerEditor()">添加服务器</button>
          </div>

          <div v-if="mediaServers.length" class="space-y-3">
            <div v-for="server in mediaServers" :key="server.id" class="rounded-xl border border-white/10 bg-white/[0.03] p-4">
              <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div class="min-w-0">
                  <div class="flex items-center gap-2">
                    <span class="font-medium text-white">{{ server.name }}</span>
                    <span class="badge-neutral text-xs">{{ server.serverType }}</span>
                    <span :class="server.active ? 'badge-success' : 'badge-neutral'" class="text-xs">{{ server.active ? '启用' : '停用' }}</span>
                  </div>
                  <div class="mt-1 break-all font-mono text-xs text-white/35">{{ server.apiBaseUrl }}</div>
                </div>
                <div class="flex gap-2">
                  <button type="button" class="btn-secondary" @click="testSavedMediaServer(server)" :disabled="testingMediaServerId === server.id">
                    {{ testingMediaServerId === server.id ? '测试中...' : '测试' }}
                  </button>
                  <button type="button" class="btn-secondary" @click="openMediaServerEditor(server)">编辑</button>
                  <button type="button" class="btn-secondary text-red-300" @click="deleteMediaServer(server)">删除</button>
                </div>
              </div>
            </div>
          </div>
          <p v-else class="rounded-xl bg-white/5 p-5 text-center text-sm text-white/40">尚未添加媒体服务器</p>

          <div v-if="showMediaServerEditor" class="mt-5 rounded-xl border border-blue-500/20 bg-blue-500/5 p-4 space-y-4">
            <div class="flex items-center justify-between">
              <h4 class="font-medium text-white">{{ editingMediaServerId ? '编辑媒体服务器' : '添加媒体服务器' }}</h4>
              <button type="button" class="btn-icon" @click="closeMediaServerEditor">×</button>
            </div>
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label class="block text-sm text-white/70 mb-2">配置名称 *</label>
                <input v-model="mediaServerForm.name" class="input-field" placeholder="例如：客厅 Jellyfin" />
              </div>
              <div>
                <label class="block text-sm text-white/70 mb-2">类型 *</label>
                <select v-model="mediaServerForm.serverType" class="input-field">
                  <option value="EMBY">Emby</option>
                  <option value="JELLYFIN">Jellyfin</option>
                </select>
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm text-white/70 mb-2">API 根地址 *</label>
                <input v-model="mediaServerForm.apiBaseUrl" type="url" class="input-field" :placeholder="mediaServerForm.serverType === 'EMBY' ? 'http://emby:8096/emby' : 'http://jellyfin:8096'" />
                <p class="mt-1 text-xs text-white/30">请填写完整 API 根路径；Emby 常包含 /emby，Jellyfin 通常不包含额外路径</p>
              </div>
              <div class="sm:col-span-2">
                <label class="block text-sm text-white/70 mb-2">API Key {{ editingMediaServerId ? '' : '*' }}</label>
                <input v-model="mediaServerForm.apiKey" type="password" class="input-field" :placeholder="editingMediaServerId ? '留空则保留当前 API Key' : '请输入 API Key'" />
              </div>
            </div>
            <label class="flex items-center gap-2 cursor-pointer">
              <input v-model="mediaServerForm.isActive" type="checkbox" class="h-4 w-4 rounded" />
              <span class="text-sm text-white/70">启用此服务器</span>
            </label>
            <div class="flex flex-wrap items-center gap-3">
              <button type="button" class="btn-success" @click="testMediaServerForm" :disabled="testingMediaServerForm">{{ testingMediaServerForm ? '测试中...' : '测试连接' }}</button>
              <button type="button" class="btn-primary" @click="saveMediaServer" :disabled="savingMediaServer">{{ savingMediaServer ? '保存中...' : '保存服务器' }}</button>
              <button type="button" class="btn-secondary" @click="closeMediaServerEditor">取消</button>
            </div>
          </div>

          <p v-if="mediaServerMessage" :class="mediaServerMessage.success ? 'text-emerald-400' : 'text-red-400'" class="mt-3 text-sm">
            {{ mediaServerMessage.text }}
          </p>
        </div>

        <!-- 通知配置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-violet-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
            </svg>
            任务通知
          </h3>
          <div class="space-y-5">
            <label class="flex items-start gap-3 p-4 bg-violet-500/5 rounded-xl border border-violet-500/10 cursor-pointer">
              <input id="notificationEnabled" v-model="notificationConfig.enabled" type="checkbox" class="h-5 w-5 rounded mt-0.5" />
              <div>
                <span class="text-sm font-medium text-white">启用 Apprise 通知</span>
                <p class="text-xs text-white/40 mt-1">普通任务和手动刮削到达终态后发送通知，默认关闭</p>
              </div>
            </label>

            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div class="sm:col-span-2">
                <label for="appriseServerUrl" class="block text-sm text-white/70 mb-2">Apprise 服务地址</label>
                <input id="appriseServerUrl" v-model="notificationConfig.serverUrl" type="url" class="input-field" placeholder="http://apprise:8000" />
              </div>
              <div>
                <label for="appriseConfigKey" class="block text-sm text-white/70 mb-2">配置 Key</label>
                <input id="appriseConfigKey" v-model="notificationConfig.configKey" type="text" class="input-field" placeholder="ostrm" />
                <p class="mt-1 text-xs text-white/30">对应 Apprise API 中保存的配置标识</p>
              </div>
              <div>
                <label for="appriseTags" class="block text-sm text-white/70 mb-2">通知标签</label>
                <input id="appriseTags" v-model="notificationConfig.tags" type="text" class="input-field" placeholder="all" />
                <p class="mt-1 text-xs text-white/30">由 Apprise 将标签路由到一个或多个下游渠道</p>
              </div>
            </div>

            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <label class="flex items-center gap-2 p-3 bg-white/5 rounded-xl cursor-pointer">
                <input v-model="notificationConfig.notifyOnSuccess" type="checkbox" class="h-4 w-4 rounded" />
                <span class="text-sm text-white/70">成功通知</span>
              </label>
              <label class="flex items-center gap-2 p-3 bg-white/5 rounded-xl cursor-pointer">
                <input v-model="notificationConfig.notifyOnPartialSuccess" type="checkbox" class="h-4 w-4 rounded" />
                <span class="text-sm text-white/70">部分完成通知</span>
              </label>
              <label class="flex items-center gap-2 p-3 bg-white/5 rounded-xl cursor-pointer">
                <input v-model="notificationConfig.notifyOnFailure" type="checkbox" class="h-4 w-4 rounded" />
                <span class="text-sm text-white/70">失败通知</span>
              </label>
            </div>

            <label class="flex items-start gap-3 p-4 bg-white/5 rounded-xl cursor-pointer">
              <input v-model="notificationConfig.includeFullPath" type="checkbox" class="h-5 w-5 rounded mt-0.5" />
              <div>
                <span class="text-sm font-medium text-white">通知中显示完整路径</span>
                <p class="text-xs text-white/40 mt-1">完整路径有助于定位失败文件，但通知发送到外部渠道时可能包含目录结构等隐私信息。关闭后仅展示目录名和文件名。</p>
              </div>
            </label>

            <div class="flex flex-wrap items-end gap-3">
              <div class="w-44">
                <label for="notificationMaxDetails" class="block text-sm text-white/70 mb-2">每类最多展示</label>
                <input id="notificationMaxDetails" v-model.number="notificationConfig.maxDetailItems" type="number" min="1" max="20" class="input-field" />
              </div>
              <button type="button" @click="testNotification" class="btn-success" :disabled="testingNotification">
                {{ testingNotification ? '发送中...' : '发送测试通知' }}
              </button>
              <span v-if="notificationTestResult" :class="notificationTestResult.success ? 'text-emerald-400' : 'text-red-400'" class="text-sm font-medium">
                {{ notificationTestResult.message }}
              </span>
            </div>
          </div>
        </div>

        <!-- 日志配置 -->
        <div class="card">
          <h3 class="text-lg font-semibold text-white mb-4 flex items-center gap-2">
            <svg class="w-5 h-5 text-emerald-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
            </svg>
            日志配置设置
          </h3>
          <div class="space-y-5">
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <label for="logRetentionDays" class="block text-sm text-white/70 mb-2">日志保留时间</label>
                <v-select
                  id="logRetentionDays"
                  v-model.number="logConfig.retentionDays"
                  :options="logRetentionOptions"
                  :reduce="(opt: any) => opt.value"
                  :clearable="false"
                  class="vue-select-md"
                />
                <p class="mt-1 text-xs text-white/30">系统将在每天凌晨1:30自动清理过期日志文件</p>
              </div>
              <div>
                <label for="logLevel" class="block text-sm text-white/70 mb-2">日志级别</label>
                <v-select
                  id="logLevel"
                  v-model="logConfig.level"
                  :options="logLevelOptions"
                  :reduce="(opt: any) => opt.value"
                  :clearable="false"
                  class="vue-select-md"
                />
                <p class="mt-1 text-xs text-white/30">系统仅保留等于或高于所选级别的日志记录</p>
              </div>
            </div>

            <div class="border-t border-white/6 pt-5">
              <label class="flex items-start gap-3 cursor-pointer">
                <input id="reportUsageData" v-model="logConfig.reportUsageData" type="checkbox" class="h-5 w-5 rounded mt-0.5" />
                <div class="flex-1">
                  <span class="text-sm font-medium text-white">上报使用数据</span>
                  <p class="text-xs text-white/40 mt-1">帮助我们改进产品体验。即使勾选此选项，也不会上报任何用户隐私信息，仅收集匿名的功能使用统计数据。</p>
                </div>
              </label>
            </div>

            <div class="p-4 bg-amber-500/5 border border-amber-500/10 rounded-xl">
              <div class="flex items-start gap-3">
                <svg class="w-5 h-5 text-amber-400 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clip-rule="evenodd" />
                </svg>
                <div>
                  <h4 class="text-sm font-medium text-amber-300">注意事项</h4>
                  <ul class="mt-2 text-xs text-white/50 space-y-1">
                    <li>· 日志级别变更将在下次应用重启后生效</li>
                    <li>· Debug级别会产生大量日志，建议仅在调试时使用</li>
                    <li>· 日志清理任务会同时清理前端和后端的过期日志文件</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 保存按钮 -->
        <div class="flex justify-end gap-4 pt-4">
          <button type="button" @click="goBack" class="btn-secondary">取消</button>
          <button type="button" @click="saveSettings" class="btn-primary" :disabled="saving">
            <svg v-if="saving" class="loading-spinner -ml-1 mr-2 w-4 h-4" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ saving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 成功提示 -->
    <Teleport to="body">
      <div v-if="showSuccess" class="fixed top-4 right-4 bg-emerald-500 text-white px-6 py-3 rounded-xl shadow-2xl z-50 animate-slide-down flex items-center gap-2">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path>
        </svg>
        设置保存成功！
      </div>
    </Teleport>

    <!-- 错误提示 -->
    <Teleport to="body">
      <div v-if="errorMessage" class="fixed top-4 right-4 bg-red-500 text-white px-6 py-3 rounded-xl shadow-2xl z-50 animate-slide-down flex items-center gap-2">
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
        </svg>
        {{ errorMessage }}
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { authenticatedApiCall } from '~/core/api/client'
import { useAuthStore } from '~/core/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const availableExtensions = ref([])
const selectedExtensions = ref([])
const tmdbConfig = ref({ apiKey: '', language: 'zh-CN', region: 'CN', proxyHost: '', proxyPort: '', baseUrl: 'https://api.themoviedb.org', imageBaseUrl: 'https://image.tmdb.org' })
const scrapingConfig = ref({ enabled: true, keepSubtitleFiles: false, useExistingScrapingInfo: false })
const aiConfig = ref({ enabled: false, baseUrl: 'https://api.openai.com/v1', apiKey: '', model: 'gpt-3.5-turbo', qpmLimit: 60 })
const notificationConfig = ref({
  enabled: false,
  notifyOnSuccess: true,
  notifyOnPartialSuccess: true,
  notifyOnFailure: true,
  includeFullPath: true,
  maxDetailItems: 5,
  serverUrl: 'http://apprise:8000',
  configKey: 'ostrm',
  tags: 'all'
})
const logConfig = ref({ retentionDays: 7, level: 'info', reportUsageData: true })
const showApiKey = ref(false)
const saving = ref(false)
const showSuccess = ref(false)
const errorMessage = ref('')
const testingAi = ref(false)
const aiTestResult = ref(null)
const testingNotification = ref(false)
const notificationTestResult = ref(null)
const mediaServers = ref([])
const showMediaServerEditor = ref(false)
const editingMediaServerId = ref(null)
const savingMediaServer = ref(false)
const testingMediaServerForm = ref(false)
const testingMediaServerId = ref(null)
const mediaServerMessage = ref(null)
const mediaServerForm = ref({ name: '', serverType: 'JELLYFIN', apiBaseUrl: '', apiKey: '', isActive: true })

// TMDB 域名选项
const tmdbApiDomainOptions = [
  { label: '默认域名1', value: 'https://api.themoviedb.org' },
  { label: '默认域名2', value: 'https://api.tmdb.org' },
  { label: '自定义', value: 'custom' }
]

const tmdbImageDomainOptions = [
  { label: '默认域名1', value: 'https://image.tmdb.org' },
  { label: '自定义', value: 'custom' }
]

// 当前选中的域名
const tmdbApiDomain = ref('https://api.themoviedb.org')
const tmdbImageDomain = ref('https://image.tmdb.org')

// 域名变更处理
const handleApiDomainChange = (value) => {
  if (value !== 'custom') {
    tmdbConfig.value.baseUrl = value
  }
}

const handleImageDomainChange = (value) => {
  if (value !== 'custom') {
    tmdbConfig.value.imageBaseUrl = value
  }
}

// Vue Select 选项数据
const tmdbLanguageOptions = [
  { label: '中文（简体）', value: 'zh-CN' },
  { label: '中文（繁体）', value: 'zh-TW' },
  { label: 'English', value: 'en-US' }
]

const tmdbRegionOptions = [
  { label: '中国', value: 'CN' },
  { label: '台湾', value: 'TW' },
  { label: '香港', value: 'HK' },
  { label: '美国', value: 'US' }
]

const logRetentionOptions = [
  { label: '1天', value: 1 },
  { label: '3天', value: 3 },
  { label: '5天', value: 5 },
  { label: '7天', value: 7 },
  { label: '30天', value: 30 }
]

const logLevelOptions = [
  { label: 'Debug', value: 'debug' },
  { label: 'Info', value: 'info' },
  { label: 'Warn', value: 'warn' },
  { label: 'Error', value: 'error' }
]

const loadCurrentSettings = async () => {
  availableExtensions.value = ['.mp4', '.avi', '.mkv', '.mov', '.wmv', '.flv', '.webm', '.m4v', '.3gp', '.3g2', '.asf', '.divx', '.f4v', '.m2ts', '.m2v', '.mts', '.ogv', '.rm', '.rmvb', '.ts', '.vob', '.xvid', '.iso']
  try {
    const response = await authenticatedApiCall('/system/config')
    if (response?.code === 200 && response.data) {
      const config = response.data
      if (config.mediaExtensions?.length) selectedExtensions.value = [...config.mediaExtensions]
      else selectedExtensions.value = ['.mp4', '.avi', '.rmvb', '.mkv']
      if (config.tmdb) {
        tmdbConfig.value = { ...tmdbConfig.value, ...config.tmdb }
        // 根据配置初始化域名选项
        const apiDomain = config.tmdb.baseUrl || 'https://api.themoviedb.org'
        const imageDomain = config.tmdb.imageBaseUrl || 'https://image.tmdb.org'
        // 检查是否是预设选项
        const presetApiDomains = tmdbApiDomainOptions.map(o => o.value)
        const presetImageDomains = tmdbImageDomainOptions.map(o => o.value)
        tmdbApiDomain.value = presetApiDomains.includes(apiDomain) ? apiDomain : 'custom'
        tmdbImageDomain.value = presetImageDomains.includes(imageDomain) ? imageDomain : 'custom'
      }
      if (config.scraping) scrapingConfig.value = { ...scrapingConfig.value, ...config.scraping }
      if (config.ai) aiConfig.value = { ...aiConfig.value, ...config.ai }
      if (config.notifications) notificationConfig.value = { ...notificationConfig.value, ...config.notifications }
      if (config.log) logConfig.value = { ...logConfig.value, ...config.log }
    } else selectedExtensions.value = ['.mp4', '.avi', '.rmvb', '.mkv', '.iso']
  } catch { selectedExtensions.value = ['.mp4', '.avi', '.rmvb', '.mkv', '.iso'] }
}

const loadMediaServers = async () => {
  try {
    const response = await authenticatedApiCall('/media-servers')
    if (response?.code === 200) mediaServers.value = response.data || []
  } catch {
    mediaServerMessage.value = { success: false, text: '媒体服务器配置加载失败' }
  }
}

const openMediaServerEditor = (server = null) => {
  editingMediaServerId.value = server?.id || null
  mediaServerForm.value = server
    ? { name: server.name, serverType: server.serverType, apiBaseUrl: server.apiBaseUrl, apiKey: '', isActive: server.active }
    : { name: '', serverType: 'JELLYFIN', apiBaseUrl: '', apiKey: '', isActive: true }
  mediaServerMessage.value = null
  showMediaServerEditor.value = true
}

const closeMediaServerEditor = () => {
  showMediaServerEditor.value = false
  editingMediaServerId.value = null
}

const validateMediaServerForm = () => {
  if (!mediaServerForm.value.name || !mediaServerForm.value.apiBaseUrl) throw new Error('请填写配置名称和 API 根地址')
  if (!editingMediaServerId.value && !mediaServerForm.value.apiKey) throw new Error('请填写 API Key')
}

const saveMediaServer = async () => {
  savingMediaServer.value = true
  mediaServerMessage.value = null
  try {
    validateMediaServerForm()
    const response = await authenticatedApiCall(
      editingMediaServerId.value ? `/media-servers/${editingMediaServerId.value}` : '/media-servers',
      { method: editingMediaServerId.value ? 'PUT' : 'POST', body: mediaServerForm.value }
    )
    if (response?.code !== 200) throw new Error(response?.message || '保存失败')
    await loadMediaServers()
    closeMediaServerEditor()
    mediaServerMessage.value = { success: true, text: '媒体服务器配置已保存' }
  } catch (error) {
    mediaServerMessage.value = { success: false, text: error.message || '保存失败' }
  } finally {
    savingMediaServer.value = false
  }
}

const testMediaServerForm = async () => {
  testingMediaServerForm.value = true
  mediaServerMessage.value = null
  try {
    validateMediaServerForm()
    const response = editingMediaServerId.value && !mediaServerForm.value.apiKey
      ? await authenticatedApiCall(`/media-servers/${editingMediaServerId.value}/test`, { method: 'POST' })
      : await authenticatedApiCall('/media-servers/test', { method: 'POST', body: mediaServerForm.value })
    if (response?.code !== 200) throw new Error(response?.message || '连接测试失败')
    const result = response.data
    mediaServerMessage.value = { success: true, text: `连接成功：${result.serverName || '媒体服务器'} ${result.version || ''}，发现 ${result.libraryCount} 个媒体库` }
  } catch (error) {
    mediaServerMessage.value = { success: false, text: error.message || '连接测试失败' }
  } finally {
    testingMediaServerForm.value = false
  }
}

const testSavedMediaServer = async (server) => {
  testingMediaServerId.value = server.id
  mediaServerMessage.value = null
  try {
    const response = await authenticatedApiCall(`/media-servers/${server.id}/test`, { method: 'POST' })
    if (response?.code !== 200) throw new Error(response?.message || '连接测试失败')
    mediaServerMessage.value = { success: true, text: `${server.name} 连接成功，发现 ${response.data.libraryCount} 个媒体库` }
  } catch (error) {
    mediaServerMessage.value = { success: false, text: error.message || '连接测试失败' }
  } finally {
    testingMediaServerId.value = null
  }
}

const deleteMediaServer = async (server) => {
  if (!confirm(`确定删除媒体服务器“${server.name}”吗？`)) return
  mediaServerMessage.value = null
  try {
    const response = await authenticatedApiCall(`/media-servers/${server.id}`, { method: 'DELETE' })
    if (response?.code !== 200) throw new Error(response?.message || '删除失败')
    await loadMediaServers()
    mediaServerMessage.value = { success: true, text: '媒体服务器配置已删除' }
  } catch (error) {
    mediaServerMessage.value = { success: false, text: error.message || '删除失败' }
  }
}

const saveSettings = async () => {
  if (selectedExtensions.value.length === 0) { errorMessage.value = '请至少选择一个媒体文件后缀'; setTimeout(() => errorMessage.value = '', 3000); return }
  saving.value = true
  errorMessage.value = ''
  try {
    const response = await authenticatedApiCall('/system/config', {
      method: 'POST',
      body: { mediaExtensions: selectedExtensions.value, tmdb: tmdbConfig.value, scraping: scrapingConfig.value, ai: aiConfig.value, notifications: notificationConfig.value, log: logConfig.value }
    })
    if (response?.code === 200) { showSuccess.value = true; setTimeout(() => showSuccess.value = false, 3000) }
    else { errorMessage.value = response?.message || '保存设置失败'; setTimeout(() => errorMessage.value = '', 3000) }
  } catch { errorMessage.value = '保存设置失败'; setTimeout(() => errorMessage.value = '', 3000) }
  finally { saving.value = false }
}

const testAiConfig = async () => {
  if (!aiConfig.value.baseUrl || !aiConfig.value.apiKey || !aiConfig.value.model) { aiTestResult.value = { success: false, message: '请填写完整的 AI 配置信息' }; return }
  testingAi.value = true
  aiTestResult.value = null
  try {
    const response = await authenticatedApiCall('/system/test-ai-config', { method: 'POST', body: { baseUrl: aiConfig.value.baseUrl, apiKey: aiConfig.value.apiKey, model: aiConfig.value.model } })
    aiTestResult.value = response?.code === 200 ? { success: true, message: 'AI 配置测试成功' } : { success: false, message: response?.message || 'AI 配置测试失败' }
  } catch { aiTestResult.value = { success: false, message: '测试 AI 配置失败' } }
  finally { testingAi.value = false; setTimeout(() => aiTestResult.value = null, 3000) }
}

const testNotification = async () => {
  if (!notificationConfig.value.serverUrl || !notificationConfig.value.configKey) {
    notificationTestResult.value = { success: false, message: '请填写 Apprise 服务地址和配置 Key' }
    return
  }
  testingNotification.value = true
  notificationTestResult.value = null
  try {
    const response = await authenticatedApiCall('/system/test-notification', {
      method: 'POST',
      body: { notifications: notificationConfig.value }
    })
    notificationTestResult.value = response?.code === 200
      ? { success: true, message: '测试通知发送成功' }
      : { success: false, message: response?.message || '测试通知发送失败' }
  } catch {
    notificationTestResult.value = { success: false, message: '测试通知发送失败' }
  } finally {
    testingNotification.value = false
    setTimeout(() => notificationTestResult.value = null, 5000)
  }
}

const toggleApiKeyVisibility = () => showApiKey.value = !showApiKey.value
const goBack = () => router.back()

onMounted(() => {
  loadCurrentSettings()
  loadMediaServers()
})
</script>

<style scoped>
/* Vue Select 中等高度样式 */
.vue-select-md {
  --vs-height: 46px;
}

.vue-select-md :deep(.vs__dropdown-toggle) {
  padding-top: 8px;
  padding-bottom: 8px;
  min-height: 46px;
}
</style>
