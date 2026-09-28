# 「API 设置」模型清单更新提示词（2026-09-10 第二版，联网核实 + 官方文档确认）

> 用法：把下面分隔线以内的内容整段复制给 dsh，让它照做。
> 本版已核实：火山方舟精确模型 ID（官方模型发布公告 docs/82379/1159178）、硅基流动精确模型 ID（官方 models 页 + 第三方聚合清单）。价格单位：元/百万 Token（国际模型按 ≈7.2 汇率折算）。

---

## 任务：更新 AI Roleplay 的「API 设置」模型清单

请更新 `app/src/main/java/com/mysticat/roleplay/data/ModelCatalog.kt` 中过时的模型清单。该文件按 Base URL 关键词匹配服务商，返回三套预设：chat（日常对话）、creation（角色卡生成等创作）、image（生图）。`SettingsScreen.kt` 通过 `ModelCatalog.baseUrlPresets() / chatPresets() / creationPresets() / imagePresets()` 消费。

**硬性约束：**
1. 只改 `ModelCatalog.kt` 里的数据，不改函数签名、不改 SettingsScreen、不动 UI。
2. `gemini` 和 `googleapis` 两个键继续同步维护。
3. **chat 列表内部按成本从低到高排序**，只选适合文本聊天（文聊）的模型——不要放纯代码/纯视觉特化模型。
4. **creation 列表内部也按成本从低到高排序**，但选型标准改为「效率与性能兼顾」：优先中高速强模型（flash/turbo/air 档），旗舰放最后做升级选项。
5. **imageByKeyword 只允许 OpenAI 兼容 `/images/generations` 端点的服务商**：OpenAI、智谱、硅基流动、OpenRouter、火山方舟。**禁止把 dashscope(通义 wanx) 和 googleapis 加进 image 列表**——它们走原生端点，会 404（此前实战已踩坑）。
6. 改完跑 `gradlew assembleDebug` 确认编译通过；在代码注释里标明「清单更新于 2026-09-10，来源：各厂商官方文档」。

### 一、volcengine（火山方舟，ID 已从官方发布公告核实，直接照抄）

- chatByKeyword["volcengine"]（成本升序）：
  `doubao-seed-character-260628` → `doubao-seed-2-1-turbo-260628` → `doubao-seed-2-1-pro-260915`
  ⭐ `doubao-seed-character-260628` 是字节 0628 新发布的**角色扮演专用模型**（Human-like 自然聊天、情感递进、主动激活对话），对本 App 的文聊场景是首选，必须放第一位。
  ⚠️ 2026-09-16：`doubao-seed-2-1-pro-260628` **已更名为 `doubao-seed-2-1-pro-260915`**（火山当日"0915 版本全量上线方舟"公告），价格不变 ¥6/¥30，清单已同步。
- creationByKeyword["volcengine"]：
  `doubao-seed-2-1-turbo-260628` → `doubao-seed-2-1-pro-260915`
- imageByKeyword["volcengine"]（Ark 端点 ark.cn-beijing.volces.com/api/v3/images/generations 已确认 OpenAI 兼容）：
  `doubao-seedream-4-5-250628`（若广场已下线则删）→ `doubao-seedream-5-0-260128` → `doubao-seedream-5-0-pro-260628`

### 二、siliconflow（硅基流动，ID 已从官方 models 页核实）

- chatByKeyword["siliconflow"]（成本升序，参考价 输入/输出 ¥/M）：
  `deepseek-ai/DeepSeek-V4-Flash`（约 1/2，284B MoE、1M 上下文，文聊性价比之王）→
  `zai-org/GLM-5.2`（约 0.95/2.55）→
  `Qwen/Qwen3.8-Max`（约 2/6，需在模型广场确认在售，不在则用 `Qwen/Qwen3.6-35B-A3B`）→
  `deepseek-ai/DeepSeek-V4-Pro`（约 4/12）
  旧的 `Qwen/Qwen2.5-7B-Instruct`、`deepseek-ai/DeepSeek-V3` 删除。
- creationByKeyword["siliconflow"]：`deepseek-ai/DeepSeek-V4-Flash` → `Qwen/Qwen3.8-Max` → `deepseek-ai/DeepSeek-V4-Pro`（V4-Flash 自带 Think 高/Max 档，效率性能兼顾；旧的 DeepSeek-R1、QwQ-32B 删除）。
- imageByKeyword["siliconflow"]：`Kwai-Kolors/Kolors`（最便宜）→ `black-forest-labs/FLUX.1-schnell`；⚠️ FLUX.2 托管名（可能为 `black-forest-labs/FLUX.2-schnell`）先在硅基流动模型广场搜「FLUX」核实，有 2 代就替换 1 代，没有就保留 1 代并加 TODO 注释。
- baseURL `https://api.siliconflow.cn/v1` 不变。

### 三、其他服务商（对话/创作按成本升序重排）

- **deepseek**（api.deepseek.com，模型名不变指向 V4）：
  chat：`deepseek-chat`（对应 V4-Flash 级，便宜）→ `deepseek-reasoner`（对应 V4-Pro 级深度思考）
  creation：`deepseek-chat` → `deepseek-reasoner`
- **openai**：
  chat：`gpt-5.6-luna`（中端，约 $1/$6）→ `gpt-6-astra`（09-03 新旗舰，$10/$50，很贵放最后）⚠️ID 按官方文档核实
  creation：`gpt-5.6-luna` → `gpt-6-astra`
- **moonshot / kimi**（api.moonshot.cn）：
  chat/creation：`kimi-latest`（指向当前主力，成本较低）→ `kimi-k3`（约 ¥3/15 旗舰）⚠️k3 精确 ID 核实
- **dashscope / aliyun**（阿里云百炼）：
  chat：`qwen3.8-flash`（约 0.8/2.7）→ `qwen-plus` → `qwen3.8-max`（旗舰）⚠️3.8 代际 ID 核实，查不到保留 `qwen-plus`/`qwen-max` 旧名
  creation：`qwen3.8-flash` → `qwen3.8-max`
  **不加进 imageByKeyword**（wanx 走原生端点）。
- **zhipu / bigmodel**（open.bigmodel.cn）：
  chat：`glm-4-flash`（免费兜底）→ `glm-5.3-flash`（极便宜，1M 上下文）→ `glm-5.3`
  creation：`glm-5.3-flash` → `glm-5.3`
  image：`cogview-3-flash`（免费/超低价兜底）→ `cogview-4` ⚠️（cogview 系列确认走 OpenAI 兼容端点，4 代精确名核实；智谱若有 `glm-image` 新端点再评估）
- **openrouter**（openrouter.ai/api/v1）：
  chat：`google/gemini-3.8-flash`（$0.75/$3.75）→ `deepseek/deepseek-chat` → `moonshotai/kimi-k3` ⚠️ → `openai/gpt-6-astra`
  creation：`google/gemini-3.8-flash` → `openai/gpt-6-astra`
  image：`google/gemini-3-pro-image`（Nano Banana 2）⚠️ → `openai/gpt-image-2` ⚠️（openrouter 托管名均需核实）
- **gemini / googleapis**（generativelanguage.googleapis.com/v1beta/openai）：
  chat/creation：`gemini-3.8-flash`（$0.75/$3.75，9 月 2 日 GA）→ `gemini-3.5-pro`（推理天花板档）
  **不加进 imageByKeyword**（Gemini 原生端点 404 教训）。旧的 `gemini-2.0-flash`/`2.5-flash`/`2.5-pro` 全删。

### 四、defaultChatPresets（跨服务商默认推荐，成本升序）

`glm-4-flash` → `deepseek-chat` → `glm-5.3-flash` → `qwen3.8-flash` → `doubao-seed-character-260628` → `kimi-latest` → `gemini-3.8-flash` → `gpt-5.6-luna`

### 五、defaultImagePresets（成本升序）

`cogview-3-flash` → `Kwai-Kolors/Kolors` → `doubao-seedream-5-0-260128` → `gpt-image-2`（或 `gpt-image-1.5`，OpenAI 侧 1.5 更便宜，若核实仍在售则放 gpt-image-2 前）

### 六、baseUrlPresets

对话 9 个现有预设全部有效不动。生图 imageBaseUrlPresets 保持现有 5 家（OpenAI/智谱/硅基流动/OpenRouter/火山方舟），**不要新增通义和 Google**。

### 过时项清理对照（务必删掉或替换）

| 旧值 | 处理 |
|---|---|
| `gpt-4o / gpt-4.1 / o3-mini`（openai chat/creation） | 替换为 gpt-5.6-luna / gpt-6-astra |
| `gemini-2.0-flash / 2.5-flash / 2.5-pro` | 替换为 3.8-flash / 3.5-pro |
| `dall-e-3` | 删除 |
| `cogview-3-plus` | 替换为 cogview-4（核实后） |
| `doubao-1.5-pro-32k`、`doubao-seed-1.6-250615`、`doubao-seedream-3-0-t2i-250415` | 升到 seed-2-1 / seedream-5-0 系列（精确 ID 见第一节） |
| `Qwen/Qwen2.5-7B-Instruct`、`deepseek-ai/DeepSeek-V3`、`DeepSeek-R1`、`QwQ-32B`（siliconflow） | 升到 V4 / Qwen3.8 系列 |

### 验收标准

1. `gradlew assembleDebug` 通过；
2. 设置页选每个 Base URL 预设后，对话/创作/生图三组下拉按成本从低到高弹出；
3. volcengine 的角色扮演模型 `doubao-seed-character-260628` 出现在对话推荐首位；
4. 所有 ⚠️ 项的核实结果（确认的 ID 或保留旧值的 TODO 注释）写在最终回复里。
