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
  <footer class="footer-glass">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col sm:flex-row justify-between items-center gap-4">
        <!-- 左侧信息 -->
        <div class="flex items-center gap-2 text-sm text-white/40">
          <span class="font-semibold gradient-text">Ostrm</span>
          <span class="font-mono">{{ appVersion }}</span>
          <span class="hidden sm:inline">·</span>
          <span class="hidden sm:inline">© {{ currentYear }}</span>
        </div>

        <!-- 右侧链接 -->
        <div class="flex items-center gap-6">
          <a
            href="https://t.me/ostrm6"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center gap-1.5 text-sm text-white/40 hover:text-sky-400 transition-colors"
            title="加入 Telegram 群组 @ostrm6"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M21.75 3.75 10.5 15m11.25-11.25-7.2 16.2-4.05-4.95-6.45-2.7 17.7-8.55Z" />
            </svg>
            <span class="hidden sm:inline">@ostrm6</span>
          </a>
          <a
            href="https://github.com/hienao/ostrm"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center gap-1.5 text-sm text-white/40 hover:text-white/70 transition-colors"
            title="查看 GitHub 仓库"
          >
            <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.30.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
            </svg>
            <span class="hidden sm:inline">GitHub</span>
          </a>
        </div>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'
import { useRuntimeConfig } from '#app'

const config = useRuntimeConfig()
const appVersion = computed(() => config.public.appVersion || 'dev')
const currentYear = new Date().getFullYear()
</script>

<style scoped>
.gradient-text {
  background: linear-gradient(to right, #60A5FA, #A78BFA);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
</style>
