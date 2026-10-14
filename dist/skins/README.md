# 皮肤库（`dist/skins/`）

这里是**皮肤投稿上架后**的落地目录，与 `dist/discover/`（发现页精选）同一套机制：
没有服务器，全是静态文件，App 拉到索引即见。

```
dist/skins/index.json      索引：id / name / oneLiner / author / file / specVersion
dist/skins/<id>.json       皮肤包原文（就是能给 App 直接导入的那份）
```

- 索引里的 `file` 是**相对路径**（如 `dist/skins/dawn-paper.json`），取包走
  `https://raw.githubusercontent.com/CityNanFlower/whale/main/<file>`，
  国内可用同一套镜像前缀代理（与更新、发现页走同一条链）。
- **投稿**走本仓 Issues 的「[皮肤投稿](https://github.com/CityNanFlower/whale/issues/new?template=skin-submission.yml)」表单，
  或在 App 里「我的 → 皮肤 → 投稿」。审过打 `采用` 标签，机器人会自动落盘到这里。

## 许可

这一目录下的皮肤包由投稿者创作，以 **Creative Commons Attribution 4.0（CC BY 4.0）** 授权：
可自由复制、修改、再分发（含商用），条件是保留署名（投稿时登记的 GitHub 账号）与来源仓库。

- 许可证全文：https://creativecommons.org/licenses/by/4.0/legalcode
- 摘要（非法律文本）：https://creativecommons.org/licenses/by/4.0/

投稿即表示作者同意以上授权，作者仍保留自己作品的著作权。仓库自身的代码与配置仍是 AGPL-3.0（见根 `LICENSE`）。

**本目录不含任何字体文件**：皮肤包里的字体字段只是字族令牌名（`sans` / `serif` / `notoserif` /
`yozai` / `wenkai` / `notomono`），字体由 App 按需下载或由用户自行提供。
