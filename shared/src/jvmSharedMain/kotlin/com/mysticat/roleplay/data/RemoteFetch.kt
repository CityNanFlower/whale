package com.mysticat.roleplay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 远程字节拉取：用户粘贴的角色卡直链、精选内容（清单＋卡文件）都走这里。
 *
 * 第 54 轮从 `DiscoverCatalog` 抽出来独立成对象——"网址导入"按用户口径挪到角色卡创建处之后，
 * 拉取能力不再属于发现页；两端共用同一套限额 / 超时 / 重试 / 失败分类，提示文案才只有一份。
 *
 * **多候选源（[fetchBytes] 收 List）**：精选内容托管在 GitHub Release 附件上，而 GitHub 直连在
 * 国内网络常超时或被 TLS 中间拦截（第 54 轮回溯用户报的"发现页网络错误"：本机系统代理
 * 127.0.0.1:53579 给 github.com 换了张 `Scholar Root CA v1` 签的证书，浏览器装了根证书所以
 * "网页能打开"，但 OkHttp / Android 都不认）。同一份内容在若干公共镜像上可直取——2026-09-21
 * 逐张比对过镜像拿到的卡文件字节与 Release 声明的 sha256 一致。
 * **镜像优先、原始地址兜底**：与"检查更新"同一条口径（[githubCandidates]），原始地址放最后是
 * 因为实测它在本机 12 秒无响应，放前面等于每次都要先白等一轮。
 */
object RemoteFetch {

    /**
     * GitHub 加速镜像前缀（问题 #24：国内直连 GitHub 会超时）。检查更新、安装包下载与发现页共用同一份。
     * 顺序来自 2026-09-13 本机实测：gh-proxy 0.87s、ghfast 1.38s、原始地址 12s 无响应。
     * 2026-09-21 复测：三家都能完整代理 discover-v1 的清单与卡附件，字节与 Release 的 sha256 一致。
     */
    val GITHUB_MIRROR_PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://ghfast.top/",
        "https://ghproxy.net/"
    )

    /**
     * 把一个 GitHub 链接展开成「镜像优先 + 原始地址兜底」的候选列表；
     * 非 GitHub 链接（将来换国内直连存储）原样返回。清单与卡文件都走这里。
     */
    fun githubCandidates(url: String): List<String> {
        if (url.isBlank()) return emptyList()
        val isGitHub = "github.com/" in url || "githubusercontent.com/" in url
        return if (isGitHub) GITHUB_MIRROR_PREFIXES.map { it + url } + url else listOf(url)
    }

    /** 单文件上限：PNG 卡带图常见几 MB，30MB 已盖住极端情况，再大基本是拉错了东西 */
    private const val MAX_BYTES = 30L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 浏览器 UA：部分卡站对非浏览器 UA 直接 403。按最常见的桌面 Chrome 形态走；
     * 真实卡站实测仍有拦截的，再回来补 Referer/UA 适配。
     */
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** 拉取失败的可读分类，UI 直接展示 message */
    class FetchException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun fetchBytes(url: String): ByteArray = fetchBytes(listOf(url))

    /**
     * 按顺序试多个候选 URL，返回第一个成功拉到的字节。
     *
     * 约束与单源一致：仅 http/https——file/content 等一律拒绝，本地文件导入必须显式走文件选择器
     * （避免"贴个路径"绕过用户知情）；Content-Length 超限与流读超限都按「文件过大」报，
     * 防止内存被大文件撑爆。
     *
     * 重试口径：**单源**（用户自己贴的直链）网络层失败整轮重试一次——实测弱网/代理环境下连接
     * 会被中途截断（okio "unexpected end of stream"），一次重试能兜住绝大多数瞬时抖动；
     * **多源**（精选）不再额外重试，因为下一个候选源本身就是一次全新尝试，额外一轮只会让
     * "全挂"时成倍等待。分类失败（HTTP 码 / 过大 / 协议）不重试。
     */
    suspend fun fetchBytes(urls: List<String>): ByteArray = withContext(Dispatchers.IO) {
        val candidates = urls.map { it.trim() }.filter { it.isNotEmpty() }
        if (candidates.isEmpty()) throw FetchException("没有可用的下载地址")
        val passes = if (candidates.size == 1) 2 else 1
        var firstClassified: FetchException? = null
        var lastIo: java.io.IOException? = null
        for (pass in 0 until passes) {
            if (pass > 0) runCatching { Thread.sleep(400) } // 给对端一点恢复时间
            for (url in candidates) {
                try {
                    return@withContext fetchOnce(url)
                } catch (e: FetchException) {
                    if (firstClassified == null) firstClassified = e
                } catch (e: java.io.IOException) {
                    lastIo = e
                }
            }
            // 全挂在"分类失败"上（如 HTTP 404）→ 重试没有意义，直接收工
            if (firstClassified != null && lastIo == null) break
        }
        // 分类失败更具体（HTTP 码 / 过大），优先报它；否则报网络层失败
        throw firstClassified ?: FetchException(networkMessage(lastIo), lastIo)
    }

    private fun fetchOnce(rawUrl: String): ByteArray {
        val url = normalizeUrl(rawUrl) ?: throw FetchException("只支持 http/https 链接")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw FetchException("下载失败（HTTP ${resp.code}）")
            resp.header("Content-Length")?.toLongOrNull()?.let {
                if (it > MAX_BYTES) throw FetchException("文件过大（上限 30MB）")
            }
            val body = resp.body ?: throw FetchException("下载失败（空响应）")
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            body.byteStream().use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw FetchException("文件过大（上限 30MB）")
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        }
    }

    /**
     * 规范化用户粘贴/输入的链接，返回 null = 不是 http/https。
     *
     * 为什么需要：中文输入法会把冒号、斜杠、点号打成全角（`：／．。`），从聊天软件复制的
     * 链接也常带零宽字符——这类"看起来是网址"的输入直接判失败，用户只会看到"只支持
     * http/https 链接"，完全不知道自己在哪一步错了。全角转半角只覆盖 `U+FF01..U+FF5E`
     * 与几个常见中文标点，不改动路径里的正常字符；协议名统一小写（路径保持原样，
     * 免得把大小写敏感的文件名改坏）。
     */
    private fun normalizeUrl(raw: String): String? {
        val s = buildString {
            for (c in raw) {
                when {
                    c.code in 0xFF01..0xFF5E -> append((c.code - 0xFEE0).toChar())
                    c == '\u3000' -> append(' ')                 // 全角空格
                    c == '\u3002' -> append('.')                 // 。
                    c == '\u2018' || c == '\u2019' -> append('\'')
                    c == '\u201C' || c == '\u201D' -> append('"')
                    c.code == 0xFEFF || c.code in 0x200B..0x200D -> Unit // 零宽 / BOM
                    else -> append(c)
                }
            }
        }.trim()
        val isHttp = s.startsWith("http://", true)
        val isHttps = s.startsWith("https://", true)
        if (!isHttp && !isHttps) return null
        val schemeLen = if (isHttps) 8 else 7
        return s.substring(0, schemeLen).lowercase() + s.substring(schemeLen)
    }

    /**
     * 网络层失败分类：原来一律"网络错误：<原始异常文案>"，排查时看不出是证书、DNS 还是超时。
     * TLS 那条尤其要写明白——本机实测的失败正是"能 ping 通、浏览器能打开、App 连不上"。
     */
    private fun networkMessage(e: java.io.IOException?): String = when {
        e == null -> "网络错误：连接失败"
        e is javax.net.ssl.SSLException ->
            "TLS 证书校验失败（该网站被安全软件/代理拦截，或证书不受信任）"
        e is java.net.UnknownHostException -> "域名解析失败（${e.message ?: "找不到主机"}）"
        e is java.net.SocketTimeoutException -> "连接超时"
        e is java.net.ConnectException -> "无法连接（${e.message ?: "连接被拒绝"}）"
        else -> "网络错误：${e.message ?: "连接失败"}"
    }
}
