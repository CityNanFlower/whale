package com.mysticat.roleplay.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysticat.roleplay.data.CardImport
import com.mysticat.roleplay.data.PngCardCodec
import com.mysticat.roleplay.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 桌面「拖拽导入 / 粘贴图片」的桥（M11 ④）。
 *
 * 真正摸 OLE 拖放（java.awt.dnd.DropTarget）与系统剪贴板图片（DataFlavor.imageFlavor）的代码
 * 只能住在 desktopApp——jvmSharedMain 会被 Android 一起编译，那边没有 AWT。而"拖进来之后干什么"
 * （导入角色卡、给聊天加附件、换头像）的状态与业务全在共享层，所以这里只放**状态与入口**，
 * desktopApp 负责把系统事件翻译进来。模式与 [DesktopShortcuts] / [DesktopFeatures] 相同。
 *
 * Android 侧没有任何调用点，全部状态保持默认值，零影响。
 */
object DesktopDragDrop {

    /** 有文件正拖在窗口上方（进入=true，离开/落下=false）。desktopApp 的 DropTarget 监听器维护 */
    var dragOver by mutableStateOf(false)

    /**
     * 当前页注册的图片接收器：参数＝**已落盘**的本地图片路径（进过 [Repository.saveImageBytes]，
     * 不会引用用户随手可能删掉的源文件），返回 true＝本页消费了。
     * 由聊天页（加附件）、角色编辑器（换头像）、用户页（换头像）用 SideEffect 挂最新闭包。
     * 没有页面注册时拖图/贴图只给一条提示，不静默吞掉。
     */
    var imageSink: ((String) -> Boolean)? = null

    /** 角色卡导入成功后 +1：角色列表页监听它重读磁盘（外壳持有 charVm，见 MainScreen） */
    var importedRevision by mutableIntStateOf(0)
        private set

    /** 上限与选择器同一口径（DesktopPlatformUi 的 64MB） */
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024

    private val CARD_EXTS = setOf("json", "txt")
    private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

    fun isCardFile(f: File): Boolean = f.extension.lowercase() in CARD_EXTS
    fun isImageFile(f: File): Boolean = f.extension.lowercase() in IMAGE_EXTS

    /** PNG 角色卡：图里嵌着 `chara` tEXt 数据的才算（台账 11；纯图片的 PNG 仍走图片链路） */
    private fun isPngCardFile(f: File): Boolean =
        f.extension.lowercase() == "png" && runCatching {
            PngCardCodec.extractCardJson(f.readBytes()) != null
        }.getOrDefault(false)

    /** UTF-8 为主，顺手剥 BOM（Windows 记事本存的 JSON 常带） */
    private fun readTextBestEffort(f: File): String? = runCatching {
        f.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    }.getOrNull()

    /**
     * 处理**拖入**的一批文件。IO 线程调用（读文件 / 落盘都在这）。
     * 分流：JSON/TXT → 角色卡导入（多张时逐张试，单张坏卡不拖累其余）；
     * 图片 → 落盘后交给当前页的 [imageSink]。
     * 返回给 toast 用的汇总消息；null＝没认出任何可处理的东西（不必打扰）。
     */
    suspend fun handleFiles(paths: List<String>): String? = withContext(Dispatchers.IO) {
        val files = paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) return@withContext null
        val cards = files.filter { isCardFile(it) || isPngCardFile(it) }
        if (cards.isNotEmpty()) {
            var imported = 0
            var lastName: String? = null
            for (f in cards.take(5)) {
                // PNG 卡走字节抽取（tEXt chara），JSON/TXT 走文本
                val text = if (isCardFile(f)) readTextBestEffort(f)
                    else runCatching { CardImport.cardJsonFromBytes(f.readBytes()) }.getOrNull()
                if (text == null) continue
                runCatching {
                    val card = CardImport.fromTavernJson(text)
                    Repository.saveCharacter(card)
                    imported++
                    lastName = card.name
                }
            }
            importedRevision++
            when {
                imported > 1 -> "已导入 $imported 张角色卡（最新「$lastName」）"
                imported == 1 -> "已导入「$lastName」，到角色页就能看到"
                else -> "这个 JSON 不是鲸鱼认得的角色卡格式"
            }
        } else {
            val images = files.filter { isImageFile(it) }
            if (images.isEmpty()) return@withContext null
            var used = 0
            for (img in images) {
                val saved = saveImageFile(img) ?: continue
                if (imageSink?.invoke(saved) == true) used++
            }
            when {
                used > 0 -> "已添加 $used 张图片"
                imageSink == null -> "当前页面不支持拖入图片（拖 JSON 或 PNG 角色卡可以导入）"
                else -> "这些图片读取失败，换一张试试"
            }
        }
    }

    /**
     * 处理**粘贴**来的剪贴板图片字节（Ctrl+V，desktopApp 编码成 PNG 后交进来）。
     * IO 线程调用。返回给 toast 用的消息。
     */
    suspend fun handleImageBytes(bytes: ByteArray, ext: String = "png"): String? =
        withContext(Dispatchers.IO) {
            if (bytes.size > MAX_IMAGE_BYTES) return@withContext "这张图片太大了（上限 64MB）"
            val saved = runCatching { Repository.saveImageBytes(bytes, ext) }.getOrNull()
                ?: return@withContext "这张图片保存失败"
            if (imageSink?.invoke(saved) == true) "已粘贴图片" else "当前页面不支持粘贴图片"
        }

    /** 拖入的图片文件先抄进应用私有目录（引用用户桌面上的源文件，人家一删就是断链） */
    private fun saveImageFile(f: File): String? = runCatching {
        if (f.length() == 0L || f.length() > MAX_IMAGE_BYTES) return@runCatching null
        val bytes = f.readBytes()
        val ext = f.extension.lowercase().let { if (it == "jpeg") "jpg" else it }
        Repository.saveImageBytes(bytes, ext)
    }.getOrNull()
}
