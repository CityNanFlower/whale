package com.mysticat.roleplay.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mysticat.roleplay.ui.Platform
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 背景音乐（用户口径：**可以内置一些音乐、白噪音，也支持用户上传**）。
 *
 * 两半：
 * - [BgmSources]：内置音源的**音源文件**（打包进来的真采样录音，见下）与播放前的一次落盘；
 * - [BgmPlayer]：播放控制器（循环、音量、给朗读让路、退到后台就停），音源的"此刻该不该响"只在这里判断一次。
 *
 * ## 为什么从"运行期合成"改成"真采样"（用户原话）
 *
 * 「这些环境音你去找素材库，那些真采样的音频，**用声频合成的背景音有点伤耳朵**」。
 * 最初的六个白噪音全是运行期用滤波器合成出来的（零素材、零版权、APK 不涨），
 * 雨声/海浪/篝火那三档听感确实"电子味"——噪声合成能糊出个大概的频谱，糊不出真雨点的颗粒度。
 * 于是：雨声/海浪/篝火/森林/溪流/夜晚虫鸣**改成素材库里的 CC0 真录音**
 * （`shared/assets/bgm/<id>.wav`，来源与加工记录见 `shared/assets/SOURCES.txt`），
 * 第一次用到时复制到 `cacheRoot/bgm/` 再走原路播放。白/粉/布朗三档仍是合成——
 * 纯噪声本来就是算出来的信号，录一段与生成一段在波形上没有区别。
 *
 * **合成器没有删**（[synth]）：它是"这个构建里没打包音源"时的兜底——宁可听感差一点，
 * 也不要出现"点了开关却一声不响"（本项目最难查的一类故障）。桌面 smoke 仍会逐档合成一遍，
 * 所以这条路一直是被测着的。
 */

/**
 * 用户上传的音频能不能播——返回 null = 可以，否则是**给用户看的原因**（两个平台共用一份文案）。
 *
 * 存在的理由：用户塞一个本平台解不了的格式进来的结果是"开关开着、音源也选着，却一点声音都没有"，
 * 而且**没有任何报错**。宁可当场拒绝并说清楚，也不要静默不出声——
 * 这条在本项目已经反复踩过（起播失败静默是最难查的一类）。
 *
 * 两端能放的格式不一样（桌面加了 Java Sound 的三个解码 SPI，见 [Voice.supportedAudioExtensions]），
 * 所以那行提示里的清单**必须从平台声明里取**，不能写死。
 */
fun bgmFormatRejection(fileName: String): String? {
    val supported = Voice.supportedAudioExtensions.sorted().joinToString(" / ")
    val ext = fileName.substringAfterLast('.', "").lowercase()
    if (ext.isBlank()) return "认不出这个文件的格式（请用 $supported）"
    if (ext !in Voice.supportedAudioExtensions) return "本平台放不了 .$ext 格式的音频，请用 $supported"
    return null
}

/**
 * 音乐库里的一首曲目（用户口径：上传音乐保存成列表，支持重命名/删除/新增）。
 *
 * [displayName] 是**应用内显示名**——重命名只改它，文件本体一直是 [fileName]
 * （生成名，存 `accounts/<id>/bgm/`），绝不改用户的源文件。引用方式＝`"lib:<id>"`
 * （`AiSettings.bgmSource` 与 `SessionBgm.sourceId` 都用它）。
 */
@Serializable
data class BgmLibraryEntry(
    val id: String,
    val displayName: String,
    /** bgm 目录里的文件名（生成名） */
    val fileName: String,
    val addedAt: Long = 0L
)

/** 一个内置音源（[id] 是落进 `AiSettings.bgmSource` 的值，别改） */
data class BgmSource(
    val id: String,
    val label: String,
    val hint: String,
    /**
     * 这一档**应该有**打包进来的真采样资源（见 `shared/assets/bgm/<id>.wav`）。
     *
     * 只有"该有却没有"才值得在日志里留一条警告——白/粉/布朗三档本来就是合成的，
     * 每次冷启动都会走一遍兜底，若不加这个标记，日志里会天天出现一条假的"资源缺失"。
     */
    val sampled: Boolean = false
)

/** 内置音源表 + 合成 + 落盘缓存 */
object BgmSources {

    /** `bgmSource` 取它 = 用用户上传的文件（见 `AiSettings.bgmUserPath`） */
    const val SOURCE_USER = "user"

    val builtin: List<BgmSource> = listOf(
        BgmSource("rain", "雨声", "素材库真录音：细密的雨点，最常用的一档", sampled = true),
        BgmSource("waves", "海浪", "素材库真录音：岸边涨落的水声", sampled = true),
        BgmSource("fire", "篝火", "素材库真录音：炉火的轰鸣与噼啪", sampled = true),
        BgmSource("forest", "森林", "素材库真录音：林间鸟鸣与树叶声", sampled = true),
        BgmSource("stream", "溪流", "素材库真录音：持续流淌的水声", sampled = true),
        BgmSource("night", "夜晚虫鸣", "素材库真录音：夜里的虫声", sampled = true),
        // 下面三档**没有素材**、也不需要有：白/粉/布朗噪声本来就是算出来的信号
        // （"录一段白噪声"与"生成一段白噪声"在波形上没有区别），所以继续走 [synth]。
        BgmSource("white", "白噪声", "程序合成：全频段等能量，掩蔽感最强"),
        BgmSource("pink", "粉噪声", "程序合成：低频更足，比白噪声柔和"),
        BgmSource("brown", "布朗噪声", "程序合成：更低更闷，接近远处的风声")
    )

    fun labelOf(id: String): String =
        if (id == SOURCE_USER) "我的音乐" else builtin.firstOrNull { it.id == id }?.label ?: id

    /**
     * 取内置音源的本地文件（不存在就从安装包里复制一份出来）。**必须在后台线程调用**（首次要约 1MB 的复制）。
     * 返回 null = 不是已知音源。落点 `cacheRoot/bgm/`：与发现页缩略图同一套规矩——
     * 放 `images/`（被角色卡引用的图片）会被孤图回收当成垃圾删掉。
     */
    fun fileFor(id: String): File? {
        val src = builtin.firstOrNull { it.id == id } ?: return null
        val dir = Repository.cacheSubDir("bgm")
        val f = File(dir, "$id.$ASSET_EXT")
        if (f.isFile && f.length() > WAV_HEADER_BYTES) return f
        // ① 优先用**打包进来的真采样**
        val packed = assetBytes(id)
        if (packed != null && packed.size.toLong() > WAV_HEADER_BYTES) {
            return Repository.writeCacheBytes(dir, "$id.$ASSET_EXT", packed)
        }
        // ② 兜底：这个构建没带音源（或资源被裁剪过）→ 现场合成，至少不是"点了没声"。
        //    只有"本该有资源的档"才值得留警告（噪声三档本来就该走这里）
        if (src.sampled) WhaleLog.w("BgmSources", "内置音源 $id 没有打包资源，回退运行期合成")
        return Repository.writeCacheBytes(dir, "$id.$ASSET_EXT", synth(id))
    }

    /**
     * 读安装包里打包的音源字节（null = 这个构建里没有）。**测试与自检要的钩子**。
     *
     * 两端走的是**同一个口**：Gradle 把 `shared/assets/` 同时挂给了 APK 与桌面 jar 的 JVM 资源
     * （见 `shared/build.gradle.kts` 里那两处 `resources.srcDir`），所以 Android 与桌面都可以
     * 用类加载器读——不必为任何一个宿主另加注入口（这也是当初没走 Android `assets/` 的原因）。
     */
    fun assetBytes(id: String): ByteArray? = runCatching {
        BgmSources::class.java.classLoader
            ?.getResourceAsStream("$ASSET_DIR/$id.$ASSET_EXT")
            ?.use { it.readBytes() }
    }.getOrNull()

    /** 打包资源所在目录（与 `shared/assets/bgm/` 对应） */
    private const val ASSET_DIR = "bgm"

    /**
     * 内置音源的文件后缀。**固定 wav**：无损 ⇒ 循环接缝不会被编码器填充搅坏
     * （mp3/aac 的编码器延迟会在循环点留一段几十毫秒的静音，连续环境音上听得很清楚）。
     */
    private const val ASSET_EXT = "wav"

    // ─────────────────────────── 合成 ───────────────────────────

    private const val SAMPLE_RATE = 22050

    /**
     * 循环片段长度（秒）：16s @ 22.05k 单声道 16bit ≈ 690KB。
     * 再长收益很小（噪声的"循环感"靠周期化处理消除，不靠时长），再短则调制起伏会太机械。
     */
    private const val LOOP_SECONDS = 16

    private const val WAV_HEADER_BYTES = 44L

    /**
     * 生成某音源的完整 WAV 字节（**确定性**：同 id 每次结果一致，便于自检比对）。
     *
     * ## 无缝循环是**做出来的，不是淡出来的**（第一版栽在这）
     *
     * 第一版是"生成 n+尾巴个样本、尾部与头部交叉淡化"。它对付不了低频：淡化只能让接缝**不断裂**，
     * 但循环点是"时间往回跳 0.25 秒"，对布朗噪声这种慢信号，这一跳就是一次实打实的低音阶跃
     * （实测自检直接判失败）。真正的做法是**让信号本身就是周期的**：
     * 白噪声先按"一个周期"生成（[noise]），再对它做**环形滤波**（[circular]：把输入当周期信号跑两遍、
     * 取第二遍）——滤波器状态跨过接缝时是连续的，于是输出天然首尾相接，
     * 一个淡入淡出都不需要，也没有任何"往回跳"。
     */
    fun synth(id: String): ByteArray {
        val n = SAMPLE_RATE * LOOP_SECONDS
        // 种子按 id 固定：换机器/换次运行生成的文件逐字节相同（缓存失效重建不会"换个声音"）
        val rnd = Random(id.hashCode().toLong() * 31 + 7)
        val noise = FloatArray(n) { rnd.nextFloat() * 2f - 1f }
        val out = when (id) {
            "pink" -> pink(noise)
            "brown" -> brown(noise)
            "rain" -> rain(noise)
            "waves" -> waves(noise)
            "fire" -> fire(noise, Random(id.hashCode().toLong() * 131 + 17))
            else -> noise.copyOf() // white / 未知 id
        }
        normalize(out, 0.7f)
        return encodeWav(out)
    }

    /**
     * **环形滤波**：把输入当作"无限重复的周期信号"过一遍 [process]。
     *
     * 跑两遍、只收第二遍：第一遍让滤波器/积分器从零状态收敛到"周期稳态"
     * （稳定性带来的收敛极其彻底——极点模长 0.94~0.9995 跑满一整周期 35 万个样本后，
     * 初值的影响是 e^-35 ~ e^-176 量级，远低于 16bit 量化台阶），
     * 于是收下的这一遍与下一遍**逐样本相同**，循环点上不存在任何跳变。
     */
    private inline fun circular(n: Int, process: (Int) -> Float): FloatArray {
        val out = FloatArray(n)
        for (i in 0 until n * 2) {
            val y = process(i % n)
            if (i >= n) out[i - n] = y
        }
        return out
    }

    private fun pink(noise: FloatArray): FloatArray {
        // Paul Kellett 的"经济型"粉噪声滤波器：6 个一阶极点拟合 1/f 谱，够用且便宜
        var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
        return circular(noise.size) { i ->
            val w = noise[i]
            b0 = 0.99886f * b0 + w * 0.0555179f
            b1 = 0.99332f * b1 + w * 0.0750759f
            b2 = 0.96900f * b2 + w * 0.1538520f
            b3 = 0.86650f * b3 + w * 0.3104856f
            b4 = 0.55000f * b4 + w * 0.5329522f
            b5 = -0.7616f * b5 - w * 0.0168980f
            val y = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
            b6 = w * 0.115926f
            y
        }
    }

    private fun brown(noise: FloatArray): FloatArray {
        // 白噪声积分（低频更重）：每步泄露一点，否则会一路漂到饱和
        var acc = 0f
        return circular(noise.size) { i ->
            acc = (acc + 0.02f * noise[i]) * 0.9995f
            acc
        }
    }

    private fun rain(noise: FloatArray): FloatArray {
        // 雨 = 大量细小水滴的叠加 ⇒ 带通噪声（1.6k 附近）+ 一层薄低频底噪（"远处的雨幕"）
        val band = Biquad.bandPass(SAMPLE_RATE.toFloat(), 1600f, 0.7f)
        val rumble = Biquad.lowPass(SAMPLE_RATE.toFloat(), 220f, 0.7f)
        return circular(noise.size) { i ->
            val w = noise[i]
            band.process(w) + rumble.process(w) * 0.55f
        }
    }

    private fun waves(noise: FloatArray): FloatArray {
        // 海浪 = 低通噪声 + **缓慢起伏的包络**（涨潮感），再加一层随包络浮现的"泡沫"高频
        val body = Biquad.lowPass(SAMPLE_RATE.toFloat(), 900f, 0.6f)
        val foam = Biquad.highPass(SAMPLE_RATE.toFloat(), 2500f, 0.7f)
        val n = noise.size
        return circular(n) { i ->
            // 包络用**整数个周期**的余弦（2 次 + 1 次），所以它本身也是周期的——
            // 单一正弦会听出"每 8 秒一次"的机械感，两条叠加后整体仍是整段一循环
            val t = i.toFloat() / n
            val swell = 0.5f - 0.5f * kotlin.math.cos(2f * Math.PI.toFloat() * 2f * t)
            val slow = 0.5f - 0.5f * kotlin.math.cos(2f * Math.PI.toFloat() * 1f * t)
            val env = 0.25f + 0.75f * (swell * 0.7f + slow * 0.55f).coerceIn(0f, 1f)
            val w = noise[i]
            (body.process(w) * 2.2f + foam.process(w) * 0.5f) * env
        }
    }

    private fun fire(noise: FloatArray, rnd: Random): FloatArray {
        // 篝火 = 低沉的火焰轰鸣（低通噪声）+ 随机"噼啪"（极短的高频爆音，指数衰减）
        val rumble = Biquad.lowPass(SAMPLE_RATE.toFloat(), 320f, 0.8f)
        val crack = Biquad.highPass(SAMPLE_RATE.toFloat(), 1800f, 0.7f)
        val n = noise.size
        val decay = (SAMPLE_RATE * 0.02f).toInt() // 爆音约 20ms 衰减完
        // 爆音包络先按周期生成：跨过接缝的那一段**绕回开头**（取较强者），
        // 否则末尾被截断的爆音就是循环点上的一声"咔"（激励不连续，滤波后仍是不连续）
        val burstEnv = FloatArray(n)
        for (i in 0 until n) {
            // 每秒约 5 次爆音，幅度随机（有的只是"啵"一声）
            if (rnd.nextFloat() < 5f / SAMPLE_RATE) {
                val amp = 0.5f + rnd.nextFloat()
                for (k in 0 until decay) {
                    val at = (i + k) % n
                    val v = amp * (1f - k.toFloat() / decay)
                    if (v > burstEnv[at]) burstEnv[at] = v
                }
            }
        }
        return circular(n) { i ->
            val w = noise[i]
            rumble.process(w) * 1.6f + crack.process(w * burstEnv[i]) * 1.1f
        }
    }

    /** 归一化到目标峰值：六个音源响度相当，用户来回切不会"这个突然很响" */
    private fun normalize(out: FloatArray, target: Float) {
        var peak = 0f
        for (s in out) if (kotlin.math.abs(s) > peak) peak = kotlin.math.abs(s)
        if (peak <= 0.0001f) return
        val g = target / peak
        for (i in out.indices) out[i] = (out[i] * g).coerceIn(-1f, 1f)
    }

    /** 16bit 单声道 PCM 的 WAV（自己拼头：`AudioSystem.write` 那条路桌面才认，且长度字段不好控） */
    private fun encodeWav(samples: FloatArray): ByteArray {
        val n = samples.size
        val out = ByteArray(44 + n * 2)
        fun ascii(pos: Int, s: String) {
            for (i in s.indices) out[pos + i] = s[i].code.toByte()
        }
        fun int32(pos: Int, v: Int) {
            out[pos] = (v and 0xFF).toByte()
            out[pos + 1] = ((v shr 8) and 0xFF).toByte()
            out[pos + 2] = ((v shr 16) and 0xFF).toByte()
            out[pos + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun int16(pos: Int, v: Int) {
            out[pos] = (v and 0xFF).toByte()
            out[pos + 1] = ((v shr 8) and 0xFF).toByte()
        }
        ascii(0, "RIFF")
        int32(4, 36 + n * 2)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        int32(16, 16)          // fmt 块长度
        int16(20, 1)           // PCM
        int16(22, 1)           // 单声道
        int32(24, SAMPLE_RATE)
        int32(28, SAMPLE_RATE * 2) // 字节率
        int16(32, 2)           // 块对齐
        int16(34, 16)          // 位深
        ascii(36, "data")
        int32(40, n * 2)
        for (i in 0 until n) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            int16(44 + i * 2, v)
        }
        return out
    }

    /** 二阶 IIR（RBJ cookbook）：雨声/海浪/篝火的音色全靠它 */
    private class Biquad private constructor(
        private val b0: Float, private val b1: Float, private val b2: Float,
        private val a1: Float, private val a2: Float
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x: Float): Float {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        companion object {
            fun lowPass(fs: Float, f0: Float, q: Float): Biquad {
                val w0 = 2.0 * Math.PI * f0 / fs
                val cos = kotlin.math.cos(w0).toFloat()
                val alpha = (kotlin.math.sin(w0) / (2.0 * q)).toFloat()
                val a0 = 1f + alpha
                return Biquad(
                    (1f - cos) / 2f / a0, (1f - cos) / a0, (1f - cos) / 2f / a0,
                    -2f * cos / a0, (1f - alpha) / a0
                )
            }

            fun highPass(fs: Float, f0: Float, q: Float): Biquad {
                val w0 = 2.0 * Math.PI * f0 / fs
                val cos = kotlin.math.cos(w0).toFloat()
                val alpha = (kotlin.math.sin(w0) / (2.0 * q)).toFloat()
                val a0 = 1f + alpha
                return Biquad(
                    (1f + cos) / 2f / a0, -(1f + cos) / a0, (1f + cos) / 2f / a0,
                    -2f * cos / a0, (1f - alpha) / a0
                )
            }

            fun bandPass(fs: Float, f0: Float, q: Float): Biquad {
                val w0 = 2.0 * Math.PI * f0 / fs
                val cos = kotlin.math.cos(w0).toFloat()
                val alpha = (kotlin.math.sin(w0) / (2.0 * q)).toFloat()
                val a0 = 1f + alpha
                return Biquad(alpha / a0, 0f, -alpha / a0, -2f * cos / a0, (1f - alpha) / a0)
            }
        }
    }
}

/**
 * BGM 播放控制器（全局单例：同一时刻最多一份环境音在响）。
 *
 * ## 架构改版（用户口径：背景音乐只在会话中生效、一个会话一份配置）
 *
 * - **"该不该响"的判定源变了**：以前直接吃全局 `AiSettings`（开机就响、全 App 都响）；
 *   现在必须有**活跃会话**（[enterSession]）——聊天页（含桌面第三栏的聊天窗）在组合中才有活跃会话，
 *   切走/关闭聊天页 `ChatViewModel.onCleared` 会 [leaveSession]，音乐随之停。
 * - **会话级配置**（[SessionBgm]）：跟随全局默认 / 本会话静音 / 本会话自选音源。**音量不在这里面**——
 *   全 App 只有一个音量（`AiSettings.bgmVolume`），会话内与设置页共用（用户口径"优先共享"）。
 * - **与朗读并行**（用户口径"尽可能并行"）：朗读让路从"整段停"改为**音量 ducking**——
 *   朗读期间压到 [DUCK_VOLUME_FACTOR]，读完延迟 [DUCK_LINGER_MS] 恢复，音乐不再被掐断。
 *
 * ## 修过的两处（用户 2026-09-21 真机反馈）
 *
 * 1. **"从没配过 BGM 的会话进去不响"**：「在不在会话里」与「会话配了什么」原先挤在同一个 null 上
 *    （`activeSessionBgm == null` 既表示没在会话、又表示会话没配置），于是"跟随全局默认"这种
 *    **最常见的状态反而不出声**，得手动点一次「跟随全局默认」写进去才响。判据已拆成 [inSession]。
 * 2. **桌面切会话时音乐被旧会话"顺手停掉"**：见 [sessionHandle] 的说明（新的先进入、旧的后离开）。
 *
 * 职责边界不变：它只回答"此刻该不该响、响哪一个、多大声"，怎么发声交给 [AudioPlayerEngine]。
 *
 * 三条时序约定（都是踩出来的）：
 * 1. **起播/停播一律在后台线程**：桌面 `Clip.open()` 要把整段音频解成 PCM 载入内存，
 *    在主线程上就是"点一下开关，界面卡住"（朗读那条路 2026-09-17 已经栽过一次，注释在 `TtsSpeaker`）。
 * 2. **状态发布回主线程**：界面读的是 Compose 状态（[currentPath]/[lastError]），
 *    跨线程写 Compose 状态会拿到撕裂的快照。
 * 3. **所有引擎操作串行**：内部只有一条单线程执行器，换源时"先停旧的再起新的"天然有序，
 *    不会出现旧引擎的 stop 追上新引擎的 start（那会让声音在中途被掐掉）。
 */
object BgmPlayer {

    /** 正在播放的本地文件路径（null = 没在播）。界面用它显示"正在播放：雨声" */
    var currentPath: String? by mutableStateOf(null)
        private set

    /** 最近一次起播失败的原因（界面据此提示，口径同 `AudioPlayerEngine.lastError`） */
    var lastError: String? by mutableStateOf(null)
        private set

    /** 音乐库试听正在放的文件路径（null = 没在试听） */
    var previewPath: String? by mutableStateOf(null)
        private set

    /** 试听起播失败的原因 */
    var previewError: String? by mutableStateOf(null)
        private set

    /**
     * 活跃会话的 BGM 配置。null = 该会话**没配过**（等价于 [SessionBgm.MODE_GLOBAL]，跟随全局默认）。
     *
     * ⚠ 它**不**表示"在不在会话里"——曾有一段时间这两件事挤在同一个 null 上，
     * 于是"从没配过 BGM 的新会话"被判成"不在会话中"，进去一声不响，
     * 用户得手动点一次「跟随全局默认」才响（2026-09-21 反馈）。判据已拆到 [inSession]。
     */
    @Volatile
    private var activeSessionBgm: SessionBgm? = null

    /**
     * 当前是否在会话里（进聊天页的 [enterSession] 置真，[leaveSession] 置假）。
     * **"只在会话中生效"的唯一判据**，开机不再自动出声（曾经的老行为）。
     */
    @Volatile
    private var inSession = false

    /**
     * 会话句柄：最近一次 [enterSession] 发的号。只有号还最新的那次 [leaveSession] 才真的生效。
     *
     * 存在的理由是一个**必然发生的时序**：桌面三栏切会话时，Compose 先把新会话组合出来
     * （新 `ChatViewModel.init` → [enterSession]，真的比旧的先跑），再到 apply 阶段跑旧
     * `ViewModelStore.clear()` → 旧的 `onCleared` → [leaveSession]。不带句柄的话，
     * 旧会话的"离开"永远压在新会话的"进入"后面，**切一次会话 BGM 就断一次**
     * （用户 2026-09-21 反馈"桌面端切换会话，bgm 有时也放不出来"）。
     */
    @Volatile
    private var sessionHandle = 0L

    /** 发号器：只在 UI 线程自增，用 AtomicLong 是为了不用去想"万一别的线程也进会话" */
    private val sessionSeq = java.util.concurrent.atomic.AtomicLong(0L)

    /**
     * 朗读把它压低的等待时间（毫秒）。
     *
     * 为什么不立即恢复：流式自动朗读是**按句**发声的，句与句之间引擎会短暂空闲，
     * `speaking` 会掉回 false 再升回去；立刻恢复的话 BGM 音量会在一句话中间反复起落。
     * 1.2 秒足够盖住句间空隙，又短到用户察觉不出"读完还要等一下"。
     */
    private const val DUCK_LINGER_MS = 1200L

    /** 朗读期间的 BGM 音量因子：并行听——音乐压到两成垫底，不掐断（用户 2026-09-21 口径） */
    private const val DUCK_VOLUME_FACTOR = 0.2f

    @Volatile
    private var latest: AiSettings? = null

    /** 被朗读压低（[duckForSpeech]） */
    @Volatile
    private var ducked = false

    /** 应用退到后台（Android 生命周期回调置位；桌面恒为 false——窗口最小化不该停音乐） */
    @Volatile
    private var background = false

    /** 只有 [io] 线程读写：当前引擎与它正在放的文件 */
    private var engine: AudioPlayerEngine? = null
    private var playingPath: String? = null

    /** 试听专用引擎（与正式 BGM 分开，互不打断对方的播放） */
    private var previewEngine: AudioPlayerEngine? = null

    /** 取消"几秒后自动停试听"用的令牌（同 [unduckToken] 的用法） */
    @Volatile
    private var previewToken = 0

    /**
     * 设置页换音源时试听多久（秒）。
     *
     * 为什么是 6：内置环境音是 16 秒的无缝循环，太短听不出"是不是这个感觉"；
     * 而用户是在逐个换着挑，太长就得等它放完——6 秒足够认出雨声/海浪的区别，也不碍事。
     */
    private const val PREVIEW_SECONDS = 6

    /** 取消"延迟恢复"用的令牌（比较与自增都不在 [io] 线程上，故 @Volatile） */
    @Volatile
    private var unduckToken = 0

    private val io: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "whale-bgm").apply { isDaemon = true }
        }

    // ─────────────────────────── 对外入口（任意线程可调） ───────────────────────────

    /** 启动时调一次：把全局设置读进来备用（**不再直接开播**——BGM 只在会话中生效） */
    fun syncFromSettings() {
        sync(Repository.loadSettings())
    }

    /**
     * 设置变化后调它（`Repository.saveSettings` 里已经挂了一处，所以正常路径不必手动调）。
     * 幂等：同一个音源只会被起播一次，只改音量就只改音量。
     */
    fun sync(settings: AiSettings) {
        latest = settings
        schedule()
    }

    /**
     * 进入会话（ChatViewModel 创建时调）：[bgm] = 该会话的配置，**null = 从没配过 ⇒ 跟随全局默认**。
     *
     * 返回本会话的**句柄**——离开时必须原样交回 [leaveSession]（理由见 [sessionHandle]）。
     */
    fun enterSession(bgm: SessionBgm?): Long {
        activeSessionBgm = bgm
        inSession = true
        val handle = sessionSeq.incrementAndGet()
        sessionHandle = handle
        schedule()
        return handle
    }

    /** 会话配置变了（本会话设定里改了音源/音量）：重新解析。句柄不变（还是同一个会话） */
    fun updateSession(bgm: SessionBgm?) {
        activeSessionBgm = bgm
        schedule()
    }

    /**
     * 离开会话（ChatViewModel.onCleared）：音乐停（"只在会话中生效"的另一半）。
     *
     * [handle] 必须是本会话 [enterSession] 返回的那个号：**迟到的离开不生效**——
     * 桌面切会话是"新的先进入、旧的后离开"，不带号就会把新会话的音乐一起停掉。
     */
    fun leaveSession(handle: Long) {
        if (handle != sessionHandle) return
        inSession = false
        activeSessionBgm = null
        schedule()
    }

    /**
     * 拖动音量滑条时调它：**只改实时音量、不落盘**。
     *
     * 为什么与 [sync] 分开：滑条每拖一格都会回调一次，走 `Repository.saveSettings`
     * 就是每秒几十次写 `settings.json`（串行写队列还会排起长队）。界面在拖动中调这里，
     * 松手（界面里是 `onValueChangeFinished` → [commitVolume]）再把整个设置落盘一次。
     */
    fun setVolumeLive(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        val s = latest
        if (s != null) latest = s.copy(bgmVolume = v)
        // ⚠ 试听引擎也要跟着动：设置页里聊天页已离开组合（没有活跃会话），此刻**唯一听得到的就是试听**。
        //    只改正式 BGM 的音量，用户在设置页拖滑条会一点反馈都没有（当时定性的两半之一）。
        io.execute { runCatching { previewEngine?.setVolume(v) } }
        if (s != null) schedule()
    }

    /**
     * 松手落盘：把音量写进**全局设置**（`AiSettings.bgmVolume`）。
     *
     * 音量全 App 只有这一个数——会话内的滑条与设置页的滑条是同一个值
     * （用户口径：优先共享，不要分成两套）。所以两处松手都调它，不再往 `SessionBgm.volume` 写。
     */
    fun commitVolume(volume: Float) {
        val s = Repository.loadSettings()
        Repository.saveSettings(s.copy(bgmVolume = volume.coerceIn(0f, 1f))) // 内部会 sync 回来
    }

    /** 朗读开读：BGM 压低音量让路（不再整段停，见 [DUCK_VOLUME_FACTOR]） */
    fun duckForSpeech() {
        ducked = true
        io.execute { unduckToken++; applyOnIo() }
    }

    /** 朗读读完 / 被停止：延迟一下再恢复原音量（理由见 [DUCK_LINGER_MS]） */
    fun unduckAfterSpeech() {
        ducked = false
        val my = ++unduckToken
        runCatching {
            io.schedule({ if (my == unduckToken && !ducked) applyOnIo() }, DUCK_LINGER_MS, TimeUnit.MILLISECONDS)
        }
    }

    /** Android：整个应用退到后台/回到前台（别的 Activity 起来不该停，所以按"进程级"判断） */
    fun setAppForeground(foreground: Boolean) {
        background = !foreground
        schedule()
    }

    /** 立即停播（换账号/退出登录时用；设置里关开关走 [sync] 那条路） */
    fun stopNow() {
        latest = null
        ducked = false
        background = false
        activeSessionBgm = null
        inSession = false
        sessionHandle = 0L
        io.execute {
            unduckToken++
            stopEngineOnIo()
            stopPreviewOnIo()
            publish(null, null)
        }
    }

    /**
     * 音乐库「试听」：放一遍就停（不循环）。与正式 BGM 用不同引擎实例，互相不打断；
     * 再次调用=切换试听对象；[stopPreview] 停。起播失败原因给 [previewError]——
     * m4a 这类"声明支持但个别文件解不了"的问题靠它当场现形，不再无声无息。
     */
    fun preview(file: File) = io.execute { previewOnIo(file, null) }

    /**
     * **设置页换音源时的几秒钟试听**（用户口径：「选择不同的声音时，需要给几秒钟的试听」）。
     *
     * 与 [preview] 的差别：① 只响 [PREVIEW_SECONDS] 秒就自己停，用户不必再点一下；
     * ② 直接收**音源 id**（内置档 / `lib:<id>`），文件解析放在后台线程——
     * 内置音源第一次用到要从安装包里复制约 1MB 出来，不该卡在点 chip 的那一帧上。
     *
     * 解析不到文件时照样给 [previewError]（"指了名却没文件"不许静默）。
     */
    fun previewSource(sourceId: String, seconds: Int = PREVIEW_SECONDS) = io.execute {
        val s = latest
        val file = if (s == null) null else pathFor(sourceId, s)?.let { File(it) }
        if (file == null) {
            val reason = if (sourceId.isBlank()) "还没选音源" else "这个音源的音频文件不见了"
            WhaleLog.w("BgmPlayer", "试听失败：$reason（$sourceId）")
            stopPreviewOnIo()
            publishPreview(null, reason)
        } else {
            previewOnIo(file, seconds)
        }
    }

    fun stopPreview() {
        io.execute {
            stopPreviewOnIo()
        }
    }

    // ─────────────────────────── 内部 ───────────────────────────

    /** [preview] / [previewSource] 的公共实现。**必须在 [io] 线程上跑** */
    private fun previewOnIo(file: File, seconds: Int?) {
        stopPreviewOnIo()
        val e = Voice.audioPlayerFactory()
        val ok = runCatching {
            // 音量＝用户的音量设置，**不是写死的 1.0**（理由见 [previewVolume]）
            e.start(file, loop = false, volume = previewVolume()) {
                Platform.ui.runOnUiThread { previewPath = null }
            }
        }.getOrDefault(false)
        if (!ok) {
            val reason = e.lastError ?: "播放器起播失败（未给出原因）"
            WhaleLog.w("BgmPlayer", "试听起播失败（${file.name}）：$reason")
            publishPreview(null, reason)
            return
        }
        previewEngine = e
        publishPreview(file.absolutePath, null)
        if (seconds != null && seconds > 0) {
            val my = ++previewToken
            // 令牌比对：这几秒内用户又点了别的音源/手动停了，就别去停那一个
            runCatching {
                io.schedule(
                    { if (my == previewToken && previewEngine === e) stopPreviewOnIo() },
                    seconds * 1000L,
                    TimeUnit.MILLISECONDS
                )
            }
        }
    }

    private fun schedule() = io.execute { applyOnIo() }

    /** 一次"现在该怎么响"的解析结果 */
    private class Desired(val path: String?, val volume: Float, val missingHint: String? = null)

    /**
     * 真的去看一眼"现在该放什么"（**在 [io] 线程上跑**）。
     * 读 [latest] 而不是参数：连点几下开关时，执行的是最后那一份设置。
     */
    private fun applyOnIo() {
        val s = latest
        val desired = if (s == null) null else resolveDesired(s)
        val playing = engine != null && playingPath != null
        when {
            // 不该响（不在会话/后台/关了/音源空）：停掉。missingHint 非空时把原因给出去——
            // "指了名却没文件"不再是静默无声（最难查的一类遗留问题，用户 2026-09-21 又踩到）
            desired == null || desired.path == null -> {
                if (playing) stopEngineOnIo()
                publish(null, desired?.missingHint)
            }
            playingPath == desired.path && engine != null -> {
                // 同源：只更新音量（拖滑条/朗读压低恢复时每秒会来很多次，不能每次都重启播放）
                runCatching { engine?.setVolume(currentVolume(desired.volume)) }
                publish(desired.path, null)
            }
            else -> {
                stopEngineOnIo()
                val e = Voice.audioPlayerFactory()
                val ok = runCatching {
                    e.start(File(desired.path), loop = true, volume = currentVolume(desired.volume)) { /* 循环播放不会回调 */ }
                }.getOrDefault(false)
                if (ok) {
                    engine = e
                    playingPath = desired.path
                    publish(desired.path, null)
                } else {
                    // 起播失败必须留痕（本项目的老坑：静默无声最难查）
                    val reason = e.lastError ?: "播放器起播失败（未给出原因）"
                    WhaleLog.w("BgmPlayer", "BGM 起播失败（${File(desired.path).name}）：$reason")
                    publish(null, reason)
                }
            }
        }
    }

    /** 朗读压低时的实际音量 */
    private fun currentVolume(target: Float): Float =
        if (ducked) target * DUCK_VOLUME_FACTOR else target

    /**
     * 试听用的音量＝用户的**音量设置**（[AiSettings.bgmVolume]）。
     *
     * 曾经写死 `1f`：设置页里聊天页已离开组合（没有活跃会话 ⇒ 正式 BGM 不响），
     * 那时**唯一听得到的声音就是试听**，于是"拖音量滑条 → 一点变化都没有"，
     * 用户报的「BGM 声音调节好像不生效」一半就出在这里。
     *
     * 音量 0 时试听就是静音，这是**对**的行为——卡片上写着当前百分比，不会让人以为坏了。
     */
    private fun previewVolume(): Float = (latest?.bgmVolume ?: 1f).coerceIn(0f, 1f)

    /**
     * 解析"现在该放什么"：
     * ① 不在会话里 / 退后台 → 不响；② 会话配置 off → 不响；③ on → 自选音源；
     * ④ 跟随全局（含"从没配过"＝null） → 全局默认（`bgmEnabled` + `bgmSource`）。
     *
     * ⚠ **音量四条路都取 `s.bgmVolume`**，会话不再有自己的音量。
     * 旧口径是"本会话自定义时 `SessionBgm.volume` 覆盖全局"，后果是：在会话里拖音量滑条
     * （[setVolumeLive] 只改 `latest.bgmVolume`）**毫无反应**，必须松手把全会话配置写回去才生效；
     * 而且设置页那个滑条对这类会话**永远无效**。用户口径是"优先与设置页共享音量"，
     * 于是收敛成一个数——[SessionBgm.volume] 从此不参与解析（字段留着只为读得懂老 JSON）。
     */
    private fun resolveDesired(s: AiSettings): Desired {
        if (!inSession) return Desired(null, 0f)
        if (background) return Desired(null, 0f)
        // 没配过 ⇒ 跟随全局默认（用户 2026-09-21：新会话进去就该响，不该再点一次「跟随全局默认」）
        val bgm = activeSessionBgm ?: SessionBgm(SessionBgm.MODE_GLOBAL)
        return when (bgm.mode) {
            SessionBgm.MODE_OFF -> Desired(null, 0f)
            SessionBgm.MODE_ON -> when {
                bgm.sourceId.isBlank() -> Desired(null, 0f)
                else -> Desired(pathFor(bgm.sourceId, s), s.bgmVolume, missingHintFor(bgm.sourceId, s))
            }
            else -> // "global" 与历史未知值
                if (!s.bgmEnabled) Desired(null, 0f)
                else Desired(pathFor(s.bgmSource, s), s.bgmVolume, missingHintFor(s.bgmSource, s))
        }
    }

    /** 音源 id → 本地文件。`lib:` 前缀 = 音乐库曲目；内置档的现场合成兜底照旧 */
    private fun pathFor(sourceId: String, s: AiSettings): String? = when {
        sourceId.isBlank() -> null
        sourceId == BgmSources.SOURCE_USER -> // 老数据兼容：迁移进音乐库之前还可能指着全局单文件
            s.bgmUserPath.takeIf { it.isNotBlank() && File(it).isFile }?.let { File(it).absolutePath }
        sourceId.startsWith("lib:") ->
            Repository.bgmLibraryFileFor(sourceId.removePrefix("lib:"))?.absolutePath
        else -> BgmSources.fileFor(sourceId)?.absolutePath
    }

    /** 指了名却拿不到文件时的用户提示（null = 不需要；有了它"无声"就不再无声无息） */
    private fun missingHintFor(sourceId: String, s: AiSettings): String? {
        if (sourceId.isBlank() || pathFor(sourceId, s) != null) return null
        return "背景音乐「${labelFor(sourceId, s)}」的音频文件不见了（可能被移动或删除），请重新选择音源"
    }

    /** 音源 id → 给用户看的名字（会话配置缺省音量时也用它标注来源） */
    fun labelFor(sourceId: String, s: AiSettings): String = when {
        sourceId == BgmSources.SOURCE_USER -> s.bgmUserName.ifBlank { "我上传的音乐" }
        sourceId.startsWith("lib:") ->
            Repository.listBgmLibrary().firstOrNull { it.id == sourceId.removePrefix("lib:") }
                ?.displayName ?: "我的音乐"
        else -> BgmSources.labelOf(sourceId)
    }

    private fun stopEngineOnIo() {
        val e = engine ?: run { playingPath = null; return }
        engine = null
        playingPath = null
        runCatching { e.stopAndRelease() }
    }

    private fun stopPreviewOnIo() {
        previewToken++
        val e = previewEngine ?: return
        previewEngine = null
        runCatching { e.stopAndRelease() }
        publishPreview(null, null)
    }

    private fun publish(path: String?, error: String?) {
        Platform.ui.runOnUiThread {
            currentPath = path
            lastError = error
        }
    }

    private fun publishPreview(path: String?, error: String?) {
        Platform.ui.runOnUiThread {
            previewPath = path
            previewError = error
        }
    }
}
