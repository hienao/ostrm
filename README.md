# Ostrm

**一个用于将[OpenList](https://github.com/OpenListTeam/OpenList) 文件列表转换为 STRM 流媒体文件的全栈应用（原OpenList to Stream项目）**

[![License](https://img.shields.io/github/license/hienao/ostrm?style=flat-square)](https://github.com/hienao/ostrm/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/hienao/ostrm?style=flat-square&color=yellow)](https://github.com/hienao/ostrm/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/hienao/ostrm?style=flat-square&color=blue)](https://github.com/hienao/ostrm/network/members)
[![GitHub contributors](https://img.shields.io/github/contributors/hienao/ostrm?style=flat-square&color=orange)](https://github.com/hienao/ostrm/graphs/contributors)
[![GitHub issues](https://img.shields.io/github/issues/hienao/ostrm?style=flat-square&color=red)](https://github.com/hienao/ostrm/issues)
[![Docker](https://img.shields.io/docker/pulls/hienao6/ostrm?color=%2348BB78&logo=docker&label=pulls&style=flat-square)](https://hub.docker.com/r/hienao6/ostrm)

[功能介绍](#功能介绍) • [使用说明](#使用说明)

## 功能介绍

- 🎬 **STRM 文件生成**: 自动将 OpenList 文件列表转换为 STRM 流媒体文件
- 📋 **任务管理**: 支持创建、编辑和删除转换任务，Web 界面操作
- ⏰ **定时执行**: 基于 Cron 表达式的定时任务调度
- 🔄 **增量更新**: 支持增量和全量两种更新模式
- 🔗 **URL编码控制**: 支持灵活配置STRM链接的URL编码行为，处理特殊字符和中文路径
- 🌐 **Base URL替换**: 支持STRM文件生成时的基础URL替换，适配不同网络环境
- 🔍 **AI刮削**: 支持根据文件名、文件路径等信息，可配置AI进行媒体刮削
- 🔐 **用户认证**: 基于 JWT 的安全认证系统
- 📊 **系统日志**: 支持 Web 端实时查看、搜索、过滤和下载系统日志
- 🐳 **容器化部署**: 完整的 Docker 支持，一键部署

## 首页截图

![首页截图](screenshots/home.jpg)


## 使用说明

详细的使用说明请参考：[快速开始指南](https://ostrm.51cloud.de/quick-start.html)


## 技术架构

### 🏗️ 全栈技术栈
- **前端**: Nuxt.js 3.13.0 + Vue 3.4.0 + Tailwind CSS 3.4.15
- **后端**: Spring Boot 3.3.9 + MyBatis 3.0.4 + Quartz Scheduler
- **数据库**: SQLite 3.47.1.0 + Flyway 11.4.0 迁移
- **构建**: Gradle + Java 21 + Node.js
- **容器化**: Docker 多阶段构建 + Caddy
- **认证**: JWT + Spring Security

### 📁 项目结构
```
├── frontend/           # Nuxt.js 前端应用
│   ├── pages/         # 自动路由 Vue 页面
│   ├── components/    # 可复用 Vue 组件
│   ├── middleware/    # 路由中间件 (auth, guest)
│   └── assets/        # 静态资源和 CSS
├── backend/           # Spring Boot 后端应用
│   └── src/main/java/com/hienao/openlist2strm/
│       ├── controller/  # REST API 控制器
│       ├── service/     # 业务逻辑层
│       ├── mapper/      # MyBatis 数据访问
│       ├── entity/      # 数据库实体
│       ├── job/         # Quartz 定时任务
│       └── config/      # Spring 配置
└── docker-compose.yml # 容器编排
```

### 🔧 核心功能
- **认证系统**: JWT Token (Cookie 存储) + 中间件保护
- **任务调度**: Quartz 定时器 (RAM 存储模式)
- **数据库**: SQLite + Flyway 版本管理
- **API 设计**: RESTful API + 统一响应格式
- **容器部署**: 多阶段构建 + 卷映射


## 📋 更新日志

详细的更新日志请查看：[更新历史](https://ostrm.51cloud.de/update-log.html)

## 项目统计

### ⭐ Star 历史

[![Star History Chart](https://api.star-history.com/svg?repos=hienao/ostrm&type=Date)](https://star-history.com/#hienao/ostrm&Date)

## 许可证

本项目采用 [GNU General Public License v3.0](https://github.com/hienao/ostrm/blob/main/LICENSE) 许可证。

### 许可证摘要

- ✅ 商业使用、修改、分发、专利使用、私人使用
- ⚠️ 衍生作品必须使用相同许可证
- ⚠️ 必须包含许可证和版权声明
- ⚠️ 必须说明更改内容
- ❌ 不提供责任和保证

---

如有问题或建议，欢迎提交 [Issue](https://github.com/hienao/openlist-strm/issues)。# oSTRM Fork - zake1989
