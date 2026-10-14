package com.mysticat.roleplay.data

/**
 * 供应商接入画像（2026-09-14 建立）。
 *
 * 目的：把原先"只存在于代码里"的适配（思考参数形态、温度是否可用、生图协议与返回格式、
 * 尺寸格式、注意事项）集中声明，**同时作为请求逻辑与设置界面的唯一事实来源**：
 * - AiClient 的 thinkingBody / effectiveMaxTokens / supportsTemperature / 生图分支都读这里；
 * - 设置界面据此显示"该服务商实际会发什么参数"，并在切换供应商时自动配置好必要项
 *   （例如智谱只能返回 url，切过去就把返回格式改成 url）。
 *
 * 每一条都注明核实日期与来源；未核实的写"待核实"而不是猜。
 *
 * pricingUrl 已于 2026-09-14 逐条实测并修正：MiniMax 旧链接已 404，智谱/腾讯云/通义旧链接
 * 指向的页面上没有价格——现均换为各家真正的定价页。**价格数值本身不进代码**（会调价，
 * 硬编码即误导），代码只提供定价页入口；已核实的价格对照表见
 * `docs/鲸鱼-API与模型接入梳理.md` 第五节（含一手/二手可信度标注）。
 *
 * 2026-09-15 二次实测（ZCode 内置浏览器实读，取代被 403/JS 挡住的 WebFetch）：
 * - 智谱 `open.bigmodel.cn/pricing` 可正常跳转到 `docs.bigmodel.cn/.../pricing`，页面上有完整价格表；
 * - 火山旧链接（`/pricing?product=ark`）是 SPA 无价格 → 换成文档版价格页 `docs/82379/1099320`
 *   （会 302 到 `docs.volcengine.com/docs/82379/1544106`，有完整价目表）；
 * - 通义 `help.aliyun.com/.../billing-for-model-studio` 会 302 到 `.../model-pricing`，有完整价格表；
 * - OpenAI `platform.openai.com/docs/pricing` 会 302 到 `developers.openai.com/api/docs/pricing`，
 *   故直接写后者；
 * - MiniMax：`.io` 国际站在国内直连 **ERR_CONNECTION_CLOSED**（"页面空白/抓不到"的真实原因不是渲染，
 *   而是连不上）→ 换成国内站文档中心的同款价格页 `platform.minimax.cn/docs/guides/pricing-paygo`，
 *   并拿到**人民币**刊例价（M3 2.1/8.4、M2.7 2.1/8.4、M2.7-highspeed 4.2/16.8、image-01 ¥0.025/张）。
 */
object ProviderProfiles {

    /** 思考参数形态（决定发什么字段） */
    enum class ThinkingKind(val paramLabel: String) {
        /** thinking {"type": disabled/enabled} */
        THINKING_TYPE("thinking.type"),

        /** enable_thinking: true/false */
        ENABLE_THINKING("enable_thinking"),

        /** reasoning_effort: low/high（无"完全关闭"档） */
        EFFORT_LOW_HIGH("reasoning_effort"),

        /** reasoning_effort: none/high（none = 关闭思考） */
        EFFORT_NONE_HIGH("reasoning_effort"),

        /**
         * reasoning_effort: low/high/max ＋ thinking {"type": disabled} 关闭。
         * DeepSeek（官方 2026-10-12 核实：effort 默认 high）与 Kimi K3（默认 max、关不掉）
         * 同属一族，但"关闭"的处理不同：DeepSeek 能真关（disabled），Kimi 关不掉（降为 low）。
         */
        EFFORT_LOW_HIGH_MAX("reasoning_effort"),

        /**
         * OpenRouter 聚合层自有参数：顶层 `reasoning: {"effort": …}` 对象（不是 reasoning_effort），
         * effort=none 关闭思考；OpenRouter 负责把它映射到各家自己的参数形态（2026-10-12 官方文档核实）。
         */
        REASONING_OBJECT("reasoning.effort"),

        /**
         * 火山方舟 Chat API 的 `reasoning_effort`（2026-10-12 浏览器抓官方《深度思考》页一手核实）：
         * 官方 7 档 none/minimal/low/medium/high/xhigh/max，"支持该字段的模型均接受全部 7 档、
         * 部分取值自动映射"——清单三个模型全在支持表里：doubao-seed-2-1-pro/turbo **默认 high**
         * （none/minimal→关闭、xhigh/max→high）、doubao-seed-character **默认 medium**。
         * ⇒ xhigh/max 与 high 等效（映射规则明确），按"不给效果重复的选项"不设档；
         * 关闭档发 effort=none（官方"none：关闭思考"），不再走 thinking.type。
         */
        ARK_EFFORT("reasoning_effort"),

        /**
         * 点点（dots3-note-prev）：`chat_template_kwargs: {"enable_thinking": true/false}`。
         * 与顶层 `enable_thinking`（ENABLE_THINKING，硅基流动在用）不同——官方文档写的是
         * chat_template_kwargs 包一层（2026-09-18 官方文档逐条抄录）。
         */
        CHAT_TEMPLATE_ENABLE_THINKING("chat_template_kwargs.enable_thinking"),

        /** 未适配：不发参数，保持模型默认 */
        NONE("（未适配，保持模型默认）")
    }

    /**
     * 生图协议。
     *
     * 两种口径的中文说明都挂在枚举上，别在界面里各写一份 `when`
     * （原来能力卡片一个 `when`、自定义供应商弹窗一个 `listOf(A to B)`，加一种协议要改两处）。
     * [cardText] = 能力卡片上带端点路径的口径；[shortName] = 选项 chip 上的短口径。
     */
    enum class ImageProtocol(val cardText: String, val shortName: String) {
        /** OpenAI 兼容 /images/generations */
        OPENAI("OpenAI 兼容 /images/generations", "OpenAI 兼容"),
        /** MiniMax：/image_generation + aspect_ratio + data.image_urls/base64 */
        MINIMAX("MiniMax 私有 /image_generation", "MiniMax 私有"),
        /** 通义/千问：原生 multimodal-generation/generation + input.messages + 星号尺寸 */
        DASHSCOPE("通义原生 multimodal-generation", "通义原生"),
        /**
         * OpenRouter 聚合生图：端点是 `POST /images`（不是 OpenAI 的 /images/generations），
         * 尺寸不用 size 而用 `resolution`(1K/2K/4K) ＋ `aspect_ratio`（2026-10-12 官方文档核实，
         * 模型清单 `GET /api/v1/images/models` 公开可查）；响应与 OpenAI 同形（data[0].b64_json）。
         */
        OPENROUTER("OpenRouter 聚合 /images", "OpenRouter 聚合")
    }

    /** 思考强度档位（label 给用户看，mode 是内部值，hint 说明实际发什么） */
    data class ThinkingLevel(val label: String, val mode: String, val hint: String)

    /**
     * 一家供应商要连上它的语音接口，需要用户填的**一个凭据字段**（代价：界面由此驱动，见 [SpeechProtocol]）。
     *
     * [key] 是存储键（`speechCredentials[家][key]`），[label] 是输入框标题，[hint] 是框下的一行小字。
     */
    data class CredentialField(
        val key: String,
        val label: String,
        val required: Boolean = true,
        val hint: String = ""
    )

    /**
     * 语音合成的**协议**（TTS v2）。
     *
     * 一开始我把范围收成"只接 OpenAI 兼容"，用户 2026-09-16 拍了一句"不一定非得是 OpenAI 兼容吧"——
     * 对的：真正的约束是"一家一份 Key + 一个我们实现了协议的适配器"，形状可以各自适配
     * （图片那边早就是这么做的：`ImageProtocol.MINIMAX`）。
     *
     * **2026-09-16 新增 [credentialFields]**：凭据形态不同的家（火山要 AppID+AccessToken、腾讯要
     * SecretId+SecretKey）装不进"一个字符串一把 Key"的 `providerKeys`，所以凭据改成**按协议声明字段**、
     * 界面照单渲染输入框 —— 加一家只需要加一个协议分支 + 一组字段，不用再动界面。
     */
    enum class SpeechProtocol(val label: String, val credentialFields: List<CredentialField>) {
        /** `POST {base}/audio/speech` → 直接返回音频字节（OpenAI / 硅基流动 / 本地部署的兼容实现） */
        OPENAI(
            "OpenAI 兼容 /audio/speech",
            listOf(CredentialField("apiKey", "API Key", hint = "留空则用「模型与API」里这家那把 Key"))
        ),

        /** MiniMax 原生 `POST {base}/t2a_v2` → JSON 里 hex 编码的音频 */
        MINIMAX(
            "MiniMax 原生 /t2a_v2",
            listOf(CredentialField("apiKey", "API Key", hint = "留空则用「模型与API」里这家那把 Key"))
        ),

        /**
         * 阿里百炼原生 `POST {base}/api/v1/services/audio/tts/SpeechSynthesizer`
         * （2026-09-17 一手核实官方《非实时语音合成 HTTP API 参考》）。
         * **非流式响应只给音频 URL**（OSS 预签名、24 小时有效）⇒ AiClient 要再下载一次。
         */
        DASHSCOPE(
            "通义原生 SpeechSynthesizer",
            listOf(CredentialField("apiKey", "API Key", hint = "留空则用「模型与API」里这家那把 Key"))
        ),

        /**
         * 火山引擎豆包语音：**一家两代接入方式**，靠"填了哪组凭据"自动分流。
         *
         * - **新版控制台（推荐）＝ API Key**：`POST {base}/api/v3/tts/unidirectional`，
         *   头 `X-Api-Key` + `X-Api-Resource-Id` + `X-Api-Request-Id`；响应是 **NDJSON 分片**
         *   （每行一个 JSON、`data` 是 base64 音频块），2026-09-17 核实官方《HTTP Chunked/SSE 单向流式-V3》；
         * - **旧版控制台 ＝ AppID + Access Token**：`POST {base}/api/v1/tts`，凭据放在请求体 `app` 里、
         *   头部 `Authorization: Bearer;<token>`（**分号**）。
         *
         * ⚠️ 两代**不能混用**：新版只有 API Key 时带上 AppID/Access-Key 那两个头会报
         * `load grant: requested grant not found`。
         */
        VOLC(
            "火山豆包语音（V3 API Key / 旧版 AppID）",
            listOf(
                CredentialField(
                    "apiKey", "API Key（新版控制台）", required = false,
                    hint = "新版控制台「API Key」页创建；填了它就走 V3 接口（推荐）"
                ),
                CredentialField(
                    "resourceId", "Resource-Id（选填）", required = false,
                    hint = "默认按音色自动判断：2.0 音色 → seed-tts-2.0、1.0 音色 → seed-tts-1.0"
                ),
                CredentialField(
                    "appId", "AppID（仅旧版控制台）", required = false,
                    hint = "旧版控制台的 App ID；新版控制台不用填，请改用上面的 API Key"
                ),
                CredentialField(
                    "accessToken", "Access Token（仅旧版控制台）", required = false,
                    hint = "旧版控制台的 Access Token（不是方舟的 ark- Key）"
                ),
                CredentialField(
                    "cluster", "Cluster（仅旧版，选填）", required = false,
                    hint = "默认 volcano_tts；用声音复刻/ICL 时填 volcano_icl"
                )
            )
        ),

        /**
         * 腾讯云语音合成（**又一套凭据 + TC3 签名**）：`POST https://tts.tencentcloudapi.com`
         * （Action `TextToVoice`，Version `2019-08-23`），2026-09-17 核实官方接口文档。
         * 鉴权是 **TC3-HMAC-SHA256**（SecretId 明文 + SecretKey 做密钥派生签名），
         * 音频在响应 `Response.Audio` 里以 base64 返回；`VoiceType` 是**数字**音色 id、`ModelType` 是档位
         * （3 = 大模型音色 / 2 = 精品 / 1 = 基础）—— 本 App 把"语音模型"这个字段借用成 ModelType。
         */
        TENCENT(
            "腾讯云 TextToVoice（TC3 签名）",
            listOf(
                CredentialField("secretId", "SecretId", hint = "腾讯云访问管理 CAM → API 密钥管理"),
                CredentialField("secretKey", "SecretKey", hint = "与 SecretId 成对；只存在本机、请勿外传"),
                CredentialField(
                    "region", "地域", required = false,
                    hint = "默认 ap-guangzhou（语音合成支持的地域见官方文档）"
                )
            )
        ),

        /**
         * xAI 语音合成（2026-10-12 核实官方 docs.x.ai）：`POST {base}/tts` → **原始音频字节**。
         * 请求体 `text`（≤15000 字符）＋ `voice_id`（默认 eve，大小写不敏感）＋ `language`
         * （BCP-47，支持 auto）＋ `output_format`{codec} ＋ `speed`（0.7~1.5）。
         * **没有"模型"字段**（音色即一切），与火山/腾讯同款处理。
         */
        XAI(
            "xAI 原生 /tts（voice_id + language）",
            listOf(CredentialField("apiKey", "API Key", hint = "留空则用「模型与API」里这家那把 Key"))
        ),

        /**
         * 小米 MiMo 语音合成（2026-10-12 核实官方 mimo.mi.com）：**走 chat/completions** ——
         * 目标文本放 `role:"assistant"` 消息里（不可放 user），可选 user 消息传风格指令；
         * `audio`{format: wav|pcm16, voice} 选音色；响应在 `choices[0].message.audio.data`（base64）。
         * 限时免费；唱歌/风格控制走文本里的标签（App 不做风格注入）。
         */
        MIMO(
            "小米 MiMo chat/completions + audio",
            listOf(CredentialField("apiKey", "API Key", hint = "留空则用「模型与API」里这家那把 Key"))
        )
    }

    /** 语音识别（ASR）协议：形状不同的家各自适配（与 [SpeechProtocol] 同一个思路） */
    enum class AsrProtocol(val label: String) {
        /** OpenAI 兼容 `POST {base}/audio/transcriptions`（multipart：file + model → {text}） */
        OPENAI("OpenAI 兼容 /audio/transcriptions"),

        /**
         * 火山豆包语音识别（**极速版**，2026-09-17 加）：`POST {base}/api/v3/auc/bigmodel/recognize/flash`，
         * 头 `X-Api-Key` + `X-Api-Resource-Id`(`volc.bigasr.auc_turbo`) + `X-Api-Request-Id`，
         * 体里音频走 **base64**（`audio.data`）—— 这是手机端唯一可行的形状：
         * 官方"标准版"（`volc.seedasr.auc`）只收**公网音频 URL**，本地录音没有公网地址。
         */
        VOLC_FLASH("火山录音文件识别极速版 /recognize/flash"),

        /**
         * xAI 语音识别（2026-10-12 核实官方 docs.x.ai）：`POST {base}/stt`（multipart：file，
         * 可选 language/keyterm/format），响应 JSON 的 `text` 字段。**没有 model 字段**，
         * 与 VOLC_FLASH 同款走"无模型"分支。
         */
        XAI("xAI 原生 /stt（multipart file）"),

        /**
         * 小米 MiMo 语音识别（2026-10-12 核实官方 mimo.mi.com）：**走 chat/completions** ——
         * `model=mimo-v2.5-asr`，音频以 `input_audio.data`（data URL，base64）放进 user 消息，
         * 语种用 `asr_options.language`（auto/zh/en）；识别文本在响应 content 里。
         * ⚠ 官方只收 **wav/mp3** 音频（10MB 上限）——Android 端录音是 m4a，适配层会拦截提示。
         */
        MIMO("小米 MiMo chat/completions + input_audio")
    }

    data class ProviderProfile(
        val keyword: String,
        val name: String,
        val chatBaseUrl: String?,
        val imageBaseUrl: String?,
        val thinking: ThinkingKind,
        /** 是否"设计上总是思考"（关不掉）：Kimi K3、智谱 GLM-5.3 系 */
        val alwaysThinking: Boolean = false,
        /** 是否接受 temperature（Kimi 会报 invalid temperature） */
        val temperatureSupported: Boolean = true,
        /** 生图返回格式被服务商固定时的值（null = 用户可选） */
        val imageFormatForced: String? = null,
        /**
         * 该家的生图接口**不接受 `response_format` 字段**（发了要么被忽略、要么被当未知参数拒绝）。
         * 目前只有 OpenAI：官方 API 参考写明该参数"不支持 GPT image 模型"，
         * 该系永远返回 base64，现行替代参数是 `output_format`。
         * 置 true 后 AiClient 生图时**不再发这个字段**（`imageFormatForced` 仍保留，用于界面显示"固定为 base64"）。
         */
        val imageFormatUnsupported: Boolean = false,
        /**
         * 该家的生图接口**不接受 `size` 字段**（发了要么被忽略、要么被当未知参数拒绝）。
         * 目前只有 xAI：官方生图走 `aspect_ratio` ＋ `resolution`（1k/2k），没有 OpenAI 的 size
         * （2026-10-12 官方文档核实，OpenAI SDK 示例也不传 size）。置 true 后 AiImage 不再发 size。
         */
        val imageSizeUnsupported: Boolean = false,
        val imageProtocol: ImageProtocol = ImageProtocol.OPENAI,
        /**
         * 该家默认会把**思考内容混在 `content` 里**返回，需要显式传 `reasoning_split: true`
         * 才会拆到 `reasoning_content`（MiniMax 官方文档：这个参数不控制"要不要思考"，
         * 只控制思考返回到哪个字段；不传时思考被 `<think>…</think>` 包着塞进 content）。
         * 置 true 后 AiClient 每次请求都会带上该参数。
         */
        val splitsReasoning: Boolean = false,
        /** 生图尺寸参数形态（说明用；实际转换在 AiClient） */
        val imageSizeFormat: String = "1024x1024",
        val visionNote: String? = null,
        /**
         * 该家的**语音合成** Base URL（OpenAI 兼容 `/audio/speech`）；null = 本版不适配。
         * 只有一手核实过的家才填（硅基流动、OpenAI 官方）——
         * 火山豆包语音 / 阿里百炼 / MiniMax / 智谱都不是 OpenAI 形状或未核实，想用请走「自定义供应商」。
         */
        val speechBaseUrl: String? = null,
        /** 语音合成协议（默认 OpenAI 兼容；MiniMax 那种原生形状单独标） */
        val speechProtocol: SpeechProtocol = SpeechProtocol.OPENAI,
        /**
         * 该家的**语音识别**（ASR）Base URL（OpenAI 兼容 `POST /audio/transcriptions`）；null = 不支持。
         * 与 [speechBaseUrl] 同域即可（OpenAI / 硅基流动都是同一个 `/v1`），单独留字段是为了
         * "某家只做识别不做合成"这类情况；形状目前只有 multipart 一种，接别的家时照
         * [SpeechProtocol] 的做法再加 `AsrProtocol` 枚举。
         */
        val asrBaseUrl: String? = null,
        /** 语音识别协议（默认 OpenAI 兼容；火山极速版那种单独标） */
        val asrProtocol: AsrProtocol = AsrProtocol.OPENAI,
        val note: String? = null,
        val docsUrl: String? = null,
        val pricingUrl: String? = null
    )

    /** 默认档（多数服务商）：默认 = 不传参；关闭 = 关掉；深度 = 开到最深 */
    private val defaultLevels = listOf(
        ThinkingLevel("默认", "", "不传思考参数，由模型决定"),
        ThinkingLevel("关闭", "off", "关闭思考（更快更省）"),
        ThinkingLevel("深度", "high", "开启思考，尽量深入")
    )

    /**
     * DeepSeek（2026-10-12 核实官方 thinking_mode 文档）：`reasoning_effort` 支持 low/high/max，
     * 官方默认 high（此前"深度"档发 thinking enabled，实际与官方默认同义——这次按真实档位重排）。
     * 「关闭」发 thinking disabled；「默认」档落盘 "high"（显式 effort=high，与不传参等价）：
     * 老用户存的 "high" 原是「深度」档，新表里语义不变、自动落到「默认」档；
     * 从未设置过的空值由 UI 兜底显示为「默认」（不传参＝官方默认 high，行为一致）。
     */
    private val deepseekLevels = listOf(
        ThinkingLevel("关闭", "off", "thinking.type=disabled（真关闭）"),
        ThinkingLevel("较低", "low", "reasoning_effort=low（CoT 更短，更快更省）"),
        ThinkingLevel("默认", "high", "reasoning_effort=high（官方默认）"),
        ThinkingLevel("深度", "max", "reasoning_effort=max（最深最慢）")
    )

    /**
     * Kimi：K3 总是思考、关不掉，只能用 reasoning_effort 调深浅（官方 2026-10-12 核实：
     * 支持 low/high/max，默认 max——「最高」与不传参效果相同，仍合并）。
     */
    private val kimiLevels = listOf(
        ThinkingLevel("较低", "off", "reasoning_effort=low（最省）"),
        ThinkingLevel("较高", "high", "reasoning_effort=high"),
        ThinkingLevel("最高", "max", "reasoning_effort=max（也是模型默认）")
    )

    /**
     * 智谱 GLM-5.3 系（2026-10-12 核实官方模型页 docs.bigmodel.cn）：**始终思考、彻底关不掉**
     * （thinking.type 只支持 enabled，传 disabled 报 1210），思考强度走 `reasoning_effort`
     * low/high/max、**默认 max** —— 与 Kimi K3 同一族，档位同款三档：
     * 「最高」与不传参效果相同仍合并；老用户存的 "off"（旧「最低」档）落码兼容为 effort=low。
     */
    private val glm53Levels = listOf(
        ThinkingLevel("较低", "off", "reasoning_effort=low（最快最省）"),
        ThinkingLevel("较高", "high", "reasoning_effort=high"),
        ThinkingLevel("最高", "max", "reasoning_effort=max（也是模型默认）")
    )

    /**
     * OpenRouter 聚合层（2026-10-12 核实官方 reasoning 文档）：`reasoning.effort` 官方取值
     * max/xhigh/high/medium/low/minimal/none（xhigh 与 max 分配比例相同，不重复设档；
     * minimal 只有部分模型声明 supported_efforts，不进默认表）。各模型实际接受哪些档由
     * 模型元数据的 supported_efforts 决定，OpenRouter 会对不支持的值做映射/降级。
     */
    private val reasoningObjectLevels = listOf(
        ThinkingLevel("默认", "", "不传参数，由模型决定"),
        ThinkingLevel("关闭", "off", "reasoning.effort=none"),
        ThinkingLevel("较低", "low", "reasoning.effort=low"),
        ThinkingLevel("中等", "medium", "reasoning.effort=medium"),
        ThinkingLevel("较高", "high", "reasoning.effort=high"),
        ThinkingLevel("最高", "max", "reasoning.effort=max")
    )

    /**
     * 小米 MiMo V2.6 系（2026-10-12 官方核实）：默认**开启**思考，`thinking {"type": enabled|disabled}`
     * 可显式关闭——没有更细的强度参数 ⇒ 只有"默认（思考开）/关闭"两档。
     * 旧表里的「深度」档发 enabled，与不传参效果完全相同（V2.6 默认即思考），按
     * "不给效果重复的选项"（2026-09-14 用户反馈）砍掉。
     */
    private val mimoLevels = listOf(
        ThinkingLevel("默认", "", "不传参数（V2.6 默认开启思考）"),
        ThinkingLevel("关闭", "off", "thinking.type=disabled")
    )

    /**
     * MiniMax（2026-10-12 复查官方核实：百炼"深度思考"页 + MiniMax 直供页）：**M3 是混合思考**，
     * `thinking {"type": adaptive|disabled"}` 控制——API 取值只有 **adaptive（自适应，默认）/disabled（关闭）**，
     * 没有 "enabled"（那是开源权重侧的说法）；M2.x（M2.7/M2.5/M2.1）是**仅思考模式**、参数收不了
     * ⇒ 只给 M3 配两档（默认=不传参数走 adaptive；关闭=disabled），M2.x 不给档位。
     */
    private val minimaxM3Levels = listOf(
        ThinkingLevel("默认", "", "不传参数（adaptive，模型自决是否思考）"),
        ThinkingLevel("关闭", "off", "thinking.type=disabled（直接回复，不推理）")
    )

    /** OpenAI：只有 low/high，没有"关闭" */
    private val effortLevels = listOf(
        ThinkingLevel("默认", "", "不传参数，由模型决定"),
        ThinkingLevel("较低", "off", "reasoning_effort=low"),
        ThinkingLevel("较高", "high", "reasoning_effort=high")
    )

    /**
     * 火山方舟（2026-10-12 官方《深度思考》页一手核实）：reasoning_effort 七档全收但 xhigh/max 自动映射回
     * high ⇒ 档位到 high 封顶；关闭发 effort=none（官方"none：关闭思考"）。**默认值按模型分两组**，
     * 各自把"与默认同效"的那档并掉（不给效果重复的选项）：
     * - **doubao-seed-2-1-pro/turbo/lite、evolving 默认 high** ⇒ 「较高」并入「默认」；
     * - **doubao-seed-character（及 2-0 系）默认 medium** ⇒ 「中等」并入「默认」。
     * 老存值兼容：high（原「深度」档＝thinking enabled）在 2-1 表外落入未知值、UI 吸附「默认」
     * （行为同效），character 表如实落「较高」。
     */
    private val volcHighDefaultLevels = listOf(
        ThinkingLevel("默认", "", "不传参数（2.1 系默认 high）"),
        ThinkingLevel("关闭", "off", "reasoning_effort=none（官方：关闭思考）"),
        ThinkingLevel("较低", "low", "reasoning_effort=low（轻量思考）"),
        ThinkingLevel("中等", "medium", "reasoning_effort=medium（均衡）")
    )

    private val volcMediumDefaultLevels = listOf(
        ThinkingLevel("默认", "", "不传参数（character／2.0 系默认 medium）"),
        ThinkingLevel("关闭", "off", "reasoning_effort=none（官方：关闭思考）"),
        ThinkingLevel("较低", "low", "reasoning_effort=low（轻量思考）"),
        ThinkingLevel("较高", "high", "reasoning_effort=high（深度分析）")
    )

    /**
     * 腾讯 TokenHub（2026-10-12 浏览器抓官方《深度思考》专篇一手核实）：推理深度走 reasoning_effort，
     * **hy3：low/high/none、默认 high**；**hy4-preview：high/none、默认 high**——不传参＝high≡较高，
     * 按"不给效果重复的选项"合并；关闭沿用 thinking {"type": disabled}（官方开关节口径）。
     * hunyuan-role 系不在推理深度支持列表（维持 thinking 开关三档）。
     */
    private val hy3Levels = listOf(
        ThinkingLevel("关闭", "off", "thinking.type=disabled（官方开关节）"),
        ThinkingLevel("较低", "low", "reasoning_effort=low"),
        ThinkingLevel("较高", "high", "reasoning_effort=high（也是默认）")
    )

    private val hy4Levels = listOf(
        ThinkingLevel("关闭", "off", "thinking.type=disabled（官方开关节）"),
        ThinkingLevel("较高", "high", "reasoning_effort=high（也是默认）")
    )

    /**
     * OpenAI（2026-10-12 浏览器抓官方 GPT-6 指南一手核实）：**gpt-6-astra 支持 low/medium(默认)/high/xhigh/max
     * （不支持 none/minimal）**；**gpt-5.6-luna 支持 none/low/medium/high/xhigh/max**——
     * 档位表取两模型交集；none 不进表（astra 会 4xx），luna 想关请手填场景外不引导。
     * 另官方口径：思考开启时不应传 temperature/top_p（现请求仍发，未见报错，观察中）。
     */
    private val openaiLevels = listOf(
        ThinkingLevel("默认", "", "不传参数（gpt-6 默认 medium）"),
        ThinkingLevel("较低", "low", "reasoning_effort=low"),
        ThinkingLevel("中等", "medium", "reasoning_effort=medium"),
        ThinkingLevel("较高", "high", "reasoning_effort=high"),
        ThinkingLevel("更高", "xhigh", "reasoning_effort=xhigh"),
        ThinkingLevel("最高", "max", "reasoning_effort=max")
    )

    /**
     * Gemini（2026-10-12 核实）：OpenAI 兼容层把 reasoning_effort 映射到 thinking_level，
     * 实测有效值为 high/low/medium/none（minimal 文档写着支持但端点实测拒绝，不编入）。
     * Pro 档关不掉思考（none 仅 Flash 支持），故不设「关闭」档，与 GLM 同一处理思路。
     */
    private val geminiLevels = listOf(
        ThinkingLevel("默认", "", "不传参数，由模型决定"),
        ThinkingLevel("较低", "low", "reasoning_effort=low"),
        ThinkingLevel("中等", "medium", "reasoning_effort=medium"),
        ThinkingLevel("较高", "high", "reasoning_effort=high")
    )

    /**
     * 点点（dots3-note-prev）：**不传任何思考参数也会返回 reasoning_content**（官方响应说明），
     * 而本项目不读 reasoning_content ⇒ 空 mode 等于白耗思考 token 且界面看不到。
     * 所以「默认」档也必须显式发 enable_thinking=false（AiClient.thinkingBody 对 dots 同样兜底），
     * 档位因此只有两档、不设"不传参数"档（Kimi 同理：不给效果重复的选项，2026-09-14 用户反馈）。
     */
    private val dotsLevels = listOf(
        ThinkingLevel("默认", "off", "显式关闭思考（该模型不关会白耗 token）"),
        ThinkingLevel("深度", "high", "开启思考")
    )

    /**
     * 供应商清单（顺序 = 对话性能优先、国内先于美国；硅基流动只做生图）。
     * 核实日期 2026-09-14（OpenRouter/xAI/小米 MiMo＝2026-10-12）；详细来源见 docs/鲸鱼-API与模型接入梳理.md
     *
     * 2026-09-15：**曾移除 OpenRouter**（用户反馈：聚合平台不常用，且其上的模型基本都在国外、
     * 无余额可用）。2026-10-12 用户点名加回，思考参数按聚合层自有的 `reasoning {effort}` 适配；
     * 老用户在这段期间保留的 OpenRouter 配置一直可用（URL 与 Key 始终在 settings 里）。
     */
    val all: List<ProviderProfile> = listOf(
        ProviderProfile(
            keyword = "volcengine",
            name = "火山方舟",
            chatBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            imageBaseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            // 语音合成（2026-09-17 加）：豆包语音是**独立产品线**，站点是 openspeech.bytedance.com、
            // 凭据是 AppID + AccessToken（不是方舟的 ark- Key），协议见 SpeechProtocol.VOLC。
            speechBaseUrl = "https://openspeech.bytedance.com",
            speechProtocol = SpeechProtocol.VOLC,
            // 语音识别（2026-09-17 加）：同站点、同一把 API Key；用"录音文件识别极速版"（base64 传音频）
            asrBaseUrl = "https://openspeech.bytedance.com",
            asrProtocol = AsrProtocol.VOLC_FLASH,
            // 2026-10-12 浏览器抓官方《深度思考》页一手核实：清单三个模型全部支持 reasoning_effort
            // （2-1 pro/turbo 默认 high、character 默认 medium；xhigh/max 映射回 high ⇒ 档位到 high 封顶）
            thinking = ThinkingKind.ARK_EFFORT,
            note = "域名是 volces.com，需用控制台创建的推理接入点或模型名；" +
                "生图默认方形/竖屏尺寸充足，带深度思考标签的模型默认开启思考。" +
                "思考强度走 reasoning_effort（默认 high/medium 按模型，关闭=none）；" +
                "**语音合成走豆包语音那套（openspeech.bytedance.com，需要单独的 AppID + AccessToken）**，" +
                "方舟的 Key 不能用于语音。",
            docsUrl = "https://www.volcengine.com/docs/82379",
            // 2026-09-15 实测：/pricing?product=ark 是 SPA、页面上没有价格 → 换成文档版价格页
            pricingUrl = "https://www.volcengine.com/docs/82379/1099320"
        ),
        ProviderProfile(
            keyword = "deepseek",
            name = "DeepSeek",
            chatBaseUrl = "https://api.deepseek.com/v1",
            imageBaseUrl = null,
            thinking = ThinkingKind.EFFORT_LOW_HIGH_MAX,
            note = "现行模型默认开启思考（不传参数也思考），思考 token 计入输出上限；" +
                "思考强度支持 reasoning_effort=low/high/max（官方默认 high，2026-10-12 核实）。" +
                "deepseek-chat / deepseek-reasoner 已于 2026-07-24 弃用，旧配置会自动迁移。",
            docsUrl = "https://api-docs.deepseek.com/zh-cn/guides/thinking_mode",
            pricingUrl = "https://api-docs.deepseek.com/zh-cn/quick_start/pricing"
        ),
        ProviderProfile(
            keyword = "bigmodel",
            name = "智谱",
            chatBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
            imageBaseUrl = "https://open.bigmodel.cn/api/paas/v4",
            thinking = ThinkingKind.THINKING_TYPE,
            alwaysThinking = true,
            imageFormatForced = "url",
            note = "GLM-5.3 / 5.3-FLASH 强制思考（只支持 enabled，传 disabled 报 1210），" +
                "思考强度走 reasoning_effort low/high/max（官方默认 max，2026-10-12 核实）。" +
                "生图只返回 URL（不支持 b64_json），App 会把图下载到本地。",
            docsUrl = "https://docs.bigmodel.cn/cn/guide/start/model-overview",
            pricingUrl = "https://open.bigmodel.cn/pricing"
        ),
        ProviderProfile(
            keyword = "moonshot",
            name = "Kimi/Moonshot",
            chatBaseUrl = "https://api.moonshot.cn/v1",
            imageBaseUrl = null,
            thinking = ThinkingKind.EFFORT_LOW_HIGH,
            alwaysThinking = true,
            temperatureSupported = false,
            note = "K3 总是思考、关不掉，只能用 reasoning_effort 调深浅；不接受 temperature 参数" +
                "（传了会报 invalid temperature，App 已自动不发）。k2.5 与 moonshot-v1 系已下线。",
            docsUrl = "https://platform.kimi.com/docs/guide/kimi-k3-quickstart",
            pricingUrl = "https://platform.kimi.com/docs/pricing/chat"
        ),
        ProviderProfile(
            keyword = "dashscope",
            name = "阿里通义/千问",
            chatBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            imageBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            // 语音合成（TTS v2 Step 4）：**原生** SpeechSynthesizer（不是 OpenAI 兼容），
            // 所以这里给的是站点根（协议分支自己拼 /api/v1/services/audio/tts/…）
            speechBaseUrl = "https://dashscope.aliyuncs.com",
            speechProtocol = SpeechProtocol.DASHSCOPE,
            thinking = ThinkingKind.ENABLE_THINKING,
            imageProtocol = ImageProtocol.DASHSCOPE,
            imageSizeFormat = "1024*1024（星号）",
            note = "兼容模式只支持对话与向量，生图必须走原生端点（App 已自动切换）；" +
                "尺寸用星号写法，返回 24 小时有效的图片链接（App 会自动下载保存）。" +
                "语音合成同理走原生 SpeechSynthesizer（返回音频 URL，App 会二次下载）。" +
                "思考（2026-10-12 复核）：qwen3.8 商业档是 enable_thinking 开关（默认开），" +
                "另有 thinking_budget 连续值限制思考长度（无离散档位，不进档位表）；" +
                "reasoning_effort 只属 qwen3.8-omni/开源 2.4t 系（不在清单）。",
            docsUrl = "https://help.aliyun.com/zh/model-studio/",
            pricingUrl = "https://help.aliyun.com/zh/model-studio/billing-for-model-studio"
        ),
        ProviderProfile(
            keyword = "tencentmaas",
            name = "腾讯云 TokenHub",
            chatBaseUrl = "https://tokenhub.tencentmaas.com/v1",
            imageBaseUrl = "https://tokenhub.tencentmaas.com/v1",
            thinking = ThinkingKind.THINKING_TYPE,
            note = "OpenAI 兼容入口是 /v1；hunyuan-role-latest / hy-role 为角色扮演专用模型。" +
                "不同地域需选对应站点地址，不支持跨地域调用。",
            docsUrl = "https://cloud.tencent.com/document/product/1823/130078",
            pricingUrl = "https://cloud.tencent.com/document/product/1823/130055"
        ),
        ProviderProfile(
            // 语音合成单独一个画像：腾讯云语音（tts.tencentcloudapi.com）与上面的 TokenHub 是**两个产品**，
            // 凭据也完全不是一套（那个用 API Key，这个要 CAM 的 SecretId/SecretKey + TC3 签名）。
            // 关键词取 "tencentcloud"（能命中 tts.tencentcloudapi.com，且不会撞上 tencentmaas）。
            keyword = "tencentcloud",
            name = "腾讯云语音",
            chatBaseUrl = null,
            imageBaseUrl = null,
            speechBaseUrl = "https://tts.tencentcloudapi.com",
            speechProtocol = SpeechProtocol.TENCENT,
            thinking = ThinkingKind.NONE,
            note = "腾讯云语音合成（TextToVoice）：音色是**数字** VoiceType、ModelType 分档（3 大模型 / 2 精品 / 1 基础），" +
                "凭据用访问管理 CAM 里的 SecretId + SecretKey（与「腾讯云 TokenHub」那把 Key 不是一回事）。",
            docsUrl = "https://cloud.tencent.com/document/product/1073/37995"
        ),
        ProviderProfile(
            keyword = "minimax",
            name = "MiniMax",
            chatBaseUrl = "https://api.minimaxi.com/v1",
            imageBaseUrl = "https://api.minimaxi.com/v1",
            // 语音合成：**原生 /t2a_v2**（不是 OpenAI 形状），复用同一把 Key；
            // 请求/响应形状取自多方一致的公开资料，首次使用请用「试听」确认（服务端原话会直接显示出来）
            speechBaseUrl = "https://api.minimaxi.com/v1",
            speechProtocol = SpeechProtocol.MINIMAX,
            // M3 混合思考可关（thinking.type=disabled，档位见 minimaxM3Levels）；M2.x 仅思考收不了参数——
            // 发什么由 thinkingBody 的 minimax 分支按模型名决定
            thinking = ThinkingKind.THINKING_TYPE,
            splitsReasoning = true,
            imageProtocol = ImageProtocol.MINIMAX,
            imageSizeFormat = "aspect_ratio（按比例）",
            note = "国内站 base 是 api.minimaxi.com，Key 必须与控制台地域一致（配错会 401）。" +
                "生图不是 OpenAI 协议（端点 /image_generation），App 已单独适配；" +
                "思考：M3 可关闭（thinking.type，默认 adaptive 自适应），M2.x 仅思考关不掉；" +
                "**已按官方说明补上 reasoning_split**，让思考走 reasoning_content 而不是混在正文里。" +
                "M3.1-Flash-Preview 另支持 reasoning_effort（low/medium/high/xhigh/max），未编入清单。",
            docsUrl = "https://platform.minimax.cn/docs/api-reference/api-overview",
            // 2026-09-15 实测：**.io 国际站在国内直连是 ERR_CONNECTION_CLOSED**（不是渲染问题，
            // 之前"页面空白"就是这个原因）；国内站的文档中心（platform.minimaxi.com → platform.minimax.cn）
            // 有同一份「按量计费」价格页，且是人民币刊例价，故换成 .cn 域名。
            pricingUrl = "https://platform.minimax.cn/docs/guides/pricing-paygo"
        ),
        ProviderProfile(
            keyword = "generativelanguage",
            name = "Gemini",
            chatBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
            imageBaseUrl = null,
            thinking = ThinkingKind.EFFORT_NONE_HIGH,
            note = "走 OpenAI 兼容层（2026-10-12 官方模型页复核：3.8-flash 为现行 Flash 旗舰、" +
                "3.1-pro 仍是现行 Pro，此前「存疑待实测」的警告已解除）；官方已弃用 temperature/top_p/top_k，" +
                "本档仍会发 temperature。",
            docsUrl = "https://ai.google.dev/gemini-api/docs",
            pricingUrl = "https://ai.google.dev/gemini-api/docs/pricing"
        ),
        ProviderProfile(
            keyword = "openai.com",
            name = "OpenAI",
            chatBaseUrl = "https://api.openai.com/v1",
            imageBaseUrl = "https://api.openai.com/v1",
            // 语音合成（TTS v2）：官方 /v1/audio/speech，模型 tts-1 / tts-1-hd / gpt-4o-mini-tts
            speechBaseUrl = "https://api.openai.com/v1",
            // 语音识别（ASR）：官方 /v1/audio/transcriptions，模型 whisper-1
            asrBaseUrl = "https://api.openai.com/v1",
            thinking = ThinkingKind.EFFORT_LOW_HIGH,
            imageFormatForced = "b64_json",
            // 2026-09-15 读官方 API 参考：`response_format` 明确标注"不支持 GPT image 模型"，
            // GPT image 系永远返回 base64，现行替代参数是 `output_format`(png/jpeg/webp)。
            // 故这里标记为"不接受该字段"，AiClient 生图时不再发（发了最多是冗余，最坏被当未知参数拒绝）。
            imageFormatUnsupported = true,
            note = "gpt-image 系列只返回 base64（官方无 url 选项），已固定 b64_json；" +
                "官方另注明 response_format 对 GPT image 系不受支持，故不再发送该字段。",
            docsUrl = "https://platform.openai.com/docs/api-reference/chat",
            // 注意：platform.openai.com/docs/pricing 会 302 到 developers.openai.com/api/docs/pricing
            pricingUrl = "https://developers.openai.com/api/docs/pricing"
        ),
        ProviderProfile(
            keyword = "siliconflow",
            name = "SiliconFlow",
            chatBaseUrl = null,
            imageBaseUrl = "https://api.siliconflow.cn/v1",
            // 语音合成（TTS v2）：官方文档核实 /v1/audio/speech + 8 个预置音色，见 TTS v2 调研文档
            speechBaseUrl = "https://api.siliconflow.cn/v1",
            // 语音识别（ASR）：官方文档核实 /v1/audio/transcriptions（multipart：file + model），响应 {text}
            asrBaseUrl = "https://api.siliconflow.cn/v1",
            thinking = ThinkingKind.ENABLE_THINKING,
            note = "本版作为**生图**与**语音合成**服务商（对话/创作请用其它供应商）。" +
                "曾对它发过 thinking{type} 会被判为非法 JSON，App 已改用 enable_thinking。",
            docsUrl = "https://docs.siliconflow.com/cn/userguide/introduction",
            pricingUrl = "https://cloud.siliconflow.cn/models"
        ),
        // 点点（小红书 Dots Studio，2026-09-18 接入；官方事实 2026-09-18 用浏览器渲染
        // 官方文档逐条抄录）：
        // 模型 dots3-note-prev、上下文 512K、OpenAI 兼容 /v1/chat/completions；
        // 认证头实测（伪造 Key 打真端点）接受 Authorization: Bearer ⇒ 现有 AiClient 零改动可认证；
        // 文档参数表无 temperature ⇒ 不发送；**无生图/TTS/ASR** ⇒ 三个 baseUrl 全 null（能力卡片如实显示）；
        // 平台当前免费、恢复收费前公告 ⇒ 不在应用内写死"免费"。
        ProviderProfile(
            keyword = "askdiandian",
            name = "点点（小红书 Dots）",
            chatBaseUrl = "https://note3-prev-api.askdiandian.com/v1",
            imageBaseUrl = null,
            thinking = ThinkingKind.CHAT_TEMPLATE_ENABLE_THINKING,
            temperatureSupported = false,
            note = "当前仅 dots3-note-prev 一个模型；默认已显式关闭思考" +
                "（该模型不传参数也会思考，白耗 token 且界面不显示）。不支持生图与语音。",
            docsUrl = "https://dots.ai/platform/docs",
            pricingUrl = "https://dots.ai/platform/docs"
        ),
        // 小米 MiMo（2026-10-12 接入，官方文档 mimo.mi.com 逐条核实）：
        // OpenAI 兼容 /v1/chat/completions；思考开关是 thinking {"type": enabled|disabled}（非 OpenAI 标准），
        // V2.6 全系**默认开启思考**；思考下 temperature/top_p 传了也会被强制回 1.0/0.95 ⇒ 干脆不发；
        // `max_completion_tokens` 限制思考+回答总长（与 DeepSeek 同款，低预算推理吃满会空正文）。
        // ⚠ V2.5 系 2026-10-21 下线，模型清单只编 V2.6 线。
        // 语音（2026-10-12 补配，官方 mimo.mi.com 语音篇核实）：合成与识别**都走 chat/completions**
        // （SpeechProtocol.MIMO / AsrProtocol.MIMO），同一把 Key；TTS 限时免费、ASR 只收 wav/mp3。
        ProviderProfile(
            keyword = "xiaomimimo",
            name = "小米 MiMo",
            chatBaseUrl = "https://api.xiaomimimo.com/v1",
            imageBaseUrl = null,
            speechBaseUrl = "https://api.xiaomimimo.com/v1",
            speechProtocol = SpeechProtocol.MIMO,
            asrBaseUrl = "https://api.xiaomimimo.com/v1",
            asrProtocol = AsrProtocol.MIMO,
            thinking = ThinkingKind.THINKING_TYPE,
            temperatureSupported = false,
            note = "V2.6 系（flash/pro/pro-ultraspeed）默认开启思考、可显式关闭（只有开/关两态，无更细强度）；" +
                "思考时不支持自定义 temperature（App 已自动不发）。思考与回答共享输出上限，" +
                "预算给足才不会只见思考不见正文。语音：合成（mimo-v2.5-tts，限时免费）与识别（mimo-v2.5-asr，" +
                "只收 wav/mp3——手机端录音是 m4a，识别会拦截提示；桌面 wav 可用）。不支持生图。",
            docsUrl = "https://mimo.mi.com/docs/zh-CN/quick-start/usage-guide/text-generation/deep-thinking",
            pricingUrl = "https://platform.xiaomimimo.com"
        ),
        // OpenRouter（2026-10-12 用户点名加回；官方文档核实）：
        // OpenAI 兼容 /api/v1，但思考参数是**顶层 reasoning 对象**（reasoning: {"effort": …}），
        // 由 OpenRouter 映射到背后各家；effort=none 关闭，官方全值 max/xhigh/high/medium/low/minimal/none。
        // 思考 token 与正文共享 max_tokens，低预算同样会 finish=length 空正文（重试环覆盖）。
        // 聚合层还代理**生图/语音**（2026-10-12 核实官方文档）：生图是自有 /images 端点
        // （ImageProtocol.OPENROUTER，分辨率＋宽高比，响应 data[0].b64_json）；TTS /audio/speech
        // 与 STT /audio/transcriptions 都是 OpenAI 兼容形状（音色随模型而定，如 gpt-4o-mini-tts 用 alloy 系）。
        ProviderProfile(
            keyword = "openrouter",
            name = "OpenRouter",
            chatBaseUrl = "https://openrouter.ai/api/v1",
            imageBaseUrl = "https://openrouter.ai/api/v1",
            imageProtocol = ImageProtocol.OPENROUTER,
            imageSizeFormat = "宽高比（如 16:9，App 按尺寸换算）",
            speechBaseUrl = "https://openrouter.ai/api/v1",
            asrBaseUrl = "https://openrouter.ai/api/v1",
            thinking = ThinkingKind.REASONING_OBJECT,
            note = "聚合平台：一个 Key 通各家模型（模型名带厂商前缀，如 deepseek/deepseek-v4）。" +
                "思考强度经 reasoning.effort 由 OpenRouter 代为映射，个别模型不接受时会按其规则降级；" +
                "生图/语音也走聚合（生图 /images、语音 OpenAI 兼容端点，模型如 openai/gpt-4o-mini-tts）。" +
                "海外站点，国内直连可能不稳。",
            docsUrl = "https://openrouter.ai/docs",
            pricingUrl = "https://openrouter.ai/models"
        ),
        // xAI Grok（2026-10-12 接入；docs.x.ai 核实）：
        // OpenAI 兼容 REST /v1；grok-4 系**不支持 reasoning_effort**（那是 grok-3 mini 系的参数），
        // 思考与否按模型名分（…-reasoning）→ thinking=NONE 不硬造档位。
        // 多模态（2026-10-12 补配，官方文档核实）：生图是 OpenAI 兼容 /images/generations
        // （但尺寸不用 size、用 aspect_ratio + resolution ⇒ imageSizeUnsupported）；
        // TTS /v1/tts（无模型字段，voice_id + language）；STT /v1/stt（multipart，无模型字段）。
        ProviderProfile(
            keyword = "api.x.ai",
            name = "xAI Grok",
            chatBaseUrl = "https://api.x.ai/v1",
            imageBaseUrl = "https://api.x.ai/v1",
            imageSizeUnsupported = true,
            imageSizeFormat = "宽高比 aspect_ratio ＋ 分辨率 1k/2k（App 不发 size）",
            speechBaseUrl = "https://api.x.ai/v1",
            speechProtocol = SpeechProtocol.XAI,
            asrBaseUrl = "https://api.x.ai/v1",
            asrProtocol = AsrProtocol.XAI,
            thinking = ThinkingKind.NONE,
            note = "grok-4.x 线始终思考、无档位参数（思考与否按模型名区分，选带 reasoning 的模型名即可）；" +
                "temperature 正常支持。生图模型 grok-imagine-image-quality（默认返回临时 URL，App 会下载）；" +
                "语音合成无模型概念（音色 voice_id，App 已内置官方全部 28 个音色，支持手填复刻 id）；" +
                "语音识别同样无模型字段。同一把 Key 三类能力通用。",
            docsUrl = "https://docs.x.ai/docs",
            pricingUrl = "https://docs.x.ai/docs/models"
        )
    )

    /** 按 Base URL 匹配供应商画像（火山域名是 volces.com，额外兼容） */
    fun forUrl(baseUrl: String): ProviderProfile? {
        val u = baseUrl.trim().lowercase()
        all.firstOrNull { it.keyword in u }?.let { return it }
        // 域名与关键词不一致的家，在这里显式兜底（否则"按 URL 找画像"会返回 null ⇒ 胶囊不出现、
        // 凭据也取不到那一组）：火山有**两个站点** —— 方舟 volces.com 与豆包语音 openspeech.bytedance.com
        // （后者 2026-09-17 加，语音合成走它），两个都归 volcengine 这个画像。
        return if ("volces.com" in u || "openspeech.bytedance.com" in u) {
            all.firstOrNull { it.keyword == "volcengine" }
        } else null
    }

    /**
     * 统一解析：先匹配用户自定义供应商（按 URL），再匹配内置画像。
     * 传入 custom 后，自定义供应商与内置供应商享有同一套自动适配（思考参数/温度/生图协议/返回格式）。
     */
    fun resolve(baseUrl: String, custom: List<CustomProvider> = emptyList()): ProviderProfile? {
        val u = baseUrl.trim().lowercase()
        if (u.isBlank()) return null
        custom.firstOrNull { it.chatBaseUrl.isNotBlank() && it.chatBaseUrl.trim().lowercase() == u }
            ?.let { return it.toProfile() }
        custom.firstOrNull { it.imageBaseUrl.isNotBlank() && it.imageBaseUrl.trim().lowercase() == u }
            ?.let { return it.toProfile() }
        return forUrl(baseUrl)
    }

    /** 自定义供应商 → 画像（枚举名反查，未知值退回安全默认） */
    private fun CustomProvider.toProfile(): ProviderProfile = ProviderProfile(
        keyword = "custom:$id",
        name = name.ifBlank { "自定义供应商" },
        chatBaseUrl = chatBaseUrl.takeIf { it.isNotBlank() },
        imageBaseUrl = imageBaseUrl.takeIf { it.isNotBlank() },
        thinking = ThinkingKind.entries.firstOrNull { it.name == thinkingKind } ?: ThinkingKind.NONE,
        temperatureSupported = temperatureSupported,
        imageFormatForced = imageFormatForced.takeIf { it.isNotBlank() },
        imageProtocol = ImageProtocol.entries.firstOrNull { it.name == imageProtocol } ?: ImageProtocol.OPENAI,
        note = note.takeIf { it.isNotBlank() }
    )

    /** 自定义供应商对应的 Key 存储键（与 ProviderProfile.keyword 一致） */
    fun keyIdFor(baseUrl: String, custom: List<CustomProvider> = emptyList()): String =
        resolve(baseUrl, custom)?.keyword ?: baseUrl.trim().lowercase()

    /**
     * 该家是否需要在每次请求里显式要求"把思考拆出去"。
     *
     * 目前只有 MiniMax：不传 `reasoning_split` 时，思考会被 `<think>…</think>` 包着塞进 `content`，
     * 于是思考跟着正文一起显示在聊天气泡里（用户反馈的"思考内容被当成正文回出来"）。
     * App 不读 `reasoning_content`，所以拆过去就等于不显示。
     */
    fun splitsReasoning(baseUrl: String, custom: List<CustomProvider> = emptyList()): Boolean =
        resolve(baseUrl, custom)?.splitsReasoning == true

    /**
     * 该模型是否"设计上总是思考"（传不传参数都会推理）：Kimi K3、智谱 GLM-5.3 系。
     * 用于输出预算判断——这类模型即便用户选了"关闭"也仍在思考，预算必须照样抬高。
     */
    fun alwaysThinks(baseUrl: String, model: String, custom: List<CustomProvider> = emptyList()): Boolean {
        val name = model.trim().lowercase()
        // 点点 dots3 系（小红书 Dots Studio）：**忽略** `thinking:{"type":"disabled"}`，传了仍产出整段推理
        // （真机实测约 700 字），而思考与正文共用 max_tokens ⇒ 正文为空、这一轮在会话里消失。
        // 按**模型名**判而不是走 resolve()：这类端点常被填成自建/代理基址，认模型比认基址稳。
        if (name.startsWith("dots3")) return true
        val p = resolve(baseUrl, custom) ?: return false
        return when {
            p.keyword == "moonshot" -> true
            p.keyword == "bigmodel" && "glm-5.3" in name -> true
            // MiniMax M2.x（M2.7/M2.5/M2.1）是仅思考模式（官方 2026-10-12 复核），关不掉
            p.keyword == "minimax" && "minimax-m2" in name -> true
            // OpenRouter 聚合层：已知"始终思考"家族按 slug 前缀认（deepseek 线 / Kimi 线 / GLM-5.3 线），
            // 名单外的聚合模型不硬猜——预算吃满的空正文由空正文重试环兜底（2026-10-12）
            p.keyword == "openrouter" -> name.startsWith("deepseek/") ||
                name.startsWith("moonshotai/kimi") || "glm-5.3" in name
            else -> false
        }
    }

    /**
     * 「只有思考、没有正文」时**重发一次**要给的输出预算下限（8192）。
     *
     * 依据：思考与正文**共用** `max_tokens`（[effectiveMaxTokens] 的注释），实测预算 1024 时推理
     * 1340~1496 字吃满配额、正文一个 token 都排不上（记忆链 5/5 全空）。现有的静态白名单
     * （[alwaysThinks]）只覆盖已知的几家，名单外的推理模型照样空——所以**以观测为准**：
     * 既然已经看到它吐思考了，就按这个下限重发一次，不再依赖"模型名在不在名单里"。
     */
    const val RETRY_MIN_OUTPUT_TOKENS = 8192

    /**
     * 空正文自动重试的**预算上限**（32768）：`finish_reason=length`（预算又被吃满）时按
     * 「预算翻倍再发一次」的口径抬到这里封顶——再高的空正文就不是预算问题，重发只会白花钱。
     */
    const val RETRY_MAX_OUTPUT_TOKENS = 32768

    /**
     * 「自定义预算」档·**总输出**可选档位，按当前模型生成（0.2.4 批次 B）。
     *
     * **总是思考的模型不给 8192 以下档位**：思考与正文共用 `max_tokens`
     * （见 [effectiveMaxTokens] 的注释），低档位必然推理吃光配额、正文为空——
     * 对这类模型（[alwaysThinks]）档位直接从 [RETRY_MIN_OUTPUT_TOKENS] 起跳，
     * 界面上就不会出现"选了必空"的选项。
     */
    fun budgetLevels(baseUrl: String, model: String, custom: List<CustomProvider> = emptyList()): List<Int> {
        val all = listOf(1024, 2048, 4096, 8192, 16384, 32768)
        return if (alwaysThinks(baseUrl, model, custom)) all.filter { it >= RETRY_MIN_OUTPUT_TOKENS } else all
    }

    /** 「节省」档的预设值（台账 113）：总输出 ＋ 上下文字符预算 ＋ 思考强度，一次填进那三个字段 */
    class SavingPreset(val maxTokens: Int, val contextChars: Int, val thinking: String)

    /**
     * 「节省」档的三个预设值（台账 113，用户 2026-09-30 把取值交给实现定：**关思考 ＋ 低输出 ＋ 低上下文**）。
     *
     * 取值的两条硬要求（用户原话）：**覆盖大部分供应商的最低思考模式**、**尽可能不出现"返回被吞"**。
     * - 思考强度取**该模型档次里最低的那一档**：`thinkingLevels` 里没有 `"off"` 的（思考关不掉的模型、
     *   或不支持思考参数的供应商）就留空＝不传参数，由模型自己决定——**不硬塞一个它会 4xx 的值**。
     * - 总输出取 [budgetLevels] 的**第一档**，也就是"这个模型允许的最低档"：对总是思考的模型这一档
     *   已经被抬到 [RETRY_MIN_OUTPUT_TOKENS]（8192）——思考与正文共用 `max_tokens`，再低就是
     *   推理吃光配额、正文为空（就是用户说的"返回被吞"）。所以**不能用写死的 1024**：
     *   1024 对会思考的模型必然空回复（见 [effectiveMaxTokens] 的注释）。
     *
     * @param baseUrl 当前对话 Base URL（档位按它解析）
     */
    fun savingPreset(baseUrl: String, model: String, custom: List<CustomProvider> = emptyList()): SavingPreset {
        val levels = thinkingLevels(baseUrl, model, custom).map { it.mode }
        return SavingPreset(
            maxTokens = budgetLevels(baseUrl, model, custom).firstOrNull() ?: RETRY_MIN_OUTPUT_TOKENS,
            contextChars = SAVING_CONTEXT_CHARS,
            thinking = if (levels.contains("off")) "off" else ""
        )
    }

    /**
     * 思考场景下**实际会发送**的输出上限（设置界面据此显示"当前实际发送"）。
     * 思考 token 也计入 max_tokens 预算：预算太小会推理耗光配额、正文为空（finish_reason=length）。
     * 判定"是否在思考"：
     * - 总是思考的模型（Kimi K3、GLM-5.3 系）→ 永远算思考中（连"关闭"档也关不掉）；
     * - DeepSeek 现行模型默认开思考（不传参数也思考）→ mode 非 off 即算思考中；
     * - 其余供应商 → 只有显式选"深度"（high）才算。
     */
    fun effectiveMaxTokens(
        baseUrl: String,
        mode: String,
        model: String,
        configured: Int,
        custom: List<CustomProvider> = emptyList()
    ): Int {
        if (configured >= 8192) return configured
        // "默认开思考"的家（DeepSeek / 小米 MiMo 官方口径不传参数也思考；通义 qwen3.8 系官方
        // 默认开思考、MiniMax M3 默认 adaptive／M2.x 仅思考；火山方舟 2-1 系默认 high、character 默认
        // medium、TokenHub hy3/hy4 默认 high——均 2026-10-12 官方一手核实；OpenRouter 的已知
        // 始终思考家族见 [alwaysThinks]）：mode 非 off 即算思考中；其余只有显式"深度"（high）才算。
        val thinkingOn = when (resolve(baseUrl, custom)?.keyword) {
            "deepseek", "xiaomimimo", "dashscope", "minimax", "volcengine" -> mode != "off"
            // TokenHub 只有 hy3/hy4 默认开思考；role 系不思考不抬
            "tencentmaas" -> mode != "off" &&
                ("hy3" in model.trim().lowercase() || "hy4" in model.trim().lowercase())
            else -> alwaysThinks(baseUrl, model, custom) || mode == "high"
        }
        return if (thinkingOn) 8192 else configured
    }

    /** 该基址对应的显示名（找不到就返回原 URL） */
    fun nameFor(baseUrl: String): String = forUrl(baseUrl)?.name ?: baseUrl.trim()

    /**
     * 采样惩罚（`frequency_penalty=0.3` ＋ `presence_penalty=0.5`）该不该发。
     *
     * 2026-09-26 对话质量实验：这两个值能把"逐字复读同一句台词/动作"压下去（重复样本降 40~50%），
     * 但**容忍度因家因模式而异**——Kimi 传了直接报错；DeepSeek 思考模式下参数静默无效。
     * 所以按「模型 × 思考模式」分档：目前只对 **DeepSeek 非思考档**（显式选"关闭"、且不是 reasoner 系）
     * 发送；其余家（含未知供应商）一律不发，宁可少一项也不要让请求 4xx。
     */
    fun penalties(
        baseUrl: String,
        thinkingMode: String,
        model: String,
        custom: List<CustomProvider> = emptyList()
    ): Pair<Double, Double>? {
        if (resolve(baseUrl, custom)?.keyword != "deepseek") return null
        val name = model.trim().lowercase()
        if ("reasoner" in name) return null
        if (thinkingMode != "off") return null
        return 0.3 to 0.5
    }

    /** 支持对话/创作的供应商预设（设置页胶囊用） */
    fun chatProviderUrls(): List<String> = all.mapNotNull { it.chatBaseUrl }

    /** 支持生图的供应商预设 */
    fun imageProviderUrls(): List<String> = all.mapNotNull { it.imageBaseUrl }

    /** 支持**语音合成**（OpenAI 兼容 /audio/speech）的供应商预设（TTS v2） */
    fun speechProviderUrls(): List<String> = all.mapNotNull { it.speechBaseUrl }

    /** 该 Base URL 是不是"本版适配过的语音供应商"（用于设置页提示语与回退判断） */
    fun supportsSpeech(baseUrl: String): Boolean = all.any { it.speechBaseUrl == baseUrl.trim() }

    /** 该 Base URL 用哪套语音协议（匹配不到就按 OpenAI 兼容处理——自定义/本地部署都是这一类） */
    fun speechProtocol(baseUrl: String): SpeechProtocol =
        resolve(baseUrl)?.speechProtocol ?: SpeechProtocol.OPENAI

    /** 该 Base URL 的语音接口要填哪些凭据字段（界面照这个渲染输入框） */
    fun speechCredentialFields(baseUrl: String): List<CredentialField> =
        speechProtocol(baseUrl).credentialFields

    /** 支持**语音识别**（ASR）的供应商预设（Step 3）：识别与合成共用同一份凭据 */
    fun asrProviderUrls(): List<String> = all.mapNotNull { it.asrBaseUrl }

    /** 该 Base URL 是不是"本版适配过的识别供应商" */
    fun supportsAsr(baseUrl: String): Boolean = all.any { it.asrBaseUrl == baseUrl.trim() }

    /** 该 Base URL 用哪套识别协议（匹配不到就按 OpenAI 兼容处理——自定义/本地部署都是这一类） */
    fun asrProtocol(baseUrl: String): AsrProtocol =
        resolve(baseUrl)?.asrProtocol ?: AsrProtocol.OPENAI

    /**
     * **既能对话、又能生图**的供应商（跟随模式下用，2026-09-15 用户要求）。
     * 跟随模式是"一套供应商同时管对话与生图"，所以只列两边都有的家；
     * DeepSeek / Kimi / Gemini 只在 [chatProviderUrls] 里、没有生图接口，列出来只会让人选到打不通的配置。
     */
    fun chatUrlsWithImage(): List<String> = all.mapNotNull {
        it.chatBaseUrl?.takeIf { _ -> it.imageBaseUrl != null }
    }

    /**
     * 思考强度档位：按供应商与模型给出。顺序很重要——
     * Kimi / DeepSeek 虽然也用 `reasoning_effort`，但档位语义与 OpenAI 不同
     * （Kimi 关不掉、默认 max；DeepSeek 能真关闭、默认 high），
     * 所以必须**排在"参数形态"判断之前**，否则会被通用 effort 档位覆盖（2026-09-14 实机发现）。
     */
    fun thinkingLevels(baseUrl: String, model: String): List<ThinkingLevel> {
        val p = resolve(baseUrl) ?: return emptyList()
        val m = model.trim().lowercase()
        return when {
            // MiniMax 按模型分（2026-10-12 MiniMax 自家 API 文档一手核实）：M3 可关（disabled 真
            // 跳过思考）；M2.x 收 disabled 但**忽略**（思考照旧）⇒ 无档位；M3.1-Flash-Preview 发
            // disabled 直接 **400**（requires adaptive thinking）⇒ 也不给档位（preview 未编清单，
            // 手填它的用户不引导去关思考）
            p.keyword == "minimax" ->
                if ("minimax-m3" in m && "m3.1" !in m) minimaxM3Levels else emptyList()
            p.thinking == ThinkingKind.NONE -> emptyList()
            p.keyword == "moonshot" -> kimiLevels
            p.keyword == "deepseek" -> deepseekLevels
            p.keyword == "bigmodel" && "glm-5.3" in m -> glm53Levels
            p.keyword == "askdiandian" -> dotsLevels
            p.keyword == "generativelanguage" -> geminiLevels
            p.keyword == "openrouter" -> reasoningObjectLevels
            p.keyword == "xiaomimimo" -> mimoLevels
            // 火山方舟：reasoning_effort 档位按模型默认值分两组（2026-10-12 官方一手核实，
            // 见两张 volc*Levels 的注释）；手填清单外模型按 2-1 组处理
            p.keyword == "volcengine" ->
                if ("character" in m || "2-0" in m) volcMediumDefaultLevels else volcHighDefaultLevels
            // 腾讯 TokenHub 按模型分：hy3/hy4 有 effort 档；role 系维持 thinking 开关三档
            p.keyword == "tencentmaas" -> when {
                "hy3" in m -> hy3Levels
                "hy4" in m -> hy4Levels
                else -> defaultLevels
            }
            // OpenAI：gpt-6/gpt-5.6 的 effort 全值表（2026-10-12 官方一手核实）
            p.keyword == "openai.com" -> openaiLevels
            p.thinking == ThinkingKind.EFFORT_LOW_HIGH || p.thinking == ThinkingKind.EFFORT_NONE_HIGH ->
                effortLevels
            p.thinking == ThinkingKind.EFFORT_LOW_HIGH_MAX ->
                effortLevels
            // 其它服务商用默认三档（默认 / 关闭 / 深度）
            else -> defaultLevels
        }
    }

    /**
     * 带自定义供应商的档位解析（界面用）：自定义供应商按 OpenAI 通用档位处理。
     */
    fun thinkingLevels(
        baseUrl: String,
        model: String,
        custom: List<CustomProvider> = emptyList()
    ): List<ThinkingLevel> {
        val p = resolve(baseUrl, custom) ?: return emptyList()
        if (p.keyword.startsWith("custom:")) {
            return when (p.thinking) {
                ThinkingKind.NONE -> emptyList()
                ThinkingKind.ENABLE_THINKING -> defaultLevels
                ThinkingKind.EFFORT_LOW_HIGH, ThinkingKind.EFFORT_NONE_HIGH -> effortLevels
                ThinkingKind.EFFORT_LOW_HIGH_MAX -> effortLevels
                // 自定义供应商按 OpenAI 通用 effort 三档处理（方舟 effort 形状同族）；
                // REASONING_OBJECT 给全六档（含"关闭"）——聚合层支持 effort=none（2026-10-12 修正）
                ThinkingKind.REASONING_OBJECT -> reasoningObjectLevels
                ThinkingKind.THINKING_TYPE -> defaultLevels
                ThinkingKind.CHAT_TEMPLATE_ENABLE_THINKING -> defaultLevels
                ThinkingKind.ARK_EFFORT -> effortLevels
            }
        }
        return thinkingLevels(baseUrl, model)
    }
}
