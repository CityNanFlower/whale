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
        DASHSCOPE("通义原生 multimodal-generation", "通义原生")
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
        VOLC_FLASH("火山录音文件识别极速版 /recognize/flash")
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
     * Kimi：K3 总是思考、关不掉，只能用 reasoning_effort 调深浅。
     * 注意「默认」与「最高」都等于 `max`（不传参时服务端默认就是 max），
     * 所以这里**合并成一个档位**，避免两个选项效果完全一样造成误解（2026-09-14 用户反馈）。
     */
    private val kimiLevels = listOf(
        ThinkingLevel("较低", "off", "reasoning_effort=low（最省）"),
        ThinkingLevel("最高", "high", "reasoning_effort=max（也是模型默认）")
    )

    /** 智谱 GLM-5.3 系：强制思考，传 disabled 会报错，关闭档降为 low */
    private val glm53Levels = listOf(
        ThinkingLevel("默认", "", "不传参数（强制思考）"),
        ThinkingLevel("最低", "off", "reasoning_effort=low（关不掉，只能降强度）"),
        ThinkingLevel("深度", "high", "开启思考")
    )

    /** OpenAI：只有 low/high，没有"关闭" */
    private val effortLevels = listOf(
        ThinkingLevel("默认", "", "不传参数，由模型决定"),
        ThinkingLevel("较低", "off", "reasoning_effort=low"),
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
     * 核实日期 2026-09-14；详细来源见 docs/鲸鱼-API与模型接入梳理.md
     *
     * 2026-09-15：**移除 OpenRouter**（用户反馈：聚合平台不常用，且其上的模型基本都在国外、
     * 无余额可用）。老用户原有的 OpenRouter 配置不受影响——URL 与 Key 仍保存在 settings 里，
     * 只是不再有专属画像与预设胶囊，`resolve()` 返回 null 时按通用逻辑处理（发 temperature、
     * 不注入思考参数）；模型名需要手动填写。
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
            thinking = ThinkingKind.THINKING_TYPE,
            note = "域名是 volces.com，需用控制台创建的推理接入点或模型名；" +
                "生图默认方形/竖屏尺寸充足，带深度思考标签的模型默认开启思考。" +
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
            thinking = ThinkingKind.THINKING_TYPE,
            note = "现行模型默认开启思考（不传参数也思考），思考 token 计入输出上限。" +
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
            note = "GLM-5.3 / 5.3-FLASH 强制思考（传 disabled 会报错），选「最低」也只是降到 low。" +
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
                "语音合成同理走原生 SpeechSynthesizer（返回音频 URL，App 会二次下载）。",
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
            thinking = ThinkingKind.NONE,
            splitsReasoning = true,
            imageProtocol = ImageProtocol.MINIMAX,
            imageSizeFormat = "aspect_ratio（按比例）",
            note = "国内站 base 是 api.minimaxi.com，Key 必须与控制台地域一致（配错会 401）。" +
                "生图不是 OpenAI 协议（端点 /image_generation），App 已单独适配；" +
                "思考强度参数未适配（M2.x 关不掉思考），但**已按官方说明补上 reasoning_split**，" +
                "让思考走 reasoning_content 而不是混在正文里。",
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
            note = "走 OpenAI 兼容层；3.5-pro 从未发布，Pro 档现为 gemini-3.1-pro-preview。" +
                "⚠️ 清单里的 gemini-3.8-flash 在官方模型列表/定价表/更新日志三处都查不到" +
                "（现行最新 Flash 是 gemini-3.6-flash），存疑待实测；" +
                "另官方已弃用 temperature/top_p/top_k，本档仍会发 temperature。",
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
        val p = resolve(baseUrl, custom) ?: return false
        val name = model.trim().lowercase()
        return when {
            p.keyword == "moonshot" -> true
            p.keyword == "bigmodel" && "glm-5.3" in name -> true
            else -> false
        }
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
        val thinkingOn = when {
            alwaysThinks(baseUrl, model, custom) -> true
            resolve(baseUrl, custom)?.keyword == "deepseek" -> mode != "off"
            else -> mode == "high"
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
     * Kimi 虽然也用 `reasoning_effort`，但它的档位语义与 OpenAI 不同（关不掉、默认就是 max），
     * 所以必须**排在"参数形态"判断之前**，否则会被通用 effort 档位覆盖（2026-09-14 实机发现）。
     */
    fun thinkingLevels(baseUrl: String, model: String): List<ThinkingLevel> {
        val p = resolve(baseUrl) ?: return emptyList()
        val m = model.trim().lowercase()
        return when {
            p.thinking == ThinkingKind.NONE -> emptyList()
            p.keyword == "moonshot" -> kimiLevels
            p.keyword == "bigmodel" && "glm-5.3" in m -> glm53Levels
            p.keyword == "askdiandian" -> dotsLevels
            p.thinking == ThinkingKind.EFFORT_LOW_HIGH || p.thinking == ThinkingKind.EFFORT_NONE_HIGH ->
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
                ThinkingKind.THINKING_TYPE -> defaultLevels
                ThinkingKind.CHAT_TEMPLATE_ENABLE_THINKING -> defaultLevels
            }
        }
        return thinkingLevels(baseUrl, model)
    }
}
