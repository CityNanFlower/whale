package com.mysticat.roleplay.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 主题模式（问题 #9）。
 * OTHER 预留给后续「皮肤中心」：现阶段与 SYSTEM 一样跟随系统，
 * 等皮肤中心接入后在这里读取皮肤自带的明暗定义。
 */
enum class ThemeMode(val id: String, val label: String) {
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    SYSTEM("system", "跟随系统"),
    OTHER("other", "其他");

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

    private var loaded = false

    /** 首次从设置里读取；旧数据只有 darkTheme 布尔值时按它推导主题模式 */
    fun ensureLoaded() {
        if (loaded) return
        val s = Repository.loadSettings()
        mode = ThemeMode.fromId(s.themeMode)
            ?: when (s.darkTheme) {
                true -> ThemeMode.DARK
                false -> ThemeMode.LIGHT
                null -> ThemeMode.SYSTEM
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
        loaded = true
    }

    /** 当前是否为暗色：浅色/深色写死，跟随系统与其他（皮肤中心未接入）都看系统 */
    fun isDark(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, ThemeMode.OTHER -> systemDark
    }

    fun setMode(m: ThemeMode, persist: Boolean = false) {
        mode = m
        if (persist) {
            Repository.saveSettings(Repository.loadSettings().copy(themeMode = m.id))
        }
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
