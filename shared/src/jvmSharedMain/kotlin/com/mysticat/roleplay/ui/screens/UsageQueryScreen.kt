package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.UsageLog
import kotlin.math.floor
import com.mysticat.roleplay.ui.BackArrowButton
import com.mysticat.roleplay.ui.SectionCard
import com.mysticat.roleplay.ui.ArrowScrollRow
import com.mysticat.roleplay.ui.WhaleBackHandler
import com.mysticat.roleplay.ui.WhaleChip
import com.mysticat.roleplay.ui.isDesktopLayout
import kotlinx.coroutines.flow.first

/**
 * 用量统计（台账 112）：**"我到底花了多少"这一页**。
 *
 * 数据源＝[UsageLog] 落盘的 JSONL（`accounts/<id>/usage/log.jsonl`），每次真实 API 请求一行，
 * 覆盖六条链路：主对话 / 对话辅助 / 创作 / 生图 / 语音朗读 / 语音输入。**聚合与筛选全在这一页做**——
 * 数据层只负责追加与按时间区间读（`events`），这样口径改一次就够，不必迁移历史数据。
 *
 * 三个筛选维度各自独立、可叠加：区间（今天/昨天/近七天/近三十天/自定义）、链路、供应商、会话。
 * 区间既决定**分桶粒度**也决定**画什么图**：
 * - **今天/昨天**＝按小时 24 格 → **两张 Canvas 图**：请求次数＝波状图（平滑曲线＋面积填充）、
 *   token＝按模型分色的堆叠柱＋图例。今天与昨天都给满 24 格且不截到当前时刻
 *   （用户 09-30：横轴是"一天里的钟点"这个固定刻度，随时刻伸缩的轴每刷新一次形状都不一样）。
 * - **近七天/近三十天/自定义（≤ 一年）**＝按天 → **两张热力图**，形态照 GitHub 的贡献图
 *   （列＝周、行＝周一到周日、顶上标月份、超宽横向滚动），色深表量、底部色阶图例当纵轴。
 *
 * 拆两张是因为量纲差三个数量级，挤一张必有一根被压平。
 *
 * **选中＝一根虚线 ＋ 跟着走的弹框**（台账 116 第三刀，用户 09-30 口径，取代原先"点一下弹卡片"）：
 * 桌面鼠标**悬停到哪一列**就选哪一列（整列都算，不必落在柱子/曲线上）；手机上只有**按住**期间才显示，
 * 拖动换列、抬手即收。曲线图额外在选中的那个数据点上画一个圆圈（见 [RequestsWaveCard]）；
 * 热力图则是给选中的那一格套一圈**虚线**。
 *
 * ⚠ 两处口径，页面上必须讲清，写文案/改数字时别踩：
 * - **生图没有 token**（图像端点不回 usage），只记张数（`Event.images`）；**语音两条链路同理**
 *   （TTS 直接回音频字节、ASR 回文本，协议里就没有 usage），见 [UsageLog.kindHasTokens]——
 *   所以"生成 4 张图"这一项永远显示 token 未知，**不能写成"生图用量"**。
 * - **部分端点不回 usage**（例如 OpenAI 官方流式要不带 `stream_options` 就没有），
 *   那些行**计一次请求、token 计 0**。因此"token 合计"永远可能小于"请求次数"，
 *   页面上把 [UsageLog.Summary.unreported] 的条数显式写出来，否则用户会以为漏算了。
 *
 * 短区间那两张图是 **Compose Canvas 自绘**（决策见台账 §二 17②）：不引第三方图表库——
 * 十来行画法换不来一个依赖，而打包时还要多背一份 jar。热力图格子最多 366（自定义上限一年），
 * 用普通 Compose 布局铺（要放月份与周几标签、还要能横向滚），不走 Canvas。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageQueryScreen(
    onBack: () -> Unit,
    /** 桌面三栏：内嵌进第三栏（入口＝第二栏「用量统计」分组），不套 Scaffold/顶栏 */
    embedded: Boolean = false,
) {
    WhaleBackHandler { onBack() }

    // 筛选态
    var rangeIdx by remember { mutableIntStateOf(0) }
    var kindFilter by remember { mutableStateOf<String?>(null) }
    var providerFilter by remember { mutableStateOf<String?>(null) }
    var sessionFilter by remember { mutableStateOf<String?>(null) }
    // 库里没记录时不至于"看起来是空区间"：默认给今天，进来就能看到时间轴
    val range: UsageLog.Range = UsageLog.Range.entries[rangeIdx]
    // 自定义区间两端（台账 116-C）：默认近七天，两端都是**整天**（含首含尾）
    var customFrom by remember { mutableLongStateOf(UsageLog.dayStartDaysAgo(6)) }
    var customTo by remember { mutableLongStateOf(UsageLog.dayStartDaysAgo(0)) }

    // 底表读一次给下面所有块共用（别让每块各自读盘）。切换筛选不重读，改 rev 才重读。
    var rev by remember { mutableIntStateOf(0) }
    val all = remember(rev) { UsageLog.events() }

    // 区间窗口＝桶区间覆盖的**整段**（首桶起点 → 末桶终点）。
    // ⚠ 上界不能省：自定义区间可能选在上个月，只卡下界会把今天的行也一起算进来。
    val buckets = remember(range, rev, customFrom, customTo) {
        UsageLog.bucketsOf(range, customFrom = customFrom, customTo = customTo)
    }
    val windowStart = buckets.first().fromMs
    val windowEnd = buckets.last().toMs
    val inRange = remember(all, windowStart, windowEnd) {
        all.filter { it.at >= windowStart && it.at < windowEnd }
    }
    // 短区间（今天/昨天）按小时分桶 → 两张曲线/柱状图；长区间改热力图（116-C）
    val hourly = range == UsageLog.Range.TODAY || range == UsageLog.Range.YESTERDAY

    // 可选项按**当前区间内实际出现过的**取值给（而不是写死五种链路全摆出来）——
    // 写死的话用户会看到一颗点不开的"生图"胶囊，点完得到一句"没有符合筛选的记录"。
    val kinds = remember(inRange) { inRange.map { it.kind }.distinct() }
    val providers = remember(inRange) { inRange.map { UsageLog.providerHost(it.provider) }.filter { it.isNotEmpty() }.distinct() }
    val sessions = remember(inRange) { inRange.map { UsageLog.sessionLabel(it) }.distinct() }

    // 筛选后的集合。⚠ 三个筛选是**与**关系；sessionFilter 存显示名（会话 id 对用户没意义）
    val filtered = remember(inRange, kindFilter, providerFilter, sessionFilter) {
        inRange.filter { e ->
            (kindFilter == null || e.kind == kindFilter) &&
                (providerFilter == null || UsageLog.providerHost(e.provider) == providerFilter) &&
                (sessionFilter == null || UsageLog.sessionLabel(e) == sessionFilter)
        }
    }
    // 每根柱子的计数与 token：柱子按桶边界切片，不重新分桶（数据层的 [UsageLog.bucketsOf] 是口径）
    val perBucket = remember(filtered, buckets) {
        buckets.map { b -> filtered.filter { it.at >= b.fromMs && it.at < b.toMs } }
    }
    val summary = remember(filtered) { UsageLog.summarize(filtered) }
    // 分色图（116-F）：模型顺序**按名字升序**（跨区间稳定，不随谁多谁少跳位）＋ 各模型总量给图例
    val models = remember(filtered) { UsageLog.modelOrder(filtered) }
    val tokensByModel = remember(filtered) { UsageLog.tokensByModel(filtered) }

    val body: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 区间（116-C 加了第五档「自定义」）─────────────────────
            item {
                // 116-G：桌面溢出给左右箭头（滚轮/拖动照旧），手机横滑不变
                ArrowScrollRow(Modifier.fillMaxWidth()) {
                    UsageLog.Range.entries.forEachIndexed { i, r ->
                        WhaleChip(
                            selected = r == range,
                            onClick = { rangeIdx = i },
                            label = { Text(r.label) }
                        )
                    }
                }
            }
            // 自定义区间：两个日期按钮，各自弹日历。**含首含尾**、上限一年
            if (range == UsageLog.Range.CUSTOM) {
                item {
                    CustomRangeRow(
                        from = customFrom,
                        to = customTo,
                        onFrom = { d ->
                            customFrom = d
                            // 上限一年：起点往后拖太远时把结束端拉回来（含首含尾 ⇒ 差 ≤ 上限）
                            val limit = UsageLog.customSpanLimitMs()
                            if (customTo < d) customTo = d
                            else if (customTo - d > limit) customTo = d + limit
                        },
                        onTo = { d ->
                            customTo = d
                            val limit = UsageLog.customSpanLimitMs()
                            if (customFrom > d) customFrom = d
                            else if (d - customFrom > limit) customFrom = d - limit
                        },
                    )
                }
            }

            // ── 空态：把"为什么是空的"讲清楚，而不是光一个 0 ──────────────
            if (inRange.isEmpty()) {
                item { UsageEmptyCard(range) }
            } else {
                // ── 小计 ────────────────────────────────────────────
                item { SummaryCard(summary, filtered) }

                // ── 图表（116-B/C/D/F）：短区间两张图、长区间两张热力图 ──────────
                // **固定两张**：量纲差三个数量级，挤一张必有一根被压平（用户 09-30 口径）。
                // 没有 token 可报时第二张不删、改为写明"这段没有 token"——删掉会让人以为漏了。
                item {
                    ChartsPair(
                        hourly = hourly,
                        buckets = buckets,
                        perBucket = perBucket,
                        models = models,
                        tokensByModel = tokensByModel,
                        // 热力图排法随区间走：自定义最长一年＝53 周，只能横过来（见 [HeatStyle]）
                        heatStyle = if (range == UsageLog.Range.CUSTOM) HeatStyle.GITHUB else HeatStyle.CALENDAR,
                    )
                }

                // ── 链路小计：四条链各花了多少，一眼看出是谁在花钱 ──────
                if (kinds.size > 1) {
                    item { KindBreakdownCard(filtered) }
                }

                // ── 三个筛选：链路 / 供应商 / 会话（各一行胶囊）──────────
                if (kinds.isNotEmpty()) {
                    item {
                        FilterRow(
                            label = "链路",
                            options = kinds.map { it to UsageLog.kindLabel(it) },
                            selected = kindFilter,
                            onSelect = { kindFilter = if (kindFilter == it) null else it },
                        )
                    }
                }
                if (providers.size > 1) {
                    item {
                        FilterRow(
                            label = "供应商",
                            // 键仍是 host（同名不同地址不能被并成一条），显示的是厂商名（台账 116-K）
                            options = providers.map { it to UsageLog.providerLabel(it) },
                            selected = providerFilter,
                            onSelect = { providerFilter = if (providerFilter == it) null else it },
                        )
                    }
                }
                if (sessions.size > 1) {
                    item {
                        FilterRow(
                            label = "会话",
                            options = sessions.map { it to it },
                            selected = sessionFilter,
                            onSelect = { sessionFilter = if (sessionFilter == it) null else it },
                        )
                    }
                }

                // ── 筛选后的明细（倒序：最近的在最上面）──────────────────
                if (filtered.isEmpty()) {
                    item {
                        SectionCard(contentPadding = 14.dp) {
                            Text(
                                "没有符合当前筛选的记录（点上面的胶囊可以清掉筛选）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            "明细 · ${filtered.size} 条",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    items(filtered.sortedByDescending { it.at }, key = { "${it.at}-${it.sessionId}-${it.model}" }) { e ->
                        UsageRow(e)
                    }
                }
            }
        }
    }

    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            EmbeddedPageHeader("用量统计", onBack)
            Box(Modifier.weight(1f)) { body() }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("用量统计") },
                    navigationIcon = { BackArrowButton(onBack) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) { body() }
        }
    }
}

/**
 * 两张图表摆一起：**宽屏并排、窄屏上下叠**（用户 09-30："桌面端可以把 API 请求次数和 token 并排放"）。
 * 判据用**可用宽度**而不是"是不是桌面端"——桌面窗口缩窄时也该回落成叠放，宽平板/分屏同样受益。
 * 阈值 720dp：两张各分到 350dp 上下，扣掉 46dp 的刻度列还剩 300dp 画 24 格，柱高与钟点都读得出；
 * 再窄就该叠着放了（手机上那 360dp 一分为二以后，横轴刻度会挤成一团）。
 */
@Composable
private fun ChartsPair(
    hourly: Boolean,
    buckets: List<UsageLog.Bucket>,
    perBucket: List<List<UsageLog.Event>>,
    models: List<String>,
    tokensByModel: Map<String, Int>,
    heatStyle: HeatStyle,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 桌面端**一律并排**（用户 09-30 口径）；其它平台按宽度决定（宽平板/分屏照样受益）。
        // ⚠ 阈值不能照"整个窗口有多宽"来想：桌面是三栏外壳，这一页住在**第三栏**里——
        // 实测 1456px 的窗口只留出 664dp 可用，定 720dp 就永远是叠的（用户 09-30"怎么变都是上下排"）。
        // 两张各 ~300dp ＋ 10dp 间距就够画 24 格，故取 620dp。
        if (isDesktopLayout || maxWidth >= 620.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TwoCharts(hourly, buckets, perBucket, models, tokensByModel, heatStyle, Modifier.weight(1f), Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TwoCharts(hourly, buckets, perBucket, models, tokensByModel, heatStyle, Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
            }
        }
    }
}

/** 两张图本体（第一张＝请求次数，第二张＝token）：并排与叠放共用这一份调用，免得写两遍 */
@Composable
private fun TwoCharts(
    hourly: Boolean,
    buckets: List<UsageLog.Bucket>,
    perBucket: List<List<UsageLog.Event>>,
    models: List<String>,
    tokensByModel: Map<String, Int>,
    heatStyle: HeatStyle,
    first: Modifier,
    second: Modifier,
) {
    if (hourly) {
        RequestsWaveCard(buckets, perBucket, first)
        TokensStackCard(buckets, perBucket, models, tokensByModel, second)
    } else {
        HeatmapCard("API 请求次数", buckets, perBucket, Metric.REQUESTS, heatStyle, first)
        HeatmapCard("token 活动", buckets, perBucket, Metric.TOKENS, heatStyle, second)
    }
}

/** 一张图画哪个口径 */
private enum class Metric { TOKENS, REQUESTS }

/**
 * 分色图的调色板（台账 116-F）。**长度必须等于 [UsageLog.MODEL_PALETTE_SIZE]**——
 * 色号＝`modelColorIndex(模型名) % 长度`，两张对不上就会数组越界（桌面自检断言这条）。
 * 十个色相彼此拉开（不照抄参考图的配色，只借"按模型分色"这个形式）。
 */
val UsageModelPalette: List<Color> = listOf(
    Color(0xFF4C8DFF), Color(0xFFF2994A), Color(0xFF27AE60), Color(0xFFEB5757),
    Color(0xFF9B51E0), Color(0xFF2D9CDB), Color(0xFFB8860B), Color(0xFF56A6CC),
    Color(0xFFBB6BD9), Color(0xFF6FCF97),
)

// ── 短区间（今天/昨天）：两张图 ────────────────────────────────────────────

/**
 * 「API 请求次数」＝**波状图**（台账 116-D，形态取自用户参考图 2）：一条带填充面积的**平滑曲线**。
 * 之所以不沿用柱状：一天 24 根柱子读不出"什么时候在用"的形状，曲线一眼就能看出峰在哪。
 * 纵轴真刻度（0/一半/上界）＋横向网格线，横轴按桶边界标 `xx:xx`（116-I）。
 * 选中某一列（鼠标悬停／手机按住）出**虚线 ＋ 曲线上的圆圈 ＋ 白卡**（116 第三刀）。
 */
@Composable
private fun RequestsWaveCard(
    buckets: List<UsageLog.Bucket>,
    perBucket: List<List<UsageLog.Event>>,
    modifier: Modifier = Modifier,
) {
    val values = perBucket.map { it.size }
    val lineColor = MaterialTheme.colorScheme.primary
    SectionCard(title = "API 请求次数", contentPadding = 14.dp, modifier = modifier) {
        ChartBody(
            buckets = buckets,
            values = values,
            metric = Metric.REQUESTS,
            perBucket = perBucket,
            caption = "曲线＝每小时发出的请求数，这段合计 ${values.sum()} 次",
        ) { slotPx, axisMax, cursor ->
            val plotH = size.height
            // 折线点取**桶中心**：高点落在该小时中间，与横轴上"这个钟点"的刻度读起来一致
            val pts = values.mapIndexed { i, v ->
                Offset(slotPx * i + slotPx / 2f, plotH - v.toFloat() / axisMax * plotH)
            }
            if (pts.isEmpty()) return@ChartBody null
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                // 平滑：逐段用 Catmull-Rom 换算成三次贝塞尔——**曲线穿过每一个数据点**。
                // 原先每段拿"上一点"当控制点画到两点中点，曲线只穿过中点，于是峰顶画不到真实高度、
                // 选中的圆圈会浮在曲线外面（116 第三刀改；控制点夹进画布，免得峰谷处甩出轴外）。
                for (i in 0 until pts.size - 1) {
                    val p0 = pts[if (i == 0) 0 else i - 1]
                    val p1 = pts[i]
                    val p2 = pts[i + 1]
                    val p3 = pts[if (i + 2 > pts.size - 1) pts.size - 1 else i + 2]
                    val c1y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(0f, plotH)
                    val c2y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(0f, plotH)
                    cubicTo(p1.x + (p2.x - p0.x) / 6f, c1y, p2.x - (p3.x - p1.x) / 6f, c2y, p2.x, p2.y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, plotH)
                lineTo(pts.first().x, plotH)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx()))
            // 圆圈就画在这个桶的数据点上；曲线现在穿过它，所以圆心与线重合
            cursor?.let { pts.getOrNull(it) }
        }
    }
}

/**
 * 「token」＝**按模型分色**的堆叠柱 ＋ 图例（台账 116-F）：一根柱子＝这个时段各模型的 token 摞起来，
 * 每段一色；色号由模型名定（[UsageLog.modelColorIndex]），**跨区间、跨筛选都不变**，两张截图才比得出来。
 * 段序按模型名升序（[UsageLog.modelOrder]）——顺序跳位就没法和图例对照。
 */
@Composable
private fun TokensStackCard(
    buckets: List<UsageLog.Bucket>,
    perBucket: List<List<UsageLog.Event>>,
    models: List<String>,
    totals: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val values = perBucket.map { UsageLog.summarize(it).totalTokens }
    // 颜色只能在组合层取（draw lambda 不是 @Composable，里面读 MaterialTheme 会直接编不过）
    val slotColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    // 色号按**这一批模型**分配（同批不撞色）——直接用名字取模会撞（deepseek-flash 与 kimi-k3 都落 1 号）
    val slots = remember(models) { UsageLog.modelColorSlots(models) }
    SectionCard(title = "token", contentPadding = 14.dp, modifier = modifier) {
        if (values.sum() <= 0) {
            Text(
                "这段区间没有可统计的 token——生图与语音端点在协议里就不回 usage，只计请求次数与张数/字数",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        ChartBody(
            buckets = buckets,
            values = values,
            metric = Metric.TOKENS,
            perBucket = perBucket,
            caption = "纵轴＝token 合计（输入＋输出），这段合计 ${formatTokens(values.sum())}",
            slots = slots,
        ) { slotPx, axisMax, _ ->
            val plotH = size.height
            val barW = (slotPx * 0.72f).coerceAtMost(30.dp.toPx())
            perBucket.forEachIndexed { i, es ->
                val cx = slotPx * i + slotPx / 2f
                val left = cx - barW / 2f
                drawRect(slotColor, Offset(left, plotH - 3.dp.toPx()), Size(barW, 3.dp.toPx()))
                val byModel = UsageLog.tokensByModel(es)
                var acc = 0
                models.forEach { m ->
                    val v = byModel[m] ?: 0
                    if (v <= 0) return@forEach
                    val h = v.toFloat() / axisMax * plotH
                    val bottom = plotH - acc.toFloat() / axisMax * plotH
                    drawRect(
                        color = UsageModelPalette[slots[m] ?: 0],
                        topLeft = Offset(left, bottom - h),
                        size = Size(barW, h)
                    )
                    acc += v
                }
            }
            // 堆叠柱不画圆圈：柱子本身就有高度，圆圈套在哪一段上都读不出来（用户口径：圆圈是"曲线图"要的）
            null
        }
        LegendRow(models, totals, slots)
    }
}

// ── 长区间（近七天/近三十天/自定义）：两张热力图 ────────────────────────────

/**
 * 热力图的两种排法（用户 09-30 口径，两种都要）：
 * - [CALENDAR]（近七天/近三十天）：**行＝周、列＝星期一到星期日**，每行行首标那周第一天的日期。
 *   这几天的"哪一天在用"是主要问题，日期摆在左边一眼能读出来；7~30 天最多 5 行，竖着放正好。
 * - [GITHUB]（自定义，最长一年）：**列＝周、行＝周一到周日**，顶上标月份、横向滚动。
 *   一年＝53 周：竖着排就是 53 行高的长条，一屏根本放不下，只能照 GitHub 的贡献图横过来。
 */
private enum class HeatStyle { CALENDAR, GITHUB }

/**
 * 长区间的**热力图**（台账 116-C）：一格＝一天，**按真实日历排**，颜色越深量越大；
 * 真实天数＝有色的格数（占位格不上色），所以"这个月有多少天"直接看得见，不写死 30 格。
 *
 * 两张（请求次数 / token）各一张、共用同一套网格：量纲差三个数量级，共用纵轴必有一头被压平。
 * 纵轴的对应物是底部的**色阶图例**（少 → 多），读者靠它把颜色读回数值。
 * 选中某一天（鼠标悬停／手机按住）出**虚线格子 ＋ 白卡**——这块此前完全没有交互。
 *
 * ⚠ 两种排法的**行高都取 [HEAT_ROW_H]（格子居中），不是格子边长**：行高一旦被行首那个日期文字的
 * 行高顶大，指针定位按固定步长算就会整行漂移（日期越长行越高，越往下越偏）。
 */
@Composable
private fun HeatmapCard(
    title: String,
    buckets: List<UsageLog.Bucket>,
    perBucket: List<List<UsageLog.Event>>,
    metric: Metric,
    style: HeatStyle,
    modifier: Modifier = Modifier,
) {
    val gitHub = style == HeatStyle.GITHUB
    val values = perBucket.map { if (metric == Metric.TOKENS) UsageLog.summarize(it).totalTokens else it.size }
    val max = values.maxOrNull() ?: 0
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.onSurface
    val grid = remember(buckets) { UsageLog.heatmapGrid(buckets) }
    val weeks = remember(buckets) { UsageLog.heatmapWeeks(buckets) }
    val months = remember(buckets, gitHub) {
        if (gitHub) UsageLog.heatmapMonthLabels(buckets) else emptyList()
    }
    // (周, 星期) → 桶下标；两种排法互为转置、都查这一张表（占位格不在表里 ⇒ 点不出来）
    val cellIndex = remember(grid) {
        HashMap<Pair<Int, Int>, Int>().also { m ->
            grid.forEach { if (it.index >= 0) m[it.week to it.weekday] = it.index }
        }
    }
    val density = LocalDensity.current
    val stepPx = with(density) { (HEAT_ROW_H + HEAT_GAP).toPx() }
    val headPx = with(density) { (HEAT_HEAD_H + HEAT_GAP).toPx() }
    val labelPx = with(density) { HEAT_LABEL_W.toPx() }
    val scroll = rememberScrollState()
    val scrollDp = with(density) { scroll.value.toDp() }
    // 选中的桶（鼠标悬停／手机按住）；换区间、改筛选后桶变了就自动清掉
    var cursor by remember(buckets, perBucket) { mutableStateOf<Int?>(null) }
    // GITHUB 排法打开就停在**最近**那一头：自定义能到 53 列，停在一年前看不出最近怎么样。
    // ⚠ 只在这一份网格**第一次量出来**时滚一次——拿 `scroll.maxValue` 当 key 的话，布局每次重算
    // （改窗口大小、工具提示进出…）都会再把视图拽回末尾，用户正在悬停时网格会突然移一段，
    // 表现成"选中的格子跟鼠标对不上"（用户 09-30 报"自定义时间一长鼠标位置好像有问题"）。
    LaunchedEffect(weeks, gitHub) {
        if (!gitHub) return@LaunchedEffect
        scroll.scrollTo(snapshotFlow { scroll.maxValue }.first { it > 0 })
    }

    SectionCard(title = title, contentPadding = 14.dp, modifier = modifier) {
        if (max <= 0) {
            Text("这段区间没有记录", style = MaterialTheme.typography.bodySmall, color = labelColor)
            return@SectionCard
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // 滚动可视宽＝整行减去左列周几标签（弹框往左还是往右放、就按这个宽度判）
            val viewportW = maxWidth - HEAT_LABEL_W
            if (gitHub) {
                // ── GitHub 排法：左列周几（固定不动）＋ 可横向滚动的网格 ──
                // ⚠ **每一列自己带指针**（不是整块挂一条再按坐标反算）：横向滚动会把内容移走，而"指针坐标到底
                // 是内容坐标还是视口坐标"正是最容易算错的地方——用户 09-30 报的"自定义区间一长、热力图鼠标
                // 位置有问题"就是它（选中的落到了已经滚出屏幕的那一端）。一格一列认自己的行号之后，
                // 只剩"y ÷ 行高 = 星期几"这一件与滚动无关的事。
                Row(Modifier.fillMaxWidth()) {
                    Column {
                        Spacer(Modifier.height(HEAT_HEAD_H + HEAT_GAP))
                        for (wd in 0 until 7) {
                            Box(
                                Modifier.width(HEAT_LABEL_W).height(HEAT_ROW_H),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // 只标一/三/五/日（同 GitHub 的稀疏标法）：七行全标会把图挤窄，四行够定位
                                if (wd % 2 == 0) {
                                    Text(
                                        UsageLog.WEEKDAY_NAMES[wd].removePrefix("周"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = labelColor,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(Modifier.height(HEAT_GAP))
                        }
                    }
                    // weight：滚动列只占剩下的宽度；**内容的宽必须用 requiredWidth**——
                    // weight 会给这一列一个"固定宽度"约束（min＝槽宽），而 `Modifier.width` 默认
                    // `enforceIncoming = true` 会把自己夹进传入约束里 ⇒ 内容被压成视口宽、`maxValue = 0`，
                    // 网格画得出去却一寸也滚不动（用户 09-30："点击没问题，但拖不到底"）。
                    // requiredWidth 明确忽略传入约束，内容才真的比视口宽、滚得动。
                    Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                        Box(Modifier.requiredWidth(gridWidth(weeks))) {
                            Column {
                                // 月份行：整块网格宽，一个月一格、按列偏移摆
                                Box(Modifier.height(HEAT_HEAD_H)) {
                                    months.forEach { m ->
                                        Text(
                                            m.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = labelColor,
                                            maxLines = 1,
                                            modifier = Modifier.align(Alignment.TopStart)
                                                .offset(x = (HEAT_CELL + HEAT_GAP) * m.week)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(HEAT_GAP))
                                Row {
                                    for (w in 0 until weeks) {
                                        Column(
                                            Modifier
                                                .width(HEAT_CELL)
                                                .pickByPointer(
                                                    locate = { p ->
                                                        cellIndex[w to (p.y / stepPx).toInt().coerceIn(0, 6)]
                                                    },
                                                    onPick = { cursor = it },
                                                )
                                        ) {
                                            for (wd in 0 until 7) {
                                                val idx = cellIndex[w to wd]
                                                Box(
                                                    Modifier.height(HEAT_ROW_H),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (idx == null) HeatBlank()
                                                    else HeatCellBox(values[idx], max, base, empty, ring, idx == cursor)
                                                }
                                                Spacer(Modifier.height(HEAT_GAP))
                                            }
                                        }
                                        Spacer(Modifier.width(HEAT_GAP))
                                    }
                                }
                            }
                            // 弹框画在**滚动内容里**、贴着选中那一列：锚点就是内容坐标，不用把滚动量减回去
                            // （滚动量只用来判"往左还是往右放得下"——判错也只是换个边，卡片仍贴着那一列）
                            cursor?.let { idx ->
                                val week = grid.firstOrNull { it.index == idx }?.week ?: 0
                                val colStart = (HEAT_CELL + HEAT_GAP) * week
                                val x = if (colStart + HEAT_CELL + 6.dp + TIP_W <= scrollDp + viewportW) {
                                    colStart + HEAT_CELL + 6.dp
                                } else {
                                    (colStart - 6.dp - TIP_W).coerceAtLeast(0.dp)
                                }
                                BucketTipCard(
                                    title = UsageLog.dayLabel(buckets[idx].fromMs),
                                    metric = metric,
                                    events = perBucket.getOrElse(idx) { emptyList() },
                                    // 热力图的颜色是"这一天有多大"、不分模型 ⇒ 弹框里不画色块（画了会以为是模型色）
                                    slots = null,
                                    modifier = Modifier.offset(x = x, y = 0.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // ── 日历排法：星期列头 ＋ 一周一行（行首是那周第一天的日期）。这一排**不滚动**，
                // 坐标就是内容坐标，可以整块挂一条指针按坐标反算 ──
                Column(
                    Modifier.fillMaxWidth().pickByPointer(
                        locate = { p -> calendarIndexAt(p, stepPx, headPx, labelPx, weeks, cellIndex) },
                        onPick = { cursor = it },
                    )
                ) {
                    Row(Modifier.height(HEAT_HEAD_H), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(HEAT_LABEL_W))
                        for (wd in 0 until 7) {
                            Box(Modifier.width(HEAT_CELL), contentAlignment = Alignment.Center) {
                                Text(
                                    HEAT_WEEKDAY_HEADS[wd],
                                    style = MaterialTheme.typography.labelSmall,
                                    color = labelColor,
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.width(HEAT_GAP))
                        }
                    }
                    Spacer(Modifier.height(HEAT_GAP))
                    for (w in 0 until weeks) {
                        Row(Modifier.height(HEAT_ROW_H), verticalAlignment = Alignment.CenterVertically) {
                            // 行首标这一周**第一个有数据的格子**的日期：首周可能从周三才开始，标出来才不迷路
                            val firstIdx = (0 until 7).firstNotNullOfOrNull { wd -> cellIndex[w to wd] }
                            Text(
                                firstIdx?.let { UsageLog.monthDayLabel(buckets[it].fromMs) } ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(HEAT_LABEL_W)
                            )
                            for (wd in 0 until 7) {
                                val idx = cellIndex[w to wd]
                                if (idx == null) HeatBlank()
                                else HeatCellBox(values[idx], max, base, empty, ring, idx == cursor)
                                Spacer(Modifier.width(HEAT_GAP))
                            }
                        }
                        Spacer(Modifier.height(HEAT_GAP))
                    }
                }
                // 弹框放在选中那一格所在列的左/右侧（[tipX]），不压住那一列
                cursor?.let { idx ->
                    val major = grid.firstOrNull { it.index == idx }?.weekday ?: 0
                    val left = HEAT_LABEL_W + (HEAT_CELL + HEAT_GAP) * major
                    BucketTipCard(
                        title = UsageLog.dayLabel(buckets[idx].fromMs),
                        metric = metric,
                        events = perBucket.getOrElse(idx) { emptyList() },
                        slots = null,
                        modifier = Modifier.offset(x = tipX(left, left + HEAT_CELL, maxWidth), y = 0.dp),
                    )
                }
            }
        }
        HeatLegend(max, metric, base, empty)
    }
}

/** 热力图一格：有色；[selected] 时套一圈**虚线**（用户 09-30："选中某日出现的是一根虚线"） */
@Composable
private fun HeatCellBox(
    v: Int,
    max: Int,
    base: Color,
    empty: Color,
    ring: Color,
    selected: Boolean,
) {
    Box(
        Modifier.size(HEAT_CELL)
            .background(heatColor(v, max, base, empty), RoundedCornerShape(3.dp))
            .then(if (selected) Modifier.dashedBorder(ring) else Modifier)
    )
}

/** 占位格（区间首尾补空、把整块凑成完整矩形的那几格）：不上色，只占位 */
@Composable
private fun HeatBlank() {
    Spacer(Modifier.size(HEAT_CELL))
}

/** 热力图整块网格的宽（列数 × 每列占位）；月份标签行与网格共用它才对得齐 */
private fun gridWidth(weeks: Int): Dp = (HEAT_CELL + HEAT_GAP) * weeks

/**
 * 日历排法的指针定位：x → 第几列（星期）、y → 第几行（周）；横坐标要先让出左边那列日期标签。
 * GitHub 排法**不用**它——那边每一列自己带指针（横行滚动时按整块坐标反算会算错，见 `HeatmapCard` 里的说明）。
 */
private fun calendarIndexAt(
    p: Offset, stepPx: Float, headPx: Float, labelPx: Float, weeks: Int, cellIndex: Map<Pair<Int, Int>, Int>,
): Int? {
    if (p.y < headPx) return null                       // 星期列头上不算选中
    if (p.x < labelPx) return null                      // 行首日期标签不属于网格
    val week = ((p.y - headPx) / stepPx).toInt().coerceIn(0, weeks - 1)
    val weekday = ((p.x - labelPx) / stepPx).toInt().coerceIn(0, 6)
    return cellIndex[week to weekday]
}

/**
 * 热力图的四个尺寸。**行高与格子边长分开**（[HEAT_ROW_H] > [HEAT_CELL]）：格子小一点更像 GitHub
 * 的贡献图，而 16dp 的格子放不下 11sp 的文字，行高给 18dp 才不至于把"09-15"这类标签裁掉。
 * 四个值一起决定整块尺寸与指针步长（[githubIndexAt] / [calendarIndexAt] 里的 step 就是它们算的），
 * 改任何一个都要回头看对齐与命中。
 */
private val HEAT_CELL = 16.dp
private val HEAT_GAP = 3.dp

/** 一行的行高（格子在里面居中）——**指针定位的固定步长就以它为准**，别让文字行高把它顶大 */
private val HEAT_ROW_H = 18.dp

/** 顶部的月份行 ／ 星期列头 的高度 */
private val HEAT_HEAD_H = 18.dp

/** 左侧标签列宽（日历排法放"09-15"，GitHub 排法只放"一"，取两者的较大值以共用一套对齐） */
private val HEAT_LABEL_W = 40.dp

/** 日历排法的列头（周一到周日，对应 `UsageLog.WEEKDAY_NAMES` 的顺序） */
private val HEAT_WEEKDAY_HEADS = listOf("一", "二", "三", "四", "五", "六", "日")


/** 热力图色阶：0／无数据＝空槽色，有量则按最大值归一取 4 档深浅 */
private fun heatColor(v: Int, max: Int, base: Color, empty: Color): Color {
    if (v <= 0 || max <= 0) return empty
    val f = (v.toFloat() / max).coerceIn(0f, 1f)
    val alpha = when {
        f <= 0.25f -> 0.32f
        f <= 0.5f -> 0.52f
        f <= 0.75f -> 0.74f
        else -> 1f
    }
    return base.copy(alpha = alpha)
}

/**
 * 选中那一格的标记：**虚线描边**（用户 09-30："选中某时/某日出现的是一根虚线"）。
 * 不用实心高亮——热力图整块都是色阶，再糊一层就看不出原来多深了。
 */
private fun Modifier.dashedBorder(color: Color, radius: Dp = 3.dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))),
    )
}

/** 热力图的色阶图例：**这就是热力图的纵轴**——颜色本身说不出数值，得有这把尺子 */
@Composable
private fun HeatLegend(max: Int, metric: Metric, base: Color, empty: Color) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // 左＝这把尺子的量程，右＝尺子本身（GitHub 也把 Less/More 摆在右下角）
        Text(
            "最深＝" + if (metric == Metric.TOKENS) formatTokens(max) else "$max 次",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
        Spacer(Modifier.weight(1f))
        Text("少", style = MaterialTheme.typography.labelSmall, color = labelColor)
        Spacer(Modifier.width(4.dp))
        listOf(0, max / 4, max / 2, max * 3 / 4, max).forEach { v ->
            Box(
                Modifier.size(11.dp)
                    .background(heatColor(v, max, base, empty), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(2.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text("多", style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}

// ── 两张短区间图共用的零件 ────────────────────────────────────────────────

/**
 * 图表外壳：左列**真刻度**（0/一半/上界）、右边画布（含横向网格线）、下面一行横轴刻度。
 * 画什么交给调用方（[draw]）——波状图与堆叠柱的画法完全不同，但轴、网格、光标、弹框是同一套。
 *
 * **选中＝一根虚线 ＋ 跟着走的弹框**（台账 116 第三刀，用户 09-30 口径）：
 * 桌面鼠标**悬停到哪一列**就选哪一列（不必落在柱子/曲线上，落在这一列的横轴区间内即可）；
 * 手机上只有**按住**期间才显示，随手指拖动换列、抬手即收。[draw] 返回的圆心会在虚线上层画一个圆圈
 * （波状图给位置，堆叠柱给 null——柱子本身有高度，套圆圈读不出量）。
 *
 * ⚠ 刻度列与画布**必须同高**（共用 [PLOT_H]），差 1dp 刻度就压在网格线外面。
 */
@Composable
private fun ChartBody(
    buckets: List<UsageLog.Bucket>,
    values: List<Int>,
    metric: Metric,
    perBucket: List<List<UsageLog.Event>>,
    caption: String,
    /** 分色图的色号（模型 → 调色板位）；请求次数图不分色，传 null ⇒ 弹框里不画色块 */
    slots: Map<String, Int>? = null,
    /** 画数据；**返回选中点上那个圆圈的圆心**（不画圆圈的图返回 null） */
    draw: DrawScope.(slotPx: Float, axisMax: Int, cursor: Int?) -> Offset?,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dashColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val dotColor = MaterialTheme.colorScheme.primary
    val haloColor = MaterialTheme.colorScheme.surface
    // ⚠ 高度按**刻度上界**算而不是按最大值：按最大值画的话最高那根永远顶到框，
    // 加上刻度就会读成"超过上界"。上界取整（[niceAxisMax]）也让 0/一半/上界 三档都是整数。
    val axisMax = niceAxisMax(values.maxOrNull() ?: 0)
    // 光标落在哪一桶（鼠标悬停／手机按住）；换区间、改筛选后桶变了就自动清掉
    var cursor by remember(buckets, perBucket) { mutableStateOf<Int?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 画布宽＝整行减去刻度列：槽宽要按它算，用整个 Box 的宽会把光标偏到右边去
        val canvasWpx = with(LocalDensity.current) { (maxWidth - AXIS_W).toPx() }.coerceAtLeast(1f)
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = TICK_H / 2)) {
                AxisColumn(axisMax, metric, labelColor)
                Canvas(
                    Modifier.weight(1f).height(PLOT_H)
                        // 画布本身没有文字节点，加一条描述：读屏能读，自动化冒烟也能按它点到画布
                        .semantics { contentDescription = "用量图表" }
                        // 触摸＝按住才选中、鼠标＝悬停即选中；整列都算数（见 [pickByPointer] / [bucketAt]）
                        .pickByPointer(
                            locate = { p -> bucketAt(p.x, buckets.size, canvasWpx) },
                            onPick = { cursor = it },
                        )
                ) {
                    // 横向网格线：上半与顶端各一条（0 那条就是底槽，不重复画）
                    listOf(axisMax / 2.0, axisMax.toDouble()).forEach { tick ->
                        val y = size.height * (1f - (tick / axisMax).toFloat())
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val slotPx = size.width / buckets.size
                    val marker = draw(slotPx, axisMax, cursor)
                    // 虚线画在数据上层（半透明，不至于压掉曲线），圆圈再压在虚线上
                    cursor?.let { i ->
                        val cx = slotPx * i + slotPx / 2f
                        drawLine(
                            color = dashColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, size.height),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        )
                        marker?.let { m ->
                            // 实心白点打底再套一个环：曲线从圆圈中间穿过时也认得出这是标记
                            drawCircle(haloColor, 4.dp.toPx(), m)
                            drawCircle(dotColor, 4.dp.toPx(), m, style = Stroke(width = 2.dp.toPx()))
                        }
                    }
                }
            }
            // 横轴与画布**同宽**：左边让出刻度列，刻度才落在正确的横坐标上
            BucketTimeAxis(buckets, Modifier.padding(start = AXIS_W))
            Spacer(Modifier.height(4.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = labelColor)
        }
        // ── 弹框：放在虚线**左/右侧**（见 [tipX]），不压住选中那一列 ──
        cursor?.let { i ->
            val slot = (maxWidth - AXIS_W) / buckets.size
            val colStart = AXIS_W + slot * i
            BucketTipCard(
                // 时段用连字符连起来（用户 09-30）：半角 `~` 在中文字体里浮在半空，读着像个上标
                title = "${UsageLog.timeLabel(buckets[i].fromMs)}-${UsageLog.timeLabel(buckets[i].toMs)}",
                metric = metric,
                events = perBucket.getOrElse(i) { emptyList() },
                slots = slots,
                modifier = Modifier.offset(x = tipX(colStart, colStart + slot, maxWidth), y = 0.dp),
            )
        }
    }
}

/** 弹框宽度（图表与热力图共用；改它要连 [tipX] 的算位一起想） */
private val TIP_W = 196.dp

/**
 * 弹框的横向落点：**摆在虚线左侧或右侧，绝不压住"当前这一列"**（用户 09-30 口径）。
 *
 * 两条理由：① 压在这一列上会把被选中的数据挡住（"显示不全"）；
 * ② 桌面悬停时卡片若盖住指针所在的那一列，指针一进去就被判成离开画布 ⇒ 卡片闪一下又出来
 * （用户实测"卡片闪烁"，token 图尤其明显——那张的卡片有按模型拆的几行，盖住的面积最大）。
 * 所以先把卡片推到这一列的**外侧**：右边放得下放右边、放不下放左边；两边都不够（窄屏手机上
 * 24 列那张图）才退回夹取——手机上只有按住期间才出卡片，退回夹取不会闪。
 */
private fun tipX(colStart: Dp, colEnd: Dp, maxW: Dp, width: Dp = TIP_W, gap: Dp = 6.dp): Dp {
    val right = colEnd + gap
    if (right + width <= maxW) return right
    val left = colStart - gap - width
    if (left >= 0.dp) return left
    return right.coerceIn(0.dp, (maxW - width).coerceAtLeast(0.dp))
}

/**
 * 指针横坐标 → 桶下标。**整列都算数**（用户 09-30："不一定要落在柱子/曲线上，在对应的轴上就显示"），
 * 所以按槽宽直接除、不做任何命中判定；夹进 [0, count-1] 免得最右侧手指滑出去时越界。
 */
private fun bucketAt(x: Float, count: Int, widthPx: Float): Int {
    if (count <= 0) return 0
    return (x / (widthPx / count)).toInt().coerceIn(0, count - 1)
}

/** 按住多久算"选中"：太短会被页面滚动的起手误触，太长又显得没反应 */
private const val MARKER_HOLD_MS = 250L

/**
 * 「选中某时/某日」的两条指针通道（用户 09-30 口径；两张图、两种热力图排法共用同一份）：
 * - **触摸＝按住才选中**（[MARKER_HOLD_MS]），随手指换成相邻的列/格，抬手即收；
 * - **鼠标＝悬停即选中**，不必按下去，只要落在对应的那一列/那一格上就出。
 *
 * [locate] 把指针位置翻成桶下标（图是"横坐标→列"，热力图还要看行，两种排法算法不同）；
 * [onPick] 收到 null 表示收起。
 *
 * ⚠ [locate] 用 [rememberUpdatedState] 兜住、**不能拿去当 `pointerInput` 的 key**：
 * 每次重组都会新建这个 lambda，当 key 就等于每次重组都重启手势协程，长按刚选中就被打断。
 */
@Composable
private fun Modifier.pickByPointer(
    locate: (Offset) -> Int?,
    onPick: (Int?) -> Unit,
): Modifier {
    val locateNow = rememberUpdatedState(locate)
    val pickNow = rememberUpdatedState(onPick)
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // 250ms 内抬手＝普通点击（这一页点击没有别的含义，不做事）；
                // waitForUpOrCancellation 返回 null＝手势被页面滚动吃掉 ⇒ 也不能算按住，
                // 否则上下滚页面时会在图上闪出一根虚线
                var cancelled = false
                val lifted = withTimeoutOrNull(MARKER_HOLD_MS) {
                    val up = waitForUpOrCancellation()
                    if (up == null) cancelled = true
                    up
                }
                if (lifted != null || cancelled) return@awaitEachGesture
                pickNow.value(locateNow.value(down.position))
                try {
                    while (true) {
                        val e = awaitPointerEvent()
                        val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        pickNow.value(locateNow.value(ch.position))
                    }
                } finally {
                    pickNow.value(null)
                }
            }
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull() ?: continue
                    // 按下时交给上面那条按住分支，别在拖动中抢指针
                    if (ch.pressed) continue
                    when (e.type) {
                        PointerEventType.Move -> pickNow.value(locateNow.value(ch.position))
                        PointerEventType.Exit -> pickNow.value(null)
                        else -> {}
                    }
                }
            }
        }
}

/**
 * 柱子被选中后浮出的白卡：上行＝时段（或日期）＋ 合计，下行＝每个模型一行（色块 ＋ 名字 ＋ 数值）。
 * [slots] 必须**与主图同一份**——色号在弹框里重算的话，同一模型会和柱子不是一个颜色；
 * 为 null（请求次数图与热力图）就不画色块，免得让读者以为那张图也分了色。
 */
@Composable
private fun BucketTipCard(
    /** 抬头：按小时＝`12:00 ~ 13:00`，按天（热力图）＝`9月15日 周一` */
    title: String,
    metric: Metric,
    events: List<UsageLog.Event>,
    slots: Map<String, Int>?,
    modifier: Modifier = Modifier,
) {
    val total = if (metric == Metric.TOKENS) UsageLog.summarize(events).totalTokens else events.size
    // 两种图都给"按模型拆分"（次数图给各模型的次数）：时段总看不出是谁花的，拆开才有用
    val rows = if (metric == Metric.TOKENS) {
        UsageLog.tokensByModel(events).entries.sortedBy { it.key }.map { it.key to it.value }
    } else {
        events.groupBy { it.model.ifBlank { UsageLog.UNKNOWN_MODEL } }
            .entries.sortedBy { it.key }.map { it.key to it.value.size }
    }
    Surface(
        modifier = modifier.width(TIP_W),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (metric == Metric.TOKENS) formatTokens(total) else "$total 次",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            rows.forEach { (model, v) ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (slots != null) {
                        Box(
                            Modifier.size(8.dp)
                                .background(UsageModelPalette[slots[model] ?: 0], RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        model,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (metric == Metric.TOKENS) formatTokens(v) else "$v 次",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 纵轴刻度列：三档（上界 / 一半 / 0），与画布同高 */
@Composable
private fun AxisColumn(axisMax: Int, metric: Metric, color: Color) {
    Column(Modifier.width(AXIS_W).height(PLOT_H), verticalArrangement = Arrangement.SpaceBetween) {
        AxisTick(formatAxisValue(axisMax.toDouble(), metric), color, -TICK_H / 2)
        AxisTick(formatAxisValue(axisMax / 2.0, metric), color, 0.dp)
        AxisTick("0", color, TICK_H / 2)
    }
}

/**
 * 横轴刻度：落在**桶边界**上（`UsageLog.axisTicks`）——标签中心压在边界线上，
 * 左端贴住图的左沿（`00:00` 就是这一天开始），右端补"这段结束"那一格（按小时＝`24:00`）。
 * 原来每格 `weight(1f)` 居中 ⇒ 刻度读起来是"那一小时的正中"、左端缩进半格，且步长硬取 6
 * 导致末格只跨 5 格（`00:00 06:00 12:00 18:00 23:00`）。
 */
@Composable
private fun BucketTimeAxis(buckets: List<UsageLog.Bucket>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    BoxWithConstraints(modifier.fillMaxWidth().padding(top = 2.dp)) {
        val slot = maxWidth / buckets.size
        UsageLog.axisTicks(buckets).forEach { tick ->
            // 中心落在边界线上；两端各夹住半个标签宽，免得文字被裁到图外
            val x = (slot * tick.pos - AXIS_W / 2)
                .coerceIn(0.dp, (maxWidth - AXIS_W).coerceAtLeast(0.dp))
            Box(
                Modifier.align(Alignment.TopStart).offset(x = x).width(AXIS_W),
                contentAlignment = Alignment.Center
            ) {
                Text(tick.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            }
        }
    }
}

/** 分色图的图例：色块 ＋ 模型名 ＋ 该区间该模型的总量（色号＝主图那一份，见 [UsageLog.modelColorSlots]） */
@Composable
private fun LegendRow(models: List<String>, totals: Map<String, Int>, slots: Map<String, Int>) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        models.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(9.dp)
                        .background(UsageModelPalette[slots[m] ?: 0], RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    m,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatTokens(totals[m] ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
            }
        }
    }
}

/**
 * 自定义区间的两端（台账 116-C）：两个日期按钮，各自弹日历。**含首含尾**、上限一年
 * ——夹取在调用方做（改一端时把另一端拉进区间），这里只管选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeRow(from: Long, to: Long, onFrom: (Long) -> Unit, onTo: (Long) -> Unit) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    var picking by remember { mutableStateOf<Int?>(null) }   // 0＝选起始，1＝选结束
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DateButton("从", from) { picking = 0 }
        Text("～", Modifier.padding(horizontal = 8.dp), color = labelColor)
        DateButton("到", to) { picking = 1 }
    }
    picking?.let { which ->
        // key(which)：换一端时重建 state，否则 DatePicker 会拿上一次选中的日期打开
        key(which) {
            val state = rememberDatePickerState(initialSelectedDateMillis = if (which == 0) from else to)
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { utc ->
                            // 日历给的是 UTC 午夜，换算成本机时区的当天零点（否则西半球整体偏一天）
                            val d = UsageLog.dayStartFromUtcMidnight(utc)
                            if (which == 0) onFrom(d) else onTo(d)
                        }
                        picking = null
                    }) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("取消") } },
            ) {
                DatePicker(state = state)
            }
        }
    }
}

@Composable
private fun DateButton(label: String, ms: Long, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Text("$label ${UsageLog.monthDayLabel(ms)}", style = MaterialTheme.typography.labelMedium)
    }
}

/** 画布高度。**纵轴刻度列与画布必须同值**，差 1dp 刻度就压在网格线外面 */
private val PLOT_H = 120.dp

/** 纵轴刻度列宽（"12.3 万" 四个字 + 一点余量） */
private val AXIS_W = 46.dp

/** 一个刻度占的高度：只用来算上/下两档的 dy 与留白，**不能用它去限制文字高度**（见 [AxisTick]） */
private val TICK_H = 16.dp

/**
 * 纵轴上的一格刻度。[dy] 把文字中心挪到网格线上：最上一档 −半格高、底档 +半格高。
 *
 * ⚠ **不要给文字套固定高度的容器**：`Text` 会按传进来的 `maxHeight` 摆版并裁掉上下两条边——
 * 原先这里是 `height(12.dp)` 的 Box，桌面上"上界"与"0"两档的半个字就这样被切掉了
 * （用户 09-30 桌面实测"纵轴的数字没显示全"）。文字自由排版、只用 offset 挪位置才不会被裁；
 * 代价是最上/最下一档会伸出刻度列边界，所以 [ChartBody] 那行的上下留白必须 ≥ [TICK_H] 的一半。
 */
@Composable
private fun AxisTick(text: String, color: Color, dy: Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().offset(y = dy),
    )
}

/**
 * 纵轴上界取"好看"的整数：1/2/5×10ⁿ（140 → 200，7 → 10，3 → 5）。
 * 刻度值是上界与它的一半，所以上界最好是 2 或 10 的倍数——5 的那档会出现 2.5，
 * 靠 [formatAxisValue] 补一位小数读。
 *
 * 公开是为了让桌面无窗口自检直接断言它（自检引用 `ui.screens` 里的纯函数是既有做法）——
 * 取整一旦写错，界面上表现为"最高那根柱子顶到框外还够不着刻度"，当场很难看出来。
 */
fun niceAxisMax(v: Int): Int {
    if (v <= 0) return 1
    var mag = 1
    while (mag * 10L <= v) mag *= 10
    val n = v.toDouble() / mag
    val step = when {
        n <= 1.0 -> 1
        n <= 2.0 -> 2
        n <= 5.0 -> 5
        else -> 10
    }
    return step * mag
}

/** 纵轴刻度值：token 走万/亿缩写（与小计同口径），半格（2.5）保留一位小数 */
private fun formatAxisValue(v: Double, metric: Metric): String = when {
    metric == Metric.TOKENS && v >= 100_000_000 -> "%.2f 亿".format(v / 100_000_000.0)
    metric == Metric.TOKENS && v >= 10_000 -> "%.1f 万".format(v / 10_000.0)
    v == floor(v) -> v.toLong().toString()
    else -> "%.1f".format(v)
}

/** 小计卡：请求次数 / token / 生图张数 三个数 + 两条口径说明 */
@Composable
private fun SummaryCard(s: UsageLog.Summary, filtered: List<UsageLog.Event>) {
    val images = filtered.sumOf { it.images ?: 0 }
    // 语音链路的**字符数**（116-M）：端点不回 token，但字数拿得到 ⇒ 卡片上单开一格，
    // 与"生图"同款口径——这段区间里没有语音用量就显示 `—`（用户 09-30：「最上面的卡片要补上字符数」）
    val chars = filtered.sumOf { it.chars ?: 0 }
    SectionCard(contentPadding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("请求", "${s.requests}", Modifier.weight(1f))
            StatTile("token", formatTokens(s.totalTokens), Modifier.weight(1f))
            StatTile("生图", if (images > 0) "$images 张" else "—", Modifier.weight(1f))
            StatTile("字符", if (chars > 0) "$chars" else "—", Modifier.weight(1f))
        }
        if (s.unreported > 0) {
            // ⚠ 分两类写，别笼统说"端点没回报"（台账 116-A 之后）：语音朗读/语音输入按协议就不计 token，
            // 那不是采集漏了；只有对话类行"应该有却没有"才需要用户去查配置。
            val byDesign = filtered.count {
                it.totalTokens == null && (it.images ?: 0) == 0 && !UsageLog.kindHasTokens(it.kind)
            }
            val byEndpoint = s.unreported - byDesign
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    if (byEndpoint > 0) "$byEndpoint 次端点没有回报 token" else null,
                    // 字数已经单列一格，这里不再重复写个数
                    if (byDesign > 0) "$byDesign 次语音请求按协议不计 token" else null
                ).joinToString("；") + "（都已计请求次数，token 计 0）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s.truncated > 0) {
            Text(
                "${s.truncated} 次因输出预算被截断（可以调高会话预算）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (s.promptTokens > 0 && s.completionTokens > 0) {
            Text(
                "输入 ${formatTokens(s.promptTokens)} · 输出 ${formatTokens(s.completionTokens)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 链路小计：各条链各多少次/多少 token，钱花在哪一条上一眼看到。
 *
 * ⚠ **两列都固定宽度且右对齐**（台账 116-J，参考图 3 是实锤）：原来次数列的宽度跟着内容走、
 * token 列左对齐，于是"2 次 / 1 次"两行的数字左边界参差，扫一眼对不齐、也读不出谁多谁少。
 */
@Composable
private fun KindBreakdownCard(filtered: List<UsageLog.Event>) {
    val groups = remember(filtered) { filtered.groupBy { it.kind } }
    SectionCard(title = "按链路", contentPadding = 14.dp) {
        groups.entries.sortedByDescending { it.value.size }.forEach { (kind, es) ->
            val s = UsageLog.summarize(es)
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    UsageLog.kindLabel(kind),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.width(76.dp)
                )
                Text(
                    "${s.requests} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp)
                )
                Text(
                    if (s.totalTokens > 0) formatTokens(s.totalTokens)
                    else if (s.images > 0) "无 token" else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** 一行"标签 ＋ 选项胶囊"，选项多时横向滚（供应商/会话都可能有好几项） */
@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        ArrowScrollRow(Modifier.fillMaxWidth()) {
            options.forEach { (value, text) ->
                WhaleChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text, maxLines = 1) }
                )
            }
        }
    }
}

/** 一行明细：链路 ＋ 模型/provider/时间/token */
@Composable
private fun UsageRow(e: UsageLog.Event) {
    val provider = UsageLog.providerLabel(e.provider)
    val host = UsageLog.providerHost(e.provider)
    val tokens = when {
        e.totalTokens != null -> formatTokens(e.totalTokens!!)
        (e.images ?: 0) > 0 -> "无 token · ${e.images} 张图"
        // 语音/生图这类端点按协议就不回 usage（116-A），写清楚是哪一类，别让用户以为漏算了；
        // 语音量得出**字数**（116-M 用户 09-30：「不计 token 就记字符数」），有就一并写出来
        !UsageLog.kindHasTokens(e.kind) -> listOfNotNull(
            "该链路不计量 token",
            e.chars?.takeIf { it > 0 }?.let { "$it 字" }
        ).joinToString(" · ")
        else -> "端点未回报 token"
    }
    SectionCard(contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                UsageLog.kindLabel(e.kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(68.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    e.model.ifBlank { host.ifBlank { "（未记模型）" } },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        UsageLog.timeLabel(e.at),
                        // 认得的家用厂商名，自填地址退回 host（台账 116-K）
                        provider.takeIf { it.isNotEmpty() },
                        UsageLog.sessionLabel(e).takeIf { e.sessionId.isNotBlank() || e.characterName.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                tokens,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 区间内一条记录都没有：不画 0，画"这段时间还没有用量"与下一步去哪儿看 */
@Composable
private fun UsageEmptyCard(range: UsageLog.Range) {
    SectionCard(contentPadding = 16.dp) {
        Text(
            when (range) {
                UsageLog.Range.TODAY -> "今天还没有用量记录"
                UsageLog.Range.YESTERDAY -> "昨天没有用量记录"
                UsageLog.Range.WEEK -> "最近七天还没有用量记录"
                UsageLog.Range.MONTH -> "最近三十天还没有用量记录"
                UsageLog.Range.CUSTOM -> "这个区间里没有用量记录"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "这里统计的是**真实发出过的 API 请求**：聊天、灵感与前情提要这类辅助、" +
                "角色卡与世界书这类创作、以及生图、语音朗读与语音输入，各记一条。" +
                "端点不回报 token 时只计请求次数（生图另记张数、语音另记字符数）。" +
                "没看到记录通常是没配 API，或者这期间确实没调用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一个数（标题 ＋ 数值），小计卡里并排三个 */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** token 数按万/亿缩写（"12.3 万"比"123456"好读一个量级） */
private fun formatTokens(n: Int): String = when {
    n >= 100_000_000 -> "%.2f 亿".format(n / 100_000_000.0)
    n >= 10_000 -> "%.1f 万".format(n / 10_000.0)
    else -> n.toString()
}
