# 快速开始

欢迎使用 OpenList to Stream！本指南将帮助您在 5 分钟内完成从安装到创建第一个 STRM 文件的完整流程。

## 前置条件

在开始之前，请确保您已经：

- ✅ 安装了 Docker（或 Docker Compose）
- ✅ 有一个正在运行的 OpenList 服务器
- ✅ 准备好存储 STRM 文件的目录

## 第一步：部署应用

### 使用 Docker Compose（推荐）

创建 `docker-compose.yml`：
```yaml
services:
  app:
    image: hienao6/ostrm:latest
    container_name: ostrm
    ports:
      - "3111:80"
    volumes:
      - ./data/config:/maindata/config    # 配置文件和数据库存储
      - ./data/db:/maindata/db            # 数据库文件存储
      - ./logs:/maindata/log              # 日志文件存储
      - ./strm:/app/backend/strm          # STRM 文件输出目录
    restart: always
```

然后运行：
```bash
docker-compose up -d
```

### 使用 dev-docker.sh 脚本（推荐）

项目提供了增强的开发环境脚本：
```bash
# 初始化开发环境
./dev-docker.sh install

# 启动完整环境
./dev-docker.sh start

# 开发模式启动（支持热重载）
./dev-docker.sh start-dev
```

**Windows 用户**：建议使用 Git Bash 运行 `dev-docker.sh`。

## 第二步：访问应用

打开浏览器，访问：`http://localhost:3111`

## 第三步：注册账户

1. 在首页点击「注册」按钮
2. 填写用户信息：
   - 用户名：您的用户名
   - 邮箱：您的邮箱地址
   - 密码：设置一个安全密码
3. 点击「注册」完成账户创建
4. 使用刚创建的账户登录系统

## 第四步：配置 OpenList 服务器

1. 登录后，点击首页的「添加配置」按钮
2. 填写服务器信息：
   - **Base URL**：您的 OpenList 服务器地址（如：`http://192.168.1.100:3000`）
   - **Token**：OpenList 的访问令牌（在 OpenList 设置中获取）
3. 点击「测试连接」确保配置正确
4. 点击「保存」完成配置

::: tip 连接测试
如果连接测试失败，请检查：
- OpenList 服务器是否正在运行
- 网络连接是否正常
- Token 是否正确且有效
- 服务器地址是否正确（包含端口号）
- Token 是否有足够的权限访问指定路径
:::

## 第五步：创建第一个任务

1. 点击顶部导航栏的「任务管理」
2. 点击「添加任务」按钮
3. 配置任务信息：
   - **任务名称**：给任务起个名字（如：电影库转换）
   - **选择 OpenList 配置**：选择刚才创建的配置
   - **OpenList 路径**：选择要转换的 OpenList 路径
   - **STRM 输出路径**：设置 STRM 文件的保存路径
   - **更新模式**：选择「增量更新」（首次运行建议选择「全量更新」）
   - **是否刮削**：如果需要自动获取媒体信息，开启此选项
   - **保留字幕文件**：如果需要保留字幕文件，开启此选项
   - **使用已有刮削信息**：如果 OpenList 中已有图片和 NFO 文件，开启此选项
4. 点击「测试路径」确保路径配置正确
5. 点击「保存」完成任务创建

### 文件处理器链说明

系统在处理视频文件时，自动执行以下处理器：

| Order | 处理器 | 功能 |
|-------|--------|------|
| 10 | FileDiscoveryHandler | 文件发现 |
| 20 | FileFilterHandler | 文件过滤 |
| 30 | StrmGenerationHandler | STRM 文件生成 |
| 40 | NfoDownloadHandler | NFO 文件下载 |
| 41 | ImageDownloadHandler | 图片文件下载 |
| 42 | SubtitleCopyHandler | 字幕文件复制 |
| 50 | MediaScrapingHandler | 媒体刮削 |
| 60 | OrphanCleanupHandler | 孤立文件清理 |

### 字幕文件支持

系统支持自动下载以下格式的字幕文件：
- `.srt` - SubRip 字幕
- `.ass` - Advanced SubStation Alpha
- `.vtt` - WebVTT 字幕
- `.ssa` - SubStation Alpha
- `.sub` - SubViewer
- `.idx` - VobSub 索引

### 图片文件支持

系统支持三级图片下载优先级：
1. **本地文件**：检查本地是否存在对应图片文件
2. **OpenList 文件**：从 OpenList 同级目录下载
3. **刮削获取**：通过 TMDB API 刮削

**支持的图片类型：**
- `-poster.jpg` - 海报图片
- `-fanart.jpg` - 背景图
- `-thumb.jpg` - 缩略图
- 任意命名图片（降级策略）

## 第六步：执行任务

### 手动执行（推荐首次使用）

1. 在任务列表中找到刚创建的任务
2. 点击任务右侧的「立即执行」按钮
3. 系统会开始处理文件，您可以在任务详情页查看进度
4. 等待任务完成

### 查看执行详情

任务执行时，您可以查看：
- 处理的文件数量
- 成功/失败统计
- STRM 文件生成数量
- NFO/图片/字幕下载统计
- 详细的执行日志

### 设置定时执行

1. 在任务详情页，点击「编辑」
2. 在「Cron 表达式」字段中设置执行时间
   - `0 2 * * *` - 每天凌晨2点执行
   - `0 */6 * * *` - 每6小时执行一次
3. 点击「保存」生效

## 第七步：查看结果

任务执行完成后：

1. **检查 STRM 文件**：在您设置的输出目录中查看生成的 STRM 文件
2. **检查刮削文件**：查看对应的 NFO、海报、字幕文件
3. **使用 STRM 文件**：将 STRM 文件添加到您的媒体服务器（如 Plex、Jellyfin 等）
4. **查看日志**：在「日志」页面查看详细的执行日志

## 常用 Cron 表达式

| 表达式 | 说明 |
|--------|------|
| `0 2 * * *` | 每天凌晨2点 |
| `0 */6 * * *` | 每6小时 |
| `0 0 * * 0` | 每周日午夜 |
| `0 0 1 * *` | 每月1号午夜 |

## 下一步

恭喜！您已经成功创建了第一个 STRM 文件。接下来您可以：

- 📖 [添加更多 OpenList 配置](./add-openlist.md)
- 📋 [创建更多转换任务](./add-task.md)
- 🔄 [配置 Emby / Jellyfin 媒体库刷新](./media-server-refresh.md)
- 🔔 [配置 Apprise 任务通知](./notifications.md)
- ⚙️ [配置系统设置](./system-config.md)
- 📊 [查看执行日志](./log.md)
- ❓ [查看常见问题](./faq.md)

## 遇到问题？

如果在使用过程中遇到问题，可以：

1. 查看 [常见问题](./faq.md)
2. 检查 [执行日志](./log.md)
3. 在 [GitHub Issues](https://github.com/hienao/ostrm/issues) 提交问题
4. 查看项目 [Wiki](https://github.com/hienao/ostrm/wiki)

### 常见问题快速排查

**字幕文件未下载？**
- 确认已开启「保留字幕文件」选项
- 检查 OpenList 目录中是否存在字幕文件
- 验证字幕格式是否支持

**图片文件未下载？**
- 确认已开启刮削选项
- 检查「使用已有刮削信息」是否正确设置
- 验证 OpenList 目录中是否存在图片文件

**URL 编码问题？**
- 如果路径包含中文或特殊字符，建议开启 URL 编码选项
- 在系统设置中配置「默认启用 URL 编码」

---

现在您可以开始享受 OpenList to Stream 带来的便利了！🎉
