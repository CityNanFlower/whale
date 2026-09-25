package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/** 全局 API / 生成参数配置（存于本地设置文件） */
@Serializable
data class AiSettings(
    // ---- 对话（OpenAI 兼容 /chat/completions）----
    val chatBaseUrl: String = "https://api.deepseek.com/v1",
    /**
     * 对话供应商的 Key。**落盘时以 [providerKeys] 为准**（一家一份 Key），
     * 这里保留字段是为了兼容老数据（升级前只有一个全局 Key）：
     * `Repository.loadSettings()` 会把它解析成"当前对话供应商的那份"。
     */
    val chatApiKey: String = "",
    // deepseek-chat/deepseek-reasoner 已于 2026-07-24 弃用，新默认 deepseek-flash（旧值在 AiClient 里自动映射）
    val chatModel: String = "deepseek-flash",
    val temperature: Double = 0.85,
    val maxTokens: Int = 2048,
    val extraSystemPrompt: String = "",

    // ---- 生图（OpenAI 兼容 /images/generations）----
    val imageEnabled: Boolean = false,
    val imageBaseUrl: String = "",
    val imageApiKey: String = "",
    val imageModel: String = "",
    val imageSize: String = "1024x1024",
    val imageResponseFormat: String = "b64_json", // "url" 或 "b64_json"

    // 最近 N 条消息随请求发送（过长上下文按此截断）
    val historyLimit: Int = 40,

    // ---- 创作（AI 生成角色卡，可用思考/推理类模型）----
    /**
     * 创作供应商（2026-09-15 用户要求：三个模型页各自独立选供应商）。
     * 留空表示沿用对话供应商 —— 老数据没有这个字段，默认就等于"跟对话同一家"。
     */
    val creationBaseUrl: String = "",
    val creationModel: String = "",

    // ---- 思考强度（仅对确认支持的供应商注入参数，其余忽略）----
    /** 对话思考强度：""模型默认 | "off"关闭 | "high"深度 */
    val chatThinking: String = "",
    /** 创作思考强度：同上 */
    val creationThinking: String = "",

    // ---- 语音朗读 TTS（P-F2 v1：系统 TTS；v2 再加供应商合成的字段）----
    /**
     * 新回复自动朗读。**默认关闭**：朗读有成本（v2 按字符计费）也容易吵到人，必须用户显式打开。
     * 打开后只朗读"本轮正在生成的这条回复"（流式边生成边读），历史消息一律手动点。
     */
    val ttsAutoRead: Boolean = false,
    /** 语速 0.5~2.0（1.0 = 系统默认） */
    val ttsSpeed: Float = 1.0f,
    /** 音高 0.5~2.0（1.0 = 系统默认） */
    val ttsPitch: Float = 1.0f,
    // ---- 语音朗读 v2：供应商语音合成（OpenAI 兼容 /audio/speech）----
    /**
     * 朗读引擎：`"system"`＝系统 TTS（v1，免费离线）/ `"builtin"`＝供应商合成（v2，按字符计费、音色更好）。
     * 选了 builtin 但 [ttsBaseUrl] 为空（或那个家没配 Key）时**自动回退系统 TTS**，功能不会因为没配而不出声。
     */
    val ttsProvider: String = "system",
    /** 供应商合成的 Base URL（如 https://api.siliconflow.cn/v1）；本地部署填 http://192.168.x.x:8880/v1 */
    val ttsBaseUrl: String = "",
    /** 语音合成模型，如 FunAudioLLM/CosyVoice2-0.5B */
    val ttsModel: String = "",
    /** 音色：多数家是"模型名:音色名"（如 FunAudioLLM/CosyVoice2-0.5B:anna），也可以是音色克隆返回的 uri */
    val ttsVoice: String = "",

    // ---- 混合音色（火山，2026-09-17）----
    /**
     * 是否启用**混合音色**（把 2~3 个音色按权重混合成一个人格，猫箱那种"创作音色"的玩法）。
     * 官方口径（豆包语音"超强混音"）：`speaker=custom_mix_bigtts` + `mix_speaker.speakers[]`，
     * 最多 3 个源、权重之和为 1；**可混的源只有 1.0 官方音色与复刻音色（`S_`）**，
     * 2.0（`*_uranus_bigtts`）、`DiT_`、`saturn_` 不支持。
     */
    val ttsMixEnabled: Boolean = false,
    /** 参与混合的音色与权重（只在 [ttsMixEnabled] 为真且 ≥2 条时生效；发送前会按比例归一化） */
    val ttsMixSpeakers: List<MixSpeaker> = emptyList(),
    /** 存下来的混合音色预设（用户要求"混出来的音色能存进预设"）；点一下就套用 [ttsMixSpeakers] */
    val ttsMixPresets: List<MixPreset> = emptyList(),

    /**
     * **本机复刻出来的音色**（声音复刻，2026-09-22）：录音复刻的产物 + 你手填进来的控制台槽位。
     * 它是"音色库"而不是"当前选择"——当前用的那个仍在 [ttsVoice] 里。
     */
    val ttsCloneVoices: List<CloneVoice> = emptyList(),

    // ---- 语音专用凭据（Step 1，2026-09-16）----
    /**
     * 语音接口的凭据，**按家一份、按协议结构化**：`key = 供应商标识`（与 [providerKeys] 同一套 id）、
     * `value = 字段名 → 值`（字段名由 `ProviderProfiles.speechCredentialFields()` 按协议声明）。
     *
     * 为什么要结构化：`providerKeys` 一家只装得下一个字符串，装不下火山的 AppID+AccessToken
     * 或腾讯的 SecretId+SecretKey；界面也因此改成"按字段渲染输入框"，加协议不用动界面。
     *
     * **留空 = 用「模型与API」里这家的那把 Key**（见 [speechCredential]），所以老数据与
     * 硅基流动/MiniMax 这类"一家一把 Key"的家**不需要重填**。落盘前由 Repository 逐个 value 加密。
     */
    val speechCredentials: Map<String, Map<String, String>> = emptyMap(),

    // ---- 语音输入 ASR（Step 3，2026-09-16）----
    /**
     * 识别引擎：`"system"`＝系统识别（免费、不要 Key，识别率取决于手机）/ `"builtin"`＝供应商识别
     * （`POST {base}/audio/transcriptions`，硅基流动那批多数免费）。
     * 选了 builtin 但没配地址/模型/凭据时**自动回退系统识别**（并在界面说明原因），不会点了没反应。
     */
    val asrProvider: String = "system",
    /** 供应商识别的 Base URL（如 https://api.siliconflow.cn/v1） */
    val asrBaseUrl: String = "",
    /** 识别模型，如 FunAudioLLM/SenseVoiceSmall 或 whisper-1 */
    val asrModel: String = "",
    /**
     * 识别语言（选填，ISO-639-1 如 zh / en）。
     * **默认空 = 不传这个字段**（官方文档只写了 file + model），让服务端自动判断；
     * 觉得中文识别不听话时再填。
     */
    val asrLanguage: String = "",
    /**
     * 麦克风怎么用：`false`＝按住说话、松手识别（手机默认）/ `true`＝点一下开始、再点一下结束（旁边有「×」取消）。
     *
     * 手机默认按住（单手、就近、松手即结束）；**桌面端不读这个开关**，一律点按——鼠标"按住不放"很难受，
     * 且按下时手指占着按键、没有第二只手点取消。桌面端因此固定点按，界面里也不渲染这个选择（见 VoiceScreen）。
     */
    val asrTapToTalk: Boolean = false,

    /**
     * 跳过（）里的动作描写。
     * 本 App 的排版约定是"动作、神态、心理用（）括注"，只读对白通常更顺耳，所以默认跳过；
     * 关掉则连括注内容一起读（去掉括号本身）。
     */
    val ttsSkipActionText: Boolean = true,

    // ---- 背景音乐 BGM（第 59 轮）----
    /**
     * 环境音开关。**全局设置**（不是剧情、不随角色走）：背景音乐是"一个人待着时的房间声"，
     * 换角色不该把它掐掉——要按角色换音源的话，等有了「场景音效」再说。
     */
    val bgmEnabled: Boolean = false,
    /**
     * 音源：内置音源 id（见 `BgmSources.builtin`，如 `rain`/`waves`）+ `"user"`（我的文件），
     * 空串 = 还没选过（界面据此提示"先选一个音源"，而不是静默不出声）。
     */
    val bgmSource: String = "",
    /** 音量 0~1（0 = 静音，但仍记账"在播"） */
    val bgmVolume: Float = 0.5f,
    /**
     * 用户上传的音频路径（只在使用 `"user"` 音源时读），空 = 没传过。
     * ⚠ 这是**用户文件**，不是缓存：`reclaimImages`（按时回收孤图）与「清除缓存」都不碰它，
     * 回收规则见 `Repository.saveBgmFile`（bgm 目录只留一份）。
     */
    val bgmUserPath: String = "",
    /** 上传音频的显示名（界面里"正在用 XX.mp3"；文件本体在 bgm 目录里是生成名） */
    val bgmUserName: String = "",

    // ---- 按供应商记忆的 API Key（2026-09-14）----
    /**
     * key = 供应商标识（内置用 ProviderProfile.keyword，自定义用 CustomProvider.id），
     * value = 明文 Key（**落盘前由 Repository 用 Security 加密**，与 chatApiKey 同一套 Keystore）。
     * 切换供应商时自动保存当前 Key、并取回该供应商原来保存的 Key。
     */
    val providerKeys: Map<String, String> = emptyMap(),

    // ---- 生图专用 Key（P1-12 引入，2026-09-15 重构后降级为"兼容读取"）----
    /**
     * 重构后 Key **一家一份**（[providerKeys]），三个模型页选到哪家就用哪家的 ——
     * 结构上不可能串台，也不再需要单独填生图 Key。这里保留字段只为了读老数据：
     * `Repository.loadSettings()` 会按「imageProviderKeys → providerKeys → 旧的全局 imageApiKey」
     * 逐级解析出 [imageApiKey]，新界面不再露出单独的"生图专用 Key"入口。
     */
    val imageProviderKeys: Map<String, String> = emptyMap(),

    // ---- 用户自定义供应商（2026-09-14）----
    val customProviders: List<CustomProvider> = emptyList(),

    // ---- 主题（null = 跟随系统；仅作旧数据回退，新值写 themeMode）----
    val darkTheme: Boolean? = null,

    // ---- 外观（问题 #9 / #4）----
    /** 主题模式："light" | "dark" | "system" | "other"；空则回退旧字段 darkTheme */
    val themeMode: String = "",
    /** 字体（AppFont.id）；空 = 系统默认 */
    val fontFamily: String = "",
    /** 字号缩放：1.0 = 标准，范围 0.85~1.4 */
    val fontScale: Float = 1.0f,
    /** 用户上传的字体文件路径（优先于 fontFamily）；空 = 用内置字体 */
    val customFontPath: String = "",
    /** 上传字体的显示名 */
    val customFontName: String = "",
    /** 已上传的字体列表（问题 #20：以前只能存一个，换一个旧的就被顶掉了） */
    val customFonts: List<CustomFontEntry> = emptyList(),

    // ---- 用户角色卡分类（含预设，可增删排序；未初始化时用默认预设）----
    val categories: List<String> = emptyList(),
    val categoriesInitialized: Boolean = false,
    /**
     * 是否已为**库里已有的卡**补过一次分类（第 57 轮的自动登记是后来才有的，老卡当时没登记）。
     * 只补一次、补完置真：否则用户删掉某个分类后，只要还有卡带着它，下次进角色卡页就会被加回来
     * ——"删了又回来"比没有这个功能更糟。
     */
    val categoriesBackfilled: Boolean = false,

    // ---- 用户预设设定：默认使用的设定 id ----
    val defaultUserSettingId: String = "",
    /**
     * 「我的设定（预设）」那一栏是否展开（用户 2026-09-23 反馈：设定多了会把下面五张分区卡顶得很远，
     * 翻起来费劲）。**默认收起**——它只是个开关状态，不是内容，所以不跟着账号数据走；
     * 用户展开过一次就一直展开着（他自己的选择，不替他复位）。
     */
    val presetsExpanded: Boolean = false
) {
    /** 创作供应商的生效 URL（留空 = 沿用对话供应商，老数据即此） */
    val creationBaseUrlEffective: String get() = creationBaseUrl.ifBlank { chatBaseUrl }

    /**
     * 取某家供应商在「API 配置」里存的 Key（**一家一份**，2026-09-15 重构）。
     *
     * `providerKeys` 非空 ⇒ 严格按供应商取（没有就是没有，界面会置灰提示去填）；
     * `providerKeys` 为空 ⇒ 老数据（升级前只有一个全局 Key），回落到 [chatApiKey]，
     * 但**只对"它当初那家"生效**，避免把 A 家的 Key 当 B 家的用。
     */
    fun keyFor(baseUrl: String): String {
        val id = ProviderProfiles.keyIdFor(baseUrl, customProviders)
        if (providerKeys.isNotEmpty()) return providerKeys[id].orEmpty()
        return if (id == ProviderProfiles.keyIdFor(chatBaseUrl, customProviders)) chatApiKey else ""
    }

    /** 生图供应商实际使用的 Key：兼容读取生图专用 Key（P1-12），其余按 [keyFor] 走共享规则 */
    fun imageKey(): String {
        val id = ProviderProfiles.keyIdFor(imageBaseUrl, customProviders)
        imageProviderKeys[id]?.takeIf { it.isNotBlank() }?.let { return it }
        if (providerKeys.isEmpty() && imageProviderKeys.isEmpty()) {
            // 老数据：旧字段 imageApiKey 优先，其次才是沿用对话 Key（与重构前一致）
            imageApiKey.takeIf { it.isNotBlank() }?.let { return it }
            return chatApiKey
        }
        return keyFor(imageBaseUrl)
    }

    /**
     * 语音接口要用的凭据（TTS / ASR 共用，Step 1）：**按家优先取 [speechCredentials]，
     * 该家没有（或整组都空）则回退到 [keyFor] 那一把 Key** —— 这就是改动前的行为，
     * 所以老存档、以及硅基流动 / MiniMax 这种"一家一把 Key"的家一把都不用重填。
     */
    fun speechCredential(baseUrl: String): Map<String, String> {
        val id = ProviderProfiles.keyIdFor(baseUrl, customProviders)
        val stored = speechCredentials[id].orEmpty().filterValues { it.isNotBlank() }
        if (stored.isNotEmpty()) return stored
        // ⚠️ **火山/腾讯不做这个回退**：它们的语音凭据与"模型与API"里那把 Key 根本不是一回事
        // （火山语音要 AppID+AccessToken 或语音控制台的 API Key —— 方舟的 ark- Key 拿过去只会 401/403；
        // 腾讯语音要 CAM 的 SecretId/SecretKey）。早期无脑回退，导致"只配了方舟 Key"的用户在语音页
        // 看到"已配置"、点了才报授权错（2026-09-17 检视发现）。
        val proto = ProviderProfiles.speechProtocol(baseUrl)
        if (proto == ProviderProfiles.SpeechProtocol.VOLC || proto == ProviderProfiles.SpeechProtocol.TENCENT) {
            return emptyMap()
        }
        return mapOf("apiKey" to keyFor(baseUrl))
    }

    /**
     * 该家的语音凭据是否已填齐（必填字段都有值）——界面据此决定模型/音色/试听是否置灰。
     * 本地 http 服务（不需要 Key）不走这里，由调用方按"是不是 https"单独放行。
     */
    fun hasSpeechCredential(baseUrl: String): Boolean {
        val cred = speechCredential(baseUrl)
        // 多字段凭据的家各有各的"可用"口径（不能套用"必填字段都填了"那条通用规则——
        // 火山的 5 个字段全是选填，空集 all{} 恒真会让界面永远放行）
        when (ProviderProfiles.speechProtocol(baseUrl)) {
            ProviderProfiles.SpeechProtocol.VOLC ->
                return !cred["apiKey"].isNullOrBlank() ||
                    (!cred["appId"].isNullOrBlank() && !cred["accessToken"].isNullOrBlank())
            ProviderProfiles.SpeechProtocol.TENCENT ->
                return !cred["secretId"].isNullOrBlank() && !cred["secretKey"].isNullOrBlank()
            else -> Unit
        }
        return ProviderProfiles.speechCredentialFields(baseUrl)
            .filter { it.required }
            .all { !cred[it.key].isNullOrBlank() }
    }
}

/** 混合音色的一个源：[voice] 是音色 id（1.0 官方音色或复刻音色 `S_xxx`），[factor] 是权重 */
@Serializable
data class MixSpeaker(
    val voice: String = "",
    val factor: Float = 0.5f
)

/** 存下来的**混合音色预设**：一个名字 + 一组源与权重（点一下就能套用回 [AiSettings.ttsMixSpeakers]） */
@Serializable
data class MixPreset(
    val name: String = "",
    val speakers: List<MixSpeaker> = emptyList()
)

/**
 * **本机复刻出来的音色**（声音复刻，2026-09-22 用户要求"复刻音色做应用内录音与合成"）。
 *
 * [id] 是服务端认的音色代号。它有两种来源，**都得记在本地**，原因见下：
 * 1. 控制台给你的预付费槽位（`S_` 开头）；
 * 2. App 在「录音复刻」里自己命名的新音色（后付费模式）——这种 id 是我们生成的，
 *    前缀毫无特征（`S_` 是官方保留前缀，客户端**不许**自己造），服务端以外没人能认出来。
 *
 * ⚠ 所以**判"这是不是复刻音色"必须查这份清单**，不能只看 `S_` / `icl_` 前缀：
 * 漏了它，录音复刻出来的音色会被当成普通 2.0 音色（Resource-Id 发 `seed-tts-2.0`），
 * 合成必然报 `resource ID is mismatched with speaker`。
 */
@Serializable
data class CloneVoice(
    val id: String = "",
    /** 本机显示名（"我的声音" / "旁白大叔"…）。服务端没有名字这个概念，是自己取的 */
    val name: String = "",
    /** 建立时间（毫秒）；只用于排序与"什么时候录的" */
    val createdAt: Long = 0L,
    /**
     * 服务端说这个音色已经能用了（status 2/4）。false = 音频已上传、还在训练。
     * 留这个字段是为了**长训练不白等**：用户关掉页面也能在列表里点「查状态」接着看。
     */
    val ready: Boolean = true
)

/** 一份用户上传的字体文件（问题 #20） */@Serializable
data class CustomFontEntry(
    val path: String = "",
    val name: String = ""
)

/**
 * 用户自定义供应商（2026-09-14）：让非内置平台也能享受与内置供应商一样的自动适配
 * （思考参数形态、温度支持、生图协议与返回格式）。字段语义与 [ProviderProfiles.ProviderProfile] 一致，
 * 界面选择后由 ProviderProfiles.resolve() 统一返回同一套能力描述。
 */
@Serializable
data class CustomProvider(
    val id: String = "",
    val name: String = "",
    /** 对话 Base URL（留空表示该平台只用于生图） */
    val chatBaseUrl: String = "",
    /** 生图 Base URL（留空表示该平台只用于对话） */
    val imageBaseUrl: String = "",
    /** 思考参数形态（存 ThinkingKind.name；NONE = 不发参数） */
    val thinkingKind: String = "NONE",
    /** 是否接受 temperature 参数 */
    val temperatureSupported: Boolean = true,
    /** 生图返回格式被平台固定时的值（"url" / "b64_json"；空 = 用户可选） */
    val imageFormatForced: String = "",
    /** 生图协议（存 ImageProtocol.name：OPENAI / MINIMAX / DASHSCOPE） */
    val imageProtocol: String = "OPENAI",
    /** 备注（显示在接入能力卡片里） */
    val note: String = ""
)

/** 角色卡 */
@Serializable
data class CharacterCard(
    val id: String,
    val name: String,
    val tagline: String = "",
    val persona: String = "",
    val scenario: String = "",
    val greeting: String = "",
    // 本地文件绝对路径，或 http(s) URL，或 null
    val avatarUri: String? = null,
    // 默认聊天背景：**手机端那份**，同时也是唯一的历史字段（老卡只有它）
    val backgroundUri: String? = null,
    /**
     * 桌面端默认聊天背景（A 批次，用户 2026-09-18「聊天背景分端」）。
     * `null` = 没单独设过 ⇒ 回退用 [backgroundUri]（老卡即此形态：两端同一张，不必重设）；
     * `""` = 桌面端**明确不要背景** —— 所以「移除」写空串，写 null 会让那张图又冒出来。
     * 取用一律走 `ui/ChatBackgrounds.kt` 的 `backgroundFor()`，别在各处手写这个三元。
     */
    val backgroundUriDesktop: String? = null,
    // 多开场白：新会话随机抽一条；空则回退 greeting
    val greetings: List<String> = emptyList(),
    // 形态标签：""陪伴（默认）| "experience"体验（多开局/剧情玩法）
    val formTag: String = "",
    // 多分类：新卡直接写这里；空则回退到旧版单分类字段 category
    val categories: List<String> = emptyList(),
    // 旧版单分类字段（迁移用，新卡不再写入）
    val category: String = "",
    /**
     * 角色专属音色（E 批次，2026-09-21）：`null` = 从没设过（跟着设置页的全局音色走）。
     *
     * 取用**一律**走 `data/CharacterVoices.kt` 的 `effective()`，别在各处手写字段回退——
     * 那个函数还负责"按家认门牌"（卡里的合成地址在本机不存在时改用本机同一家的地址）。
     */
    val voice: CharacterVoice? = null,

    // ── v2 规范字段（E1 角色卡引擎，2026-09-22）──────────────────────────────
    // 起因：老口径把 description / personality / mes_example **揉成 [persona] 一个块**
    // （导入时我们自己加【背景故事】【性格特点】小标题），system_prompt / post_history_instructions /
    // creator_notes / creator / character_version 则**整块丢弃**。于是卡主写的分层信息进不来，
    // 导出去也只剩我们拼的那一大坨。现在每个字段各自独立落库、独立往返。
    //
    // ⚠ **老卡不反向拆**（第 66 轮定稿）：库里已存的卡 persona 是拼好的文本，正则拆会把用户手改过的拆坏。
    // ⇒ 装配口径是"**新字段为空时仍按老口径整块注入 [persona]**；只要有新字段就改走分层注入"
    // （见 `AiClient.buildSystemPrompt`），老卡行为一字不变。

    /** v2 `description`：角色描述（外貌 / 身份 / 背景等**会改变行为的事实**） */
    val description: String = "",
    /** v2 `personality`：性格摘要（3~6 个能在聊天里真实体现的特质） */
    val personality: String = "",
    /** v2 `mes_example`：对话示例（社区约定 `<START>` 分块、`{{char}}:` / `{{user}}:` 前缀） */
    val mesExample: String = "",
    /**
     * v2 `creator_notes`：面向**用户**的说明，**绝不进 prompt**——规范明确禁止把它用于提示词工程。
     * 鲸鱼把它当"玩法介绍"展示（导入卡时能看到卡主写了什么）。
     */
    val creatorNotes: String = "",
    /** v2 `creator`：卡作者（展示用，同样不进 prompt） */
    val creator: String = "",
    /** v2 `character_version`：卡版本（展示用，不进 prompt） */
    val characterVersion: String = "",
    /**
     * v2 `system_prompt`：**只保真、不注入**。
     *
     * 调研（`docs/调研-角色卡写作范式-2026-09.md` §一）的结论是"卡里的 system_prompt 应留空，
     * 避免与 App 拼装冲突"——我们的 system prompt 由 App 统一控制（视角约定是地基）。
     * 但**丢弃**会让作者写的东西凭空消失、导出后也找不回来，所以存下来 + 原样写回，
     * 只是不参与装配。E2 的注入层级里没有它，这是有意的（台账 §二 E2 范围也只列到 post-history）。
     */
    val systemPrompt: String = "",
    /** v2 `post_history_instructions`：**注入到本轮最后一条用户消息末尾**（post-history 位，见 ChatViewModel.tailHint） */
    val postHistory: String = "",

    // ── 工具形态（E4，字段先落地）────────────────────────────────────────────
    /** 工具形态的任务说明（RP 卡为空）；导入导出走 `extensions.whale.task_brief` */
    val taskBrief: String = "",
    /** 工具形态的输出格式要求（RP 卡为空）；导入导出走 `extensions.whale.output_format` */
    val outputFormat: String = "",

    // ── 玩法形态（E4 拆档，2026-09-23）──────────────────────────────────────
    /**
     * 玩法规则（**硬约束**）：这一局的规则与判定口径，写成"必须… / 不可…"。
     * 它是玩法形态唯一的"对错来源"——没有它，模型会为了顺着用户而放宽规则（报告 §四）。
     */
    val playRules: String = "",
    /** 玩法状态项：这一局要记住什么（进度 / 计分 / 已确认的结论），注入为【本局要记的状态】 */
    val playState: String = "",
    /** 每轮末尾给几个编号选项；0 ＝ 不给选项（自由作答类玩法，如"千万不要说水"） */
    val playOptions: Int = 0,

    /**
     * 世界书（E3，2026-09-24）：关键词触发的背景资料，兼容 SillyTavern 的 `character_book`。
     *
     * `null` ＝ 这张卡没有世界书（**所有老卡即此形态**，导出的 JSON 与装配出的提示词一字不变）。
     * 命中判断与装配见 `data/WorldBookEngine.kt` 与 `AiClient.buildSystemPrompt`——
     * 这里只负责"跟着卡走"（导入、导出、备份都是卡的一部分）。
     *
     * 世界书独立化之后（见 [worldBookId]）这个字段有了第二重身份：**解析结果的落点**。
     * 卡 JSON 落盘时这里恒为 `null`（正文住账号级书文件），界面与提示词拿到的卡是
     * `Repository` 读出时把引用解析回这份正文之后的副本——下游（提示词装配 / 命中面板 /
     * 导出）照旧只读本字段，不需要知道引用这回事。
     */
    val worldBook: WorldBook? = null,

    /**
     * 世界书的**账号级引用**：书实体住 `accounts/<id>/worldbooks/<本字段>.json`，
     * 可被多张卡共享（"这张卡的世界观"升格为"这个账号的世界观"）。
     * `null` ＝ 没有引用（老卡形态）。读取时 `Repository` 把引用解析回 [worldBook]；
     * 卡 JSON 落盘时正文被剥掉只留引用，所以**书文件才是唯一事实来源**——
     * 改一本共享的书，所有引用它的卡下一轮对话立即生效。
     */
    val worldBookId: String? = null,

    /**
     * **别人的 `extensions`**（`whale` 节点之外的全部内容）原样存着，导出时合并写回。
     * 起因：导入别人的卡再导出，会把对方扩展里的数据整块抹掉——那是人家的数据，不该我们做主。
     * 存的是原 JSON 对象文本（`{}` 或空串都表示"没有"）。
     */
    val extensionsRaw: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 读取分类：优先 categories，否则旧字段 category，最后兜底「其他」 */
    fun categoriesOrDefault(): List<String> =
        if (categories.isNotEmpty()) categories
        else if (category.isNotBlank()) listOf(category)
        else listOf("其他")

    /** 生效开场白列表：greetings 优先，回退 greeting，供新会话随机抽取 */
    fun effectiveGreetings(): List<String> =
        greetings.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            .ifEmpty { greeting.trim().takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList() }

    /** 形态标签显示名（存储值仍为 "experience" / "tool" / "play"，只换展示叫法） */
    fun formTagLabel(): String = when (formTag) {
        "experience" -> "多线"
        "tool" -> "工具"
        "play" -> "玩法"
        else -> "陪伴"
    }

    /** 列表徽标文案；陪伴是默认形态、**不挂徽标** ⇒ 返回 null（别把所有卡都标成「陪伴」） */
    fun formTagBadge(): String? = when (formTag) {
        "experience" -> "多线"
        "tool" -> "工具"
        "play" -> "玩法"
        else -> null
    }

    /** 是不是工具形态卡（E4）——装配模式、编辑器字段裁剪、聊天页开关全部由它派生 */
    fun isTool(): Boolean = formTag == "tool"

    /** 是不是玩法形态卡（E4 拆档）——同上，装配模式与编辑器字段都按它裁 */
    fun isPlay(): Boolean = formTag == "play"

    /**
     * 这张卡吃到了 v2 分层字段没有（E2 装配路径的判据）。
     *
     * **只看会进 prompt 的那三个**——`creator_notes` / `creator` / `character_version` 不进 prompt，
     * 它们有没有值**不该**改变装配路径（否则给老卡填个作者名就会悄悄换掉注入口径）。
     */
    fun hasLayeredPersona(): Boolean =
        description.isNotBlank() || personality.isNotBlank() || mesExample.isNotBlank()

    /**
     * 角色设定的**合并文本**：分层字段拼成带小标题的一段，全空则回退老合并块 `persona`。
     *
     * 只给"不关心分层、只要一段人话"的地方用：卡片详情页、列表搜索、导出成的卡图、复制为文本。
     * ⚠ **绝不要拿它去装配提示词** —— 那是 `AiClient.buildSystemPrompt` 的事（分层 or 老口径两套口径，
     * 还带宏展开）。这里没有宏、也不该有。
     */
    fun personaDisplayText(): String =
        if (hasLayeredPersona()) {
            listOfNotNull(
                description.trim().takeIf { it.isNotBlank() }?.let { "【角色描述】\n$it" },
                personality.trim().takeIf { it.isNotBlank() }?.let { "【性格特点】\n$it" },
                mesExample.trim().takeIf { it.isNotBlank() }?.let { "【对话示例】\n$it" }
            ).joinToString("\n\n")
        } else persona.trim()
}

/**
 * 系统提示词的装配模式（E2，2026-09-22）。
 *
 * 为什么要枚举而不是 `forSuggestion: Boolean`：灵感回复那条路本来就是"换掉前几段、中间资料块共用"
 * 的现成先例；再加工具形态就是第三个分支，布尔参数会退化成参数汤，而另起一套模板要维护两份
 * 分层注入 / 宏 / 排序，必然漂移（台账 §5.2）。
 *
 * 住 `commonMain`：E4 的 `EngineSpec`（UI 要读）里也要引用它。
 */
enum class PromptMode {
    /** 角色扮演（默认）：完整地基 + 视角约定 + 分层角色设定 + 风格 */
    RP,
    /**
     * 工具形态（E4）：不扮演、用户消息是**待处理素材**，输出是可直接拿走的成品。
     * 人格降级为风格滤镜（由 [CharacterCard.taskBrief] 承载），不带风格 / 记忆 / 摘要 / 用户设定。
     */
    TOOL,
    /**
     * 玩法形态（E4 拆档，2026-09-23）：陪用户玩**一局**有规则的玩法（猜谜 / 对弈 / 情景问答）。
     *
     * 与 [TOOL] 的分野是**评判标准**：工具问"拿到的东西能不能直接用"，玩法问"规则守得住、进度记得住、
     * 这一局好不好玩"（报告 §三）。所以它复用角色扮演的连续性设备（记忆承载这一局的进度），
     * 但不扮演虚构角色、不演剧情；规则是卡里的**硬约束**（[CharacterCard.playRules]）。
     */
    PLAY,
    /** 灵感回复：不扮演任何角色，替**用户**代笔写台词 */
    SUGGEST
}

/**
 * 角色专属音色（E 批次，2026-09-21 定的口径：设置页是默认音色，角色可设个性化音色，
 * 用开关控制展开；卡片里带预设参数，导入后能匹配就自动匹配）。
 *
 * **与 [AiSettings] 的 tts\* 字段一一对齐**，但只带"听起来会变"的那几项：引擎/地址/模型/音色/语速/音高。
 * **混合音色（2026-09-22 加入）也算"听起来会变"**：卡自己就能是一个混出来的人格
 * （火山专属，见 [mixEnabled]）——在此之前混音只属于全局那一套音色，卡一指定音色就被强制关掉。
 *
 * 导入时**原样保留**：本机没有这个家、或清单里没有这个音色，都不清空字段（用户明确说"不好实现就算了"，
 * 所以只做"能匹配就直接用"，不做静默改写）；不可用只在编辑页标一行字。
 */
@Serializable
data class CharacterVoice(
    /** 开关本体。导出时也带着它，导回来的卡保持卡主设定的开 / 关状态 */
    val enabled: Boolean = false,
    /** 供应商关键词（`ModelCatalog.speechProviderKeyword` 那套：siliconflow / volcengine …），"认家"用它 */
    val provider: String = "",
    /** 合成地址。本机没有这个地址时按 [provider] 换成**本机同一家**的地址（见 [CharacterVoices.effective]） */
    val baseUrl: String = "",
    /** 语音合成模型（火山是 Resource-Id、腾讯是 ModelType 档位，与设置页同一口径） */
    val model: String = "",
    /** 音色 id（硅基流动这类要 "模型:音色" 全写，与设置页同一口径）。开了混音时它**不参与合成**（只做回退） */
    val voice: String = "",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,

    // ---- 混合音色（火山，2026-09-22）----
    /**
     * 这个角色是不是一个**混出来的人格**（把 2~3 个音色按权重混成一把声音）。
     *
     * 生效条件与全局同一口径（[AiClient.volcV3Speech]）：开关为真**且**有效源 ≥2 条。
     * 开关为假时 [voice] 仍按单音色走 —— 老卡与其它家的卡因此一字不变。
     */
    val mixEnabled: Boolean = false,
    /** 参与混合的音色与权重（只在火山可用；发送前按比例归一化，最多 3 条） */
    val mixSpeakers: List<MixSpeaker> = emptyList()
) {
    /** 有效混音源（去空、至多 3 条）——"够不够混"的判定两处共用（[CharacterVoices] 与编辑页提示） */
    fun mixSources(): List<MixSpeaker> =
        if (!mixEnabled) emptyList()
        else mixSpeakers.filter { it.voice.isNotBlank() }.take(3)

    /** 完全没内容（导出时据此整个节点都不写，免得每张卡都多一段噪声） */
    fun isEmpty(): Boolean =
        !enabled && provider.isBlank() && baseUrl.isBlank() && model.isBlank() && voice.isBlank() &&
            mixSources().size < 2
}

/**
 * 形态标签取值（value = 存储值）：陪伴 = 单开场白；多线 = 多条不同开局，新会话随机抽一条；
 * 玩法 = 陪玩一局（E4 拆档）；工具 = 任务处理（E4）。
 *
 * **顺序与 [Engines.all] 一致**（玩法在工具前面，用户 2026-09-23 口径）：两处都是"形态在界面上
 * 出现的顺序"，对不上就会出现"筛选条一个顺序、编辑器另一个顺序"。改这里就得同步改那边。
 */
val CharacterFormTags = listOf("陪伴" to "", "多线" to "experience", "玩法" to "play", "工具" to "tool")

/** 玩法形态「每轮给几个编号选项」的可选档位；**0 ＝ 不给选项**（自由作答类玩法，如"千万不要说水"） */
val PlayOptionChoices = listOf(0, 2, 3, 4, 5)

/** 角色卡分类（用于「角色卡」页分类筛选） */val CharacterCategories = listOf(
    "古风", "现代", "奇幻", "科幻", "二次元", "恋爱", "悬疑", "其他"
)

/** 单条消息：role = "user" | "assistant" | "scene" */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    // swipe 多版本：null = 只有 content 一版（旧数据）；否则为全部版本列表，
    // 不变量：content 恒等于 variants[activeIndex]（读取方一律用 content，无需感知 variants）
    val variants: List<String>? = null,
    val activeIndex: Int = 0,
    // 用户随消息发的图片（本地文件路径）；仅视觉模型会话可用
    val imageUri: String? = null
) {
    /** 追加一个 swipe 版本并切换到它（重新生成时用） */
    fun withVariant(text: String): ChatMessage {
        val vs = (variants ?: listOf(content)).toMutableList()
        vs.add(text)
        return copy(content = text, variants = vs.toList(), activeIndex = vs.lastIndex)
    }

    /** 切换到第 [i] 个版本 */
    fun switchedTo(i: Int): ChatMessage {
        val vs = variants ?: return this
        if (i !in vs.indices) return this
        return copy(content = vs[i], activeIndex = i)
    }

    /** 编辑生效版本内容（同步回 variants） */
    fun edited(text: String): ChatMessage {
        val vs = variants ?: return copy(content = text)
        val idx = activeIndex.coerceIn(vs.indices)
        return copy(content = text, variants = vs.toMutableList().also { it[idx] = text })
    }

    /** 删除第 [i] 个版本（至少保留一个） */
    fun withoutVariant(i: Int): ChatMessage {
        val vs = variants ?: return this
        if (vs.size <= 1 || i !in vs.indices) return this
        val kept = vs.toMutableList().also { it.removeAt(i) }
        val ni = if (i < activeIndex) activeIndex - 1
        else if (i == activeIndex) activeIndex.coerceAtMost(kept.lastIndex)
        else activeIndex
        return copy(content = kept[ni], variants = kept.toList(), activeIndex = ni)
    }

    /** 版本数（无 variants 即 1） */
    val variantCount: Int get() = variants?.size ?: 1
}

/** 一次会话（每个角色可拥有多个会话） */
@Serializable
data class Conversation(
    val id: String,
    val characterId: String,
    val title: String = "新会话",
    val messages: List<ChatMessage> = emptyList(),
    // 会话级聊天背景覆盖（分端，语义与 CharacterCard.backgroundUriDesktop 完全一致）；
    // 本端为空则用角色卡本端那份
    val backgroundUri: String? = null,
    val backgroundUriDesktop: String? = null,
    // 会话级背景音乐配置（第 63 轮）：null = 没设置过 = 跟随全局默认
    val bgm: SessionBgm? = null,
    // 本会话采用的用户设定（传达给角色的设定文本）
    val userSetting: String = "",
    // 叙事节奏（**旧字段，只读兼容**）："fast"快进 | ""标准 | "slow"细腻。
    // 0.1.3 起叙事风格走 NarrativeStyle（见下），读取时由 Conversation.narrativeStyle() 惰性迁移；新代码不要再写这两个字段。
    val pacing: String = "",
    // 叙事氛围（**旧字段，只读兼容**，可多选）："novel"小说 | "heart"心动；空 = 基础
    val atmosphere: List<String> = emptyList(),
    // 叙事风格（P-F1）：各轴正交，见 data/NarrativeStyles.kt。
    // 为 null = 老数据还没迁移过，此时按 pacing/atmosphere 推出等效风格，用户下次动任一轴才落盘。
    val narrative: NarrativeStyle? = null,
    // 角色记忆（会话级）：用户手动维护或 AI 自动整理，每轮注入 system prompt
    val memory: String = "",
    // 记忆整理已覆盖的消息数（下次增量更新的起点）
    val memoryUntil: Int = 0,
    // 前情提要（自动摘要）：长对话溢出部分由 AI 压缩，注入 system prompt
    val summary: String = "",
    // 摘要已覆盖的消息数（history 索引），避免重复摘要
    val summarizedUntil: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** UI 上把任意图片引用转成 Coil 可加载的 model */
fun imageModel(uri: String?): Any? {
    if (uri.isNullOrBlank()) return null
    return if (uri.startsWith("http://") || uri.startsWith("https://")) uri else java.io.File(uri)
}

/**
 * 这个引用是不是**本机文件路径**（相对于 http(s) 外链、空串、content:// 这类非文件引用）。
 *
 * 为什么要专门有它（第 59 轮，桌面端事故）：全项目过去一律写 `startsWith("/")` 当这个判据，
 * 那是 **Android 的形态**（`/data/user/0/...`）；桌面端 `Repository.saveImageBytes` 返回的是
 * `File.absolutePath`，形如 `D:\...\images\img_x.png`（盘符 + 反斜杠），**恒不以 `/` 开头**。
 * 判据恒假的表现全在"看不见的地方"，桌面端因此三处功能整体失效：
 * ① 「清除缓存」把 `referencedImagePaths()` 算成空集 ⇒ **用户所有图片（头像/背景/聊天图）都被当孤儿删掉**；
 * ② 全量备份的 `assets` 恒为空 ⇒ 换机恢复后图片全断链（同一个坑，Android 侧不存在）；
 * ③ 删除图片/字体文件的分支永不执行（`deleteImageFile` / `deleteFontFile`）。
 * 所以判据只能有一份、且必须两种形态都认。用它与 [imageModel] 的口径对齐：非外链即本机文件。
 */
fun isLocalFilePath(uri: String?): Boolean {
    if (uri.isNullOrEmpty()) return false
    if (uri.startsWith("/")) return true                       // Android / Linux：/data/user/0/...
    if (uri.length >= 3 && uri[1] == ':' && (uri[2] == '\\' || uri[2] == '/')) {
        return uri[0].isLetter()                               // Windows：D:\… 或 D:/
    }
    return uri.startsWith("\\\\")                              // Windows UNC：\\server\share
}

/**
 * 会话级背景音乐配置（第 63 轮，口径：背景音乐**只在会话中生效**、一个会话一份配置）。
 *
 * - [mode]：`"global"`=跟随全局默认（`AiSettings.bgm*`）｜`"off"`=本会话静音｜`"on"`=本会话用自己的音源。
 *   null 的 `Conversation.bgm` 与 `"global"` 等价，只是前者还表示"从没动过"。
 * - [sourceId]：mode=on 时生效——内置音源 id（`BgmSources.builtin`，如 `rain`）或音乐库曲目（`lib:<id>`，
 *   清单见 `Repository` 的 bgm 音乐库）。
 * - [volume]：**已退役（第 72 轮），不再参与解析**——音量全 App 只有一个数（`AiSettings.bgmVolume`），
 *   会话内滑条与设置页滑条共用它（口径：优先共享）。字段留着只为读得懂老 JSON：
 *   老会话里存过的会话音量会被忽略（不再出现"设置页调了、这个会话不跟着变"这种不生效）。
 */
@Serializable
data class SessionBgm(
    val mode: String = "global",
    val sourceId: String = "",
    val volume: Float = -1f
) {
    companion object {
        const val MODE_GLOBAL = "global"
        const val MODE_OFF = "off"
        const val MODE_ON = "on"
    }
}
