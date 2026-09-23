package com.mysticat.roleplay.data

/**
 * 角色专属音色的解析（E 批次，2026-09-21）。
 *
 * 一条链路只在这里被翻译一次：**全局设置 + 角色卡 → 真正拿去合成的设置**。
 * 朗读（系统 TTS / 供应商合成）、试听、缓存键全部吃这个结果，所以"换角色换音色"
 * 与"同一句话不重复付费"两件事自动同时成立（缓存键取自生效设置里的地址/模型/音色/语速/音高）。
 *
 * 三条口径，都是照着用户原话来定的：
 * 1. **开关开了才覆盖，覆盖就是整套覆盖**。卡的 provider/baseUrl/model/voice/speed/pitch 全部生效，
 *    不与全局做逐字段混合——半套覆盖最难解释"为什么听起来是这样"。
 *    **混合音色也是这套里面的**（2026-09-22）：卡开着混音就按卡的源发，卡没开就关掉全局混音。
 * 2. **认家**：卡里带的合成地址在本机直接认得就用它；换机器、换镜像导致本机没这个地址时，
 *    按 `provider` 关键词找**本机同一家**的地址（卡片只要写对家，就不必跟着对方的域名走）。
 * 3. **绝不静默改写**：本机没有这个家、或清单里没有这个音色，字段一律原样保留，
 *    只在编辑页标一行字（用户原话"不好实现就算了"，所以只做"能匹配就直接用"）。
 */
object CharacterVoices {

    /** 卡里音色在本机的落地情况（编辑页那一行提示用它，别的都不该据此改数据） */
    data class Availability(
        /** 实际会用的合成地址（可能已被"认家"换成本机那家的地址） */
        val baseUrl: String,
        /** 本机认得这个地址（内置预设里能匹配到，或它就是你加过的自定义供应商） */
        val homeFound: Boolean,
        /** 这家有可用凭据（本地 http 服务视为可用——它本来就不需要 Key） */
        val credentialOk: Boolean,
        /** 卡里的音色在这家的预设清单里。清单为空（未知家 / 手填 id 的家）时视作可用，不误报 */
        val voiceKnown: Boolean
    )

    /**
     * 把卡上的音色并进全局设置。下面任一条不成立就**原样返回 [global]**（＝与本轮之前完全一致）：
     * 卡没有 voice、开关没开、既没写音色又没凑成混音、连合成地址都定不下来。
     */
    fun effective(global: AiSettings, card: CharacterCard?): AiSettings {
        val v = card?.voice ?: return global
        if (!v.enabled) return global
        val mix = v.mixSources()
        // 混音卡可以**不填单音色**（火山混音时 speaker 固定 custom_mix_bigtts，单音色栏根本不参与合成），
        // 所以"有内容"的判定要算上混音源，否则混音卡会被当成空卡直接放弃覆盖。
        if (v.voice.isBlank() && mix.size < 2) return global
        val url = resolveUrl(global, v)
        if (url.isBlank()) return global
        // 混音是火山专属协议（`custom_mix_bigtts` + `mix_speaker.speakers[]`），别家的地址发过去必是 400：
        // 认不出这家是火山时按单音色走，与卡里没开混音一样（宁可听起来是单音色，也不要直接报错）。
        val useMix = mix.size >= 2 && ProviderProfiles.speechProtocol(url) == ProviderProfiles.SpeechProtocol.VOLC
        return global.copy(
            // 卡里既然指定了音色，引擎必然是"供应商合成"——系统 TTS 没有换音色这一说
            ttsProvider = "builtin",
            ttsBaseUrl = url,
            ttsModel = v.model,
            ttsVoice = v.voice,
            ttsSpeed = v.speed,
            ttsPitch = v.pitch,
            // 混合音色从 2026-09-22 起**也属于角色音色**（用户反馈"角色专属音色也要支持混合音色"）：
            // 卡开着混音就按卡的源与权重发；卡没开就显式关掉全局混音——不关的话，全局开着混音时
            // 卡的音色会被 `custom_mix_bigtts` 顶掉，表现成"个性化没生效"（这正是老口径要解决的问题）。
            ttsMixEnabled = useMix,
            ttsMixSpeakers = if (useMix) mix else emptyList()
        )
    }

    /**
     * 这张卡的音色**到底会不会生效**（而不是原样退回全局设置）。
     *
     * 判据就是 [effective] 的引用身份：生效时它返回一份 `copy`，不生效时原样返回 [global]。
     * 界面用它区分"设了就是这把声音"与"设了但缺东西、实际还在用全局音色"——后者最坑：
     * 试听会放出全局音色，用户会以为"个性化没生效"，而界面上一点提示都没有。
     */
    fun applies(global: AiSettings, card: CharacterCard?): Boolean = effective(global, card) !== global

    /** 卡里音色在本机的落地情况（[effective] 的真值来源，UI 提示用同一份判断） */
    fun availability(global: AiSettings, voice: CharacterVoice): Availability {
        val url = resolveUrl(global, voice)
        val home = url.isNotBlank() && isKnownHome(global, url)
        val presets = if (url.isBlank()) emptyList() else ModelCatalog.speechVoices(url, voice.model)
        val mix = voice.mixSources()
        return Availability(
            baseUrl = url,
            homeFound = home,
            credentialOk = url.isNotBlank() &&
                // 与语音设置页同一口径：本地 http 服务（内网部署）本来就不需要 Key
                (global.hasSpeechCredential(url) || !url.startsWith("https://")),
            // 没有预设清单的家（自定义 / 本地部署 / 手填 id）无从判断，视作可用。
            // ⚠ 混音的源要拿**混音源清单**去判，不能拿单音色预设（两码事，会误报"这个音色没有"）。
            voiceKnown = when {
                voice.mixEnabled && mix.size >= 2 -> {
                    val sources = ModelCatalog.volcMixSourceVoices()
                    sources.isEmpty() || mix.all { it.voice in sources }
                }
                else -> presets.isEmpty() || voice.voice in presets
            }
        )
    }

    /**
     * 定合成地址。优先级：**卡里那个地址本机认得** → 按 [CharacterVoice.provider] 找本机同家的地址
     * → 卡里那个地址（本机还没有这个家也照发，失败会走既有的"回退系统语音 + 提示"路径，
     * 比悄悄换成别的声音诚实）。全空返回 ""（调用方据此放弃覆盖）。
     */
    private fun resolveUrl(global: AiSettings, v: CharacterVoice): String {
        val own = v.baseUrl.trim()
        val byKeyword = localUrlForKeyword(v.provider)
        return when {
            own.isNotBlank() && (isKnownHome(global, own) || byKeyword == null) -> own
            byKeyword != null -> byKeyword
            else -> own
        }
    }

    /** 本机有没有这个家：内置语音预设认得，或者它是你加过的自定义供应商（本地部署走这条） */
    private fun isKnownHome(global: AiSettings, url: String): Boolean =
        ProviderProfiles.supportsSpeech(url) || ProviderProfiles.resolve(url, global.customProviders) != null

    /**
     * 按关键词找**本机**这一家的语音地址。
     * 只查内置预设：自定义供应商的 keyword 是 `custom:<id>`（含本机 id，卡里不会、也不该出现）。
     */
    private fun localUrlForKeyword(keyword: String): String? {
        val kw = keyword.trim().lowercase()
        if (kw.isBlank()) return null
        return ProviderProfiles.speechProviderUrls()
            .firstOrNull { ModelCatalog.speechProviderKeyword(it) == kw }
    }

    // ────────────────────────── 声音类型（第 69 轮）──────────────────────────

    /**
     * 声音的"类型"：**单一音色 / 混合音色 / 复刻音色**——界面上是一排胶囊，用户一眼看清自己在配哪一种。
     *
     * 为什么不新增一个存储字段、而是**从已有数据推**：类型与数据一旦各存一份，就可能出现
     * "类型写着混合、数据其实只有 1 个源"这种自相矛盾的状态，卡与设置的往返、导出导入的保真
     * 都要多守一条不变量。推出来的类型**不可能与数据打架**，也别无第二处口径。
     */
    enum class VoiceKind { SINGLE, MIX, CLONE }

    /**
     * 判断当前是哪种声音类型。顺序即优先级：
     * ① 混音开关开着且凑够 2 个源 = 混合（此时单音色栏不参与合成，忽略它）
     * ② 音色 id 是复刻音色（`S_` / `icl_` / **本机复刻音色库里的 id**）或模型栏已指到 ICL 那一档 = 复刻
     * ③ 其余 = 单一音色
     *
     * [mixSupported] 是"这家支不支持混音"（只有火山）：**必须传** —— 换供应商时 `mixEnabled` 不会被自动清掉，
     * 不带上它就会出现"切到别家后类型仍是混合、音色栏被藏起来又找不到混音源"的死角
     * （`effective` 早就是按这条口径忽略别家混音的，这里与它对齐）。
     *
     * [cloneIds] 是**本机复刻音色库**（[AiSettings.ttsCloneVoices] 的 id）。录音复刻出来的音色 id
     * 是我们自己命名的、前缀毫无特征，不查库就认不出来（见 `CloneVoice` 的注释）。
     */
    fun kindOf(
        voice: String,
        model: String,
        mixEnabled: Boolean,
        mixSources: List<MixSpeaker>,
        mixSupported: Boolean = true,
        cloneIds: Collection<String> = emptyList()
    ): VoiceKind = when {
        mixSupported && mixEnabled && mixSources.count { it.voice.isNotBlank() } >= 2 -> VoiceKind.MIX
        isCloneVoice(voice, model, cloneIds) -> VoiceKind.CLONE
        else -> VoiceKind.SINGLE
    }

    /** 便捷重载：卡/设置里的音色块直接判类型（判据与五参数版完全同一份） */
    fun kindOf(
        v: CharacterVoice,
        mixSupported: Boolean = true,
        cloneIds: Collection<String> = emptyList()
    ): VoiceKind = kindOf(v.voice, v.model, v.mixEnabled, v.mixSources(), mixSupported, cloneIds)

    /**
     * 是不是复刻音色。**不新增字段**：火山自建音色是 `S_` 开头、查询接口返回的是 `icl_` 开头
     * （见 `ModelCatalog.volcResourceIdForVoice`），或模型栏已经被指到 `seed-icl-2.0` 那一档
     * （用户选了「复刻音色」而还没粘 id 时就是这个状态，胶囊因此不会一点就跳回"单一"）。
     *
     * [cloneIds] 见 [kindOf]：录音复刻出来的 id 没有任何前缀特征，只能靠这份清单认。
     */
    fun isCloneVoice(
        voice: String,
        model: String,
        cloneIds: Collection<String> = emptyList()
    ): Boolean {
        val v = voice.trim()
        return v.startsWith("S_") || v.startsWith("icl_") ||
            model.trim() == "seed-icl-2.0" ||
            (v.isNotEmpty() && cloneIds.any { it.trim() == v })
    }
}
