<template>
  <div class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
    <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <button type="button" class="mb-3 text-sm text-blue-300 hover:text-blue-200" @click="goBack">
          ← 返回任务管理
        </button>
        <h1 class="text-2xl font-semibold text-white">手动刮削</h1>
        <p class="mt-1 text-sm text-white/45">
          {{ treeResult?.taskName || taskInfo?.taskName || '加载任务中' }}
          <span v-if="treeResult"> · {{ libraryTypeLabel(treeResult.libraryType) }}</span>
        </p>
      </div>
      <button type="button" class="btn-primary" :disabled="!canPreview || previewing" @click="loadPreview">
        <svg v-if="previewing" class="mr-2 h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
        {{ previewing ? '正在识别...' : '识别并预览' }}
      </button>
    </div>

    <div v-if="loading" class="card py-20 text-center text-white/50">正在读取任务目录...</div>
    <div v-else-if="pageError" class="rounded-xl border border-red-500/25 bg-red-500/10 p-5 text-red-200">
      {{ pageError }}
      <button type="button" class="ml-3 underline" @click="loadPage">重试</button>
    </div>

    <div v-else class="grid gap-6 lg:grid-cols-[minmax(320px,0.85fr)_minmax(0,1.4fr)]">
      <section class="card min-h-[560px]">
        <div class="mb-4">
          <h2 class="font-medium text-white">任务目录</h2>
          <p class="mt-1 text-xs text-white/40">选择包含一个电影或一部剧集的目录</p>
        </div>
        <div class="max-h-[68vh] overflow-y-auto pr-1">
          <ManualScrapingTreeNode
            v-if="treeResult?.tree"
            :node="treeResult.tree"
            :selected-path="selectedDirectory?.path"
            @select="selectDirectory"
          />
        </div>
      </section>

      <section class="card min-h-[560px]">
        <div v-if="!selectedDirectory" class="flex h-full min-h-[480px] items-center justify-center text-center">
          <div>
            <svg class="mx-auto h-12 w-12 text-white/15" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 7h6l2 2h10v10H3V7z" />
            </svg>
            <p class="mt-4 text-white/45">请从左侧选择一个媒体目录</p>
          </div>
        </div>

        <div v-else-if="previewError" class="rounded-xl border border-red-500/25 bg-red-500/10 p-5 text-red-200">
          {{ previewError }}
        </div>

        <div v-else-if="!preview" class="flex h-full min-h-[480px] items-center justify-center text-center">
          <div>
            <p class="font-medium text-white/75">{{ selectedDirectory.name }}</p>
            <p class="mt-2 break-all font-mono text-xs text-white/35">{{ selectedDirectory.path }}</p>
            <p class="mt-4 text-sm text-white/45">
              共 {{ selectedDirectory.videoFileCount }} 个媒体文件，点击“识别并预览”继续
            </p>
          </div>
        </div>

        <div v-else class="space-y-6">
          <div class="flex flex-col gap-5 sm:flex-row">
            <img
              v-if="preview.posterUrl"
              :src="preview.posterUrl"
              :alt="preview.title"
              class="h-64 w-44 shrink-0 rounded-xl bg-white/5 object-cover shadow-xl"
            >
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <span class="badge-success">{{ preview.mediaType === 'movie' ? '电影' : '电视剧' }}</span>
                <span class="text-xs text-white/35">TMDB #{{ preview.tmdbId }}</span>
              </div>
              <h2 class="mt-3 text-2xl font-semibold text-white">{{ preview.title }}</h2>
              <p v-if="preview.originalTitle && preview.originalTitle !== preview.title" class="mt-1 text-sm text-white/40">
                {{ preview.originalTitle }}
              </p>
              <div class="mt-3 flex gap-4 text-sm text-white/55">
                <span v-if="preview.year">{{ preview.year }}</span>
                <span v-if="preview.voteAverage">评分 {{ Number(preview.voteAverage).toFixed(1) }}</span>
                <span>{{ preview.videoFileCount }} 个媒体文件</span>
              </div>
              <p class="mt-4 line-clamp-6 text-sm leading-6 text-white/60">
                {{ preview.overview || '暂无简介' }}
              </p>
            </div>
          </div>

          <div class="rounded-xl border border-white/8 bg-white/[0.03] p-4">
            <label class="flex cursor-pointer items-start gap-3">
              <input v-model="renameMedia" type="checkbox" class="mt-1 h-4 w-4 rounded">
              <span>
                <span class="block text-sm font-medium text-white">重命名媒体目录和文件</span>
                <span class="mt-1 block text-xs leading-5 text-amber-200/65">
                  确认后会先重命名文件夹，再重命名其中的媒体文件。该操作会直接修改 OpenList 源目录。
                </span>
              </span>
            </label>
            <p v-if="isTaskRoot && renameMedia" class="mt-3 text-xs text-red-300">
              当前选择的是任务根目录，不能重命名。请选择其下的媒体目录。
            </p>
          </div>

          <div v-if="renameMedia" class="space-y-3">
            <h3 class="text-sm font-medium text-white/80">重命名预览</h3>
            <div class="rounded-lg bg-white/[0.03] p-3 text-sm">
              <span class="text-white/40">文件夹：</span>
              <span class="break-all font-mono text-white/75">{{ selectedDirectory.name }}</span>
              <span class="mx-2 text-white/25">→</span>
              <span class="break-all font-mono text-emerald-300">{{ preview.proposedDirectoryName }}</span>
            </div>
            <div class="max-h-48 space-y-1 overflow-y-auto">
              <div
                v-for="item in preview.proposedFileRenames"
                :key="item.sourcePath"
                class="grid gap-1 rounded-lg px-3 py-2 text-xs sm:grid-cols-[1fr_auto_1fr]"
              >
                <span class="break-all font-mono text-white/45">{{ item.sourceName }}</span>
                <span class="hidden text-white/20 sm:block">→</span>
                <span class="break-all font-mono text-blue-300">{{ item.targetName }}</span>
              </div>
            </div>
          </div>

          <div>
            <h3 class="text-sm font-medium text-white/80">将上传到所选目录</h3>
            <div class="mt-2 flex flex-wrap gap-2">
              <span
                v-for="file in displayedGeneratedFiles"
                :key="file"
                class="rounded-lg border border-white/8 bg-white/[0.03] px-3 py-1.5 font-mono text-xs text-white/60"
              >
                {{ file }}
              </span>
            </div>
          </div>

          <div v-if="executeResult" class="rounded-xl border border-emerald-500/25 bg-emerald-500/10 p-4 text-sm text-emerald-200">
            <div class="font-medium">{{ executeResult.message }}</div>
            <div class="mt-2 break-all font-mono text-xs">{{ executeResult.finalDirectoryPath }}</div>
          </div>

          <div class="flex justify-end border-t border-white/8 pt-5">
            <button
              type="button"
              class="btn-primary"
              :disabled="executing || (renameMedia && isTaskRoot)"
              @click="executeScraping"
            >
              {{ executing ? '正在刮削并上传...' : '确认刮削' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ManualScrapingTreeNode from '~/components/ManualScrapingTreeNode.vue'
import { authenticatedApiCall } from '~/core/api/client'
import logger from '~/core/utils/logger'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.taskId)

const loading = ref(true)
const previewing = ref(false)
const executing = ref(false)
const pageError = ref('')
const previewError = ref('')
const treeResult = ref(null)
const taskInfo = ref(null)
const selectedDirectory = ref(null)
const preview = ref(null)
const executeResult = ref(null)
const renameMedia = ref(false)

const canPreview = computed(() =>
  selectedDirectory.value && selectedDirectory.value.videoFileCount > 0
)
const isTaskRoot = computed(() =>
  selectedDirectory.value?.path === treeResult.value?.rootPath
)
const displayedGeneratedFiles = computed(() => {
  if (!preview.value) return []
  return renameMedia.value
    ? preview.value.renamedGeneratedFiles
    : preview.value.generatedFiles
})

const libraryTypeLabel = (type) => ({
  movie: '电影',
  tv: '电视剧',
  anime: '动画'
}[type] || type)

const loadPage = async () => {
  loading.value = true
  pageError.value = ''
  try {
    const [taskResponse, treeResponse] = await Promise.all([
      authenticatedApiCall(`/task-config/${taskId}`, { method: 'GET' }),
      authenticatedApiCall(`/task-config/${taskId}/manual-scraping/tree`, { method: 'GET' })
    ])
    if (taskResponse.code !== 200) throw new Error(taskResponse.message || '任务不存在')
    if (treeResponse.code !== 200) throw new Error(treeResponse.message || '目录读取失败')
    taskInfo.value = taskResponse.data
    treeResult.value = treeResponse.data
  } catch (error) {
    logger.error('加载手动刮削页面失败:', error)
    pageError.value = error.message || '加载任务目录失败'
  } finally {
    loading.value = false
  }
}

const selectDirectory = (node) => {
  selectedDirectory.value = node
  preview.value = null
  previewError.value = ''
  executeResult.value = null
  renameMedia.value = false
}

const loadPreview = async () => {
  if (!canPreview.value) return
  previewing.value = true
  previewError.value = ''
  executeResult.value = null
  try {
    const response = await authenticatedApiCall(`/task-config/${taskId}/manual-scraping/preview`, {
      method: 'POST',
      body: { directoryPath: selectedDirectory.value.path }
    })
    if (response.code !== 200) throw new Error(response.message || '媒体识别失败')
    preview.value = response.data
  } catch (error) {
    logger.error('手动刮削识别失败:', error)
    preview.value = null
    previewError.value = error.message || '媒体识别失败'
  } finally {
    previewing.value = false
  }
}

const executeScraping = async () => {
  if (!preview.value) return
  const action = renameMedia.value ? '重命名源目录和文件，并上传刮削信息' : '上传刮削信息'
  if (!confirm(`确认${action}？`)) return

  executing.value = true
  previewError.value = ''
  try {
    const response = await authenticatedApiCall(`/task-config/${taskId}/manual-scraping/execute`, {
      method: 'POST',
      body: {
        directoryPath: preview.value.directoryPath,
        mediaType: preview.value.mediaType,
        tmdbId: preview.value.tmdbId,
        renameMedia: renameMedia.value
      }
    })
    if (response.code !== 200) throw new Error(response.message || '手动刮削失败')
    executeResult.value = response.data
    await refreshTreeAfterExecution()
  } catch (error) {
    logger.error('手动刮削执行失败:', error)
    previewError.value = error.message || '手动刮削失败'
  } finally {
    executing.value = false
  }
}

const refreshTreeAfterExecution = async () => {
  const response = await authenticatedApiCall(`/task-config/${taskId}/manual-scraping/tree`, {
    method: 'GET'
  })
  if (response.code === 200) treeResult.value = response.data
}

const goBack = () => {
  if (taskInfo.value?.openlistConfigId) {
    router.push(`/task-management/${taskInfo.value.openlistConfigId}`)
  } else {
    router.back()
  }
}

onMounted(loadPage)
</script>
