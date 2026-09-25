# 鲸鱼 · API 与模型接入梳理

> 用途：作为未来「使用文档 · 模型接入」章节的底稿，同时作为开发/维护时的对照表。
> 整理日期：**2026-09-14**；**2026-09-15 两次补充核实**（第五节 5.3 为一轮、5.4 为二轮）。
> App 内已按本表自动适配（代码里 `data/ProviderProfiles.kt` 是唯一事实来源；模型清单在 `ModelCatalog.kt`）。
> ⚠️ **价格可信度分级（务必按标注使用）**：
> - **一手** = 直接打开官方定价页/官方 API 文档读到原文（2026-09-15 二轮全部改用浏览器实读）；
> - **二手** = 官方页面为 JS 渲染或需登录，只取得搜索结果汇总值，**用前必须回官网复核**；
> - **待核实** = 没有查到任何可用来源，**不要当准确值用**。

---

## 一、供应商总表

| 供应商 | 对话 Base URL | 生图 Base URL | 思考参数形态 | 温度 | 生图协议 | 尺寸写法 | 返回格式 | 门户/文档 |
|---|---|---|---|---|---|---|---|---|
| 火山方舟 | `https://ark.cn-beijing.volces.com/api/v3` | 同左 | `thinking.type` | 支持 | OpenAI 兼容 | `1024x1024` | 可选 | [文档](https://www.volcengine.com/docs/82379) · [价格](https://www.volcengine.com/docs/82379/1099320) |
| DeepSeek | `https://api.deepseek.com/v1` | 无 | `thinking.type` | 支持（思考模式下被忽略） | — | — | — | [文档](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode) · [价格](https://api-docs.deepseek.com/zh-cn/quick_start/pricing) |
| 智谱 | `https://open.bigmodel.cn/api/paas/v4` | 同左 | `thinking.type`（GLM-5.3 系强制思考，关闭档降为 `reasoning_effort=low`） | 支持 | OpenAI 兼容 | `1024x1024` | **固定 url** | [文档](https://docs.bigmodel.cn/cn/guide/start/model-overview) · [价格](https://open.bigmodel.cn/pricing)（302 → `docs.bigmodel.cn/cn/guide/start/pricing`） |
| Kimi / Moonshot | `https://api.moonshot.cn/v1` | 无 | `reasoning_effort`（K3 总是思考，关不掉） | **不支持**（传了报 `invalid temperature`，App 已自动不发） | — | — | — | [文档](https://platform.kimi.com/docs/guide/kimi-k3-quickstart) · [价格](https://platform.kimi.com/docs/pricing/chat) |
| 阿里通义 / 千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 同左 | `enable_thinking` | 支持 | **通义原生**（兼容模式不支持生图） | `1024*1024`（星号） | 返回 24 小时有效链接，App 自动下载 | [文档](https://help.aliyun.com/zh/model-studio/) · [价格](https://help.aliyun.com/zh/model-studio/billing-for-model-studio)（302 → `.../model-pricing`） |
| 腾讯云 TokenHub | `https://tokenhub.tencentmaas.com/v1` | 同左 | `thinking.type` | 支持 | OpenAI 兼容 | `1024x1024` | 可选 | [文档](https://cloud.tencent.com/document/product/1823/130078) · [模型价格](https://cloud.tencent.com/document/product/1823/130055) |
| MiniMax | `https://api.minimaxi.com/v1` | 同左 | **未适配**（保持模型默认） | 支持 | **私有** `/image_generation` | `aspect_ratio`（按比例） | `base64` / `url` | [文档](https://platform.minimax.cn/docs/api-reference/api-overview) · [价格](https://platform.minimax.cn/docs/guides/pricing-paygo)（**已换域名**：`.io` 在国内直连 ERR_CONNECTION_CLOSED） |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai` | 无 | `reasoning_effort`（none/high） | 支持（**但新版模型已弃用 temperature/top_p/top_k**，见注意事项） | — | — | — | [文档](https://ai.google.dev/gemini-api/docs) · [价格](https://ai.google.dev/gemini-api/docs/pricing) |
| OpenAI | `https://api.openai.com/v1` | 同左 | `reasoning_effort`（low/high，无"关闭"） | 支持 | OpenAI 兼容 | `1024x1024` | **固定 b64_json**（⚠️ 该参数对 GPT image 系官方标注"不支持"，见 6.2） | [文档](https://platform.openai.com/docs/api-reference/chat) · [价格](https://developers.openai.com/api/docs/pricing)（原 `platform.openai.com/docs/pricing` 302 到此） |
| SiliconFlow（**仅生图**） | 不支持（已从对话/创作移除） | `https://api.siliconflow.cn/v1` | — | — | OpenAI 兼容 | `1024x1024` | 可选 | [文档](https://docs.siliconflow.com/cn/userguide/introduction) · [模型广场](https://cloud.siliconflow.cn/models) |

> **2026-09-15 移除 OpenRouter**（用户反馈：聚合平台不常用，且其上模型基本在国外、无余额可用）。
> 已从 `ProviderProfiles.all` 与 `ModelCatalog` 的三套清单、视觉清单中删除。
> **对老用户的影响**：settings 里的 URL 与 Key 都还在，但 `resolve()` 不再认识它 →
> 走通用逻辑（发 temperature、不注入思考参数），模型名需手填；另外按供应商记忆的 Key
> 存的是 `providerKeys["openrouter"]`，`keyIdFor()` 现在回落成小写 URL，**Key 输入框会显示为空**，
> 需要重新粘贴一次。价格表里保留 OpenRouter 的报价，仅作**交叉验证来源**使用。

### 各供应商注意事项

- **火山方舟**：域名是 `volces.com`（不含 "volcengine"），App 已按域名显式映射；模型名用控制台里的推理接入点/模型名。
- **DeepSeek**：现行模型（deepseek-flash / deepseek-v4-pro）**不传参数也默认思考**，思考 token 计入输出上限；`deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 弃用，旧配置会自动迁移。
- **智谱**：**GLM-5.3 / 5.3-FLASH 强制思考**，传 `thinking.type=disabled` 会直接报错，所以"最低"档发的是 `reasoning_effort=low`；生图只返回 URL。
  ⚠️ **temperature 必须最多两位小数**，否则报「temperature 参数非法：限制小数点[2]位」
  （2026-09-15 用户实测 GLM-4-Flash-250414 / GLM-4.6V-Flash 失败）。App 已在
  `AiClient.safeTemperature()` 里统一四舍五入到两位并限制 0–2（历史存档里的脏值也能被兜住），
  设置页滑杆也改为从源头存干净值。
- **智谱（可用性）— 2026-09-15 已由"待核实"转为一手**：`GLM-4-Flash-250414` / `GLM-4.7-Flash` /
  `GLM-4.6V-Flash` **都在官方定价文档里，且都是免费档**。它们不在主表，而在各分类下的
  **"更多文本模型 / 更多视觉模型"折叠区**（这也是前一版误判"官方价目页未列=可能下线"的原因）。
  同一折叠区还有免费的 `GLM-Z1-Flash` / `GLM-4V-Flash` / `GLM-4.1V-Thinking-Flash`。
  `GLM-4.7-Flash` 实测**响应很慢**，根因是免费档**限 1 并发**（后台摘要/记忆任务会抢占排队），
  已改为连通性测试时固定关闭思考。
- **Gemini**：⚠️ **官方模型列表 / 定价表 / 更新日志里都查不到 `gemini-3.8-flash`**
  （现行最新 Flash 是 `gemini-3.6-flash` $1.50/$7.50，Pro 档是 `gemini-3.1-pro-preview` $2/$12）；
  只有站内横幅写"Gemini 3.8 Flash 现已推出"（见 6.2）。另外官方更新日志明确
  **`temperature` / `top_p` / `top_k` 已弃用**（新版模型），App 仍会对 Gemini 发 temperature。
- **OpenAI**：gpt-image 系列**永远返回 base64，没有 url 选项**，所以 App 固定 b64_json；
  但官方 API 参考进一步写明 `response_format` **"不支持 GPT image 模型"**（现行替代参数是
  `output_format`），App 仍在发这个冗余参数 —— **是否被拒未实测**（无余额），见 6.2。
- **Kimi**：K3 总是思考、**关不掉**，只能用 `reasoning_effort`（low/high/max，默认 max）；不接受 temperature；`kimi-k2.5` 与 `moonshot-v1` 系列已于 2026-08-31 下线。
- **通义/千问**：**兼容模式只支持对话与向量，不支持生图**（实测会 404），生图必须走原生 `multimodal-generation/generation`；尺寸用星号。
- **腾讯云 TokenHub**：OpenAI 兼容入口要带 `/v1`；不同地域要用对应站点地址（不支持跨地域），`hunyuan-role-latest` / `hy-role` 是角色扮演专用模型。
- **MiniMax**：国内站 base 是 `api.minimaxi.com`，**Key 必须与控制台地域一致**（配错会 401）；生图非 OpenAI 协议，App 单独适配。
  ⚠️ **文档/定价一律用 `.cn`，不要用 `.io`**：`.io` 国际站在国内直连是 `ERR_CONNECTION_CLOSED`
  （表现为"页面空白"），国内站 `platform.minimax.cn/docs/...` 有同款文档且是人民币刊例价。
- **SiliconFlow**：本版只作为生图供应商（对话/创作已在 0.1.2 移除，避免与国内主站模型清单混淆）。

---

## 二、模型清单（App 内下拉所见）

> **排序口径**：以下每组**已于 2026-09-15 按核实到的价格重排，规则 = 输出价升序**
> （同一供应商内为同币种，严格可比；跨币种的默认推荐清单按 **5.4 节**的折算口径）。
> 价格未查到的条目统一置于末尾，待核实后再插回正确位置。重排明细见 **5.3 节**。

### 对话模型

| 供应商 | 模型 |
|---|---|
| 火山方舟 | `doubao-seed-character-260628`（角色扮演专用，首选）、`doubao-seed-2-1-turbo-260628`、`doubao-seed-2-1-pro-260628` |
| DeepSeek | `deepseek-flash`、`deepseek-v4-pro` |
| 智谱 | `GLM-4-Flash-250414`（轻量，实测角色扮演好用）、`GLM-4.7-Flash`、`GLM-4.6V-Flash`（👁）、`GLM-5.3-Flash`（👁）、`GLM-5.3` |
| Kimi | `kimi-k3`（旗舰，👁）、`kimi-k2.6`（👁） |
| 通义/千问 | `qwen3.8-flash`（👁）、`qwen3.8-max-0902`（👁） |
| 腾讯云 | `hunyuan-role-latest`、`hy-role`（均角色扮演专用）、`hy4-preview`、`hy3`、`hy-vision-2.0-instruct`（👁，**2026-09-16 补**） |
| MiniMax | `MiniMax-M2.7-highspeed`、`MiniMax-M2.7`、`MiniMax-M3`（👁） |
| Gemini | `gemini-3.8-flash`（👁）、`gemini-3.1-pro` |
| OpenAI | `gpt-5.6-luna`（👁）、`gpt-6-astra`（👁） |

> ~~OpenRouter~~ 已于 2026-09-15 移除（不常用）——见第一节说明。

> 👁 = 支持图片输入，聊天页会出现发图按钮。图片**原生直传**（构造成 `image_url` 内容块），不做文字转述。

> **腾讯混元的视觉能力在哪（2026-09-16 核实，一手）**：**不在 `hy3` / `hy4-preview` 上** ——
> 官方能力标签只有「深度思考 / 结构化输出 / Function Calling / Cache」，两个都是纯文本；
> 混元视觉是**单独的 HY-Vision 系列**，TokenHub 的「多模态理解模型」共四款（价格为一手）：
> `hy-vision-2.0-instruct`（HY-Vision-2.0-Instruct，**单图**，不支持视频，¥7.5/¥17.5）、
> `youtu-vita`（YT-VITA，优图，可一次多图，¥1.2/¥3.5）、HY-Vision-1.5-Thinking（¥3/¥9）、HY-Vision-Video（¥3/¥9）。
> 出处：模型 ID 见 [语言模型调用概览](https://cloud.tencent.com/document/product/1823/130078)，价格见[价格页](https://cloud.tencent.com/document/product/1823/130055)。
> 清单里只收录了 `hy-vision-2.0-instruct`（唯一能直接当对话模型用的）与 `youtu-vita` 两个 ID 进"视觉模型"判定集，
> 进对话预设的只有前者（¥7.5/¥17.5 最贵故排最后）。
> **实测补充（2026-09-16 用户）**：`hy-vision-2.0-instruct` **开「深度」档能正常对话**，
> 说明它接受腾讯画像发的 `thinking.type=enabled`（虽然官方能力标签里没写"深度思考"）——
> 所以不需要为它排除思考字段。它的真实限制是**一次只能传一张图**。
> ⚠️ **留存疑点**：130078 把 HY-Vision-1.5-Thinking / HY-Vision-Video 映射到
> `hunyuan-t1-vision-20250916` / `hunyuan-turbos-vision-video-20250728`，而官方[迁移公告](https://cloud.tencent.com/announce/detail/2310)
> 明确写着这两个旧 ID「北京时间 2026 年 6 月 22 日 00:00 起正式下线」——两份官方文档互相矛盾，
> 故这两个 ID **不进清单**，等下次复核。

### 创作模型（AI 生成角色卡等）

与对话清单同源，但取"效率与性能兼顾"的档位：火山 `doubao-seed-2-1-turbo/pro`、DeepSeek `deepseek-flash` → `deepseek-v4-pro`、智谱 `GLM-4-Flash-250414` → `GLM-5.3`、通义 `qwen3.8-flash` → `qwen3.8-max-0902`、腾讯云 `hy3` / `hy4-preview`、MiniMax `M2.7-highspeed` → `M3`、OpenAI `gpt-5.6-luna` → `gpt-6-astra`。

### 生图模型

| 供应商 | 模型 |
|---|---|
| 火山方舟 | `doubao-seedream-4-0-250828`、`4-5-251128`、`5-0-260128`、`5-0-pro-260628` |
| 智谱 | `CogView-3-Flash`（**免费**）、`CogView-4`、`GLM-Image`（旗舰） |
| 通义/千问 | `qwen-image-3.0`、`qwen-image-2.0-pro`、`wan2.7-image-pro`（**注意：官方价目表里 `qwen-image-3.0-pro` 也在，只是更贵，见 5.2；App 选用更便宜的 3.0**） |
| 腾讯云 | `wand-vega-image-flash`、`wand-vega-image-pro`、`hy-image-v3`、`vidu-image-q2` |
| MiniMax | `image-01` |
| SiliconFlow | `Tongyi-MAI/Z-Image-Turbo`、`Kwai-Kolors/Kolors`、`Qwen/Qwen-Image`、`baidu/ERNIE-Image-Turbo` |
| OpenAI | `gpt-image-2.5-sunburst`、`gpt-image-2.5-flare`（2026-09-15 更新：旧的 gpt-image-1.5 / gpt-image-2 已不在官方价目表） |

> ~~OpenRouter~~ 已于 2026-09-15 移除（不常用）。

---

## 三、思考强度：每家实际发什么

App 的档位**按供应商与模型自适应**（不再统一显示"默认/关闭/深度"，因为有些模型关不掉）；
**当某家的"默认"与另一档完全等价时会合并**，避免两个选项效果一样（例：Kimi 的"默认"就是 `max`，
所以只显示「较低 / 最高」，并把"也是模型默认"写在说明里）。

| 供应商 / 模型 | 显示的档位 | 实际发送 |
|---|---|---|
| DeepSeek、火山方舟、腾讯云、智谱（非 5.3） | 默认 / 关闭 / 深度 | 默认=不发；关闭=`thinking.type=disabled`；深度=`enabled` |
| 智谱 `GLM-5.3*` | 默认 / **最低** / 深度 | 默认=不发（强制思考）；最低=`reasoning_effort=low`；深度=`thinking.type=enabled` |
| Kimi `kimi-k3` / `kimi-k2.6` | 默认 / **较低** / **最高** | 默认=不发（模型默认 max）；较低=`reasoning_effort=low`；最高=`reasoning_effort=max` |
| 通义/千问、SiliconFlow | 默认 / 关闭 / 深度 | `enable_thinking=true/false`（深度=true） |
| OpenAI | 默认 / **较低** / **较高** | `reasoning_effort=low/high`（无"关闭"档） |
| Gemini | 默认 / 关闭 / 深度 | `reasoning_effort=none/high` |
| MiniMax | — | 未适配，保持模型默认（界面会说明） |

> 设置页会显示「实际参数：xxx」与供应商注意事项，避免用户以为配置没生效。

---

## 四、回复长度上限（max_tokens）与上下文

1. **单条回复长度上限**（设置页可填 1–32000，默认 2048）：单次请求的输出上限。
2. **思考 token 计入该上限**：预算太小会出现"请求成功但正文为空"（`finish_reason=length`）。因此 App 有自动抬高规则：
   - 用户值 ≥ 8192 → 完全按用户值；
   - 用户值 < 8192 且"当前在思考" → 自动抬到 8192；
   - "在思考"的判定：**总是思考的模型（Kimi K3、GLM-5.3 系）恒为真**；DeepSeek 系"默认/深度"为真；其它供应商仅"深度"为真。
3. **创作/后台任务另有固定值**：生成角色卡固定 8192；前情提要摘要与记忆整理固定 1024 且**强制关闭思考**；起草/扩写提示词用用户设置值且强制关闭思考。
4. **上下文（输入侧）**：「携带历史条数」默认 40 条，另有 24000 字符总预算，超出从最早的消息开始丢弃。

---

## 五、价格参考（2026-09-14 首次核实，**2026-09-15 二轮全面转一手**）

> **币种与单位已按官方页面原样抄录**，不做汇率换算；跨币种之间不比较、不排序。
> 价格随时可能调整（多家今年已多轮调价），**App 内不展示价格**，只提供各家定价页入口
> （见第一节"价格"链接，对应代码 `ProviderProfiles.pricingUrl`）。
> **二轮的取证方式**：ZCode 内置浏览器逐页实读（WebFetch 对多数家被 403 / JS 渲染 / 登录墙挡住）。

### 5.1 对话 / 推理模型

| 供应商 | 模型 | 输入（缓存命中） | 输入（缓存未命中） | 输出 | 单位 | 可信度 | 来源 |
|---|---|---|---|---|---|---|---|
| 智谱 | `GLM-4-Flash-250414`（**App 默认清单第 1 位**） | **免费** | **免费** | **免费** | 每百万 tokens，128K 上下文，**免费档限 1 并发** | **一手** | docs.bigmodel.cn/cn/guide/start/pricing |
| 智谱 | `GLM-4.7-Flash` | **免费** | **免费** | **免费** | 同上（200K 上下文） | **一手** | 同上 |
| 智谱 | `GLM-4.6V-Flash`（👁） | **免费** | **免费** | **免费** | 同上（视觉理解） | **一手** | 同上 |
| 智谱 | `GLM-Z1-Flash`（未进清单） | **免费** | **免费** | **免费** | 同上（128K） | **一手** | 同上 |
| 智谱 | `GLM-4.7-FlashX` | ¥0.1 | ¥0.5 | ¥3 | 每百万 tokens（200K） | **一手** | 同上 |
| 智谱 | `GLM-4-Air-250414` | ¥0.25 | ¥0.5 | ¥0.5 | 每百万 tokens（128K） | **一手** | 同上 |
| 智谱 | `GLM-4-Long` | ¥0.5 | ¥1 | ¥1 | 每百万 tokens（1M） | **一手** | 同上 |
| 智谱 | `GLM-4-Plus` | ¥2.5 | ¥5 | ¥5 | 每百万 tokens（128K） | **一手** | 同上 |
| 智谱 | `GLM-5` | ¥1 | ¥4 | ¥18 | 每百万 tokens（输入 [0,32K)；≥32K 档 ¥1.5/¥6/¥22） | **一手** | 同上 |
| 智谱 | `GLM-5-Turbo` | ¥1.2 | ¥5 | ¥22 | 同上（≥32K 档 ¥1.8/¥7/¥26） | **一手** | 同上 |
| 智谱 | `GLM-5.1` | ¥1.3 | ¥6 | ¥24 | 同上（≥32K 档 ¥2/¥8/¥28） | **一手** | 同上 |
| 智谱 | `GLM-5.2` | ¥2 | ¥8 | ¥28 | 每百万 tokens（1M 上下文） | **一手** | 同上 |
| 智谱 | `GLM-5.3` | ¥2 | ¥8 | ¥28 | 每百万 tokens（1M 上下文） | **一手** | 同上 |
| 智谱 | `GLM-5.3-Flash`（👁） | ¥0.23 | ¥0.8 | ¥2.8 | 每百万 tokens（1M；另有"限时五折"¥0.115/¥0.4/¥1.4） | **一手** | 同上 |
| 智谱 | `GLM-5V-Turbo`（👁） | ¥1.2 | ¥5 | ¥22 | 每百万 tokens（[0,32K)；≥32K 档 ¥1.8/¥7/¥26） | **一手** | 同上 |
| 智谱 | `GLM-4.6V`（👁） | ¥0.2 | ¥1 | ¥3 | 每百万 tokens（[0,32K)；[32K,128K) 档 ¥0.4/¥2/¥6） | **一手** | 同上 |
| 智谱 | `GLM-4.6V-FlashX`（👁） | ¥0.03 | ¥0.15 | ¥1.5 | 同上（[32K,128K) 档 ¥0.03/¥0.3/¥3） | **一手** | 同上 |
| DeepSeek | `deepseek-flash`（**App 默认清单第 5 位**） | $0.003 / $0.006 | $0.15 / $0.30 | $0.60 / $1.20 | 每百万 tokens（**空闲 / 高峰**；官方页为**美元**） | **一手** | api-docs.deepseek.com/quick_start/pricing |
| DeepSeek | `deepseek-v4-pro` | $0.022 / $0.044 | $0.66 / $1.32 | $1.98 / $3.96 | 同上 | **一手** | 同上 |
| 火山方舟 | `doubao-seed-character-260628`（**App 默认清单第 3 位**） | ¥0.16 | ¥0.8 | ¥2 | 每百万 tokens（输入 [0,32K]；缓存存储 ¥0.017/百万/小时；(32K,128K] 档 ¥0.16/¥1.2/¥6） | **一手** | docs.volcengine.com/docs/82379/1544106 |
| 火山方舟 | `doubao-seed-2-1-pro-260628` | ¥1.2 | ¥6 | ¥30 | 每百万 tokens（[0,256]） | **一手** | 同上 |
| 火山方舟 | `doubao-seed-2-1-turbo-260628` | ¥0.6 | ¥3 | ¥15 | 同上 | **一手** | 同上 |
| 火山方舟 | `doubao-seed-1.6-flash`（**未进 App 清单，比 character 更便宜**） | ¥0.03 | ¥0.15 | ¥1.5 | 每百万 tokens（[0,32K]） | **一手** | 同上 |
| 火山方舟 | `deepseek-v4-flash 正式版`（火山托管） | ¥0.10 | ¥3 | ¥9 | 每百万 tokens（**2026-08-28 起上调**，此前为 ¥0.2/¥1/¥2） | **一手** | 同上 |
| 火山方舟 | `deepseek-v4-pro 正式版`（火山托管） | ¥0.30 | ¥9 | ¥27 | 同上（2026-08-28 起上调） | **一手** | 同上 |
| 通义/千问 | `qwen3.8-flash`（**App 默认清单第 4 位**） | — | ¥0.8 | ¥2.7 | 每百万 tokens（华北2·北京，0<Token≤1M） | **一手** | help.aliyun.com/zh/model-studio/model-pricing |
| 通义/千问 | `qwen3.8-max-0902` | — | ¥12 | ¥36 | 同上 | **一手** | 同上 |
| 通义/千问 | `qwen3.8-max-prime` | — | ¥24 | ¥72 | 同上（Prime 模式） | **一手** | 同上 |
| 通义/千问 | `qwen3.7-flash`（未进清单） | — | ¥0.2 | ¥0.8 | 每百万 tokens（[0,32K)；32K–256K 档 ¥0.6/¥2.4；256K–1M 档 ¥1.2/¥4.8） | **一手** | 同上 |
| 腾讯云 | `hy-role` / `hunyuan-role-latest`（**App 默认清单第 7 位**） | — | ¥2.4 | ¥9.6 | 每百万 tokens | **一手** | cloud.tencent.com/document/product/1823/130055 |
| 腾讯云 | `hy4-preview` | ¥0.3 | ¥6 | ¥18 | 每百万 tokens | **一手** | 同上 |
| 腾讯云 | `hy3` | ¥0.25 | ¥1 | ¥4 | 每百万 tokens | **一手** | 同上 |
| 腾讯云 | `hy-vision-2.0-instruct`（多模态理解，**单图**） | — | ¥7.5 | ¥17.5 | 每百万 tokens | **一手** | cloud.tencent.com/document/product/1823/130055 |
| 腾讯云 | `youtu-vita`（YT-VITA，可多图） | — | ¥1.2 | ¥3.5 | 每百万 tokens | **一手** | 同上 |
| 腾讯云 | HY-Vision-1.5-Thinking / HY-Vision-Video | — | ¥3 | ¥9 | 每百万 tokens；**ID 存疑未收录**（见第四节脚注） | **一手** | 同上 |
| 腾讯云 | `DeepSeek-V4.1-Flash 原厂直供` | ¥0.02 / ¥0.04 | ¥1 / ¥2 | ¥4 / ¥8 | 每百万 tokens（空闲 / 高峰） | **一手** | 同上 |
| OpenAI | `gpt-6-astra` | $1.00 | $10.00 | $50.00 | 每百万 tokens（短上下文；长上下文 $2/$20/$75） | **一手** | developers.openai.com/api/docs/pricing |
| OpenAI | `gpt-5.6-luna`（**App 默认清单第 6 位**） | $0.02 | $0.20 | $1.20 | 每百万 tokens（短上下文；长上下文 $0.04/$0.40/$1.80） | **一手** | 同上 |
| OpenAI | `gpt-5.6-sol` | $0.40 | $4.00 | $20.00 | 同上（长上下文 $0.80/$8/$30） | **一手** | 同上 |
| OpenAI | `gpt-5.6-terra` | $0.20 | $2.00 | $12.00 | 同上（长上下文 $0.40/$4/$18） | **一手** | 同上 |
| Kimi | `kimi-k3` | ¥2.00 | ¥20.00 | ¥100.00 | 每百万 tokens | 一手（09-14 取得，本轮未复读） | platform.kimi.com/docs/pricing/chat |
| Kimi | `kimi-k2.6` | ¥1.10 | ¥6.50 | ¥27.00 | 同上 | 一手（同上） | 同上 |
| Gemini | `gemini-3.6-flash`（**官方现行最新 Flash**） | $0.15 | $1.50 | $7.50 | 每百万 tokens（**另有免费层**） | **一手** | ai.google.dev/gemini-api/docs/pricing |
| Gemini | `gemini-3.5-flash` | $0.15 | $1.50 | $9.00 | 同上（有免费层） | **一手** | 同上 |
| Gemini | `gemini-3.1-pro-preview`（**官方 Pro 档，带 -preview**） | $0.20 | $2.00 | $12.00 | 每百万 tokens（≤200K；>200K 为 $0.40/$4/$18） | **一手** | 同上 |
| Gemini | `gemini-3.8-flash`（**App 清单里那一条**） | — | — | — | **官方模型列表 / 定价表 / 更新日志三处都 0 匹配**，价格无从核实 | **一手（确认查不到）** | `.../docs/models`、`.../docs/changelog`、`.../docs/pricing` |
| MiniMax | `MiniMax-M3` | ¥0.42（原 ¥0.84） | ¥2.10（原 ¥4.20） | ¥8.40（原 ¥16.80） | 每百万 tokens（≤512K 输入；**永久五折**；>512K 档为 ¥0.84/¥4.20/¥16.80） | **一手** | platform.minimax.cn/docs/guides/pricing-paygo |
| MiniMax | `MiniMax-M2.7` | ¥0.42 | ¥2.1 | ¥8.4 | 每百万 tokens（缓存写入 ¥2.625） | **一手** | 同上 |
| MiniMax | `MiniMax-M2.7-highspeed`（**App 默认清单第 8 位**） | ¥0.42 | ¥4.2 | ¥16.8 | 每百万 tokens（缓存写入 ¥2.625） | **一手** | 同上 |
| ~~OpenRouter~~（**已移除供应商**，仅作交叉验证来源） | `deepseek/deepseek-v4.1-flash` | $0.003 | $0.15 | $0.60 | 每百万 tokens（工作日高峰 $0.30/$1.20） | 一手（09-14） | openrouter.ai/api/v1/models |

> **DeepSeek 峰谷分时（官方页原文）**：空闲价为高峰价的一半；高峰为**周一至周五**
> **01:00–04:00 与 06:00–10:00 UTC**（即北京时间 9:00–12:00、14:00–18:00），
> 周末全天按空闲价。腾讯云"原厂直供"档的转人民币报价与之一致
> （`deepseek-flash` 空闲 ¥1/¥4、高峰 ¥2/¥8；`v4-pro` 空闲 ¥4.5/¥13.5、高峰 ¥9/¥27），
> 与 App 内标注的 ¥4 / ¥13.5 完全吻合，可互为交叉验证。
> **注意 DeepSeek 已改过名**：官方页写明 `deepseek-v4-flash` / `deepseek-v4-flash-vision-exp`
> 是旧名、模型已退役，请求会被转到 `DeepSeek-V4.1-Flash` 并按 Flash 计价；官方推荐直接用
> `deepseek-flash`（App 用的正是这个名字，无需改）。
> **火山托管与官方直连是两套价**：火山上的 `deepseek-v4-flash 正式版` 已于 2026-08-28 上调到
> ¥3/¥9（此前 ¥1/¥2），比 DeepSeek 官方空闲价贵不少——App 的 DeepSeek 走官方域名，不受影响。
> **智谱免费档的并发限制值得注意**：`GLM-4-Flash-250414` / `GLM-4.7-Flash` / `GLM-4.6V-Flash`
> 免费但**限 1 并发**（官方定价页问答原文确认：免费模型仅此两款文本+一款视觉）。
> 2026-09-15 实测遇到 `GLM-4.7-Flash` 连通性测试「时长过长」，根因就在这里——只要同时有别的
> 请求在跑（后台前情提要/记忆整理、上一轮未结束的请求），免费档就得排队。
> **别让"免费"掩盖了吞吐瓶颈**：日常聊天可以用，但并发场景会排队。
> **⚠️ `gemini-3.8-flash` 存疑（见 6.2）**：唯一说它存在的是 ai.google.dev 站内横幅
> "Gemini 3.8 Flash 现已推出。试试看。"，而官方模型列表页的"Gemini 3 稳定/预览"清单里没有它，
> 更新日志最新条目停在 2026-07-21（Gemini 3.6 Flash GA）。App 里给它的 $0.75/$3.75 与
> **任何**在售模型都对不上，属**无来源报价**。
> **⚠️ Gemini 抽样参数已弃用**：更新日志写明新版模型**弃用 `temperature` / `top_p` / `top_k`**，
> 而 App 仍对 Gemini 发 temperature（`ProviderProfiles` 里标的是"支持"）——需实测是否报错。

### 5.2 生图模型

| 供应商 | 模型 | 价格 | 计费单位 | 可信度 | 来源 |
|---|---|---|---|---|---|
| 智谱 | `CogView-3-Flash`（**App 默认清单第 1 位**） | **免费** | 多分辨率，每次请求 | **一手** | docs.bigmodel.cn/cn/guide/start/pricing |
| 通义/千问 | `qwen-image-3.0`（**App 默认清单第 2 位**） | ¥0.02 / ¥0.18 | 1K：输入 ¥0.02/张、输出 ¥0.18/张；2K 输出同为 ¥0.18/张 | **一手** | help.aliyun.com/zh/model-studio/model-pricing |
| 通义/千问 | `qwen-image-3.0-pro`（**存在，但比 3.0 贵，未进清单**） | ¥0.02 / ¥0.25（1K）；2K 输出 ¥0.5 | 同上 | **一手** | 同上 |
| 通义/千问 | `qwen-image-2.0-pro` | ¥0.5/张 | 仅输出计费 | **一手** | 同上 |
| 通义/千问 | `wan2.7-image-pro` / `wan2.7-image` | ¥0.50 / ¥0.20 | 每张（仅输出计费） | **一手** | 同上 |
| 火山方舟 | `doubao-seedream-4-0` | ¥0.20 | 每张（输入图免费） | **一手** | docs.volcengine.com/docs/82379/1544106 |
| 火山方舟 | `doubao-seedream-5-0-260128`（Lite 档） | ¥0.22 | 每张（输入图免费） | **一手**（按官方 lite 档口径；该日期 ID 与 lite/pro 的对应关系待实测） | 同上 |
| 火山方舟 | `doubao-seedream-4-5` | ¥0.25 | 每张（输入图免费） | **一手** | 同上 |
| 火山方舟 | `doubao-seedream-5-0-pro-260628` | ¥0.30 / ¥0.60 | 输出 ≤261 万像素 ¥0.30/张，>261 万 ¥0.60/张；参考图第 2 张起 ¥0.02/张（首张免费）。图层拆分场景 ¥0.15/¥0.30 | **一手**（上一版像素阈值写 236 万，**已订正为 261 万**） | 同上 |
| 智谱 | `GLM-Image` | ¥0.1 | 每次（多分辨率） | **一手** | docs.bigmodel.cn/cn/guide/start/pricing |
| 智谱 | `CogView-4` | ¥0.06 | 每次（Batch ¥0.03） | **一手** | 同上 |
| 智谱 | `CogVideoX-Flash`（视频，未进清单） | **免费** | 每次 | **一手** | 同上 |
| 腾讯云 | `wand-vega-image-flash`（**App 默认清单第 4 位**） | ¥0.45 / ¥0.675 / ¥1.008 | 1K / 2K / 4K 每张（10 元/百万 token × 45,000 / 67,500 / 100,800 token） | **一手** | cloud.tencent.com/document/product/1823/130055 |
| 腾讯云 | `WAND-Vega-Image1.0 Lite`（未进清单） | ¥0.162 / ¥0.18 / ¥0.225 | 1K / 2K / 4K 每张 | **一手** | 同上 |
| 腾讯云 | `wand-vega-image-pro` | ¥0.95（1K/2K）/ ¥1.71（4K） | 每张；输入图前 3 张免费，第 4 张起 ¥0.1/张 | **一手** | 同上 |
| 腾讯云 | `hy-image-v3`（官方名为 `Hy-Image-3.0`） | ¥0.2 | 每张（20,000 token × 10 元/百万） | **一手** | 同上 |
| 腾讯云 | `vidu-image-q2`（`Vidu-Image-q2`） | ¥0.1875 / ¥0.25 / ¥0.3125 | 文生图 1080P / 2K / 4K 每张；参考生图更高（1–3 张：¥0.25/¥0.375/¥0.625；4–7 张：¥0.3125/¥0.5/¥0.9375） | **一手** | 同上 |
| 腾讯云 | `Seedream-Image-v5.0-pro` / `lite`（腾讯云转售） | ¥0.3/¥0.6；lite ¥0.22 | 同火山口径 | **一手** | 同上 |
| OpenAI | `gpt-image-2.5-sunburst` / `gpt-image-2.5-flare` | $8 / $2 / $30 | 每百万 token（图像模态：输入/缓存命中/输出）；文本模态 $5/$1.25。**注意不是按张计价**，单张成本取决于输出 token 数 | **一手** | developers.openai.com/api/docs/pricing |
| OpenAI | 现行可选 `size` / `quality` / `output_format` | — | 任意 `宽x高`（16 的倍数、宽高比 1:3–3:1、最大 3840x2160，>2560x1440 为实验性）；quality 支持 auto/low/medium/high，2.5 系另支持 xhigh/max；output_format = png/jpeg/webp | **一手** | developers.openai.com/api/reference/resources/images/methods/generate |
| OpenAI | `gpt-image-1.5` / `gpt-image-2` | **已不在官方价目表**（0 匹配） | — | **一手**（确认下架） | 同上 |
| ~~OpenRouter~~ | 图片模型 | 未查到 | — | — | 供应商已于 2026-09-15 移除，不再需要 |
| MiniMax | `image-01` / `image-01-live` | ¥0.025 | 每张（人民币刊例价；≈$0.0037，与上一版记的 $0.0035 一致，只是币种不同） | **一手** | platform.minimax.cn/docs/guides/pricing-paygo |
| SiliconFlow | `Tongyi-MAI/Z-Image-Turbo`、`Kwai-Kolors/Kolors`、`Qwen/Qwen-Image`、`baidu/ERNIE-Image-Turbo` | 未查到 | — | **待核实** | 模型广场需登录（本轮实测 `cloud.siliconflow.cn/models` → 302 → `account.siliconflow.cn/zh/login`），无登录态抓不到 |
| SiliconFlow | `FLUX.2-pro` / `FLUX.2-flex`（**未进 App 清单**） | $0.03 / $0.06 | 每张 | 二手（沿用上一轮记录） | 硅基流动官方博客，检索于 2026-09-14 |

### 5.3 2026-09-15 一轮：补充核实与清单重排（**已改代码**）

#### (1) 一轮用浏览器补齐的数据

官方定价页多为 JS 渲染或挡爬虫（WebFetch 拿不到），改用实机浏览器打开读取：

| 供应商 | 核实到的内容 | 来源 |
|---|---|---|
| OpenAI | 旗舰对话表（短上下文，每百万 token）：`gpt-6-astra` $10/$1/$12.5/$50、`gpt-5.6-sol` $4/$0.4/$5/$20、`gpt-5.6-terra` $2/$0.2/$2.5/$12、`gpt-5.6-luna` $0.20/$0.02/$0.25/$1.20（输入/缓存命中/缓存写/输出） | developers.openai.com/api/docs/pricing |
| OpenAI | **图像模型已更名**：现行只有 `gpt-image-2.5-sunburst` 与 `gpt-image-2.5-flare`，图像模态 $8/$2/$30、文本模态 $5/$1.25（每百万 token）；**`gpt-image-1.5` / `gpt-image-2` 在该页 0 匹配（已不在售）** | 同上 |
| 智谱 | `GLM-Image` **¥0.1/次**（多模态生成）；视觉理解 `GLM-5V-Turbo` ¥5/¥22（[0,32K)）；文本模型现行价目为 GLM-5.2 ¥8/¥28、GLM-5.1 ¥6/¥24、GLM-5-Turbo ¥5/¥22、GLM-5 ¥4/¥18，缓存存储限时免费 | docs.bigmodel.cn/cn/guide/start/pricing |
| 火山方舟 | `doubao-seed-2-1-pro` ¥6/¥30（缓存命中 ¥1.2）、`doubao-seed-2-1-turbo` ¥3/¥15（当时只拿到二手） | 搜索结果 |

> ~~智谱现行价目表已不含 GLM-4.x，这三个是否已下线需实测确认~~
> **→ 一轮的这个判断是错的，二轮已推翻**：GLM-4.x 三款并非"不在价目表"，而是被折叠在
> 各分类的"更多文本模型 / 更多视觉模型"里，且都是免费档。定位方法见 5.5 节。

#### (2) 重排做了什么（`ModelCatalog.kt`）

规则：**输出价升序**；同供应商内为同币种严格可比；**价格未查到者统一置于末尾**，待核实后插回正确位置。
"角色扮演首选"这类推荐只体现在**各供应商自己的清单**里（例如火山清单仍保持
`doubao-seed-character` 在第一位），跨服务商的默认推荐清单不为此破例——它只在
Base URL 匹配不到任何供应商时才被用到，影响面小。

| 清单 | 改动 | 依据 |
|---|---|---|
| `chatByKeyword` Kimi | `k3, k2.6` → **`k2.6, k3`** | k2.6 ¥6.5/¥27 < k3 ¥20/¥100 |
| `chatByKeyword` MiniMax | `highspeed, M2.7, M3` → **`M2.7, M3, highspeed`** | M2.7 与 M3 同价（$0.30/$1.20），highspeed 更贵（$0.60/$2.40） |
| `chatByKeyword` OpenRouter | `gemini, deepseek, k3, astra` → **`deepseek, gemini, k3, astra`**（**该清单当日稍后随供应商一起移除**，此行仅存记录） | 输出价 $0.60 < $3.75 < ≈$14 < $50 |
| `creationByKeyword` Kimi / MiniMax | 同上两处同步 | 同上 |
| `imageByKeyword` OpenAI / OpenRouter | `gpt-image-1.5, gpt-image-2` → **`gpt-image-2.5-sunburst, gpt-image-2.5-flare`**（OpenRouter 那份随供应商移除） | 官方定价页 0 匹配旧 id、仅列 2.5 两档 |
| `defaultChatPresets` | 按折算价升序。**09-15 补充核实后全部条目都有价了**，不再有"未知价置尾"：GLM-4-Flash-250414（免费）→ GLM-4.7-Flash（免费）→ doubao-seed-character（¥2）→ qwen3.8-flash（¥2.7）→ deepseek-flash（¥4）→ gpt-5.6-luna（≈¥8.1）→ hy-role（¥9.6）→ MiniMax-M2.7-highspeed（≈¥16.2）→ gemini-3.8-flash（≈¥25.4）→ kimi-k3（¥100） | 见 5.4 折算口径；智谱免费档来自 09-15 补充核实 |
| `defaultImagePresets` | 一轮先做了一半（`qwen-image-3.0` ¥0.18 在前，其余未知价在后）；**二轮已按查到的一手价重排完成**，见 5.5 节 | 同上 |

> **未动的部分（有意为之）**：DeepSeek（flash ¥4 < v4-pro ¥13.5）、通义（¥2.7 < ¥36）、
> OpenAI（$1.20 < $50）、Gemini（$3.75 < $12）、智谱（GLM-5.3-Flash ¥2.8 < GLM-5.3 ¥28）、
> 火山对话（turbo ¥15 < pro ¥30）、智谱生图（CogView-4 ¥0.06 < GLM-Image ¥0.1）等
> **原本就与价格一致，未做无谓改动**；腾讯 / SiliconFlow / 火山 Seedream 4.x 等价格未知的清单同样保持原样。

#### (3) 链接问题

原先错误的四处定价页链接（MiniMax 404、智谱/腾讯云/通义指向无价格页）已于 2026-09-14 修正，
一轮实读确认 `open.bigmodel.cn/pricing` 可正常跳转到 docs 定价页。
**二轮又发现两处需要修正**（见 5.5）：火山 `/pricing?product=ark` 是 SPA、页面上没有价格
→ 换成文档版价格页；OpenAI `platform.openai.com/docs/pricing` 会 302 → 直接写 302 后的地址。
**两处均已改代码。**

### 5.4 跨币种折算口径（仅用于默认推荐清单排序）

默认推荐清单（`defaultChatPresets` / `defaultImagePresets`）跨供应商、币种不统一，
排序时按 **2026-09-14 人民币兑美元中间价 6.7698 ≈ 6.77** 折算（来源：中国货币网/上证报，二手）。
**折算仅用于排序，不代表实际结算价**；官方定价页的原始币种金额以第五节表格为准。
跨币种比较本身有精度损失，若某条的折算结果处于相邻两名之间，不要据此做"更便宜"的结论。

### 5.5 2026-09-15 二轮：浏览器逐家实读（**已改代码 + 改链接**）

#### (1) 这轮是怎么取到数的（方法可复用）

- **工具**：ZCode 内置浏览器（`iab`）。**不是用户的 Edge** —— 那需要 `computer-use` 桌面会话，
  本次该会话处于停止状态、无法自恢复（详见第六节开头说明）。
- **关键教训**：WebFetch 对多数家直接 403 或只拿到空壳（JS 渲染），**必须用真浏览器**。
  而"真浏览器里某页空白"**要先怀疑连通性再怀疑前端**——MiniMax 的 `.io` 国际站实际是
  `ERR_CONNECTION_CLOSED`（国内直连不可达），换国内站 `platform.minimax.cn` 同款文档立刻就通了。
  **以后遇到"空白页"，先试着换成该服务的国内域名。**
- **定位被折叠的价格表**：智谱的 GLM-4.x 藏在"更多文本模型 / 更多视觉模型"折叠区，
  页面正文里**默认不渲染** → 必须先在页面上点开折叠项再读取（或用 `getByText(...).click()`）。
  一轮之所以误判"GLM-4.x 已不在价目表"，就是没展开折叠区。
- **失败兜底**：智谱定价页的"详细价格"按钮会**新开一个标签页**指向 docs 定价文档；
  点完必须同时列 `tabs.list()` 与 `user.openTabs()`，再 `claimTab()` 认领新页。

#### (2) 二轮逐家结果一览

| 供应商 | 结果 | 可信度 |
|---|---|---|
| 智谱 | 完整价目表拿到（GLM-5.3 ¥8/¥28、GLM-5.3-Flash ¥0.8/¥2.8、GLM-4-Flash-250414/4.7-Flash/4.6V-Flash **免费**、CogView-3-Flash **免费**）；**GLM-4.x 疑点关闭** | 一手 |
| 火山方舟 | 完整价目表拿到（`doubao-seed-character` ¥0.8/¥2 确认；Seedream 4.0 ¥0.20、4.5 ¥0.25、5.0-lite ¥0.22、5.0-pro ¥0.30/¥0.60） | 一手 |
| DeepSeek 官方 | 完整价目表拿到（`deepseek-flash` 空闲 $0.15/$0.6、`v4-pro` $0.66/$1.98；明确旧名 `deepseek-v4-flash` 已退役） | 一手 |
| 腾讯云 TokenHub | 完整价目表拿到（`Hy-Role` ¥2.4/¥9.6、`Hy4 preview` ¥6/¥18、`Hy3` ¥1/¥4、`WAND-Vega-Image1.0` 三档、`Hy-Image-3.0` ¥0.2/张、`Vidu-Image-q2` 三档） | 一手 |
| 通义/百炼 | 完整价目表拿到（`qwen3.8-flash` ¥0.8/¥2.7、`qwen-image-3.0` ¥0.18/张、`wan2.7-image-pro` ¥0.5/张）；**并发现 `qwen-image-3.0-pro` 确实存在**（见下） | 一手 |
| OpenAI | 价格表 + **API 参考原文**拿到；发现 `response_format` 对 GPT image 系不受支持（见 6.2） | 一手 |
| Gemini | 价格表 + 模型列表 + 更新日志拿到；**`gemini-3.8-flash` 三处 0 匹配**（见 6.2） | 一手（负结论） |
| SiliconFlow | **未取到**：`cloud.siliconflow.cn/models` 302 跳登录页，无登录态 | 待核实 |
| MiniMax | 完整价目表拿到（**人民币**）：M3 ¥2.1/¥8.4（永久五折）、M2.7 ¥2.1/¥8.4、M2.7-highspeed ¥4.2/¥16.8、`image-01` ¥0.025/张 | 一手 |

#### (3) 一处**纠正**：`qwen-image-3.0-pro` 是存在的

上一版本文档按用户口述记了"通义没有 `qwen-image-3.0-pro`"，**二轮实读官方价目表后证明该说法不成立**：
阿里云百炼"千问图像生成与编辑"表里 `qwen-image-3.0-pro` 与 `qwen-image-3.0` **并列在售**，只是更贵：

| 模型 | 1K 输出 | 2K 输出 |
|---|---|---|
| `qwen-image-3.0`（App 选用） | ¥0.18/张 | ¥0.18/张 |
| `qwen-image-3.0-pro` | ¥0.25/张 | ¥0.5/张 |

**结论：App 清单里的 `qwen-image-3.0` 是对的、也是更划算的那一档，代码不需要改**；
需要改的是本文档旧版里那句错误注释（已改）。用户应当是没在控制台的模型选择器里看到 -pro 档，
但它确实在售。

#### (4) 二轮的清单重排（`ModelCatalog.kt`，仅生图侧）

对话侧 `defaultChatPresets` 一轮已完成、二轮无需再动（价格全部复核无变化）。
生图侧 `defaultImagePresets` 按**每张输出价**升序重排：

| 位置 | 模型 | 依据 |
|---|---|---|
| 1 | `CogView-3-Flash` | **免费**（一轮时因"待核实"被放在末尾，二轮转正到第 1 位） |
| 2 | `qwen-image-3.0` | ¥0.18/张（1K） |
| 3 | `doubao-seedream-5-0-260128` | ≈¥0.22/张（按火山 lite 档；该日期 ID 对应 Pro 还是 Lite 仍待实测） |
| 4 | `wand-vega-image-flash` | ¥0.45/张（1K）、¥0.675（2K）、¥1.008（4K） |
| 5–7 | `gpt-image-2.5-sunburst` / `flare` / `Kwai-Kolors/Kolors` | 前两者**按 token 计价**、无法与"按张价"直接比较；`Kolors` 价格仍未取到 → 一律置末尾 |

> **MiniMax 也在这轮转正**（见 5.5(2)）：拿到人民币刊例价后，`defaultChatPresets` 里
> `MiniMax-M2.7-highspeed` 的标注由"≈¥16.2（美元折算）"改为**官方 ¥16.8**，
> 位置仍夹在 `hy-role`（¥9.6）与 `gemini-3.8-flash`（¥25.4 存疑）之间 → **顺序不变**。
> 顺带确认腾讯云转售的 MiniMax-M3（¥2.1/¥8.4）与 MiniMax 自家刊例价一致。

> 顺带记录一个**未动但值得考虑**的项：火山 `doubao-seed-1.6-flash` 是 ¥0.15/¥1.5，
> **比 App 现在用的 `doubao-seed-character`（¥0.8/¥2）更便宜**；但 character 是角色扮演专用模型，
> 属于"效果取舍"而非纯价格问题，**不擅自替换**，留给用户决定是否加进清单。

---

## 六、待核实清单（下一轮核实的入口）

> **关于"用 Edge 核实"这件事**：本轮本该用用户的 Edge，但 `computer-use` 桌面会话处于
> **停止（kill switch）状态且无法自行恢复**，而当前唯一可用的浏览器后端是 ZCode 内置浏览器（`iab`，
> 无登录态、无用户的 Key/账号）。所以：**联网公开页面已全部用内置浏览器读完**（见 5.5；
> MiniMax 换国内域名后也读到了），**只剩需要登录态的 SiliconFlow 模型广场未取到**——
> 其登录页已在 `iab` 里开好，等用户登录后继续。

0. **按真实价格重排模型清单 —— ✅ 两轮均已落地（2026-09-15）**：
   - 对话侧 `defaultChatPresets`：一轮完成，二轮复核价格无变化，**无需再动**；
   - 生图侧 `defaultImagePresets`：**二轮重排完成**（`CogView-3-Flash` 免费转正到第 1 位），明细见 5.5(4)。
   - 仍剩：`Kwai-Kolors/Kolors`（硅基流动，需登录）与 `gpt-image-2.5-*`（按 token 计价）
     无法与"按张价"比较，**保持置尾**；火山 `doubao-seedream-5-0-260128` 对应 lite 还是 pro 待实测。
1. **价格补全 —— 二轮已全面转一手**（2026-09-15）：
   - **已转一手（本轮实读官方页）**：智谱全部（含 GLM-4.x 免费档）、火山方舟全部（含 Seedream 按张价）、
     DeepSeek 官方、腾讯云 TokenHub 全部、通义/百炼全部、OpenAI（价格表 + API 参考原文）、
     Gemini 价格表/模型列表/更新日志。
   - **本轮降级后又转正**：MiniMax（`.io` 在国内直连连不上 → 换 `.cn` 域名后拿到人民币刊例价，**已恢复一手**）；
     **仅 SiliconFlow 仍是登录墙** → 待复核。
   - **仍未取到的唯一价格**：SiliconFlow 四款生图模型；以及 OpenAI 图像模型的"单张成本"
     （官方按 token 计价，需按输出 token 数换算，官方提供了 image cost calculator 但未给静态表）。
   - **Kimi 两家**：本轮未复读，沿用 09-14 的一手值（`kimi-k3` ¥20/¥100、`kimi-k2.6` ¥6.5/¥27）。
2. **⚠️ 模型名疑点（未改动代码，需实测）**：
   - **🔴 OpenAI `response_format` 对 GPT image 系不受支持（二轮新发现，优先级最高）**：
     官方 API 参考 `POST /images/generations` 的参数表原文——
     *"`response_format`: … 仅用于 dall-e-2/dall-e-3 返回 url 或 b64_json。**该参数不支持 GPT image 模型，
     它们总是返回 base64 编码的图片**"*；现行对应参数是 **`output_format`**（png/jpeg/webp）。
     而 App 在 `AiClient.kt:824` 对 OpenAI 兼容分支**无条件发 `response_format`**，
     `ProviderProfiles` 又把 OpenAI 固定成 `b64_json` → **这个参数对 GPT image 系纯属冗余**。
     **风险**：未知参数是"被忽略"还是"被 400 拒绝"，直接决定 OpenAI 生图能不能用；
     **本轮无余额、无法实测**。修复方向很明确（OpenAI 档不发该字段，或改发 `output_format`），
     **但必须先用真实 Key 确认现状**，否则可能把"其实能用"改成"用不了"。
   - **🔴 `gemini-3.8-flash` 查不到官方出处（二轮新发现）**：官方**模型列表页**（稳定档：
     3.6 Flash / 3.5 Flash / 3.5 Flash-Lite / 3.1 Flash-Lite + Nano Banana 系；预览档：3.1 Pro /
     3 Flash / 3.5 实时翻译 / 3.1 Flash Live）、**定价表**、**更新日志**（最新条目 2026-07-21）**三处都
     没有 `gemini-3.8-flash`**。唯一"证据"是 ai.google.dev 的**站内横幅**"Gemini 3.8 Flash 现已推出。试试看。"。
     App 清单里那条 `gemini-3.8-flash` 的价格 $0.75/$3.75 与**任何**在售模型都对不上 → **属无来源报价**。
     同一清单里 `gemini-3.1-pro` 也应对齐官方 ID `gemini-3.1-pro-preview`（官方 Pro 档带 `-preview` 后缀）。
     **处理建议**：待下一轮核对官方价页后再决定是改 ID（如 `gemini-3.6-flash` $1.50/$7.50）还是保留观察；
     **本轮不擅自替换**（换 ID 会连带改变价格档位与排序）。
   - **⚠️ Gemini 抽样参数已弃用（二轮新发现）**：官方更新日志写明新版模型
     *"已弃用的参数：抽样参数 `temperature`、`top_p` 和 `top_k`"*，而 App 对 Gemini 发 temperature
     （`ProviderProfiles` 里 Gemini 标的是"支持温度"）→ 需实测是**忽略**还是会**报错**。
   - **OpenAI 图像模型已更名**：官方定价页 0 匹配 `gpt-image-1.5` / `gpt-image-2`，现行只有
     `gpt-image-2.5-sunburst` / `gpt-image-2.5-flare`（含 2026-09-08 快照）→ **代码已替换**（见 5.3 节）。
   - ~~OpenRouter 的 DeepSeek 托管名~~ → **已随供应商移除而关闭**，不再跟进
     （用户无法使用国外模型，无余额）。
   - **通义**：~~阿里没有 `qwen-image-3.0-pro`~~ → **二轮推翻**：官方价目表里 -pro 与 3.0 **并列在售**
     （-pro 更贵：1K ¥0.25/张 vs ¥0.18/张）。**App 选 `qwen-image-3.0` 是对的、也更便宜，代码不改**，
     改的是本文档旧版那句错误注释（见 5.5(3)）。
   - **智谱 GLM-4.x**：~~价格待核实~~ → **二轮转一手**：`GLM-4-Flash-250414` / `GLM-4.7-Flash` /
     `GLM-4.6V-Flash` 都在官方定价文档的折叠区里、**都是免费档（限 1 并发）** → **保留在清单里，代码不改**；
     `GLM-4.7-Flash` 响应偏慢（已把连通性测试改为固定关闭思考来规避）。
   - **智谱 temperature**：必须 ≤2 位小数（见第一节注意事项）→ **代码已修**
     （`AiClient.safeTemperature()` + 设置页滑杆取整）。
3. **智谱**：`GLM-4-Long`（¥1/¥1，1M 上下文）是否适合超长对话；`GLM-5V-Turbo`（已核价 ¥5/¥22）是否值得进清单；
   免费的 `GLM-Z1-Flash` / `GLM-4V-Flash` / `GLM-4.1V-Thinking-Flash` 是否值得加进清单。
4. **腾讯云**：官方模型名与 App 里的写法**不一致**（官方 `WAND-Vega-Image1.0 Flash/Pro/Lite`、
   `Hy-Image-3.0`、`Vidu-Image-q2`；App 写 `wand-vega-image-flash` / `hy-image-v3` / `vidu-image-q2`）
   → **嫌疑是"显示名 vs API model id"的差异，但不实测不敢改**；能力差异（lite/flash/pro）也需确认。
5. **MiniMax**：思考参数是否已支持（官方若提供即补上适配）；`image-01-live` 的画风参数是否值得做进 App
   （价格已一手核实：¥0.025/张）。
6. **通义**：`qwen-image-3.0` 在原生端点是否支持 `n>1` 与负面提示词；
   `qwen-image-3.0-pro`（¥0.25/张）是否值得作为"高质量档"加进清单。
7. **DeepSeek / 火山 / 硅基流动 / 智谱 / Kimi 的思考参数**：本版已按 2026-09-14 文档实现；若官方改版需同步（改动只需动 `ProviderProfiles.kt`）。
8. **可否在应用内展示价格**（本版**不做**：0.1.2-alpha.2 是纯修复版，加价格展示属新功能）：
   若要展示，建议只放"每百万 token 输入/输出"这类稳定的两项 + 定价页入口，
   且必须随包内常量走（不能联网取），否则一旦调价会误导用户。
9. **📋 仍需"带登录态浏览器"复核的项（只剩 SiliconFlow 一家）**：
   - **SiliconFlow**：登录后的模型广场 → 四款生图模型单价（`Z-Image-Turbo` / `Kolors` /
     `Qwen-Image` / `ERNIE-Image-Turbo`）——实测 `cloud.siliconflow.cn/models` 会 302 跳
     `account.siliconflow.cn/zh/login`，**无登录态抓不到**；登录页已在 ZCode 内置浏览器里开好待用；
   - ~~智谱 / 火山 / 腾讯云 / OpenAI / Gemini~~ → **本轮已全部读完**，见 5.5(2)，无需再用 Edge；
   - ~~MiniMax~~ → **本轮已解决**：`.io` 国际站国内直连 `ERR_CONNECTION_CLOSED`，
     换国内站 `platform.minimax.cn/docs/guides/pricing-paygo` 后拿到人民币刊例价（代码链接已改）。

---

## 七、维护约定（重要）

### 设置页结构（2026-09-14 重构后）

API 设置页顶部四个分段（页内 tab，切 tab 不丢未保存内容）：

| 分段 | 内容 |
|---|---|
| **API 配置** | 供应商胶囊（换行展示，含自定义供应商与「＋新增」）、Base URL、API Key（按供应商记忆 + 清除按钮 + 是否已保存提示）、当前接入能力卡（含使用文档/价格页入口）、自定义供应商管理 |
| **对话模型** | 对话模型（+连接测试）、对话思考强度（按供应商/模型自适应）、温度（不支持的供应商自动置灰）、单条回复长度上限（含"当前实际发送"）、携带历史条数（上下文）、全局补充系统提示词 |
| **创作模型** | 创作模型（+连接测试）、创作思考强度（按**创作模型**自适应） |
| **生图模型** | 启用开关、生图供应商胶囊、生图 Base URL、生图专用 Key（留空沿用对话 Key）、生图模型（+测试）、返回格式（按服务商枚举，强制时锁定但不动用户偏好） |

**API Key 存储机制**：一个供应商一份 Key（`AiSettings.providerKeys`，key = 画像 keyword 或自定义供应商 id），
值经 Android Keystore 加密后落盘（`Security.encrypt`，与 `chatApiKey` 同一套）；
切换供应商时自动保存当前 Key、取回目标供应商的 Key（未配置则空）；**不进备份文件**。

**自定义供应商**：填名称 + 对话/生图 URL + 能力选项（思考参数形态、是否接受温度、生图协议、固定返回格式），
保存后与内置供应商共享同一套自动适配（解析入口 `ProviderProfiles.resolve`）。

### 代码维护
- **改接入适配 = 改一张表**：所有供应商差异（思考参数形态、温度支持、生图协议/返回格式/尺寸写法、注意事项、文档链接）都写在 `app/src/main/java/com/mysticat/roleplay/data/ProviderProfiles.kt`；
  请求逻辑（`AiClient`）与设置界面（`SettingsScreen`）**都读它**，不会出现"界面说的和实际发的不一致"。
- **模型清单**在 `ModelCatalog.kt`；Base URL 预设直接来自 `ProviderProfiles`（避免两处不同步）。
- **换供应商时自动配置**：界面点击供应商胶囊会自动切换 Base URL、必要时切换模型、并把服务商硬性要求（如智谱生图必须 url）改好，同时在界面上说明改了什么。
- **新增供应商时必做两件事**：① `pricingUrl` 要**实测能打开且页面上真有价格**（本次发现 4 家的旧链接要么 404、要么指向无价格的页面；二轮又发现 2 处：火山 SPA 无价页、OpenAI 需走 302 后的地址）；② 价格数值不要写进代码，只放链接——会调价，硬编码即误导。
- **价格核实的落点**：只更新本文档第五节（连同"可信度"标注），代码侧仅在链接失效、或注释已被证伪时改动。
- **核实要用真浏览器，别在 WebFetch 上耗轮次**：WebFetch 对多数家 403 或只拿到空壳。
  ZCode 内置浏览器（`iab`）就够读**公开**定价页；**需要登录态的**（SiliconFlow 模型广场、智谱控制台）
  必须先让用户开 `computer-use` 会话用其 Edge —— 注意该桌面会话**一旦停止无法自恢复**。
  两个常见坑：价格表可能在**折叠区**里（智谱 GLM-4.x）、点"详细价格"可能**新开标签页**（要 `claimTab`）；
  第三个坑（MiniMax 踩到）：**页面空白先当作"连不上"处理**——国际域名在国内可能直接
  `ERR_CONNECTION_CLOSED`，换该服务的国内域名往往立刻就好，别急着判定"需要登录态"。
