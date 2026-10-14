# 投稿通道（维护者速查）

发现页精选是**静态文件**：App 拉 `dist/discover/discover-manifest.json`，再按清单里的相对文件名去同一层取图。
这里没有任何服务器，所以"收稿"用 Issue、"分发"用文件——两条通道分开，各自干各自擅长的事。

## 一条投稿的一生

```
投稿人开单（[内容投稿](ISSUE_TEMPLATE/content-submission.yml) / [皮肤投稿](ISSUE_TEMPLATE/skin-submission.yml)）
      ↓ 新单/改单一律触发
submission-check.yml 自动校验并回帖（内容 → submission.py｜皮肤 → skin_submission.py）
      ↓（维护者打 `采用` 标签 = 点头）
submission-adopt.yml 按标签分流落盘 → git 提交推回 main → 回帖 / 打 `已上架` / 关单
      ↓
App 下次打开发现页（或皮肤中心）拉到新清单/索引，新内容即生效（不用发版）
      ↓（要撤掉时：维护者打 `下架` 标签）
submission-retract.yml 删条目与文件（分组因此空了就连封面一起删）→ 提交推回 main → 回帖 / 打 `已下架` / 关单
      ⚠ 下架只停掉**新的**分发：已经导入 / 已经套上的人撤不回来——本地优先的应用，文件早在他自己设备上
```

两者共用**同一个 Issues 列表**（两张表单选题型），只有上架目录不同：
内容 → `dist/discover/`；皮肤 → `dist/skins/`。

## 机器人怎么读单子

`.github/scripts/` 下一支脚本管一类：`submission.py`（内容）、`skin_submission.py`（皮肤）。
两支都是同样的四个动作：

| 命令 | 干什么 |
|---|---|
| `--check` | 解析正文 → 验字段 → （内容）试下载文件 → 回一条**可更新**的校验评论（每次编辑单子只改这一条，不刷屏） |
| `--adopt` | 落盘 + 写索引：内容 → `dist/discover/`（CRLF 行尾原样保留），皮肤 → `dist/skins/`；同时写一份 result 文件给 workflow |
| `--retract` | 下架：删条目 + 删文件（`file` / `thumb`，分组因此空了连 `cover` 一起删）。认出条目靠 **标识 → `sub-<单号>` → 名称**三级，靠名字认出来时回帖里会写明。**找不到就一个字节都不改**，只回帖说明、标签原样留着 |
| `--finalize` | 回帖；**只有真落盘**（result 文件在）才打标签、摘触发标签、关单——上架打 `已上架`／摘 `采用`，下架打 `已下架`／摘 `下架`（看 result 里的 `action`） |

正文认得两种形状：仓库表单（GitHub 把字段渲染成 `### 字段名`，按**前缀**匹配标题）和 App 内预填的正文（含 ```json 块）。

本地重跑某张单子（不联网、不落盘）：

```bash
python .github/scripts/submission.py      --check --body-file issue.md --comment-out /tmp/c.md
python .github/scripts/skin_submission.py --check --body-file issue.md --comment-out /tmp/c.md
```

## 命名与约定（别破坏，App 按这个读）

- 清单里的 `file` / `thumb` / `cover` **只能是相对文件名**，不能是 URL——清单指向谁由这个仓库说了算。
- 内容文件扁平存放：`card-<id>.png`（或 `.json`）、`book-<id>.json`、`story-<id>.json`、`thumb-<id>.jpg`、`cover-<theme>.jpg`。
- 内容条目字段：`id / name / tagline / file / thumb / kind / form`；`kind` ∈ 角色 / 世界书 / 故事，`form` ∈ ""（陪伴）/ experience / play / tool。
- 皮肤：`dist/skins/index.json` 索引 ＋ `dist/skins/<id>.json` 包原文；索引字段 `id / name / oneLiner / author / file / specVersion`。
- `id` 是英文小写短名（a-z 0-9 短横线），同时是文件名的一部分——**改了 id 就是另一条内容**。内容投稿不填也行，脚本按单号起一个。
- `discover-manifest.json` 是 CRLF 提交的，脚本按原行尾写回，改完 `git diff` 应当只有新增那几行。

## 标签

| 标签 | 谁打 | 意思 |
|---|---|---|
| `内容投稿` / `皮肤投稿` | 表单自动 | 这是投稿单，机器人管（`皮肤投稿` 也由 App 内「投稿」按钮带上） |
| `采用` | 维护者 | 点头，触发自动上架 |
| `已上架` | 机器人 | 走完了，文件与清单都在 main 上 |
| `下架` | 维护者 | 反悔／要撤：触发自动下架（删条目与文件） |
| `已下架` | 机器人 | 已从发现页（或皮肤中心）撤掉；单子会被关掉 |
