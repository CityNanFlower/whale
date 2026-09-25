package com.mysticat.roleplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

/**
 * 桌面首启引导页（M13 ③，第 43 轮）：数据 / 缓存目录说明与配置 ＋ 桌面快捷方式勾选。
 *
 * 为什么是桥：真正摸 PowerShell（写 .lnk）与 AWT 目录选择框的代码只能住在 desktopApp
 * （jvmSharedMain 会被 Android 一起编译），而界面在共享层——所以模式与 [DesktopFeatures] 相同：
 * 这里放状态与接口，实现由 desktopApp 在启动时 [install]。Android 永远走不到 install，
 * 「外观」卡里的入口行也因此不出现，零回归。
 *
 * 目录配置的存储口径（台账 9(b)/14，用户 2026-09-18 拍板）：
 *  - 配置落锚点目录的 `paths.properties`——**不随数据目录走**的锚点目录（打包态＝
 *    `%APPDATA%\MysticatRoleplay`；开发态＝`%APPDATA%\MysticatRoleplay-dev`，2026-09-21 起两边分家），
 *    否则"改了数据目录后下次启动找不到配置"（配置必须先于数据目录被定位）；
 *  - `dataDir` / `cacheDir` 写**生效的绝对路径**（含默认值），解析端读到即用，缺省回默认；
 *  - 改目录**不搬旧数据**、下次启动生效，界面明确提示（引导页首启只出现一次，之后从
 *    「用户 → 外观与设置 → 数据与缓存目录」再进）；
 *  - 完成标记 `onboarding.done` 也在锚点目录——已升级的老用户没有它，会看到一次引导页
 *    （顺势补一个桌面快捷方式，无害且有用）。
 */
object DesktopOnboarding {
    interface Impl {
        /** 当前是否经 jpackage 启动器（exe）运行——只有安装版才知道 .lnk 该指向谁 */
        val shortcutAvailable: Boolean

        /** 在桌面建「鲸鱼.lnk」指向当前 exe（只认 jpackage.app-path，dev 返回 false） */
        fun createDesktopShortcut(): Boolean

        /** 弹目录选择框；取消返回 null（调用发生在 Compose UI 线程＝AWT EDT，可直接弹模态框） */
        fun pickDirectory(current: String): String?

        /** 目标目录是否在本应用安装目录树内（含等于安装目录）；dev 无安装目录恒 false（台账 12） */
        fun isInsideInstallDir(path: String): Boolean

        /** 排一个"本进程退出后拉起当前 exe"的重启计划；false＝无法自动重启（dev）（台账 1） */
        fun scheduleRestart(): Boolean
    }

    /** 桥已安装（desktopApp 正常窗口 / --shot 路径装了；Android 与 --smoke 之外的早退分支没有） */
    var installed by mutableStateOf(false)
        private set

    /** 是否显示覆盖层；desktopApp 首启（无完成标记）或 --open=onboarding、设置页入口置 true */
    var showOverlay by mutableStateOf(false)
        private set

    /** 首启＝必须点「开始使用」收尾；从设置页再进时额外给「取消」 */
    var firstLaunch by mutableStateOf(true)
        private set

    /** 本机支持建快捷方式（经 jpackage 启动器跑；dev 置灰并说明原因） */
    var shortcutSupported by mutableStateOf(false)
        private set

    /** 目录选择框可用（桥带了实现即可用；--shot 离屏与 Android 没有） */
    var pickerAvailable by mutableStateOf(false)
        private set

    var dataDirText by mutableStateOf("")
        private set
    var cacheDirText by mutableStateOf("")
        private set
    var createShortcut by mutableStateOf(true)
        private set

    /** 保存后的结果提示（覆盖层内自显；目录没改且全部成功时为 null） */
    var resultMessage by mutableStateOf<String?>(null)
        private set

    /** 保存成功且目录有改动后待确认的"立即重启生效"（台账 1；本机支持自重启才有机会为 true） */
    var pendingRestart by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    private lateinit var configFile: File
    private lateinit var markerFile: File
    private var defaultDataDir = ""
    private var defaultCacheDir = ""
    private var impl: Impl? = null

    /**
     * [config] = paths.properties（锚点目录下），[marker] = onboarding.done（同目录）。
     * [effectiveDataDir] / [effectiveCacheDir] 是本次启动实际生效的目录——引导页默认值即它们，
     * 保存时据此判断"目录改过没"（没改就不提示重启）。
     */
    fun install(
        config: File,
        marker: File,
        effectiveDataDir: String,
        effectiveCacheDir: String,
        firstLaunchNow: Boolean,
        implNow: Impl?
    ) {
        configFile = config
        markerFile = marker
        defaultDataDir = effectiveDataDir
        defaultCacheDir = effectiveCacheDir
        firstLaunch = firstLaunchNow
        impl = implNow
        // 已有配置则以配置值为准（再进引导页时看到的是"下次启动会生效"的那份）
        dataDirText = configValue(config, "dataDir") ?: effectiveDataDir
        cacheDirText = configValue(config, "cacheDir") ?: effectiveCacheDir
        createShortcut = true
        shortcutSupported = implNow != null &&
            runCatching { implNow.shortcutAvailable }.getOrDefault(false)
        pickerAvailable = implNow != null
        installed = true
        if (firstLaunchNow) showOverlay = true
    }

    private fun configValue(config: File, key: String): String? = runCatching {
        if (!config.isFile) return@runCatching null
        val p = Properties()
        config.inputStream().use { p.load(it) }
        p.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 从「外观与设置」再进引导页 */
    fun reopen() {
        if (!installed) return
        resultMessage = null
        showOverlay = true
    }

    fun closeOverlay() {
        showOverlay = false
        resultMessage = null
    }

    /** 保存后把结果提示展示在覆盖层里（覆盖层点「保存」后覆层已关，需要时再拉开） */
    fun showResult(msg: String) {
        resultMessage = msg.takeIf { it.isNotBlank() }
        if (resultMessage != null) showOverlay = true
    }

    fun updateDataDir(v: String) {
        dataDirText = v
        resultMessage = null
    }

    fun updateCacheDir(v: String) {
        cacheDirText = v
        resultMessage = null
    }

    fun browseDataDir() {
        impl?.pickDirectory(dataDirText)?.let { updateDataDir(it) }
    }

    fun browseCacheDir() {
        impl?.pickDirectory(cacheDirText)?.let { updateCacheDir(it) }
    }

    fun resetDataDir() = updateDataDir(defaultDataDir)
    fun resetCacheDir() = updateCacheDir(defaultCacheDir)

    fun toggleShortcut(on: Boolean) {
        createShortcut = on
    }

    /** 目录合法性：非空、绝对路径（Windows 上 File.isAbsolute 覆盖盘符与 UNC 两种写法） */
    fun validDir(path: String): Boolean =
        path.trim().length >= 4 && runCatching { File(path.trim()).isAbsolute }.getOrDefault(false)

    /** 目标目录是否落在本应用安装目录树内（台账 12；dev 无安装目录恒 false） */
    fun insideInstallDir(path: String): Boolean =
        runCatching { impl?.isInsideInstallDir(path.trim()) }.getOrDefault(false) == true

    /** 本机能否"退出后自动拉起新实例"（jpackage 安装版才有 exe 可拉；dev 降级为提示手动重启） */
    fun restartSupported(): Boolean =
        installed && impl != null && runCatching { impl!!.shortcutAvailable }.getOrDefault(false)

    /** 确认弹层里选「立即重启」：排好外壳后优雅退出（落窗口状态、释放单实例锁） */
    fun restartNow() {
        pendingRestart = false
        val ok = runCatching { impl?.scheduleRestart() ?: false }.getOrDefault(false)
        if (ok) {
            DesktopExit.requestExit?.invoke()
        } else {
            resultMessage = "无法自动重启，请手动退出并重新打开鲸鱼使新目录生效。"
            showOverlay = true
        }
    }

    /** 确认弹层里选「下次启动生效」＝维持旧口径 */
    fun dismissRestart() {
        pendingRestart = false
    }

    /**
     * 收尾（IO 线程做）：写 paths.properties ＋ 完成标记，勾了快捷方式就建。
     * 返回给界面显示的提示文案；全部成功且目录没改 = 空串（无提示直接进应用）。
     */
    suspend fun complete(): String = withContext(Dispatchers.IO) {
        if (!installed || busy) return@withContext ""
        // 台账 12：拒绝把数据/缓存目录设进安装目录树——1.0.1 实测后果是运行时残缺
        // （缺 jsound.dll→TTS 全挂、缺 java.security→加密明文回退），且 MSI 卸载会连根删除
        // 安装目录、连同混在里面的用户数据。在写任何配置之前就挡下，首启标记也不动。
        val rejected = listOf("数据目录" to dataDirText, "缓存目录" to cacheDirText)
            .firstOrNull { insideInstallDir(it.second) }
        if (rejected != null) {
            return@withContext "${rejected.first}不能设在应用安装目录里面（${rejected.second}）：" +
                "卸载程序会连它一起删除，程序运行也会因此残缺。请换一个安装目录之外的位置。"
        }
        busy = true
        try {
            val notes = mutableListOf<String>()
            runCatching {
                configFile.parentFile?.mkdirs()
                val p = Properties()
                p.setProperty("dataDir", dataDirText.trim())
                p.setProperty("cacheDir", cacheDirText.trim())
                p.store(configFile.outputStream(), "鲸鱼桌面数据/缓存目录（下次启动生效；改这里不会搬移已有数据）")
            }.onFailure { notes.add("目录配置保存失败：${it.message}") }

            if (createShortcut && shortcutSupported) {
                val ok = runCatching { impl?.createDesktopShortcut() ?: false }.getOrDefault(false)
                if (!ok) notes.add("桌面快捷方式创建失败（可稍后从「外观与设置」重试）")
            }
            runCatching { markerFile.writeText("ok") }
                .onFailure { notes.add("完成标记写盘失败：${it.message}") }
            firstLaunch = false
            showOverlay = false
            val changed = dataDirText.trim() != defaultDataDir || cacheDirText.trim() != defaultCacheDir
            if (changed) {
                // 台账 1：能自重启就弹"立即重启生效"确认（外壳等本进程退出再拉新实例，不撞单实例锁）；
                // dev 没有 exe 可拉，维持旧的"下次启动生效"文案
                if (restartSupported()) pendingRestart = true
                else notes.add("目录更改将在下次启动生效；已有数据不会自动搬移")
            }
            notes.joinToString("；")
        } finally {
            busy = false
        }
    }
}

/**
 * 引导页覆盖层：盖住主界面（标题栏仍可用——窗口能拖动/关闭）。
 * --shot --open=onboarding 也能渲染它（impl=null：浏览与快捷方式置灰，纯布局验收）。
 */
@Composable
fun DesktopOnboardingOverlay() {
    if (!DesktopOnboarding.showOverlay) return
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "欢迎使用鲸鱼 🐳",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "鲸鱼是纯本地的 AI 角色扮演应用：角色卡、会话记录与 API Key 都只保存在你自己的电脑上，" +
                        "没有云端同步。下面两个目录就是数据存放的位置，首次使用前可以先确认或更改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DirectoryField(
                    label = "数据目录",
                    hint = "角色卡 · 会话记录 · 账号与 API Key · 生成的图片",
                    value = DesktopOnboarding.dataDirText,
                    onValue = { DesktopOnboarding.updateDataDir(it) },
                    onBrowse = { DesktopOnboarding.browseDataDir() },
                    onReset = { DesktopOnboarding.resetDataDir() }
                )
                DirectoryField(
                    label = "缓存目录",
                    hint = "语音合成音频 · 语音识别临时文件（可随时整个删掉，会自动重建）",
                    value = DesktopOnboarding.cacheDirText,
                    onValue = { DesktopOnboarding.updateCacheDir(it) },
                    onBrowse = { DesktopOnboarding.browseCacheDir() },
                    onReset = { DesktopOnboarding.resetCacheDir() }
                )
                if (!DesktopOnboarding.validDir(DesktopOnboarding.dataDirText) ||
                    !DesktopOnboarding.validDir(DesktopOnboarding.cacheDirText)
                ) {
                    Text(
                        "目录需要是完整的绝对路径（如 D:\\鲸鱼数据）",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 台账 12：目录落进安装目录树＝卸载连根删＋运行时残缺，当场提示并禁用保存
                val dirInsideInstall = DesktopOnboarding.insideInstallDir(DesktopOnboarding.dataDirText) ||
                    DesktopOnboarding.insideInstallDir(DesktopOnboarding.cacheDirText)
                if (dirInsideInstall) {
                    Text(
                        "目录不能设在应用安装目录里面：卸载程序会连它一起删除，程序运行也会因此残缺。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                HorizontalDivider()
                Text(
                    "更改目录在下次启动生效，且不会自动搬移已有数据；之后可从「用户 → 外观与设置 → 数据与缓存目录」再改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = DesktopOnboarding.createShortcut,
                        enabled = DesktopOnboarding.shortcutSupported,
                        onCheckedChange = { DesktopOnboarding.toggleShortcut(it) }
                    )
                    Icon(Icons.Filled.DesktopWindows, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.padding(start = 6.dp))
                    Column {
                        Text("创建桌面快捷方式", style = MaterialTheme.typography.bodyMedium)
                        if (!DesktopOnboarding.shortcutSupported) {
                            Text(
                                "仅安装版可用（当前是开发运行）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DesktopOnboarding.resultMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!DesktopOnboarding.firstLaunch) {
                        TextButton(onClick = { DesktopOnboarding.closeOverlay() }) { Text("取消") }
                    }
                    Button(
                        enabled = !DesktopOnboarding.busy &&
                            DesktopOnboarding.validDir(DesktopOnboarding.dataDirText) &&
                            DesktopOnboarding.validDir(DesktopOnboarding.cacheDirText) &&
                            !dirInsideInstall,
                        onClick = {
                            scope.launch {
                                DesktopOnboarding.showResult(DesktopOnboarding.complete())
                            }
                        }
                    ) {
                        Text(if (DesktopOnboarding.firstLaunch) "开始使用" else "保存")
                    }
                }
            }
        }
    }

    // 台账 1：目录改完保存成功后弹"立即重启生效"（选「下次启动生效」＝维持旧口径）
    if (DesktopOnboarding.pendingRestart) {
        androidx.compose.material3.AlertDialog(
            // 首启只给「立即重启」（用户 2026-09-21）：选了"下次启动生效"用户会继续在默认目录里用，
            // Key/角色全写进旧位置，改天确认旧目录清理就把这批数据丢了
            onDismissRequest = { if (!DesktopOnboarding.firstLaunch) DesktopOnboarding.dismissRestart() },
            title = { Text("重启以生效") },
            text = {
                Text(
                    "新目录已保存，重启鲸鱼后生效。点「立即重启」鲸鱼会自动退出并重新打开" +
                        "（数据不会搬移；旧目录若还有残留，之后会提示你处理）。"
                )
            },
            confirmButton = {
                TextButton(onClick = { DesktopOnboarding.restartNow() }) { Text("立即重启") }
            },
            dismissButton = {
                if (!DesktopOnboarding.firstLaunch) {
                    TextButton(onClick = { DesktopOnboarding.dismissRestart() }) { Text("下次启动生效") }
                }
            }
        )
    }
}

/**
 * 旧数据目录处理弹层（台账 3）：启动检查发现旧默认目录还有用户数据时出现。
 * 「删除」就是二次确认本身——文案写明完整路径，绝不默认删（第 37 轮事故铁律）。
 */
@Composable
fun DesktopOldDirDialog() {
    val dir = DesktopOldDirNotice.dirWithData ?: return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { DesktopOldDirNotice.dirWithData = null },
        title = { Text("旧数据目录还有数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("发现旧数据目录里还有角色卡、会话等用户数据，鲸鱼没有自动删除它：")
                Text(dir, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Text(
                    "鲸鱼已改用新数据目录，旧目录不会再被写入。可以把里面的数据手动搬走后删除，" +
                        "也可以确认不再需要后在这里直接删除（配置文件会保留）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Platform.ui.deleteOldDataDir()) {
                    showToast("已删除旧数据目录内容：$dir")
                } else {
                    showToast("删除失败，请手动处理：$dir", long = true)
                }
                DesktopOldDirNotice.dirWithData = null
            }) { Text("删除旧目录数据") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    Platform.ui.revealFile(dir)
                    DesktopOldDirNotice.dirWithData = null
                }) { Text("打开文件夹") }
                TextButton(onClick = { DesktopOldDirNotice.dirWithData = null }) { Text("暂不处理") }
            }
        }
    )
}

@Composable
private fun DirectoryField(
    label: String,
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    onBrowse: () -> Unit,
    onReset: () -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = !DesktopOnboarding.validDir(value),
            trailingIcon = {
                Row {
                    IconButton(onClick = onBrowse, enabled = DesktopOnboarding.pickerAvailable) {
                        Icon(Icons.Filled.Folder, "浏览…")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Filled.RestartAlt, "恢复默认")
                    }
                }
            }
        )
    }
}
