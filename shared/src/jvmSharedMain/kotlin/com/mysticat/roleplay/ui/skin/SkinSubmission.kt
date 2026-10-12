package com.mysticat.roleplay.ui.skin

import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.tokens.WhaleSkinSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder

/**
 * 皮肤投稿：零服务器，GitHub Issue 单轨。
 *
 * 应用把一份填好规格的投稿正文预填进「新建 Issue」的地址栏，用户在浏览器里登录、勾掉合规项、
 * 提交即可，我们审过后合并上架。**正文超 [URL_LIMIT] 时 URL 塞不下**（中文按 UTF-8 百分号编码，
 * 一个字 9 个字符，地址会暴涨），这时只开空白页、正文走剪贴板——调用方必须真复制，别只提示。
 *
 * ⚠ **两条退路已在 2026-10-02 撤掉**（用户原话"我复制什么，导出什么？意义不明"）：
 * 界面上原来的「复制投稿内容 / 导出皮肤包」两个按钮没了，本文件顶部一度还在描述它们，已改。
 * [writePack] / [packFileName] **函数保留但生产代码零调用**——不是死代码要删，是 2.x 的
 * 「用户能自己写皮肤 → 能导出投稿包」（角色包与皮肤包批次）正好接上它。
 *
 * ⚠ **今天这个功能还名不副实**：`ProfileScreen` 的皮肤设置只能从 `WhaleSkin.builtins()` 里选，
 * 也就是用户能投稿的只有简约／潮汐两套内置皮（自己写皮肤＝2.0.0 第 ① 批的事）。
 * 所以副标题已从"把你调好的配色"改成"把你现在用的这套"——前者暗示用户能调，实际不能。
 *
 * 皮肤包里**只有声明式 JSON**：字体不随包分发（应用内可按需下载或自定义上传），
 * 包里只记字族名与来源声明，所以皮肤包本身是几 KB 文本，能直接贴进 Issue 正文。
 */
object SkinSubmission {

    /** 公开仓（安装包、更新清单、发现页同仓），投稿走它的 Issues。 */
    const val REPO = "CityNanFlower/whale"

    /** 预选标签：维护时按它捞待审皮肤。 */
    const val LABEL = "皮肤投稿"

    private const val ISSUE_NEW = "https://github.com/$REPO/issues/new"

    /**
     * 正文进 query 后地址会长出两三倍（中文按 UTF-8 百分号编码，一个字 9 个字符），
     * 超过这个长度就别指望各家浏览器和中间代理都吃得下：改成只开空白页，
     * 正文已经进了剪贴板，让用户自己粘。
     */
    const val URL_LIMIT = 4000

    /** 预填结果。[bodyInUrl] 为 false 时正文没进地址栏，调用方必须提示用户手动粘贴。 */
    data class Prepared(val url: String, val body: String, val bodyInUrl: Boolean)

    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    private const val ILLEGAL_FILE_CHARS = "/\\:*?\"<>|"

    /** 皮肤包 JSON：导入即换肤的那份，带缩进便于在 Issue 里直接读。 */
    fun packJson(spec: WhaleSkinSpec): String = pretty.encodeToString(spec)

    /** 落盘文件名：中文名保留，路径分隔符与 Windows 禁用字符换成下划线。 */
    fun packFileName(spec: WhaleSkinSpec): String {
        val base = "${spec.id.ifBlank { "skin" }}-${spec.name.ifBlank { "unnamed" }}"
            .map { if (ILLEGAL_FILE_CHARS.contains(it) || it.code < 32) '_' else it }
            .joinToString("")
        return "$base.whale-skin.json"
    }

    /** 生成预填用的 Issue 地址与正文。 */
    fun prepare(spec: WhaleSkinSpec, oneLiner: String, appVersion: String): Prepared {
        val body = issueBody(spec, oneLiner, appVersion)
        val enc: (String) -> String = { URLEncoder.encode(it, "UTF-8") }
        val head = "$ISSUE_NEW?labels=${enc(LABEL)}&title=${enc("皮肤投稿：${spec.name}")}"
        // 先按未编码长度判：编码后必然更长，用它比阈值没意义
        val inUrl = body.length <= URL_LIMIT
        return Prepared(
            url = if (inUrl) "$head&body=${enc(body)}" else head,
            body = body,
            bodyInUrl = inUrl
        )
    }

    /** 投稿正文：一句自述 ＋ 规格速览 ＋ 合规确认 ＋ 可直接导入的皮肤 JSON。 */
    fun issueBody(spec: WhaleSkinSpec, oneLiner: String, appVersion: String): String = buildString {
        appendLine("## 皮肤投稿：${spec.name}")
        appendLine()
        appendLine("> 由鲸鱼 $appVersion 的「我的 → 皮肤 → 投稿」生成。改完直接提交，我们审过后会合并上架。")
        appendLine()
        appendLine("**这套皮肤想营造什么感觉**")
        appendLine(if (oneLiner.isBlank()) "_（请补一句，例如「旧打字机纸感，圆角大、配色克制」）_" else oneLiner)
        appendLine()
        appendLine("**规格速览**")
        appendLine("- 浅色：${toneLine(spec.light)}")
        appendLine("- 深色：${toneLine(spec.dark)}")
        appendLine("- 圆角三档：`${spec.shape.r1} / ${spec.shape.r2} / ${spec.shape.r3} dp`，投影 `${spec.shape.shadow}`")
        appendLine(
            "- 贴纸预设：`${spec.motif}`（倍率 `${"%.2f".format(spec.motifSize)}`、" +
                "透明度 `${"%.2f".format(spec.motifOpacity)}`）"
        )
        appendLine("- 字体：界面 `${spec.fonts.ui}` · 正文 `${spec.fonts.chat}` · 元信息 `${spec.fonts.meta}`")
        appendLine()
        appendLine("**合规确认**（提交前请逐条勾掉）")
        appendLine("- [ ] 配色为我自行设计，或取自明确许可的来源")
        appendLine("- [ ] 若皮肤带字体文件，许可为 OFL-1.1 等允许再分发的协议，我会另附许可证原文与下载地址")
        appendLine("- [ ] 皮肤只含声明式数据：不执行脚本、不联网、不含任何个人隐私")
        appendLine()
        appendLine("**皮肤 JSON**（原样保留即可导入）")
        appendLine()
        appendLine("```json")
        appendLine(packJson(spec))
        appendLine("```")
    }

    /** 把皮肤包写到缓存目录的皮肤包子目录，返回落盘文件；失败返回 null。 */
    fun writePack(spec: WhaleSkinSpec): File? = runCatching {
        val dir = File(Repository.cacheRootDir, "skin-packs").apply { mkdirs() }
        File(dir, packFileName(spec)).apply { writeText(packJson(spec)) }
    }.getOrNull()

    private fun toneLine(map: Map<String, String>): String {
        val paper = map["paper"] ?: return "（未定义，走默认）"
        return "纸面 `$paper` · 主色 `${map["accent"] ?: "—"}` · 卡面 `${map["surface2"] ?: "—"}` · 正文 `${map["ink"] ?: "—"}`"
    }
}