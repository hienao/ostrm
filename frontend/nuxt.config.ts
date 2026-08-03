/*
 * Ostrm - Stream Management System
 * Copyright (C) 2024 Ostrm Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: '2025-05-15',
  devtools: { enabled: true },

  // SPA 模式配置
  ssr: false,
  srcDir: 'app',

  // 路由配置 - Nuxt 4 风格
  future: {
    compatibilityVersion: 4
  },

  // 页面配置
  pages: {
    enabled: true
  },

  // 自动导入配置
  imports: {
    dirs: [
      'core/composables/**',
      'core/utils/**',
      'modules/*/composables/**',
      'modules/*/services/**'
    ]
  },

  // 组件扫描配置
  components: {
    dirs: [
      'core/ui',
      'components',
      'modules/*/components',
      'modules/shared/components'
    ]
  },

  // Pinia 配置
  pinia: {
    storesDirs: [
      'core/stores/**',
      'modules/*/stores/**'
    ]
  },

  // Nitro 配置
  nitro: {
    prerender: {
      routes: ['/auth/login', '/auth/register']
    },
    devProxy: {
      '/api': 'http://localhost:8080/api'
    },
    routeRules: {
      '/**': {
        headers: {
          'X-Robots-Tag': 'noindex'
        }
      }
    }
  },

  // 运行时配置
  runtimeConfig: {
    public: {
      apiBase: '/api',
      appVersion: process.env.NUXT_PUBLIC_APP_VERSION || 'dev'
    }
  },

  // 路由配置
  router: {
    options: {
      strict: false
    }
  },

  // CSS
  css: ['@/assets/css/main.css'],

  // 模块
  modules: [
    '@nuxtjs/tailwindcss',
    '@pinia/nuxt'
  ],

  // Vite 构建优化
  vite: {
    resolve: {
      alias: {
        '@': __dirname + '/app'
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('vue-virtual-scroller')) {
                return 'virtual-scroller'
              }
              if (id.includes('tailwindcss')) {
                return 'tailwind'
              }
              return 'vendor'
            }
            return undefined
          }
        }
      }
    }
  },

  // 应用配置
  app: {
    head: {
      title: 'Ostrm',
      meta: [
        { charset: 'utf-8' },
        { name: 'viewport', content: 'width=device-width, initial-scale=1' },
        { name: 'description', content: 'Ostrm - Stream Management System' },
        { name: 'theme-color', content: '#05070D' }
      ],
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' },
        { rel: 'alternate icon', type: 'image/x-icon', href: '/favicon.ico' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        { rel: 'manifest', href: '/site.webmanifest' }
      ]
    }
  }
})
