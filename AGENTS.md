# Repository Instructions

## Release Branch Rules

仓库只允许按照以下顺序提升代码：

```text
dev -> beta -> main
```

- 日常开发、版本准备和版本号修改只能在 `dev` 分支完成。
- `beta` 和 `main` 是受保护的集成分支，禁止直接提交或直接修改代码，只能通过 Pull Request（MR）接收变更。
- `beta` 只接收以 `dev` 为 head branch 的 PR，即 `dev -> beta`。
- `main` 只接收以 `beta` 为 head branch 的 PR，即 `beta -> main`。
- 严禁从 `dev` 或任何其他功能分支直接向 `main` 发起或合并 PR。
- 发布前和合并前必须同时核对 PR 的 head branch 与 base branch；只检查 base branch 不够。

当用户要求“发 beta 版本”“发布 beta”“打 beta 版本”等操作时：

1. 在 `dev` 更新 `app_version.json` 中的 `beta_version`。
2. 将版本改动提交并推送到 `dev`。
3. 创建或更新 `dev -> beta` 的 PR。
4. 合并前必须确认 PR 显示为 `dev -> beta`。
5. 不得为了发布 Beta 创建或合并任何以 `main` 为 base branch 的 PR。

当用户要求“发正式版本”“发布正式版”等操作时：

1. 在 `dev` 更新 `app_version.json` 中的 `release_version`。
2. 将版本改动提交并推送到 `dev`。
3. 创建或更新 `dev -> beta` 的 PR，合并前确认 head 为 `dev`、base 为 `beta`，然后合并。
4. 等待 `dev -> beta` 合并完成后，创建或更新 `beta -> main` 的 PR。
5. 合并正式发布 PR 前必须确认 head 为 `beta`、base 为 `main`。
6. 不得创建或合并 `dev -> main`，也不得从任何其他分支直接合并到 `main`。

发布由 `.github/workflows/docker-build-push.yml` 在 PR 合并后触发：

- 合并到 `beta`：读取 `beta_version`，仅发布对应 beta 镜像标签，不更新 `latest`，不创建正式 GitHub Release。
- 合并到 `main`：读取 `release_version`，发布正式镜像标签、更新 `latest`，并创建正式 GitHub Release。

如果 PR 的 head/base 与上述规则不一致，必须停止发布并修正分支关系；在确认正确之前不得合并。不得通过直接 push、网页直接编辑或本地直接提交绕过 PR 流程修改 `beta` 或 `main`。
