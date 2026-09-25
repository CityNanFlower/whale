package com.mysticat.roleplay.data

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * PNG 角色卡编解码（台账 11，用户 2026-09-19 拍板）：把角色卡 JSON 藏进 PNG 的 **tEXt chunk**
 * （关键字 `chara`，内容为 base64 的 JSON 字符串）——这是 SillyTavern / 酒馆系卡站的通行做法，
 * 图片本身就是卡：发出去的是一张能直接看的头像/立绘，拖进支持的导入器就能导入。
 *
 * 只做 chunk 级的字节读写，不解码像素（图像归图像、数据归数据，互不触碰）——
 * 纯字节操作 Android 与桌面 JVM 通用，两端同一份代码。
 */
object PngCardCodec {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /** 主关键字：SillyTavern 标准写法（chara_card_v2 的 base64）。`ccv3` 是 v3 规范的关键字，导入时兜底认。 */
    private val CARD_KEYWORDS = listOf("chara", "ccv3")

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)

    /**
     * 从 PNG 字节里抽出角色卡 JSON 文本；没有卡 chunk 或不是 PNG 时返回 null。
     * tEXt（明文）与 zTXt（zlib 压缩）都认：卡站两种都有人用，读这边宽容一点不吃亏。
     */
    fun extractCardJson(png: ByteArray): String? {
        if (!isPng(png)) return null
        var offset = 8
        while (offset + 8 <= png.size) {
            val length = readU32(png, offset)
            if (length < 0 || offset + 12 + length > png.size) return null
            val type = String(png, offset + 4, 4, Charsets.ISO_8859_1)
            val dataStart = offset + 8
            if (type == "tEXt" || type == "zTXt") {
                val keyword = readChunkKeyword(png, dataStart, length)
                if (keyword in CARD_KEYWORDS) {
                    val text = when (type) {
                        "tEXt" -> textAfterNull(png, dataStart, length)
                        else -> inflateZtxt(png, dataStart, length)
                    }
                    val json = text?.trim()?.let { t ->
                        runCatching { String(Base64.getMimeDecoder().decode(t), Charsets.UTF_8) }.getOrNull()
                    }
                    if (!json.isNullOrBlank()) return json
                }
            }
            if (type == "IEND") return null
            offset += 12 + length
        }
        return null
    }

    /**
     * 把角色卡 JSON 嵌进 PNG：新增（或替换已有的）`chara` tEXt chunk，插在 IEND 之前。
     * 原 PNG 里已带卡数据（比如把导出的卡再导出）时剥掉旧 chunk，避免一份文件里叠两代数据。
     * 不是 PNG 或结构坏掉时返回 null（调用方决定拿渲染图兜底还是报错）。
     */
    fun embed(png: ByteArray, json: String): ByteArray? {
        if (!isPng(png)) return null
        // tEXt 的 text 字段是 Latin-1；base64 全是 ASCII，任何长度的 JSON 都能无损携带
        val payload = "chara\u0000" + Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))
        val newChunk = buildChunk("tEXt", payload.toByteArray(Charsets.ISO_8859_1))

        val out = ByteArrayOutputStream(png.size + newChunk.size)
        out.write(png, 0, 8)
        var offset = 8
        while (offset + 8 <= png.size) {
            val length = readU32(png, offset)
            if (length < 0 || offset + 12 + length > png.size) return null
            val type = String(png, offset + 4, 4, Charsets.ISO_8859_1)
            val total = 12 + length // length(4) + type(4) + data + crc(4)
            if (type == "IEND") {
                // 新卡 chunk 必须落在 IEND **之前**：IEND 之后的数据按规范不存在，白带
                out.write(newChunk)
                out.write(png, offset, total)
                break
            }
            if (type == "tEXt" || type == "zTXt") {
                // 已有的卡数据直接丢弃（新 chunk 统一插到 IEND 前），其余 tEXt（作者注释等）原样保留
                val keyword = readChunkKeyword(png, offset + 8, length)
                if (keyword !in CARD_KEYWORDS) out.write(png, offset, total)
            } else {
                out.write(png, offset, total)
            }
            offset += total
        }
        return out.toByteArray()
    }

    // ────────────────────────── chunk 级工具 ──────────────────────────

    /** data 开头到第一个 \0 之前是 keyword（Latin-1）；没有 \0 视为坏块 */
    private fun readChunkKeyword(png: ByteArray, dataStart: Int, length: Int): String? {
        if (length <= 0) return null
        val nul = indexOf0(png, dataStart, dataStart + length)
        if (nul < 0) return null
        return String(png, dataStart, nul - dataStart, Charsets.ISO_8859_1)
    }

    /** tEXt 的 text：keyword\0 之后的剩余字节（zTXt 结构不同，别走这里） */
    private fun textAfterNull(png: ByteArray, dataStart: Int, length: Int): String? {
        val nul = indexOf0(png, dataStart, dataStart + length)
        if (nul < 0) return null
        val start = nul + 1
        return String(png, start, dataStart + length - start, Charsets.ISO_8859_1)
    }

    /**
     * zTXt：keyword\0compressionMethod(1)zlib 数据。只认 method 0（zlib/deflate）——
     * 这是 zTXt 规范里唯一的合法值。
     */
    private fun inflateZtxt(png: ByteArray, dataStart: Int, length: Int): String? {
        val nul = indexOf0(png, dataStart, dataStart + length)
        if (nul < 0 || nul + 2 > dataStart + length) return null
        val method = png[nul + 1].toInt() and 0xFF
        if (method != 0) return null
        val start = nul + 2
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(png, start, dataStart + length - start)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && inflater.needsInput()) return null
                out.write(buf, 0, n)
            }
            inflater.end()
            String(out.toByteArray(), Charsets.ISO_8859_1)
        }.getOrNull()
    }

    private fun buildChunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(12 + data.size)
        writeU32(out, data.size)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(data)
        val crc = CRC32().apply { update(type.toByteArray(Charsets.ISO_8859_1)); update(data) }
        writeU32(out, crc.value.toInt())
        return out.toByteArray()
    }

    private fun readU32(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or (b[offset + 3].toInt() and 0xFF)

    private fun writeU32(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun indexOf0(b: ByteArray, from: Int, until: Int): Int {
        for (i in from until until) if (b[i].toInt() == 0) return i
        return -1
    }
}
