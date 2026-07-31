# Repository Instructions

## Release Branch Rules

发布版本时必须根据版本类型选择 Pull Request 的目标分支，并在合并前再次核对 PR 的 base branch：

- Beta 版本：将开发分支合并到 `beta`，不得合并到 `main`。
- 正式版本：将开发分支合并到 `main`，不得合并到 `beta`。
- 日常开发和版本准备默认在 `dev` 分支完成；发布 PR 通常为 `dev -> beta` 或 `dev -> main`。

当用户要求“发 beta 版本”“发布 beta”“打 beta 版本”等操作时：

1. 更新 `app_version.json` 中的 `beta_version`。
2. 创建或更新以 `beta` 为 base branch 的 PR。
3. 合并前必须确认 PR 显示为 `开发分支 -> beta`。
4. 不得为了发布 beta 创建或合并任何以 `main` 为 base branch 的 PR。

当用户要求“发正式版本”“发布正式版”等操作时：

1. 更新 `app_version.json` 中的 `release_version`。
2. 创建或更新以 `main` 为 base branch 的 PR。
3. 合并前必须确认 PR 显示为 `开发分支 -> main`。

发布由 `.github/workflows/docker-build-push.yml` 在 PR 合并后触发：

- 合并到 `beta`：读取 `beta_version`，仅发布对应 beta 镜像标签，不更新 `latest`，不创建正式 GitHub Release。
- 合并到 `main`：读取 `release_version`，发布正式镜像标签、更新 `latest`，并创建正式 GitHub Release。

如果 PR 的目标分支与上述规则不一致，必须先修改 base branch；在确认正确之前不得合并。
