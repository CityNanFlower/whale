package com.mysticat.roleplay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Modifier
import androidx.savedstate.read
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.DesktopContentBox
import com.mysticat.roleplay.ui.DesktopSplitPane
import com.mysticat.roleplay.ui.MainScreen
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.blurOnEmptyTap
import com.mysticat.roleplay.ui.isDesktopLayout
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.screens.AuthScreen
import com.mysticat.roleplay.ui.screens.BgmScreen
import com.mysticat.roleplay.ui.screens.CharacterEditorScreen
import com.mysticat.roleplay.ui.screens.ChatScreen
import com.mysticat.roleplay.ui.screens.DesktopConversationRail
import com.mysticat.roleplay.ui.screens.ManualScreen
import com.mysticat.roleplay.ui.screens.SettingsScreen
import com.mysticat.roleplay.ui.screens.VoiceScreen

/**
 * 截图专用（`--shot --open=about`）：有些界面只由"某页深处某一行"点出来（如「版本与安全」弹窗），
 * `--route` / `--tab` 都到不了，而离屏画布又是固定 1440×900、滚不到它。这里给它一个显式开关，
 * **正常启动永远读不到 true**（只有 desktopApp 的 `--shot` 分支会设它）。
 */
object ShotOverrides {
    @Volatile
    var openVersionDialog = false

    /**
     * `--open=onboarding`（第 43 轮）：直接摊开**首启引导页**覆盖层。它只在"锚点目录没有完成标记"时
     * 出现，而开发机的标记早就有了——离屏截图与真窗口要验它都得靠这个开关。
     * **正常启动永远读不到 true**（只有 desktopApp 的 `--open=` 分支会设它）。
     */
    @Volatile
    var openOnboarding = false

    /**
     * `--open=editor` / `--open=newchar`（第 34 轮）：直接摊开第三栏里的**角色编辑器**。
     * 它和「版本与安全」弹窗一样属于"只在页面深处点得到"的界面（角色卡右键 → 编辑），
     * `--route` / `--tab` / `--click` 都到不了。值 = 目标角色 id、[EDITOR_FIRST] 或 `"new"`。
     * **正常启动永远是 null**（只有 desktopApp 的 `--open=` 分支会设它）。
     */
    @Volatile
    var editorTarget: String? = null

    /**
     * 占位值："随便挑一张已有角色卡"。真 id 要等 `bootstrap` 跑完、仓储能读了才定得下来，
     * 而参数解析发生在它之前——所以到这里（组合期）再换算，见 `MainScreen.shotEditorTarget`。
     */
    const val EDITOR_FIRST = "@first"
}

/**
 * 导航图与路由表（M4 第 21 轮从 androidMain 上移到共享层）。
 *
 * 之前它住在 `MainActivity.kt` 里，桌面端就看不到；现在两端共用同一张导航图，
 * 平台差异只留在各自的入口壳（Android=MainActivity，桌面=desktopApp main）。
 */
object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
    const val EDITOR = "editor/{characterId}"
    const val CHAT = "chat/{characterId}/{conversationId}"
    const val SETTINGS = "settings"
    /** 语音朗读与语音输入（TTS v2 Step 2：从「模型与API」搬出来的独立页，入口在「我的」） */
    const val VOICE = "voice"
    /** 背景音乐（第 63 轮：音乐库 + 全局默认；会话级配置在聊天页菜单里，入口在「我的」） */
    const val BGM = "bgm"

    /** 使用手册（第 65 轮：内容随包走、离线可读；手机端走这个路由，桌面走 `ProfileGroup.MANUAL`） */
    const val MANUAL = "manual"

    /** conversationId 传这个 = 恢复该角色最近一次会话（角色卡点卡片用） */
    const val CONV_RESUME = "new"

    /** conversationId 传这个 = 强制开一个空会话（聊天记录页「+」用） */
    const val CONV_BLANK = "blank"

    fun editor(id: String) = "editor/$id"
    fun chat(characterId: String, conversationId: String) = "chat/$characterId/$conversationId"
}

/**
 * @param startRoute 覆盖起始路由，只为**离屏渲染截图**而设（`--shot --route=...` 直接看某一页）；
 *        正常启动传 null，走"没账号→登录页，有账号→主界面"的既有逻辑。
 * @param initialTab 起始 tab（0发现…4用户），同样只为截图而设——三栏布局的验收要能直接落到
 *        「聊天记录」或「角色卡」那一页，否则每次都得先用鼠标点过去。
 */
@Composable
fun AppRoot(startRoute: String? = null, initialTab: Int? = null) {
    val nav = rememberNavController()
    val loggedIn = Repository.currentAccountId().isNotBlank()
    NavHost(
        navController = nav,
        startDestination = startRoute ?: if (loggedIn) Routes.MAIN else Routes.AUTH,
        // 台账 42：点空白/纯文本退出输入框键入态（全页面共享层唯一接线点，见 blurOnEmptyTap）
        modifier = Modifier.blurOnEmptyTap()
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onLoggedIn = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.MAIN) {
            // 安卓顶层返回键（用户 2026-09-24：「没有上一级则提示是否退出，返回两次退出应用」）：
            // 第一下只提示「再按一次退出」，2 秒内第二下才真退。二级页面（聊天/设置/编辑…）的返回
            // 本来就是 popBackStack（＝退回上一级），到这里已是导航图根，系统默认行为是直接杀进程——拦下来。
            // ⚠ 只拦安卓：桌面全应用只有一个 MAIN 路由（聊天走内嵌第三栏），关窗口属窗口壳，Esc 不该弹"退出"。
            var lastBackAt by remember { mutableLongStateOf(0L) }
            WhaleBackHandler(enabled = !isDesktopLayout) {
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000L) Platform.ui.exitApp()
                else {
                    lastBackAt = now
                    showToast("再按一次返回键退出鲸鱼")
                }
            }
            MainScreen(
                onOpenChat = { card -> nav.navigate(Routes.chat(card.id, Routes.CONV_RESUME)) },
                onStartNewChat = { card -> nav.navigate(Routes.chat(card.id, Routes.CONV_BLANK)) },
                onOpenConversation = { charId, convId -> nav.navigate(Routes.chat(charId, convId)) },
                onEditCharacter = { id -> nav.navigate(Routes.editor(id)) },
                onNewCharacter = { nav.navigate(Routes.editor("new")) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenVoice = { nav.navigate(Routes.VOICE) },
                onOpenBgm = { nav.navigate(Routes.BGM) },
                onOpenManual = { nav.navigate(Routes.MANUAL) },
                onLogout = {
                    Repository.setCurrentAccount("")
                    nav.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                onSwitchAccount = {
                    nav.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                initialTab = initialTab
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("characterId") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.read { getStringOrNull("characterId") } ?: "new"
            // 设置 / 语音 / 编辑这三个独立页在宽窗口下同样是"通栏单列"会很难读，
            // 统一套桌面内容宽度外壳（手机端该外壳原样返回，Android 零变化）
            DesktopContentBox {
                CharacterEditorScreen(
                    characterId = id,
                    onBack = { nav.popBackStack() },
                    onSaved = {
                        nav.popBackStack()
                    }
                )
            }
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("characterId") { type = NavType.StringType },
                navArgument("conversationId") { type = NavType.StringType }
            )
        ) { entry ->
            // 跨平台取参：桌面端的 arguments 是 androidx.savedstate.SavedState（不是 Android 的 Bundle），
            // 统一走 SavedState 的 read { } 读法，两端同一份代码。
            val args = entry.arguments
            val characterId = args?.read { getStringOrNull("characterId") } ?: return@composable
            val conversationId = args.read { getStringOrNull("conversationId") } ?: "new"
            // 左栏重读的触发器：聊天页每次"会话变了/消息条数变了"就 +1（见 ChatScreen 的 onSessionChanged）
            var railRefreshKey by remember { mutableIntStateOf(0) }
            val chat: @Composable () -> Unit = {
                ChatScreen(
                    characterId = characterId,
                    conversationId = conversationId,
                    onBack = { nav.popBackStack() },
                    onEditCharacter = { nav.navigate(Routes.editor(characterId)) },
                    // 左栏要靠它重读：新会话是"发第一条消息时才落盘"的，不重读就永远不出现在列表里
                    onSessionChanged = { railRefreshKey++ }
                )
            }
            if (!isDesktopLayout) {
                chat()
            } else {
                // 桌面（M6）：左右分栏——左＝会话栏（切会话不离开聊天页），右＝聊天。
                // 这正是"看起来像桌面应用"的关键一步：手机上换会话要退回列表页，桌面上不该这样。
                DesktopSplitPane(
                    list = {
                        DesktopConversationRail(
                            selectedConversationId = conversationId,
                            refreshKey = railRefreshKey,
                            onOpen = { charId, convId ->
                                if (charId != characterId || convId != conversationId) {
                                    // popUpTo(MAIN)：换会话是"替换当前会话"，不是再叠一层——
                                    // 否则连点十次会话，返回栈里就压了十层
                                    nav.navigate(Routes.chat(charId, convId)) { popUpTo(Routes.MAIN) }
                                }
                            },
                            onNew = { card ->
                                nav.navigate(Routes.chat(card.id, Routes.CONV_BLANK)) { popUpTo(Routes.MAIN) }
                            }
                        )
                    },
                    content = { chat() }
                )
            }
        }
        composable(Routes.SETTINGS) {
            DesktopContentBox { SettingsScreen(onBack = { nav.popBackStack() }) }
        }
        composable(Routes.VOICE) {
            DesktopContentBox { VoiceScreen(onBack = { nav.popBackStack() }) }
        }
        composable(Routes.BGM) {
            DesktopContentBox { BgmScreen(onBack = { nav.popBackStack() }) }
        }
        composable(Routes.MANUAL) {
            // 手册是给"读"的，宽窗口下通栏会很难受，与设置/语音/编辑一样套内容宽度外壳
            DesktopContentBox { ManualScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
