package com.mysticat.roleplay.data

import java.io.File

/**
 * 「目录里每个 JSON 只解析一次」的扫描缓存。
 *
 * ## 为什么需要它
 * 角色与会话都是**一实体一文件**的 JSON 存储，列表页要的是"整个目录读一遍"。最朴素的写法
 * （`dir.listFiles().map { 解析 }`）在两种情况下白花钱：
 * ① 同一个列表在界面生命周期里被读多次（每次重组、每次切回页面）；
 * ② 冷启动后第一次读——那一次真的必须解析全部，但**它不能在界面线程上做**。
 *
 * 这里解决 ①：按「文件名 → (mtime, 大小, 已解析对象)」缓存，文件没变就直接复用；
 * 只有 mtime/大小真的变过的那个文件（原子写 = 新 mtime）才重新解析。② 由调用方负责
 * （把 [read] 放在 IO 线程上，见 `Repository` 各调用点与第 64 轮的「搬到后台」）。
 *
 * ## 为什么抽成独立类
 * 第 59 轮的教训：**能被自检直接验的东西要抽成纯单元**。这个类不碰任何全局状态
 * （不读 `Repository` 的目录、不改任何单例），所以桌面 `--smoke` 可以在一个临时目录上
 * 构造它、造几份文件、断言"第二次读不重复解析、改过的那个文件才重解析"——
 * 否则这类缓存只能靠"看起来更快"来'验'，而计时在冷启动/杀毒扫描下会抖。
 *
 * 只缓存**时间戳与大小都没变**的文件，所以外部改文件、损坏改名、账号切换都不会读到旧数据；
 * 目录换了（账号切换）缓存整体作废，文件消失的条目会被清掉（否则缓存会一直涨）。
 *
 * 公开（而非 `internal`）只为一件事：桌面 `--smoke` 在**另一个模块**里，`internal` 它看不见，
 * 而这个类的行为正是本轮要常驻守住的东西（见上面的"为什么抽成独立类"）。
 */
class DirScanCache<T>(private val parse: (File) -> T?) {
    private class Entry<T>(val modified: Long, val size: Long, val value: T)

    private val cache = HashMap<String, Entry<T>>()
    private var dirPath: String? = null

    /**
     * 真的调用了几次 [parse]（缓存命中不计数）。
     * 自检用：它是"缓存有没有生效"的**确定性判据**，比计时断言稳。
     */
    var parseCount: Int = 0
        private set

    @Synchronized
    fun read(dir: File): List<T> {
        if (dirPath != dir.absolutePath) {
            cache.clear()
            dirPath = dir.absolutePath
        }
        val files = dir.listFiles { f -> f.name.endsWith(".json") } ?: return emptyList()
        val seen = HashSet<String>(files.size)
        val out = ArrayList<T>(files.size)
        files.forEach { f ->
            seen += f.name
            val modified = f.lastModified()
            val size = f.length()
            val cached = cache[f.name]
            if (cached != null && cached.modified == modified && cached.size == size) {
                out += cached.value
            } else {
                val value = parse(f)
                if (value == null) cache.remove(f.name)
                else {
                    parseCount++
                    cache[f.name] = Entry(modified, size, value)
                    out += value
                }
            }
        }
        // 文件已不在的条目要清掉，否则缓存会一直涨
        cache.keys.retainAll(seen)
        return out
    }
}
