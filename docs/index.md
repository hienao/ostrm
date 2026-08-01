---
# https://vitepress.dev/reference/default-theme-home-page
layout: home

hero:
  name: "OStrm"
  text: "便捷的为你的OpenList影音文件生成Strm文件"
  tagline: 一个OpenList的配套使用工具
  actions:
    - theme: brand
      text: 快速开始
      link: /quick-start
    - theme: alt
      text: GitHub 仓库
      link: https://github.com/hienao/ostrm

features:
  - title: 🎬 STRM 文件生成
    details: 自动将 OpenList 文件列表转换为 STRM 流媒体文件，支持多种媒体格式
  - title: 📋 任务管理系统
    details: 完整的 Web 界面，支持任务创建、目录结构检查、手动刮削和状态监控
  - title: ⏰ 定时任务调度
    details: 基于 Cron 表达式的自动化执行，支持增量和全量更新模式
  - title: 🔍 AI 智能刮削
    details: 可选的智能媒体信息刮削功能，自动获取电影和电视剧元数据
  - title: 🧹 媒体库维护
    details: 按需检查异常目录，预览并重命名媒体目录、季目录和文件，失败后可继续执行
  - title: 🔄 媒体服务器刷新
    details: STRM 任务完成后通知 Emby 或 Jellyfin 刷新全部或指定媒体库
  - title: 🔔 多渠道任务通知
    details: 通过 Apprise 推送任务与手动刮削结果，并按类别展示失败路径和原因
  - title: 🖼️ 图片文件下载
    details: 自动下载海报、背景图和缩略图，支持本地、OpenList 和刮削三级优先级
  - title: 📄 字幕文件保留
    details: 自动复制视频同目录下的字幕文件，支持多种字幕格式
  - title: 🔐 安全认证系统
    details: 基于 JWT 的用户认证机制，确保数据安全
  - title: 🐳 容器化部署
    details: 完整的 Docker 支持，一键部署，支持环境变量配置
---
