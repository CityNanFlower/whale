package com.mysticat.roleplay.data

/**
 * 按 Base URL 推断服务商，返回该服务商常见模型。
 * 分三套：chat（日常对话）、creation（AI 生成角色卡等创作）、image（生图）。
 * 也提供各主流服务商 Base URL 预设，方便快速切换。
 *
 * 清单更新于 2026-09-14（第二轮联网核实，来源：硅基流动官方博客与模型页、DeepSeek API 文档、
 * 智谱开放文档、Kimi 平台文档、火山方舟文档、OpenRouter 模型与图片模型列表、OpenAI 图像模型文档；
 * 条目均按各家官方文档核录）。
 * 2026-09-15 价格核实与重排：DeepSeek / Kimi / 通义 / MiniMax / OpenAI / 智谱（部分）
 * 的价格已由官方定价页或官方 API 核实，**各清单已按「输出价升序」重排**（价格未查到的条目置于末尾）；
 * 价格数值与可信度标注见 docs/鲸鱼-API与模型接入梳理.md 第五、5.4 节。
 * **同日移除 OpenRouter**（用户反馈不常用）——留意 `keywordFor` 不再认识它的域名。
 * 供应商排序：对话性能优先，其次国内先于美国
 * （火山方舟→DeepSeek→智谱→Kimi→通义→硅基流动→Gemini→OpenAI）。
 * 约定：chat / creation 内部按「输出价升序」排列（同币种内严格可比；跨币种按 5.4 节口径折算）；
 * chat 只放适合纯文本聊天（文聊）的模型；
 * creation 选「效率与性能兼顾」，优先 flash/turbo/air 档，旗舰放最后；image 只放 OpenAI 兼容
 * /images/generations 端点（OpenAI、智谱、硅基流动、火山方舟），
 * 禁止通义(wanx)与 Gemini——它们走原生端点会 404（此前实战已踩坑）。
 */
object ModelCatalog {

    private val chatByKeyword: Map<String, List<String>> = mapOf(
        // 火山方舟（doubao-seed-character 为角色扮演专用模型，首选；价格 ¥0.8/¥2 也是本家最便宜，仍在首位 ✓）
        // 2026-09-16：doubao-seed-2-1-pro-260628 → **doubao-seed-2-1-pro-260915**（火山 2026-09-16 公告
        // "Doubao-Seed-2.1-pro 更新至 0915 版本、API 已全量上线方舟"；价格不变 ¥6/¥30。
        // 来源：当日新闻稿与实测文章，官方页是 SPA 未能直接读到 ⇒ 等级＝二手但多方一致）
        "volcengine" to listOf(
            "doubao-seed-character-260628",
            "doubao-seed-2-1-turbo-260628",
            "doubao-seed-2-1-pro-260915"
        ),
        // DeepSeek：deepseek-chat / deepseek-reasoner 已于 2026-07-24 弃用（官方文档核实），
        // 现行模型仅 deepseek-flash（V4.1-Flash，支持视觉、默认开思考、1M 上下文）与 deepseek-v4-pro
        "deepseek" to listOf("deepseek-flash", "deepseek-v4-pro"),
        // 智谱（2026-09-14 按官方模型总览核实精确 id：带日期后缀的 GLM-4-Flash-250414 为轻量档，
        // 角色扮演实测好用；GLM-4.6V-Flash 为视觉对话档；GLM-5.3-Flash 原生多模态）
        // 2026-09-15 补充：这三款 GLM-4.x Flash 是**免费档**（二手来源），但**限 1 并发**——
        // 用户实测 GLM-4.7-Flash 连通性测试「时长过长」即由此排队导致（见梳理文档第五节）。
        // 排序上免费档在前、付费档在后，恰好也是价格升序 ✓
        "zhipu" to listOf(
            "GLM-4-Flash-250414",
            "GLM-4.7-Flash",
            "GLM-4.6V-Flash",
            "GLM-5.3-Flash",
            "GLM-5.3"
        ),
        "bigmodel" to listOf(
            "GLM-4-Flash-250414",
            "GLM-4.7-Flash",
            "GLM-4.6V-Flash",
            "GLM-5.3-Flash",
            "GLM-5.3"
        ),
        // Moonshot / Kimi（2026-09-14 按官方模型列表核实：kimi-k3 为旗舰、kimi-k2.6 支持视觉与思考/非思考；
        // k2.5 与 moonshot-v1 系列 2026-08-31 下线；k2.7-code 系为编程向，不列入角色扮演）
        // 2026-09-15 按官方定价重排：k2.6（¥6.5/¥27）比 k3（¥20/¥100）便宜，轻量在前
        "moonshot" to listOf("kimi-k2.6", "kimi-k3"),
        "kimi" to listOf("kimi-k2.6", "kimi-k3"),
        // 通义/千问（2026-09-14 按千问模型页核实 id：qwen3.8-flash 轻量、qwen3.8-max-0902 旗舰，
        // 两者均支持视觉理解）
        "dashscope" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        "aliyun" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        "qianwen" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        // 腾讯云 TokenHub（base https://tokenhub.tencentmaas.com/v1，OpenAI 兼容）。
        // 角色扮演优先：hunyuan-role-latest / hy-role 为角色扮演专用模型。
        // 2026-09-16 补：混元的**视觉**能力不在 hy3 / hy4-preview 上（那两个是纯文本，
        // 官方能力标签只有思考/结构化输出/Function Calling/缓存），而是单独的 HY-Vision 系列；
        // hy-vision-2.0-instruct 是其中唯一可直接当对话模型用的（单图，¥7.5/¥17.5，故排最后）。
        "tencentmaas" to listOf("hunyuan-role-latest", "hy-role", "hy4-preview", "hy3", "hy-vision-2.0-instruct"),
        "tokenhub" to listOf("hunyuan-role-latest", "hy-role", "hy4-preview", "hy3", "hy-vision-2.0-instruct"),
        // MiniMax（base https://api.minimaxi.com/v1，OpenAI 兼容；Key 与区域必须匹配）
        // 2026-09-15 按官方 pay-as-you-go 重排：M2.7 与 M3 同价（$0.30/$1.20），
        // highspeed 反而更贵（$0.60/$2.40）故移到末尾
        "minimax" to listOf("MiniMax-M2.7", "MiniMax-M3", "MiniMax-M2.7-highspeed"),
        // Gemini（2026-09-14 核实：gemini-3.5-pro **从未发布**、API 无此 ID；Pro 档现为 gemini-3.1-pro(preview)；
        // 3.8 代只有 flash，另有 3.8-flash-cyber 变体）
        "gemini" to listOf("gemini-3.8-flash", "gemini-3.1-pro"),
        "googleapis" to listOf("gemini-3.8-flash", "gemini-3.1-pro"),
        // OpenAI（gpt-6-astra 为 09-03 旗舰、gpt-5.6-luna 为轻量档，均已核实存在）
        // 2026-09-15 由官方定价页核实：luna $0.20/$1.20、astra $10/$50（短上下文档），顺序本就按价格 ✓
        "openai" to listOf("gpt-5.6-luna", "gpt-6-astra"),
        // 点点（小红书 Dots Studio，2026-09-18 接入）：当前唯一模型 dots3-note-prev
        // （官方文档逐条核录）
        "askdiandian" to listOf("dots3-note-prev")
        // 2026-09-15 移除 OpenRouter（用户反馈不常用，且其模型多为国外、无余额可用）
    )

    private val creationByKeyword: Map<String, List<String>> = mapOf(
        "volcengine" to listOf("doubao-seed-2-1-turbo-260628", "doubao-seed-2-1-pro-260915"),
        "deepseek" to listOf("deepseek-flash", "deepseek-v4-pro"),
        // 创作同样优先轻量档（flash/turbo/air），旗舰放最后
        "zhipu" to listOf("GLM-4-Flash-250414", "GLM-4.7-Flash", "GLM-5.3-Flash", "GLM-5.3"),
        "bigmodel" to listOf("GLM-4-Flash-250414", "GLM-4.7-Flash", "GLM-5.3-Flash", "GLM-5.3"),
        "moonshot" to listOf("kimi-k2.6", "kimi-k3"),
        "kimi" to listOf("kimi-k2.6", "kimi-k3"),
        "dashscope" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        "aliyun" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        "qianwen" to listOf("qwen3.8-flash", "qwen3.8-max-0902"),
        // 腾讯云 TokenHub：创作按通用档（角色扮演专用档已在对话清单里）
        "tencentmaas" to listOf("hy3", "hy4-preview"),
        "tokenhub" to listOf("hy3", "hy4-preview"),
        "minimax" to listOf("MiniMax-M2.7", "MiniMax-M3", "MiniMax-M2.7-highspeed"),
        "gemini" to listOf("gemini-3.8-flash", "gemini-3.1-pro"),
        "googleapis" to listOf("gemini-3.8-flash", "gemini-3.1-pro"),
        "openai" to listOf("gpt-5.6-luna", "gpt-6-astra"),
        // 点点：创作同款模型（灵感创作走同一对话端点）
        "askdiandian" to listOf("dots3-note-prev")
    )

    // 只放 OpenAI 兼容 /images/generations 的服务商；禁止通义(wanx)/Gemini（原生端点会 404）
    private val imageByKeyword: Map<String, List<String>> = mapOf(
        // 火山方舟在售 Seedream 全系（用户 2026-09-13 自模型广场核实）
        "volcengine" to listOf(
            "doubao-seedream-4-0-250828",
            "doubao-seedream-4-5-251128",
            "doubao-seedream-5-0-260128",
            "doubao-seedream-5-0-pro-260628"
        ),
        // 智谱生图（2026-09-14 按官方模型总览核实：GLM-Image 为旗舰图像模型，CogView-4、
        // CogView-3-Flash 仍在售）
        "zhipu" to listOf("CogView-3-Flash", "CogView-4", "GLM-Image"),
        "bigmodel" to listOf("CogView-3-Flash", "CogView-4", "GLM-Image"),
        // 千问生图（2026-09-14 按千问模型页 output=image 列表核实 id；走 OpenAI 兼容 /images/generations）
        "dashscope" to listOf("qwen-image-3.0", "qwen-image-2.0-pro", "wan2.7-image-pro"),
        "aliyun" to listOf("qwen-image-3.0", "qwen-image-2.0-pro", "wan2.7-image-pro"),
        "qianwen" to listOf("qwen-image-3.0", "qwen-image-2.0-pro", "wan2.7-image-pro"),
        // 腾讯云 TokenHub 生图（2026-09-14 按控制台模型列表核实）
        "tencentmaas" to listOf(
            "wand-vega-image-flash",
            "wand-vega-image-pro",
            "hy-image-v3",
            "vidu-image-q2"
        ),
        "tokenhub" to listOf(
            "wand-vega-image-flash",
            "wand-vega-image-pro",
            "hy-image-v3",
            "vidu-image-q2"
        ),
        // MiniMax 生图（2026-09-14 按官方 API 文档核实）
        "minimax" to listOf("image-01"),
        // 硅基流动：2026-09-14 按账号可用生图模型（用户提供截图）精选；仅保留生图，对话/创作已移除
        "siliconflow" to listOf(
            "Tongyi-MAI/Z-Image-Turbo",
            "Kwai-Kolors/Kolors",
            "Qwen/Qwen-Image",
            "baidu/ERNIE-Image-Turbo"
        ),
        // openai：2026-09-15 抓官方定价页（developers.openai.com/api/docs/pricing）核实——
        // 现行图像模型只有 `gpt-image-2.5-sunburst` 与 `gpt-image-2.5-flare`，
        // 两者同价（图像模态 $8/$2/$30 每百万 token，文本模态 $5/$1.25）；
        // **旧的 gpt-image-1.5 / gpt-image-2 在该页 0 匹配（已不在售）**，故替换。
        // dall-e-3 此前已退役。
        // ⚠️ 待实测（2026-09-15 读官方 API 参考后发现的新问题）：
        //   `response_format` 官方标注"**不支持 GPT image 模型**"，GPT image 系永远返回 base64，
        //   现行替代参数是 `output_format`（png/jpeg/webp）。App 仍在对 OpenAI 发
        //   response_format=b64_json，**是否被 400 拒绝未实测**（无余额）。
        //   见 docs/鲸鱼-API与模型接入梳理.md 6.2 节。
        "openai" to listOf("gpt-image-2.5-sunburst", "gpt-image-2.5-flare")
    )

    /**
     * 跨服务商默认对话推荐 —— 2026-09-15 按已核实价格升序重排（输出价为准）。
     * 折算口径见 docs/鲸鱼-API与模型接入梳理.md 5.4 节（按 2026-09-14 中间价 1 USD ≈ 6.77 CNY）。
     * **智谱三款 GLM-4.x Flash 为免费档（2026-09-15 一手核实：官方定价文档"免费"、限 1 并发）**，故排在最前。
     * ⚠️ `gemini-3.8-flash` 存疑：Google 官方模型列表/定价表/更新日志都查不到该 ID
     *   （现行最新 Flash 是 `gemini-3.6-flash` $1.50/$7.50），只有站内横幅写"3.8 Flash 现已推出"。
     *   保留不改，但用前需实测；见 梳理文档 6.2 节。
     * 该清单只在「Base URL 匹配不到任何供应商」时使用（多为自定义供应商），
     * 各家自己的清单见 chatByKeyword。
     */
    private val defaultChatPresets = listOf(
        "GLM-4-Flash-250414", // 免费（限 1 并发）
        "GLM-4.7-Flash", // 免费（限 1 并发）
        "doubao-seed-character-260628", // 火山角色扮演专用 ¥0.8/¥2
        "qwen3.8-flash", // ¥0.8/¥2.7
        "deepseek-flash", // ¥1/¥4（空闲时段）
        "gpt-5.6-luna", // $0.20/$1.20 ≈ ¥1.4/¥8.1
        "hy-role", // 腾讯云角色扮演专用 ¥2.4/¥9.6
        "MiniMax-M2.7-highspeed", // ¥4.2/¥16.8（MiniMax 国内站人民币刊例价）
        "gemini-3.8-flash", // 存疑：官方三处查不到该 ID，$0.75/$3.75 属无来源报价（见上方告警）
        "kimi-k3" // ¥20/¥100
    )

    /**
     * 跨服务商默认生图推荐 —— 2026-09-15 二次核实后按**每张输出价**升序重排（口径同 defaultChatPresets）。
     * 逐条价格（一手，见 梳理文档 5.2 节）：
     *   `CogView-3-Flash` 免费 ｜ `qwen-image-3.0` ¥0.18/张(1K) ｜ `doubao-seedream-5-0-*` ≈¥0.22/张
     *   ｜ `wand-vega-image-flash` ¥0.45/张(1K) ｜ 其余按 token 计价或需登录，无法与"按张价"直接比较 → 置末尾
     */
    private val defaultImagePresets = listOf(
        "CogView-3-Flash", // 免费（智谱官方定价文档一手核实）
        "qwen-image-3.0", // ¥0.18/张（1K 输出价）
        "doubao-seedream-5-0-260128", // ≈¥0.22/张（按火山 lite 档；该日期 ID 对应 Pro 还是 Lite 待实测）
        "wand-vega-image-flash", // ¥0.45/张（1K）、¥0.675（2K）、¥1.008（4K）
        // ↓ 计价方式不同或价格未取到，暂置末尾
        "gpt-image-2.5-sunburst", // 按 token 计价（图像模态 $8/$30 每百万），单张成本取决于输出 token 数
        "gpt-image-2.5-flare", // 与 sunburst 同价
        "Kwai-Kolors/Kolors" // 硅基流动模型广场需登录，价格仍未取到
    )

    /**
     * 按 Base URL 推断服务商关键词。
     *
     * ⚠️ 只在**传进来的那张表**里找（2026-09-15 加固）：早先的实现取「对话表 + 生图表」的并集，
     * 于是只在生图表里的供应商（硅基流动）会被 `chatPresets` 认出来、却查不到对话清单，
     * 取 `chatByKeyword[key]!!` 直接抛 NPE 炸掉设置页（就是那条必崩）。
     * 改成"各查各表"后，**结构上就不可能出现"认得关键词却没有清单"**，
     * 也不必再靠每个调用点自己写 `containsKey` 守卫（守卫漏一处就再炸一次）。
     */
    private fun keywordFor(baseUrl: String, keys: Set<String>): String? {
        val u = baseUrl.trim().lowercase()
        keys.firstOrNull { u.contains(it) }?.let { return it }
        // ⚠️ 域名不含关键词的家必须在这里兜底，而且**必须与 `ProviderProfiles.forUrl` 的兜底同步**：
        // 2026-09-17 只修了那一处，结果"语音模型/音色"两栏在选中火山时**悄悄回落到硅基流动的清单**
        // （界面看不出报错，只是列出来的东西不对：模型显示 CosyVoice2、音色显示 …:alex）。
        // 火山有两套域名：方舟 volces.com、豆包语音 openspeech.bytedance.com。
        return if (("volces.com" in u || "openspeech.bytedance.com" in u) && "volcengine" in keys) "volcengine"
        else null
    }

    /**
     * 该 Base URL 对应的供应商关键词；**认不出就返回 null**（调用方用它区分"未知家"）。
     * 与 [keywordFor] 同一个口径，供需要"未知就另作处理"的地方使用。
     */
    fun speechProviderKeyword(baseUrl: String): String? = keywordFor(baseUrl, speechByKeyword.keys)

    /**
     * 视觉（多模态）模型清单——只收录已核实的（2026-09-13 联网核实 + 2026-09-14 复核，
     * 2026-09-16 补腾讯云混元视觉系列）。
     * 不在清单里的按纯文本模型对待。gemini-3.5-pro 已从清单移除（该模型从未发布）。
     */
    private val visionModels = setOf(
        "deepseek-flash",
        // 腾讯云 TokenHub 的混元视觉系列（2026-09-16 核实：cloud.tencent.com/document/product/1823/130078
        // 的「模型调用方式」表 + 130055 价格表的「多模态理解模型」表）。
        // ⚠️ 只收 hy-vision-2.0-instruct / youtu-vita 这两个 ID：130078 把 HY-Vision-1.5-Thinking、
        // HY-Vision-Video 映射到 hunyuan-t1-vision-20250916 / hunyuan-turbos-vision-video-20250728，
        // 而这两条正是官方迁移公告（cloud.tencent.com/announce/detail/2310）里
        // "2026-06-22 起下线"的旧 ID —— 两份官方文档互相矛盾，**存疑不收录**，等下次复核。
        "hy-vision-2.0-instruct", // HY-Vision-2.0-Instruct，单图（不支持视频），¥7.5/¥17.5
        "youtu-vita", // YT-VITA（优图），支持一次多图，¥1.2/¥3.5
        "GLM-4.6V-Flash", "GLM-5.3-Flash", "GLM-5V-Turbo",
        "kimi-k3", "kimi-k2.6",
        "qwen3.8-flash", "qwen3.8-max-0902", // 千问：2026-09-14 核实两款对话模型均支持视觉理解
        "MiniMax-M3", // 2026-09-14 用户实测支持识图
        "gemini-3.8-flash",
        "gpt-5.6-luna", "gpt-6-astra",
        "doubao-seed-character-260628",
        "doubao-seed-2-1-turbo-260628",
        "doubao-seed-2-1-pro-260915",
        // 点点 dots3-note-prev（2026-09-18 官方文档核录：图片输入格式与 OpenAI 一致 image_url{url,detail}）
        "dots3-note-prev"
    )

    /** 该模型是否支持视觉输入（设置页模型下拉中标注 👁） */
    fun isVisionModel(model: String): Boolean =
        visionModels.any { it.equals(model.trim(), ignoreCase = true) }

    /**
     * 一次只吃**一张**图的视觉模型。
     *
     * 腾讯官方对 HY-Vision 系列写明「HY-Vision 一次仅可传入单张图片」（要多图得用 `youtu-vita`）。
     * 命中时请求里只保留最近一张图，更早的图退化成纯文本——否则每轮都会把历史里的每张图
     * 重新 base64 上传一遍（一张 1MB 的 PNG 就是 1.2MB 请求体）。
     */
    private val singleImageModels = setOf("hy-vision-2.0-instruct")

    fun singleImageOnly(model: String): Boolean =
        singleImageModels.any { it.equals(model.trim(), ignoreCase = true) }

    /**
     * 主流服务商 Base URL 预设 —— 直接来自 [ProviderProfiles]（单一事实来源），
     * 避免"画像里加了供应商、设置页胶囊里却没有"这类不一致。
     */
    fun baseUrlPresets(): List<String> = ProviderProfiles.chatProviderUrls()

    fun imageBaseUrlPresets(): List<String> = ProviderProfiles.imageProviderUrls()

    /** 跟随模式用：既能对话又能生图的供应商（不列没有生图接口的家） */
    fun imageCapableChatPresets(): List<String> = ProviderProfiles.chatUrlsWithImage()

    /** API 配置用：**任何一侧有能力的**供应商都要能管理（对话、生图、或两者都有），去重保序 */
    fun allProviderPresets(): List<String> =
        (ProviderProfiles.chatProviderUrls() + ProviderProfiles.imageProviderUrls()).distinct()

    /**
     * 三家清单的取用口：都**不抛异常**，匹配不到就回落到跨服务商默认清单。
     * 设置页在**组合期**就会调它们（用户每点一次顶部栏目、每敲一个键都可能触发），
     * 所以这里必须是"全函数"——任何输入（空串、乱填的 URL、只在别家表里出现的关键词）都要有结果。
     */
    fun chatPresets(baseUrl: String): List<String> =
        chatByKeyword[keywordFor(baseUrl, chatByKeyword.keys)] ?: defaultChatPresets

    fun creationPresets(baseUrl: String): List<String> =
        creationByKeyword[keywordFor(baseUrl, creationByKeyword.keys)] ?: chatPresets(baseUrl)

    fun imagePresets(baseUrl: String): List<String> =
        imageByKeyword[keywordFor(baseUrl, imageByKeyword.keys)] ?: defaultImagePresets

    // ────────────────────── 语音合成（TTS v2，OpenAI 兼容 /audio/speech）──────────────────────
    /**
     * 各家的语音合成模型清单。**只收一手核实过的**：
     * - 硅基流动：官方文档列的 TTS 模型（CosyVoice2 已见价格 ¥0.05/千字符，其余同档）；
     * - OpenAI：官方 API 参考的三款；
     * 火山豆包语音 / 阿里百炼 CosyVoice / MiniMax / 智谱**都不是 OpenAI 形状**（或未核实），
     * 所以不在这里预设 —— 想用它们请走「自定义供应商」，把兼容地址填进去。
     */
    private val speechByKeyword: Map<String, List<String>> = mapOf(
        // 2026-09-16 控制台实测（登录后看「语音」筛选的清单）：IndexTeam/IndexTTS-2 标注 **Deprecated**、
        // fishaudio/fish-speech-1.5 已不在列表（用户实测这两个都报错）⇒ 只留现存的两个。
        // ⚠️ 同一份清单里的 XingChenASR / Qwen3-ASR / SenseVoice 是**语音识别（ASR）**，不是合成，别放进来。
        "siliconflow" to listOf(
            "FunAudioLLM/CosyVoice2-0.5B",
            "fnlp/MOSS-TTSD-v0.5"
        ),
        "openai" to listOf("gpt-4o-mini-tts", "tts-1", "tts-1-hd"),
        // MiniMax 原生 /t2a_v2（协议见 ProviderProfiles.SpeechProtocol.MINIMAX）。
        // ⚠️ 模型名取自公开资料、**未经一手文档确认**（官方文档站从本机反复超时），首次请用「试听」验证；
        // 音色（voice_id）**故意不预设**：MiniMax 有 80+ 个音色、id 形如 male-qn-qingse，
        // 与其塞一堆可能点不通的，不如让用户从控制台复制粘贴一个。
        "minimax" to listOf("speech-2.6-hd", "speech-2.6-turbo"),
        // 阿里百炼原生 SpeechSynthesizer（2026-09-17 一手核实官方《非实时语音合成 HTTP API 参考》的 model 取值）。
        // **只列官方音色列表页单列过的那三款**：cosyvoice-v2 / v3-flash / v3-plus；
        // 官方另有两款 `cosyvoice-v3.5-flash/plus`，但**音色列表页没有它们的音色表**（该页只列 v3-flash/v3-plus/v2/v1），
        // 怕用户选到"有模型没音色"的组合就一选一 400，故不预设（想用请手填模型名 + 从控制台复制音色 id）。
        // cosyvoice-v2 排第一：它是音色最全、最稳的一档，选完供应商自动落到它身上。
        "dashscope" to listOf(
            "cosyvoice-v2",
            "cosyvoice-v3-flash",
            "cosyvoice-v3-plus",
            // 2026-09-17 用户要求补：官方《非实时语音合成 Qwen-Audio-TTS/CosyVoice HTTP API 参考》里，
            // `qwen-audio-3.0-tts-plus/flash` 与 CosyVoice 系列**同一个 SpeechSynthesizer 端点**、
            // 只是 model 取值不同（注意别和"Qwen-TTS"混了 —— 那是另一个产品、另一套端点，
            // 见早期的音色调研记录，那条说的是 Qwen-TTS 不是 Qwen-Audio-TTS）。
            // 音色表见下方 DASHSCOPE_VOICES 里的同名键（与 CosyVoice 那张表不通用）。
            "qwen-audio-3.0-tts-flash",
            "qwen-audio-3.0-tts-plus"
        ),
        // 火山豆包语音（VOLC 协议）：V3 的"模型"就是 **Resource-Id**（哪一代合成引擎）。
        // ⚠️ 2026-09-17 用户决定「**复刻先不做**」⇒ 不再预设 `seed-icl-2.0`（声音复刻那一档）。
        // 代码里仍保留"粘了 `S_`/`icl_` 开头音色就自动判成复刻资源"的兜底（免得将来手动粘一个就报 mismatch），
        // 但预设、界面文案都不再引导用户去用它。
        "volcengine" to listOf("seed-tts-2.0", "seed-tts-1.0"),
        // 腾讯云把"模型"当 **ModelType 档位**用（3 大模型音色 / 2 精品 / 1 基础，官方口径），
        // 所以这一栏填的是数字；音色（VoiceType）也是数字，见 [speechVoices] 的说明。
        "tencentcloud" to listOf("3", "2", "1")
    )
    private val defaultSpeechPresets: List<String> = speechByKeyword.getValue("siliconflow")

    // ────────────────────── 语音识别（ASR / 语音输入，Step 3）──────────────────────
    /**
     * 各家的**语音识别**模型清单（`POST /audio/transcriptions`，multipart）。
     *
     * 与合成那份刻意分开：硅基流动同一份控制台清单里，合成与识别是两组模型，
     * 混在一起会 400（2026-09-16 用户就误把 ASR 的模型名当 TTS 报错过）。
     * - 硅基流动：官方文档只列了 SenseVoiceSmall / TeleAI/TeleSpeechASR 两个；
     *   星辰系列（`XingChenAGI/XingChenASR-V3.2` 等）在控制台是**免费**计费项（`free-asr-model`、¥0/千字符），
     *   但"能不能用于这个端点"**还没实测**（¥0，真机各打一次即可）⇒ 一并列出，第一次用请以实际返回为准。
     * - OpenAI：官方文档的 `whisper-1`（其余语音识别型号未一手核实，不预设）。
     */
    private val asrByKeyword: Map<String, List<String>> = mapOf(
        "siliconflow" to listOf(
            "FunAudioLLM/SenseVoiceSmall",
            "TeleAI/TeleSpeechASR",
            // 2026-09-17 用户要求补上：模型中心里 XingChenGSR-V1.0 与 XingChenASR-V3.2 同属
            // 「语音 / 语音识别」且计费 ￥0（一手：模型中心列表页）。能否用于 /audio/transcriptions 仍待实测。
            "XingChenAGI/XingChenASR-V3.2",
            "XingChenAGI/XingChenGSR-V1.0"
        ),
        "openai" to listOf("whisper-1"),
        // 火山豆包语音识别：这一栏是 **Resource-Id**（哪一档识别服务），不是模型名。
        // 极速版 `volc.bigasr.auc_turbo` = 单次请求直接返回、body 里 base64 传音频（手机端唯一可行的那档）；
        // 标准版 `volc.seedasr.auc` 只收**公网音频 URL**，本 App 用不了，故不预设。
        "volcengine" to listOf("volc.bigasr.auc_turbo")
    )

    /** 语音识别模型清单（匹配不到就回落到硅基流动那份；用户可以手填任意 id） */
    fun asrPresets(baseUrl: String): List<String> =
        asrByKeyword[keywordFor(baseUrl, asrByKeyword.keys)] ?: asrByKeyword.getValue("siliconflow")

    /**
     * 语音模型清单。**认不出的家给空清单**（自定义/本地部署自己填模型名）——
     * 早期这里回落到硅基流动那份，本地部署的用户会看到一堆点不通的对话模型名。
     */
    fun speechPresets(baseUrl: String): List<String> =
        speechByKeyword[keywordFor(baseUrl, speechByKeyword.keys)] ?: emptyList()

    /**
     * 音色清单。各家的口径：
     * - 硅基流动：8 个系统预置音色，**必须写成「模型名:音色名」**（如 FunAudioLLM/CosyVoice2-0.5B:anna）；
     *   自建/克隆音色返回的 uri 也能直接填。
     * - OpenAI：若干固定音色名，直接填 voice 字段即可。
     * - **认不出供应商时（自定义 / 本地部署）给空清单**：既不给别家的音色名，也不拼前缀 ——
     *   早期这里静默回落到硅基流动那份，本地部署的用户会看到 `本地模型:alex` 这种发给服务端必然 404 的值
     *   （标签里只显示冒号后面那段，界面上根本看不出来）。手填 id 即可。
     */
    fun speechVoices(baseUrl: String, model: String): List<String> {
        val kw = keywordFor(baseUrl, speechByKeyword.keys)
        // MiniMax：返回挑好的中文音色（见 MINIMAX_VOICES）；它是"id 即音色"，与模型无关，不用拼前缀
        if (kw == "minimax") return MINIMAX_VOICES.map { it.first }
        // 阿里百炼：**音色跟着模型版本走**（混用会 400）。手填了没预设过的模型（如 cosyvoice-v3.5-*）时
        // 按版本前缀给最接近的那张表 —— 官方音色列表页只列了 v3-flash/v3-plus/v2/v1，v3.5 系没有单列。
        if (kw == "dashscope") return dashScopeVoicesFor(model).map { it.first }
        // 火山与腾讯云的音色：腾讯那份是官方文档公开表（已抓全），火山那份是从官方音色列表实抓的 2.0 那批。
        // 腾讯：**按 ModelType 档位过滤**（档位=3 只列大模型音色、=2 只列精品），这样"档位"和"音色"两栏
        // 永远是一致的一组；双方互相跟随（选音色会自动改档位，见 VoiceScreen）
        if (kw == "tencentcloud") {
            val all = TENCENT_VOICES
            val picked = model.trim().let { if (it in listOf("1", "2", "3")) all.filter { v -> v.modelType == it } else all }
            // ⚠️ 同档没有预设音色时**给空清单**，不能 `ifEmpty { all }` ——
            // 那会让基础档（1）列出大模型音色，用户一选就是"档位与音色不匹配"
            return picked.map { it.id }
        }
        // 火山不做档位过滤：官方音色 id 自带家族、Resource-Id 会跟着音色走，过滤只会把列表弄空
        // （**但音色列表要按档位分**：2.0 / 1.0 / 复刻三档各有各的音色池）
        if (kw == "volcengine") return volcVoicesFor(model).map { it.first }
        val names = when (kw) {
            "openai" -> OPENAI_VOICES
            "siliconflow" -> SILICONFLOW_VOICE_NAMES
            // 认不出的家（自定义 / 本地部署）：不给预设，也不拼前缀（见函数头注释）
            else -> return emptyList()
        }
        // 硅基流动要求「模型名:音色名」，模型为空时先只显示音色名（用户选完模型会自动补全）
        return if (kw == "openai" || model.isBlank()) names else names.map { "$model:$it" }
    }

    /** 阿里某模型对应的音色表（精确匹配优先，其次按版本前缀兜底，最后回落到 v2） */
    private fun dashScopeVoicesFor(model: String): List<Pair<String, String>> {
        val m = model.trim().lowercase()
        DASHSCOPE_VOICES[m]?.let { return it }
        // Qwen-Audio-TTS 那一档与 CosyVoice 音色**不通用**（官方各一张表）：没预设到的 qwen 模型给空表，
        // 让用户从官方《Qwen-Audio-TTS音色列表》复制，别拿 CosyVoice 的音色去撞 400
        if ("qwen" in m) return emptyList()
        return when {
            // ⚠️ 用 `[...] ?: emptyList()` 而不是 `getValue(...)`：后者在键名被改动时会抛
            // NoSuchElementException，而这里是在**组合期**被调用的（一崩就是整页设置打不开）
            "v3.5" in m || "v3" in m -> DASHSCOPE_VOICES["cosyvoice-v3-flash"].orEmpty()
            else -> DASHSCOPE_VOICES["cosyvoice-v2"].orEmpty()
        }
    }

    /** 硅基流动的 8 个系统预置音色（男 4 / 女 4，取自官方文档） */
    val SILICONFLOW_VOICE_NAMES = listOf("alex", "benjamin", "charles", "david", "anna", "bella", "claire", "diana")

    /**
     * 腾讯云语音音色（2026-09-17 用浏览器从官方《语音合成 音色列表》页整页抓下来的，**不需要登录**）。
     *
     * **音色与 ModelType 档位绑定**（官方口径：1 基础 / 2 精品 / 3 大模型），这正是最容易配错的地方 ——
     * 所以这里把档位跟 id 打包在一起（[TencentVoice]），选音色时界面会自动把档位改对（见 VoiceScreen）。
     * - 「超自然大模型音色」（50xxxx/60xxxx）：最新一档，聊天类音色最多、最像人；
     * - 「大模型音色」（501xxx/601xxx）：同为大模型档；
     * - 「精品音色」（101xxx）：老一档但胜在通用音色齐、单价低（如 101001 智瑜）。
     * 童声、外语（WeJack/WeJames 等）、营销、催收类一律不收（本 App 是中文 RP 场景）。
     * ⚠️ 「基础」档（ModelType=1）的音色官方没列在这张表里 —— 真要用就手填 id 并把档位改成 1。
     */
    data class TencentVoice(val id: String, val label: String, val modelType: String)

    val TENCENT_VOICES: List<TencentVoice> = listOf(
        // 超自然大模型音色（档位 3）
        TencentVoice("603006", "沉稳青叔（聊天男声·超自然）", "3"),
        TencentVoice("603005", "知心大林（聊天男声·超自然）", "3"),
        TencentVoice("603003", "随和老李（聊天男声·超自然）", "3"),
        TencentVoice("602004", "暖心阿灿（聊天男声·超自然）", "3"),
        TencentVoice("603000", "懂事少年（特色男声·超自然）", "3"),
        TencentVoice("502006", "智小悟（聊天男声·超自然）", "3"),
        TencentVoice("603007", "邻家女孩（聊天女声·超自然）", "3"),
        TencentVoice("603004", "温柔小柠（聊天女声·超自然）", "3"),
        TencentVoice("602005", "专业梓欣（聊天女声·超自然）", "3"),
        TencentVoice("602003", "爱小悠（聊天女声·超自然）", "3"),
        TencentVoice("502003", "智小敏（聊天女声·超自然）", "3"),
        TencentVoice("502001", "智小柔（聊天女声·超自然）", "3"),
        TencentVoice("603001", "潇湘妹妹（特色女声·超自然）", "3"),
        // 大模型音色（档位 3）
        TencentVoice("501006", "千嶂（聊天男声·大模型）", "3"),
        TencentVoice("501005", "飞镜（聊天男声·大模型）", "3"),
        TencentVoice("501007", "浅草（聊天男声·大模型）", "3"),
        TencentVoice("601008", "爱小豪（聊天男声·大模型）", "3"),
        TencentVoice("601011", "爱小川（聊天男声·大模型）", "3"),
        TencentVoice("601014", "爱小简（聊天男声·大模型）", "3"),
        TencentVoice("501004", "月华（聊天女声·大模型）", "3"),
        TencentVoice("601009", "爱小芊（聊天女声·大模型）", "3"),
        TencentVoice("601010", "爱小娇（聊天女声·大模型）", "3"),
        TencentVoice("601012", "爱小璟（特色女声·大模型）", "3"),
        // 精品音色（档位 2）：通用/情感向，价格比大模型档低
        TencentVoice("101001", "智瑜（情感女声·精品）", "2"),
        TencentVoice("101026", "智希（通用女声·精品）", "2"),
        TencentVoice("101027", "智梅（通用女声·精品）", "2"),
        TencentVoice("101055", "智付（通用女声·精品）", "2"),
        TencentVoice("101019", "智彤（粤语女声·精品）", "2"),
        TencentVoice("101004", "智云（通用男声·精品）", "2"),
        TencentVoice("101030", "智柯（通用男声·精品）", "2"),
        TencentVoice("101054", "智友（通用男声·精品）", "2")
    )

    /**
     * 火山 **1.0 音色**（2026-09-17 从官方《音色列表》1.0 表里抓的：
     * `*_moon_bigtts` / `*_mars_bigtts` / `*_conversation_wvae_bigtts`，共 102 条，这里挑常用的 27 条）。
     *
     * **两个用途**：① 「语音模型」选 `seed-tts-1.0` 时的音色预设；② **混合音色的可混源** ——
     * 官方口径：混音的源只支持 1.0 官方音色与复刻音色（`S_`），**2.0（`*_uranus_bigtts`）不支持**。
     */
    val VOLC_VOICES_1_0: List<Pair<String, String>> = listOf(
        // 女声
        "zh_female_gaolengyujie_moon_bigtts" to "高冷御姐 1.0",
        "zh_female_sajiaonvyou_moon_bigtts" to "撒娇女友 1.0",
        "zh_female_yuanqinvyou_moon_bigtts" to "元气女友 1.0",
        "zh_female_meilinvyou_moon_bigtts" to "魅力女友 1.0",
        "zh_female_cancan_mars_bigtts" to "知性灿灿 1.0",
        "zh_female_tianmeitaozi_mars_bigtts" to "甜美桃子 1.0",
        "zh_female_tianmeixiaoyuan_moon_bigtts" to "甜美小源 1.0",
        "zh_female_qingxinnvsheng_mars_bigtts" to "清新女声 1.0",
        "zh_female_zhixingnvsheng_mars_bigtts" to "知性女声 1.0",
        "zh_female_shuangkuaisisi_moon_bigtts" to "爽快思思 1.0",
        "zh_female_linjianvhai_moon_bigtts" to "邻家女孩 1.0",
        "zh_female_kailangjiejie_moon_bigtts" to "开朗姐姐 1.0",
        "zh_female_meituojieer_moon_bigtts" to "美托姐儿 1.0",
        "zh_female_xinlingjitang_moon_bigtts" to "心灵鸡汤 1.0",
        "zh_female_tianmeiyueyue_moon_bigtts" to "甜美悦悦 1.0",
        "zh_female_qingchezizi_moon_bigtts" to "清澈梓梓 1.0",
        "zh_female_popo_mars_bigtts" to "婆婆 1.0",
        // 男声
        "zh_male_aojiaobazong_moon_bigtts" to "傲娇霸总 1.0",
        "zh_male_qingshuangnanda_mars_bigtts" to "清爽男大 1.0",
        "zh_male_yangguangqingnian_moon_bigtts" to "阳光青年 1.0",
        "zh_male_yuanboxiaoshu_moon_bigtts" to "渊博小叔 1.0",
        "zh_male_wennuanahu_moon_bigtts" to "温暖阿虎 1.0",
        "zh_male_dongfanghaoran_moon_bigtts" to "东方浩然 1.0",
        "zh_male_shenyeboke_moon_bigtts" to "深夜播客 1.0",
        "zh_male_jieshuoxiaoming_moon_bigtts" to "解说小明 1.0",
        "zh_male_shaonianzixin_moon_bigtts" to "少年梓辛 1.0",
        "zh_male_xudong_conversation_wvae_bigtts" to "徐东（对话）1.0"
    )

    /**
     * 火山的**混合音色可选源（全量中文 1.0 音色）**，与上面那份"精简预设"分开：
     * - [VOLC_VOICES_1_0]（27 条）给「语音模型 = seed-tts-1.0」时的**音色栏**用（选音色要短平快）；
     * - 这份（约 60 条）给**混合音色的源**用 —— 用户明说"混音可选的要多一点"，
     *   源越多越能调出想要的组合（官方 1.0 中文音色基本都在这了）。
     * 标签取自官方《音色列表》的名称列（**个别行官方排版有偏移**，以音色 id 为准；
     * 拿不准就直接试听 —— 反正混音本来就要试）。
     */
    val VOLC_MIX_SOURCES: List<Pair<String, String>> = listOf(
        "zh_female_gaolengyujie_moon_bigtts" to "高冷御姐",
        "zh_female_sajiaonvyou_moon_bigtts" to "撒娇女友",
        "zh_female_yuanqinvyou_moon_bigtts" to "元气女友",
        "zh_female_meilinvyou_moon_bigtts" to "魅力女友",
        "zh_female_roumeinvyou_emo_v2_mars_bigtts" to "柔美女友（多情感）",
        "zh_male_junlangnanyou_emo_v2_mars_bigtts" to "俊朗男友（多情感）",
        "zh_male_yourougongzi_emo_v2_mars_bigtts" to "优柔公子（多情感）",
        "zh_male_ruyayichen_emo_v2_mars_bigtts" to "儒雅男友（多情感）",
        "zh_male_aojiaobazong_moon_bigtts" to "傲娇霸总",
        "zh_male_qingshuangnanda_mars_bigtts" to "清爽男大",
        "zh_male_yangguangqingnian_moon_bigtts" to "阳光青年",
        "zh_male_yangguangqingnian_emo_v2_mars_bigtts" to "阳光青年（多情感）",
        "zh_male_yuanboxiaoshu_moon_bigtts" to "渊博小叔",
        "zh_male_wennuanahu_moon_bigtts" to "温暖阿虎",
        "zh_male_dongfanghaoran_moon_bigtts" to "东方浩然",
        "zh_male_shenyeboke_moon_bigtts" to "深夜播客",
        "zh_male_shenyeboke_emo_v2_mars_bigtts" to "深夜播客（多情感）",
        "zh_male_jieshuoxiaoming_moon_bigtts" to "解说小明",
        "zh_male_shaonianzixin_moon_bigtts" to "少年梓辛",
        "zh_male_qingyiyuxuan_mars_bigtts" to "青逸雨轩",
        "zh_male_xudong_conversation_wvae_bigtts" to "快乐小东",
        "zh_male_lengkugege_emo_v2_mars_bigtts" to "冷酷哥哥（多情感）",
        "zh_male_beijingxiaoye_emo_v2_mars_bigtts" to "北京小爷（多情感）",
        "zh_male_beijingxiaoye_moon_bigtts" to "北京小爷",
        "zh_male_jingqiangkanye_emo_mars_bigtts" to "京腔侃爷（多情感）",
        "zh_male_jingqiangkanye_moon_bigtts" to "京腔侃爷",
        "zh_male_guangzhoudege_emo_mars_bigtts" to "广州德哥（多情感）",
        "zh_male_guozhoudege_moon_bigtts" to "广州德哥",
        "zh_male_wenrouxiaoge_mars_bigtts" to "温柔小哥",
        "zh_male_haoyuxiaoge_moon_bigtts" to "浩宇小哥",
        "zh_male_yuzhouzixuan_moon_bigtts" to "豫州子轩",
        "zh_male_guangxiyuanzhou_moon_bigtts" to "广西远舟",
        "zh_male_zhoujielun_emo_v2_mars_bigtts" to "双节棍小哥",
        "zh_male_ruyaqingnian_mars_bigtts" to "儒雅青年",
        "zh_male_baqiqingshu_mars_bigtts" to "霸气青叔",
        "zh_male_fanjuanqingnian_mars_bigtts" to "反卷青年",
        "zh_male_qingcang_mars_bigtts" to "擎苍",
        "zh_male_silang_mars_bigtts" to "四郎",
        "zh_male_xionger_mars_bigtts" to "熊二",
        "zh_male_lanxiaoyang_mars_bigtts" to "懒音绵宝",
        "zh_male_dongmanhaimian_mars_bigtts" to "亮嗓萌仔",
        "zh_male_changtianyi_mars_bigtts" to "悬疑解说",
        "zh_male_sunwukong_mars_bigtts" to "猴哥",
        "zh_male_naiqimengwa_mars_bigtts" to "奶气萌娃",
        "zh_male_tiancaitongsheng_mars_bigtts" to "天才童声",
        "zh_female_cancan_mars_bigtts" to "灿灿",
        "zh_female_shuangkuaisisi_moon_bigtts" to "爽快思思",
        "zh_female_shuangkuaisisi_emo_v2_mars_bigtts" to "爽快思思（多情感）",
        "zh_female_tianmeitaozi_mars_bigtts" to "甜美桃子",
        "zh_female_tianmeixiaoyuan_moon_bigtts" to "甜美小源",
        "zh_female_tianmeiyueyue_moon_bigtts" to "甜美悦悦",
        "zh_female_tianxinxiaomei_emo_v2_mars_bigtts" to "甜心小美（多情感）",
        "zh_female_qingxinnvsheng_mars_bigtts" to "清新女声",
        "zh_female_qingchezizi_moon_bigtts" to "清澈梓梓",
        "zh_female_zhixingnvsheng_mars_bigtts" to "知性女声",
        "zh_female_qinqienvsheng_moon_bigtts" to "亲切女声",
        "zh_female_linjianvhai_moon_bigtts" to "邻家女孩",
        "zh_female_kailangjiejie_moon_bigtts" to "开朗姐姐",
        "zh_female_meituojieer_moon_bigtts" to "妹坨洁儿",
        "zh_female_xinlingjitang_moon_bigtts" to "心灵鸡汤",
        "zh_female_daimengchuanmei_moon_bigtts" to "呆萌川妹",
        "zh_female_wanwanxiaohe_moon_bigtts" to "湾湾小何",
        "zh_female_wanqudashu_moon_bigtts" to "湾区大叔",
        "zh_female_linjuayi_emo_v2_mars_bigtts" to "邻居阿姨（多情感）",
        "zh_female_wenrouxiaoya_moon_bigtts" to "温柔小雅",
        "zh_female_wenroushunv_mars_bigtts" to "温柔淑女",
        "zh_female_qiaopinvsheng_mars_bigtts" to "俏皮女声",
        "zh_female_mengyatou_mars_bigtts" to "萌丫头",
        "zh_female_tiexinnvsheng_mars_bigtts" to "贴心女声",
        "zh_female_jitangmeimei_mars_bigtts" to "鸡汤妹妹",
        "zh_female_popoo_mars_bigtts" to "婆婆",
        "zh_female_gufengshaoyu_mars_bigtts" to "古风少御",
        "zh_female_wuzetian_mars_bigtts" to "武则天",
        "zh_female_gujie_mars_bigtts" to "顾姐",
        "zh_female_yingtaowanzi_mars_bigtts" to "樱桃丸子",
        "zh_female_peiqi_mars_bigtts" to "佩奇猪",
        "zh_female_shaoergushi_mars_bigtts" to "少儿故事",
        "zh_female_yueyunv_mars_bigtts" to "粤语小溏",
        "zh_male_guangzhoudege_emo_v2_mars_bigtts" to "广州德哥（多情感·备用 id）",
        "zh_female_yingyujiaoyu_mars_bigtts" to "Tina老师",
        "zh_female_maomao_conversation_wvae_bigtts" to "文静毛毛",
        "zh_male_M100_conversation_wvae_bigtts" to "悠悠君子"
    )

    /** 混合音色的可选源（用户要求"混音可选的要多一点"⇒ 给全量中文 1.0 音色） */
    fun volcMixSourceVoices(): List<String> = VOLC_MIX_SOURCES.map { it.first }

    /** 可混音色的可读标签（先查全量源表，再查精简预设表） */
    fun volcMixVoiceLabel(voice: String): String =
        VOLC_MIX_SOURCES.firstOrNull { it.first == voice.trim() }?.second
            ?: VOLC_VOICES_1_0.firstOrNull { it.first == voice.trim() }?.second
            ?: voice

    /**
     * 火山「音色」列表**按 Resource-Id 分档**（三档各有各的音色池，混用会报 resource mismatch）：
     * - `seed-tts-2.0`（或留空）→ 2.0 音色（`*_uranus_bigtts`，[VOLC_VOICES]）；
     * - `seed-tts-1.0` → 1.0 音色（`*_moon/_mars/_conversation_wvae_bigtts`，[VOLC_VOICES_1_0]）；
     * - `seed-icl-2.0` → 复刻音色，**不预设**（每个人克隆出来的 `S_xxx` 都不一样，手填/粘贴）。
     */
    fun volcVoicesFor(model: String): List<Pair<String, String>> {
        val m = model.trim().lowercase()
        return when {
            "icl" in m -> emptyList()
            "1.0" in m -> VOLC_VOICES_1_0
            else -> VOLC_VOICES
        }
    }

    /** 某个腾讯音色需要的 ModelType 档位（选音色时自动配对，避免"音色与档位不匹配"的报错） */
    fun tencentModelTypeFor(voice: String): String? =
        TENCENT_VOICES.firstOrNull { it.id == voice.trim() }?.modelType

    /**
     * 载入存档时的**错配自愈**：模型/音色栏里存着"别家格式"的值时，换成本家的一套。
     *
     * 为什么需要：换供应商时若没重选模型/音色，会把上一家的值留在表单里（甚至被保存），
     * 下次进设置页照样显示 —— 用户看到的就是"火山选中的状态下模型写着 `FunAudioLLM/CosyVoice2-0.5B`"
     * 这种一眼就不对、但界面不报错的状态（2026-09-17 踩到，起因是域名映射漏一处导致自动填充回落到硅基流动）。
     *
     * 口径：**只改"能确定是错"的**（值不符合本家格式，或本家必填却为空）；用户手填的合法值一律不动。
     * 返回 null 表示无需修正。
     */
    fun healSpeechSelection(
        baseUrl: String,
        model: String,
        voice: String,
        cloneIds: Collection<String> = emptyList()
    ): Pair<String, String>? {
        val kw = keywordFor(baseUrl, speechByKeyword.keys) ?: return null
        var m = model.trim()
        var v = voice.trim()
        var changed = false

        fun set(nm: String, nv: String) {
            if (nm != m || nv != v) {
                m = nm
                v = nv
                changed = true
            }
        }

        when (kw) {
            // 火山：模型必须是 seed-* 三档（别家的模型名在这里一定不通）；音色必须是 *_bigtts 或复刻的 S_/icl_*
            "volcengine" -> {
                // 空模型要按**音色**推 Resource-Id（写死 2.0 会把 1.0 音色 / 复刻音色配成不匹配的组合）；
                // ⚠ 推不出来（这个音色不是火山的）时**不要**拿空串覆盖模型栏 —— 交下面那条"音色不合法"
                // 的分支换成本家预设音色（旧版在这里无脑猜 2.0，只会把错配推给服务端报一句看不懂的话）
                val res = volcResourceIdForVoice(v, cloneIds)
                if (res.isNotBlank() && res != m) set(res, v)
                if (v.isEmpty() || !isVolcVoiceId(v, cloneIds)) {
                    set(m, VOLC_VOICES.firstOrNull()?.first.orEmpty())
                }
                // 音色刚刚被换成本家预设、模型栏还空着（原来音色也是空的）⇒ 按新音色补齐
                if (m.isEmpty()) set(volcResourceIdForVoice(v, cloneIds), v)
            }
            // 腾讯：模型是 1/2/3 档位；音色是纯数字
            "tencentcloud" -> {
                if (m.isEmpty()) set("3", v)
                if (m !in listOf("1", "2", "3")) set(tencentModelTypeFor(v) ?: "3", v)
                if (v.isEmpty() || v.toIntOrNull() == null) {
                    // **只找同档音色**：找不到（例如基础档 1 没有预设音色）就**留空**，
                    // 由界面提示"手填 id 并把档位改对"，绝不塞一个别档的音色（那会必然不匹配）
                    val same = TENCENT_VOICES.firstOrNull { it.modelType == m }
                    if (same != null) set(m, same.id) else set(m, "")
                }
            }
            // 阿里：模型是 cosyvoice-* / qwen-*；音色**不能**带冒号（那是硅基流动的"模型:音色"写法）
            "dashscope" -> {
                if (m.isEmpty() || !(m.startsWith("cosyvoice") || m.startsWith("qwen"))) {
                    set(speechPresets(baseUrl).firstOrNull().orEmpty(), v)
                }
                if (v.contains(":")) set(m, dashScopeVoicesFor(m).firstOrNull()?.first.orEmpty())
            }
            else -> return null
        }
        return if (changed) m to v else null
    }

    /**
     * 火山音色 id 是否合法（官方 1.0/2.0 都是 `*_bigtts`；复刻音色官方有 `S_` 与 `icl_` 两种前缀）。
     * [cloneIds] ＝本机复刻音色库：录音复刻生成的 id 没有前缀特征，只认清单（见 `CloneVoice`）。
     *
     * **也是"这个音色到底属不属于火山"的判据**（合成前的一致性校验用它）：硅基流动那种
     * `模型:音色` 的冒号 id、腾讯的数字 id 到这里都是 false —— 它们被打到火山必报
     * `resource ID is mismatched with speaker`，而那句话说不出"音色填错了家"。
     */
    fun isVolcVoiceId(voice: String, cloneIds: Collection<String> = emptyList()): Boolean {
        val v = voice.trim()
        return v.contains("_bigtts") || v.startsWith("S_") || v.startsWith("icl_") ||
            (v.isNotEmpty() && cloneIds.any { it.trim() == v })
    }

    /**
     * 「语音合成模型」那一栏的**可读标签**（下拉里显示，字段值仍是原始 id/数字）。
     * 这两栏在火山/腾讯是技术参数（Resource-Id / ModelType），不写人话没人知道该选哪个。
     */
    fun speechModelLabel(baseUrl: String, model: String): String = when (keywordFor(baseUrl, speechByKeyword.keys) ?: keywordFor(baseUrl, asrByKeyword.keys)) {
        "volcengine" -> when (model.trim()) {
            "seed-tts-2.0" -> "seed-tts-2.0（官方音色 2.0 · 推荐）"
            "seed-tts-1.0" -> "seed-tts-1.0（官方音色 1.0）"
            "seed-icl-2.0" -> "seed-icl-2.0（声音复刻音色）"
            else -> model
        }
        "tencentcloud" -> when (model.trim()) {
            // ⚠️ 官方《TextToVoice》只写了 "ModelType：1-默认模型"；2/3 的档位口径来自社区资料，
            // 其中 3 在实测里被服务端接受过（回的是资源包额度错误，不是参数错）⇒ 标签里对 2/3 保持"档位"说法
            "3" -> "3（大模型音色档）"
            "2" -> "2（精品音色档）"
            "1" -> "1（默认模型，兼容旧音色）"
            else -> model
        }
        else -> model
    }

    /**
     * 火山某个音色该配的 **Resource-Id**（V3 用；选音色时自动填进「语音模型」那栏）。
     * 官方要求两者匹配，不匹配会报 `resource ID is mismatched with speaker`。
     *
     * [cloneIds] ＝本机复刻音色库（录音复刻生成的 id 没有前缀特征，只能查清单，见 `CloneVoice`）。
     * 漏了它，复刻出来的音色会被当成 2.0 官方音色发 `seed-tts-2.0` —— 合成必然 mismatch。
     */
    fun volcResourceIdForVoice(voice: String, cloneIds: Collection<String> = emptyList()): String {
        val v = voice.trim()
        return when {
            v.isEmpty() -> ""
            // 复刻音色官方有 `S_`（自建音色）与 `icl_`（查询接口返回）两种前缀，都算复刻
            v.startsWith("S_") || v.startsWith("icl_") -> "seed-icl-2.0"
            cloneIds.any { it.trim() == v } -> "seed-icl-2.0"
            "_uranus_bigtts" in v || "saturn" in v -> "seed-tts-2.0"
            "_moon_bigtts" in v || "_mars_bigtts" in v || "_conversation_wvae_bigtts" in v -> "seed-tts-1.0"
            // 是火山家族、但认不出具体代次（官方 2.0 音色命名都是 `*_uranus_bigtts`）：按 2.0 试
            "_bigtts" in v -> "seed-tts-2.0"
            // ⚠ **不是火山的音色一律不猜**（2026-09-26 台账 62 修法）：旧版在这里无脑回 2.0，
            // 于是硅基流动的 `模型:音色`、腾讯的数字 id 被打成火山 2.0 资源 ⇒ 服务端必报
            // `55000000 resource ID is mismatched with speaker`，而那句报错完全看不出"音色填错了家"。
            // 返回空串＝"判不出这一档"，由调用方当场说清（见 `AiClient.volcV3Speech` 的校验与
            // `healSpeechSelection` 的"换成本家预设音色"分支）。
            else -> ""
        }
    }

    /**
     * 点选「声音类型」胶囊时，「语音合成模型」那一栏该变成什么。
     *
     * 三档各有自己的音色池，混用必报 `resource ID is mismatched with speaker`：
     * 混合 → 1.0 或 ICL（看混音源）、复刻 → ICL、单一 → 按音色回推。
     * 规则写在这一处，设置页与角色编辑器共用——两边各写一份迟早走偏（这正是"打开混音第一次试听必失败"的老坑）。
     *
     * ⚠ **混音那一档要看源**（2026-09-22 真机点查发现）：全为复刻源时该用 `seed-icl-2.0`，
     * 否则 1.0 —— 与 `AiClient.volcV3Speech` 的自动判定同一条口径。不这么写，"复刻 → 混合"
     * 会留下 ICL 档、界面上显示"声音复刻音色"而实际混的是 1.0 音色（档位显示与实际不符）。
     * [cloneIds] ＝本机复刻音色库，混音的源与单一音色都要用它判（见 `CloneVoice`）。
     */
    fun volcModelForKind(
        kind: CharacterVoices.VoiceKind,
        voice: String,
        model: String,
        mixSources: List<MixSpeaker> = emptyList(),
        cloneIds: Collection<String> = emptyList()
    ): String {
        val m = model.trim()
        return when (kind) {
            CharacterVoices.VoiceKind.MIX -> {
                val src = mixSources.filter { it.voice.isNotBlank() }
                val allCloned = src.isNotEmpty() && src.all {
                    it.voice.trim().startsWith("S_") || it.voice.trim().startsWith("icl_") ||
                        cloneIds.any { c -> c.trim() == it.voice.trim() }
                }
                val right = if (allCloned) "seed-icl-2.0" else "seed-tts-1.0"
                // 只纠正"档位的默认值"（2.0 的默认 / 空 / 另一档的默认）；用户手填的其它值不动
                if (m == "seed-tts-2.0" || m.isBlank() || m == "seed-icl-2.0") right else m
            }
            CharacterVoices.VoiceKind.CLONE -> "seed-icl-2.0"
            // 从混音 / 复刻切回单一：按音色回推；音色栏还空着（认不出来）就保持原样，不猜
            CharacterVoices.VoiceKind.SINGLE ->
                if (isVolcVoiceId(voice, cloneIds)) volcResourceIdForVoice(voice, cloneIds) else m
        }
    }

    /**
     * 点选「混合音色」时的起手源：不足 2 个就补两条预设，已经够就不动
     * （用户可能只是想把类型标成混合，不是要重建列表）。
     */
    fun volcMixSeedSources(existing: List<MixSpeaker>): List<MixSpeaker> {
        if (existing.count { it.voice.isNotBlank() } >= 2) return existing
        val presets = volcMixSourceVoices()
        return listOf(
            MixSpeaker(presets.getOrElse(0) { "" }, 0.5f),
            MixSpeaker(presets.getOrElse(1) { "" }, 0.5f)
        )
    }

    /**
     * 火山豆包语音的音色（2026-09-17 从官方《音色列表》整页抓下来：2.0 系列近 100 条中文音色，
     * 外加 1.0/外语表；这里挑的是**贴合角色扮演 + 常用聊天**的那批，男女/年龄/风格都覆盖到）。
     *
     * 全部是 2.0 系列（`*_uranus_bigtts`）⇒ V3 的 `X-Api-Resource-Id` 取 `seed-tts-2.0`
     * （代码按音色 id 自动判，也可在「语音模型」那栏手选覆盖）。
     * 标签后的括号是官方"推荐场景"里的原始标签（角色扮演 / 通用场景等），方便按用途挑。
     */
    val VOLC_VOICES: List<Pair<String, String>> = listOf(
        // ── 官方标"角色扮演"的（最贴合 RP）──
        "zh_female_cancan_uranus_bigtts" to "知性灿灿 2.0（角色扮演）",
        "zh_female_sajiaoxuemei_uranus_bigtts" to "撒娇学妹 2.0（角色扮演）",
        "zh_female_gufengshaoyu_uranus_bigtts" to "古风少御 2.0（角色扮演）",
        "zh_female_zhishuaiyingzi_uranus_bigtts" to "直率英子 2.0（角色扮演）",
        "zh_female_yingtaowanzi_uranus_bigtts" to "樱桃丸子 2.0（角色扮演）",
        "zh_female_linxiao_uranus_bigtts" to "林潇 2.0（角色扮演）",
        "zh_female_lingling_uranus_bigtts" to "玲玲姐姐 2.0（角色扮演）",
        "zh_female_chunribu_uranus_bigtts" to "春日部姐姐 2.0（角色扮演）",
        "zh_female_nvleishen_uranus_bigtts" to "女雷神 2.0（角色扮演）",
        "zh_female_wuzetian_uranus_bigtts" to "武则天 2.0（角色扮演）",
        "zh_female_gujie_uranus_bigtts" to "顾姐 2.0（角色扮演）",
        "zh_female_ganmaodianyin_uranus_bigtts" to "感冒电音姐姐 2.0（角色扮演）",
        "zh_male_qingcang_uranus_bigtts" to "擎苍 2.0（角色扮演）",
        "zh_male_silang_uranus_bigtts" to "四郎 2.0（角色扮演）",
        "zh_male_xionger_uranus_bigtts" to "熊二 2.0（角色扮演）",
        "zh_male_lanyinmianbao_uranus_bigtts" to "懒音绵宝 2.0（角色扮演）",
        "zh_male_lubanqihao_uranus_bigtts" to "鲁班七号 2.0（角色扮演）",
        "zh_male_tangseng_uranus_bigtts" to "唐僧 2.0（角色扮演）",
        "zh_male_zhuangzhou_uranus_bigtts" to "庄周 2.0（角色扮演）",
        "zh_male_zhubajie_uranus_bigtts" to "猪八戒 2.0（角色扮演）",
        // ── 通用场景里最适合陪伴/聊天的 ──
        "zh_female_vv_uranus_bigtts" to "Vivi 2.0（通用·治愈女声）",
        "zh_female_xiaohe_uranus_bigtts" to "小何 2.0（通用·甜美活力）",
        "zh_female_meilinvyou_uranus_bigtts" to "魅力女友 2.0（御姐）",
        "zh_female_gaolengyujie_uranus_bigtts" to "高冷御姐 2.0",
        "zh_female_roumeinvyou_uranus_bigtts" to "柔美女友 2.0",
        "zh_female_wenrouxiaoya_uranus_bigtts" to "温柔小雅 2.0",
        "zh_female_wenroushunv_uranus_bigtts" to "温柔淑女 2.0",
        "zh_female_wenjingmaomao_uranus_bigtts" to "文静毛毛 2.0",
        "zh_female_zhixingnv_uranus_bigtts" to "知性女声 2.0",
        "zh_female_qiaopinv_uranus_bigtts" to "俏皮女声 2.0",
        "zh_female_tianmeitaozi_uranus_bigtts" to "甜美桃子 2.0",
        "zh_female_shuangkuaisisi_uranus_bigtts" to "爽快思思 2.0",
        "zh_female_linjianvhai_uranus_bigtts" to "邻家女孩 2.0",
        "zh_male_m191_uranus_bigtts" to "云舟 2.0（通用·男声）",
        "zh_male_taocheng_uranus_bigtts" to "小天 2.0（清澈温润男大）",
        "zh_male_aojiaobazong_uranus_bigtts" to "傲娇霸总 2.0",
        "zh_male_kailangxuezhang_uranus_bigtts" to "开朗学长 2.0",
        "zh_male_qingshuangnanda_uranus_bigtts" to "清爽男大 2.0",
        "zh_male_yangguangqingnian_uranus_bigtts" to "阳光青年 2.0",
        "zh_male_ruyaqingnian_uranus_bigtts" to "儒雅青年 2.0",
        "zh_male_gaolengchenwen_uranus_bigtts" to "高冷沉稳 2.0",
        "zh_male_baqiqingshu_uranus_bigtts" to "霸气青叔 2.0",
        "zh_male_dongfanghaoran_uranus_bigtts" to "东方浩然 2.0",
        "zh_male_yuanboxiaoshu_uranus_bigtts" to "渊博小叔 2.0",
        "zh_male_shenyeboke_uranus_bigtts" to "深夜播客 2.0",
        "zh_male_cixingjieshuonan_uranus_bigtts" to "磁性解说男声 2.0",
        "zh_male_wennuanahu_uranus_bigtts" to "温暖阿虎 2.0",
        "zh_male_jieshuoxiaoming_uranus_bigtts" to "解说小明 2.0",
        "zh_female_tvbnv_uranus_bigtts" to "TVB女声 2.0",
        "zh_male_yizhipiannan_uranus_bigtts" to "译制片男 2.0",
        "zh_female_mengyatou_uranus_bigtts" to "萌丫头 2.0",
        "zh_female_kailangjiejie_uranus_bigtts" to "开朗姐姐 2.0",
        "zh_male_kailangdidi_uranus_bigtts" to "开朗弟弟 2.0",
        "zh_male_wenrouxiaoge_uranus_bigtts" to "温柔小哥 2.0",
        "zh_male_youyoujunzi_uranus_bigtts" to "悠悠君子 2.0",
        "zh_female_qinqienv_uranus_bigtts" to "亲切女声 2.0",
        "zh_female_tiexinnvsheng_uranus_bigtts" to "贴心女声 2.0"
    )

    /**
     * 阿里百炼 CosyVoice 音色表，**按模型分档**（2026-09-17 用浏览器从官方《CosyVoice 音色列表》整页抓下来的：
     * v2 表 107 条、v3-flash 表 89 条、v3-plus 表 2 条；v1 表 20 条未收录，因为 v1 模型不预设）。
     *
     * 两条关键事实（官方页原话与表结构）：
     * 1. **音色 id 与模型版本绑定**，混用会 400 ⇒ 用户换模型时音色下拉必须跟着换（[speechVoices] 就是这么做的）；
     * 2. 版本后缀**不是**严格规律（v2 表里既有 `longxiaochun_v2` 也有 `longanyang` 这种无后缀的），
     *    所以这里一律"照表抄 id"，不做拼装、不猜。
     *
     * 收录口径：**只挑中文、且贴合角色扮演的**（本 App 是 RP 场景，"温柔闺蜜女""磁性低音男"这类比
     * 播报音、客服音、童声对味），去掉了英文/日文/韩文/印尼语音色与推销/催收/直播类场景音色；
     * 每档约 30 条，够覆盖男声/女声/青年/成熟/御姐/少女/古风等常见角色需求。
     */
    val DASHSCOPE_VOICES: Map<String, List<Pair<String, String>>> = mapOf(
        "cosyvoice-v2" to listOf(
            "longxiaochun_v2" to "龙小淳（知性积极女）",
            "longxiaoxia_v2" to "龙小夏（沉稳权威女）",
            "longanwen" to "龙安温（优雅知性女）",
            "longanli" to "龙安莉（利落从容女）",
            "longanrou" to "龙安柔（温柔闺蜜女）",
            "longanqin" to "龙安亲（亲和活泼女）",
            "longanya" to "龙安雅（高雅气质女）",
            "longyuan_v2" to "龙媛（温暖治愈女）",
            "longyue_v2" to "龙悦（温暖磁性女）",
            "longxing_v2" to "龙星（温婉邻家女）",
            "longqiang_v2" to "龙嫱（浪漫风情女）",
            "longfeifei_v2" to "龙菲菲（甜美娇气女）",
            "longhua_v2" to "龙华（元气甜美女）",
            "longmiao_v2" to "龙妙（抑扬顿挫女）",
            "longwanjun" to "龙婉君（细腻柔声女）",
            "longdaiyu" to "龙黛玉（娇率才女音）",
            "longyichen" to "龙逸尘（洒脱活力男）",
            "longanyun" to "龙安昀（居家暖男）",
            "longanshuo" to "龙安朔（干净清爽男）",
            "longanzhi" to "龙安智（睿智轻熟男）",
            "longnan_v2" to "龙楠（睿智青年男）",
            "longshu_v2" to "龙书（沉稳青年男）",
            "longshuo_v2" to "龙硕（博才干练男）",
            "longxiaocheng_v2" to "龙小诚（磁性低音男）",
            "longtian_v2" to "龙天（磁性理智男）",
            "longhan_v2" to "龙寒（温暖痴情男）",
            "longze_v2" to "龙泽（温暖元气男）",
            "longjin_v2" to "龙津（优雅温润男）",
            "longsanshu" to "龙三叔（沉稳质感男）",
            "longlaobo" to "龙老伯（沧桑岁月爷）",
            "libai_v2" to "李白（古代诗仙男）",
            "longgaoseng" to "龙高僧（得道高僧音）",
            "loongbella_v2" to "Bella2.0（精准干练女）"
        ),
        "cosyvoice-v3-flash" to listOf(
            "longanyang" to "龙安洋（阳光大男孩）",
            "longanhuan_v3" to "龙安欢（欢脱元气女）",
            "longxiaochun_v3" to "龙小淳（知性积极女）",
            "longxiaoxia_v3" to "龙小夏（沉稳权威女）",
            "longanwen_v3" to "龙安温（优雅知性女）",
            "longanli_v3" to "龙安莉（利落从容女）",
            "longanrou_v3" to "龙安柔（温柔闺蜜女）",
            "longanqin_v3" to "龙安亲（亲和活泼女）",
            "longanya_v3" to "龙安雅（高雅气质女）",
            "longyuan_v3" to "龙媛（温暖治愈女）",
            "longyue_v3" to "龙悦（温暖磁性女）",
            "longxing_v3" to "龙星（温婉邻家女）",
            "longqiang_v3" to "龙嫱（浪漫风情女）",
            "longfeifei_v3" to "龙菲菲（甜美娇气女）",
            "longhua_v3" to "龙华（元气甜美女）",
            "longmiao_v3" to "龙妙（抑扬顿挫女）",
            "longwanjun_v3" to "龙婉君（细腻柔声女）",
            "longdaiyu_v3" to "龙黛玉（娇率才女音）",
            "longanmin_v3" to "龙安闽（清纯萝莉女）",
            "longyichen_v3" to "龙逸尘（洒脱活力男）",
            "longanyun_v3" to "龙安昀（居家暖男）",
            "longanlang_v3" to "龙安朗（清爽利落男）",
            "longanzhi_v3" to "龙安智（睿智轻熟男）",
            "longnan_v3" to "龙楠（睿智青年男）",
            "longshu_v3" to "龙书（沉稳青年男）",
            "longshuo_v3" to "龙硕（博才干练男）",
            "longtian_v3" to "龙天（磁性理智男）",
            "longhan_v3" to "龙寒（温暖痴情男）",
            "longze_v3" to "龙泽（温暖元气男）",
            "longsanshu_v3" to "龙三叔（沉稳质感男）",
            "longlaobo_v3" to "龙老伯（沧桑岁月爷）",
            "longlaotie_v3" to "龙老铁（东北直率男）",
            "loongbella_v3" to "Bella3.0（精准干练女）"
        ),
        // v3-plus 官方表只有这两条（龙安洋是它的"社交陪伴标杆音色"）
        "cosyvoice-v3-plus" to listOf(
            "longanyang" to "龙安洋（阳光大男孩）",
            "longanhuan" to "龙安欢（欢脱元气女）"
        ),
        // Qwen-Audio-TTS 两档（2026-09-17 用户要求补；音色表取自官方《Qwen-Audio-TTS音色列表》，
        // **与 CosyVoice 那张表不通用**，所以单独列在这里）。只收中文、去掉了外语与童声之外的花哨项。
        "qwen-audio-3.0-tts-plus" to listOf(
            "longanlingxin" to "龙安灵心（知心温暖音）",
            "longanlufeng" to "龙安鲁风（明亮开朗音）"
        ),
        "qwen-audio-3.0-tts-flash" to listOf(
            "longanfengyue" to "龙安风悦（自然亲切音）",
            "longanyuanfei" to "龙安元妃（高傲妃子音）",
            "longanlingxi" to "龙安灵希（可爱甜美音）",
            "longanxiaoxin" to "龙安小昕（亲切活泼音）",
            "longanhuan_v3.6" to "龙安欢（欢脱元气女）",
            "longhuohuo_v3.6" to "龙火火（顽皮少年音）",
            "longchuanshu_v3.6" to "龙川叔（川普大叔）",
            "longjielidou_v3.6" to "龙杰力豆（天真男童）",
            "longpaopao_v3.6" to "龙泡泡（软糯可爱音）"
        )
    )

    /**
     * MiniMax 音色（2026-09-16 由用户从控制台导出官方 `get_voice` 列表后挑选）。
     * 选型口径：**只挑中文、且贴合角色扮演的**（本 App 是 RP 场景，"霸道少爷""冷淡学长"这类比中性播报音更对味），
     * 男 5 : 女 7 ≈ 4:6（用户指定的比例）。id 原样照抄（含空格的不能改，服务端按字面匹配）。
     */
    val MINIMAX_VOICES: List<Pair<String, String>> = listOf(
        // 男声 5
        "male-qn-jingying" to "精英青年",
        "male-qn-badao" to "霸道青年",
        "junlang_nanyou" to "俊朗男友",
        "lengdan_xiongzhang" to "冷淡学长",
        "Chinese (Mandarin)_Gentleman" to "温润男声",
        // 女声 7
        "female-shaonv" to "少女",
        "female-yujie" to "御姐",
        "female-tianmei" to "甜美女性",
        "wumei_yujie" to "妩媚御姐",
        "qiaopi_mengmei" to "俏皮萌妹",
        "tianxin_xiaoling" to "甜心小玲",
        "Chinese (Mandarin)_Sweet_Lady" to "甜美女声"
    )

    /**
     * 音色 id → 下拉里显示的可读标签。
     * 没有标签表的话，MiniMax 那一列全是 `male-qn-qingse` 这种 id，用户根本不知道哪个是什么音色。
     */
    fun speechVoiceLabel(baseUrl: String, voice: String): String {
        val kw = keywordFor(baseUrl, speechByKeyword.keys)
        if (kw == "minimax") {
            return MINIMAX_VOICES.firstOrNull { it.first == voice }?.let { "${it.second}（${it.first}）" } ?: voice
        }
        if (kw == "dashscope") {
            // 三张表都要查（用户可能把 v2 的音色留在 v3 模型下），查不到就原样显示 id
            val hit = DASHSCOPE_VOICES.values.firstNotNullOfOrNull { list ->
                list.firstOrNull { it.first == voice }
            }
            return hit?.let { "${it.second}" } ?: voice
        }
        if (kw == "tencentcloud") {
            // 腾讯的音色标签里已含"（聊天男声·超自然）"这种说明，直接用；查不到就原样显示数字
            return TENCENT_VOICES.firstOrNull { it.id == voice }?.label ?: voice
        }
        if (kw == "volcengine") {
            // 两张表都查（2.0 与 1.0 同名音色不少，先 2.0 后 1.0），查不到就原样显示 id（复刻音色走这里）
            return VOLC_VOICES.firstOrNull { it.first == voice }?.second
                ?: VOLC_VOICES_1_0.firstOrNull { it.first == voice }?.second
                ?: voice
        }
        // 硅基流动的音色写成「模型名:音色名」，标签取冒号后面那段
        val bare = voice.substringAfterLast(':')
        val label = SILICONFLOW_VOICE_LABELS[bare]
        return if (label != null) "$label（$bare）" else voice
    }

    /** 硅基流动 8 个音色的中文说明（官方文档给的定位：沉稳/低沉/磁性/欢快…） */
    private val SILICONFLOW_VOICE_LABELS = mapOf(
        "alex" to "沉稳男声", "benjamin" to "低沉男声",
        "charles" to "磁性男声", "david" to "欢快男声",
        "anna" to "沉稳女声", "bella" to "激情女声",
        "claire" to "温柔女声", "diana" to "欢快女声"
    )
    /** OpenAI 官方音色 */
    val OPENAI_VOICES = listOf(
        "alloy", "ash", "ballad", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer", "verse"
    )
}
