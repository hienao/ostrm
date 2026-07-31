import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  lang: 'zh_CN',
  title: "OpenList-Strm",
  description: "便捷的为你的OpenList影音文件生成Strm文件",
  // 使用 alpha 版本的配置
  // 注意：alpha 版本可能不支持 ignoreDeadLinks
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: '首页', link: '/' },
      { text: '功能介绍',items: [
          { text: '快速开始', link: '/quick-start' },
          { text: '添加OpenList', link: '/add-openlist' },
          { text: '添加任务', link: '/add-task' },
          { text: '目录检查与手动刮削', link: '/media-maintenance' },
          { text: '系统设置', link: '/system-config' }
        ] },
      { text: '特殊配置项说明', items: [
          { text: 'STRM Base URL 配置', link: '/strm-base-url-config' },
          { text: 'AI 识别配置', link: '/ai-recognition-config' },
          { text: 'URL编码配置', link: '/url-encoding-config' }
        ] },
      { text: '更新历史', link: '/update-log' },
      { text: '参与开发', link: '/dev' },
      { text: '常见问题', link: '/faq' }
    ],

    sidebar: [
      { text: '首页', link: '/' },
      { text: '功能介绍',items: [
          { text: '快速开始', link: '/quick-start' },
          { text: '添加OpenList', link: '/add-openlist' },
          { text: '添加任务', link: '/add-task' },
          { text: '目录检查与手动刮削', link: '/media-maintenance' },
          { text: '系统设置', link: '/system-config' }
        ] },
      { text: '特殊配置项说明', items: [
          { text: 'STRM Base URL 配置', link: '/strm-base-url-config' },
          { text: 'AI 识别配置', link: '/ai-recognition-config' },
          { text: 'URL编码配置', link: '/url-encoding-config' }
        ] },
      { text: '更新历史', link: '/update-log' },
      { text: '参与开发', link: '/dev' },
      { text: '常见问题', link: '/faq' }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/hienao/ostrm' }
    ]
  }
})
