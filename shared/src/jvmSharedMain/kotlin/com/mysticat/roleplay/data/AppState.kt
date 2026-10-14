package com.mysticat.roleplay.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysticat.roleplay.ui.tokens.CustomSkin
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSkin
import com.mysticat.roleplay.ui.tokens.WhaleSkinSpec
import kotlinx.serialization.json.Json

/**
 * 主题模式（问题 #9）。
 * 曾有一个占位项 `"other"`（「其他」，行为与 SYSTEM 相同，留给皮肤中心）——
 * 用户 2026-10-10 要求撤掉：皮肤中心没往这个入口接任何东西，选项本身只让人困惑。
 * 老存档里万一存着 `"other"`，[ThemeState.ensureLoaded] 把它按当时的行为读成 SYSTEM。
 */
enum class ThemeMode(val id: String, val label: String) {
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    SYSTEM("system", "跟随系统");

    companion object {
        fun fromId(id: String?): ThemeMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 内置字体族。
 *
 * 注意：Android 内置字族里中文实际只有两种字形——默认的「黑体」(Noto Sans CJK)
 * 和「衬线/宋体风格」(Noto Serif CJK)。无衬线(sans-serif) 与等宽(monospace)
 * 在中文下都会回退到黑体，选了看不出区别，所以不再列出。
 * 想要宋体 / 仿宋 / 黑体等，请用「上传字体」自己导入 .ttf/.otf。
 */
enum class AppFont(val id: String, val label: String) {
    SYSTEM("", "系统默认（黑体）"),
    SERIF("serif", "衬线（宋体风格）");

    companion object {
        fun fromId(id: String?): AppFont = entries.firstOrNull { it.id == (id ?: "") } ?: SYSTEM
    }
}

/** 全局外观状态：MainActivity / MystiCatTheme 观察它来决定明暗与字体，设置页通过它修改并持久化 */
object ThemeState {
    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var font by mutableStateOf(AppFont.SYSTEM)
        private set
    var fontScale by mutableStateOf(1.0f)
        private set

    /** 用户上传的字体文件路径（优先于内置字体）；为空表示用内置 */
    var customFontPath by mutableStateOf<String?>(null)
        private set
    /** 上传字体的显示名（取自文件名） */
    var customFontName by mutableStateOf("")
        private set

    /** 已上传的全部字体（问题 #20：切换/删除都不再丢历史） */
    var customFonts by mutableStateOf<List<CustomFontEntry>>(emptyList())
        private set

    /** 当前皮肤 id（R4）：内置注册表键（"minimal"/"tidal"）；社区/自定义（R5）时与 [skinJson] 成对保存 */
    var skinId by mutableStateOf("minimal")
        private set

    /** 皮肤 JSON 原文（R5 皮肤中心起使用）；空 = 走 [skinId] 的内置档 */
    var skinJson by mutableStateOf("")
        private set

    /** 当前**生效**的皮肤（坏 JSON / 未知 id 时回落简约；[skinId] 保留原值供皮肤中心显示修复入口） */
    var skinSpec by mutableStateOf(WhaleSkin.Minimal)
        private set

    /** 当前皮肤点名的字体令牌（`spec.fonts.ui` 原值：sans/serif＝平台族，其余应是 SkinFontRepo 下载档 id） */
    var skinFontToken by mutableStateOf("sans")
        private set

    /** 皮肤字体（下载档）就绪后的本地路径；空＝未就绪或皮肤点名平台族。优先级低于用户上传字体。 */
    var skinFontPath by mutableStateOf<String?>(null)
        private set

    /**
     * **自定义皮肤列表**（2026-10-04 加）：用户导入的、或从皮肤中心下载的那些，多套并存。
     *
     * 与 [skinId]／[skinJson] 的分工：[skinJson] 仍记"当前生效的那一套"的原文（镜像，兼容老数据），
     * 本列表记**全部**——下第 2 套不会再把第 1 套顶掉。内置皮肤**不在这里**（它们在注册表里）。
     */
    var customSkins by mutableStateOf<List<CustomSkin>>(emptyList())
        private set

    private var loaded = false

    /** 首次从设置里读取；旧数据只有 darkTheme 布尔值时按它推导主题模式 */
    fun ensureLoaded() {
        if (loaded) return
        val s = Repository.loadSettings()
        mode = ThemeMode.fromId(s.themeMode)
            ?: when {
                // 已撤掉的旧项「其他」当年等同跟随系统，老存档读进来照旧办
                s.themeMode == "other" -> ThemeMode.SYSTEM
                s.darkTheme == true -> ThemeMode.DARK
                s.darkTheme == false -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }
        font = AppFont.fromId(s.fontFamily)
        fontScale = s.fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        customFontPath = s.customFontPath.takeIf { it.isNotBlank() }
        customFontName = s.customFontName
        // 旧数据只有一个自定义字体，读进来时补成列表
        customFonts = s.customFonts.ifEmpty {
            val p = s.customFontPath
            if (p.isNotBlank()) listOf(CustomFontEntry(p, s.customFontName.ifBlank { "自定义字体" }))
            else emptyList()
        }
        // 旧数据只有一个自定义皮肤（skinJson 单值），读进来时补成列表的第 0 项——
        // 与上面 customFonts 的兼容读同一套写法。老备份照常能用。
        customSkins = s.customSkins.mapNotNull { WhaleSkin.parseCustom(it) }.ifEmpty {
            listOfNotNull(WhaleSkin.parseCustom(s.skinJson))
        }
        applySkin(s.skinId, s.skinJson)
        loaded = true
    }

    /** 当前是否为暗色：浅色/深色写死，跟随系统看系统（主题设置里只剩这三档） */
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    fun setMode(m: ThemeMode, persist: Boolean = false) {
        mode = m
        if (persist) {
            Repository.saveSettings(Repository.loadSettings().copy(themeMode = m.id))
        }
    }

    /**
     * 切换皮肤：[id] 走内置注册表，[json] 非空则优先按它解析（R5 皮肤中心）。
     * 坏 JSON / 未知 id 回落简约、不崩；圆角三档随 spec.shape 即时生效（[WhaleShapes.apply]）。
     */
    fun setSkin(id: String, json: String = "", persist: Boolean = false) {
        applySkin(id, json)
        if (persist) {
            Repository.saveSettings(
                Repository.loadSettings().copy(skinId = skinId, skinJson = skinJson)
            )
        }
    }

    /**
     * 收下一套**自定义**皮肤并存进列表（同 [id] 覆盖而不是追加两份），
     * [applyNow] 为真时顺手套上。坏 JSON 抛 `IllegalArgumentException`——调用处自己决定给什么文案。
     *
     * [persist] 与 [setSkin] 同一套约定：自检里必须传 `false`（不碰真实设置文件）。
     */
    fun addCustomSkin(json: String, applyNow: Boolean = true, persist: Boolean = true): CustomSkin {
        val skin = WhaleSkin.parseCustom(json)
            ?: throw IllegalArgumentException("不是有效的皮肤文件（.whale-skin.json 解析失败）")
        customSkins = customSkins.filterNot { it.id == skin.id } + skin
        if (applyNow) applySkin(skin.id, skin.json)
        if (persist) persistSkins()
        return skin
    }

    /** 移除一套自定义皮肤；若它正生效着，回落简约（不留一个"指向已删项"的悬空 id） */
    fun removeCustomSkin(id: String, persist: Boolean = true) {
        val wasEffective = skinId == id && skinJson.isNotBlank()
        customSkins = customSkins.filterNot { it.id == id }
        if (wasEffective) applySkin(WhaleSkin.Minimal.id, "")
        if (persist) persistSkins()
    }

    /**
     * 自定义列表 **＋ 当前生效那一套的镜像** 一起落盘。
     * [skinJson] 看着是历史包袱，其实是老数据的读入口（`ensureLoaded` 从它补列表），
     * 镜像不同步的话一次切换就会让下次启动读出错的皮肤。
     */
    private fun persistSkins() {
        Repository.saveSettings(
            Repository.loadSettings().copy(
                customSkins = customSkins.map { it.json },
                skinId = skinId,
                skinJson = skinJson,
            )
        )
    }

    private fun applySkin(id: String, json: String) {
        skinId = id
        skinJson = json
        val spec = if (json.isNotBlank()) {
            runCatching { Json.decodeFromString<WhaleSkinSpec>(json) }.getOrNull()
        } else {
            WhaleSkin.builtinById(id)
        }
        val effective = spec ?: WhaleSkin.Minimal
        skinSpec = effective
        WhaleShapes.apply(effective.shape.r1, effective.shape.r2, effective.shape.r3)
        resolveSkinFont(effective)
    }

    /**
     * 皮肤字体解析（第 17 轮；优先级＝用户上传 ＞ 皮肤自带 ＞ 内置档）：
     * 下载档查本地缓存，没有就触发后台拉取（不阻塞套用，完成后经 [onSkinFontReady] 回接主题）；
     * `serif`＝平台衬线（深海口径），Theme 侧读 [skinFontToken] 处理；sans/未知＝无动作。
     */
    private fun resolveSkinFont(spec: WhaleSkinSpec) {
        skinFontToken = spec.fonts.ui
        val file = SkinFontRepo.byId(spec.fonts.ui)
        skinFontPath = file?.let { SkinFontRepo.readyPath(it.id) }
        if (file != null && skinFontPath == null) SkinFontRepo.ensure(file.id)
    }

    /** SkinFontRepo 下载完成回调：只有当前皮肤点名的字体才接进主题（先切走的不接）。 */
    fun onSkinFontReady(id: String) {
        if (skinFontToken != id) return
        SkinFontRepo.readyPath(id)?.let { skinFontPath = it }
    }

    fun setFont(f: AppFont, persist: Boolean = false) {
        font = f
        if (persist) {
            Repository.saveSettings(Repository.loadSettings().copy(fontFamily = f.id))
        }
    }

    fun setFontScale(v: Float, persist: Boolean = false) {
        fontScale = v.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        if (persist) {
            Repository.saveSettings(Repository.loadSettings().copy(fontScale = fontScale))
        }
    }

    /** 设置/清除用户上传的字体。path 传 null 或空串表示恢复内置字体。 */
    fun setCustomFont(path: String?, name: String, persist: Boolean = false) {
        val p = path?.takeIf { it.isNotBlank() }
        customFontPath = p
        customFontName = if (p == null) "" else name
        // 问题 #20：上传/切换的字体都留在列表里，之后可以来回切
        if (p != null && customFonts.none { it.path == p }) {
            customFonts = customFonts + CustomFontEntry(p, name.ifBlank { "自定义字体" })
        }
        if (persist) {
            Repository.saveSettings(
                Repository.loadSettings().copy(
                    customFontPath = customFontPath ?: "",
                    customFontName = customFontName,
                    customFonts = customFonts
                )
            )
        }
    }

    /** 删除一份已上传字体；删的正好是当前在用的就回退内置（问题 #20） */
    fun removeCustomFont(entry: CustomFontEntry) {
        customFonts = customFonts.filterNot { it.path == entry.path }
        if (customFontPath == entry.path) {
            customFontPath = null
            customFontName = ""
        }
        Repository.deleteFontFile(entry.path)
        Repository.saveSettings(
            Repository.loadSettings().copy(
                customFontPath = customFontPath ?: "",
                customFontName = customFontName,
                customFonts = customFonts
            )
        )
    }

    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 1.40f
}
