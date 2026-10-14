package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mysticat.roleplay.data.SkinFontRepo
import com.mysticat.roleplay.data.ThemeState
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.components.WhaleConfirmSheet
import com.mysticat.roleplay.ui.isUsableFontFile
import com.mysticat.roleplay.ui.rememberJsonFilePicker
import com.mysticat.roleplay.ui.showToast
import com.mysticat.roleplay.ui.skin.SkinSubmitDialog
import com.mysticat.roleplay.ui.theme.LocalWhaleDark
import com.mysticat.roleplay.ui.theme.LocalWhalePalette
import com.mysticat.roleplay.ui.tokens.CustomSkin
import com.mysticat.roleplay.ui.tokens.WhalePalette
import com.mysticat.roleplay.ui.tokens.WhaleShapes
import com.mysticat.roleplay.ui.tokens.WhaleSkin
import com.mysticat.roleplay.ui.tokens.WhaleSkinSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 发现页「皮肤」轴＝**本地皮肤中心**（2026-10-07 第 17 轮；D2 拍板：C 大卡画廊 × 发现页承载）。
 *
 * 三段规格与八态定义见皮肤中心的 DESIGN 合同（设计轮 D3 定稿）：
 * 内置 7 ／ 皮肤中心（投稿内容位，未上线＝空态＋投稿入口）／ 自定义（导入 `.whale-skin.json`
 * 或粘贴 JSON，可复制分享、可移除）。
 *
 * 卡＝大卡预览（[SkinPreviewCardLarge]）：迷你聊天样张——叙述行＋气泡＋accent CTA＋贴纸角，
 * 色板/字体/圆角/贴纸全取目标皮肤真值，**点卡即套用**。带字体的皮肤套用时不阻塞：
 * 字体由 [ThemeState] 经 SkinFontRepo 后台拉取，到位后全局换字；卡上状态行只报字体一件事。
 *
 * 本组件渲染在发现页两端的懒网格里（`item(span = maxLineSpan)`），**不自带滚动**——
 * 外层再包 verticalScroll 会给网格无限高度约束直接炸。
 */
@Composable
fun SkinCenterSection(desktopPane: Boolean) {
    var showSubmit by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CustomSkin?>(null) }
    val scope = rememberCoroutineScope()
    val pickSkinJson = rememberJsonFilePicker { path ->
        if (path == null) return@rememberJsonFilePicker
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { File(path).readText() }.getOrDefault("")
            }
            importSkinJson(text)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SegmentLabel("内置 · ${WhaleSkin.builtins().size}")
        CardFlow {
            WhaleSkin.builtins().forEach { spec ->
                SkinPreviewCardLarge(
                    spec = spec,
                    selected = ThemeState.skinId == spec.id && ThemeState.skinJson.isBlank(),
                    desktopPane = desktopPane,
                    onClick = { ThemeState.setSkin(spec.id, persist = true) },
                )
            }
        }

        SegmentLabel("皮肤中心")
        EmptyCenterCard(onSubmit = { showSubmit = true })

        SegmentLabel("自定义 · ${ThemeState.customSkins.size}")
        CardFlow {
            ThemeState.customSkins.forEach { skin ->
                SkinPreviewCardLarge(
                    spec = skin.spec,
                    selected = ThemeState.skinId == skin.id && ThemeState.skinJson.isNotBlank(),
                    desktopPane = desktopPane,
                    onClick = { ThemeState.setSkin(skin.id, skin.json, persist = true) },
                    footerActions = {
                        TextButton(onClick = {
                            Platform.ui.copyToClipboard("皮肤 JSON", skin.json)
                        }) { Text("复制 JSON") }
                        TextButton(onClick = { removing = skin }) { Text("移除") }
                    },
                )
            }
            ImportTile(desktopPane, onPickFile = pickSkinJson)
        }
    }

    if (showSubmit) {
        SkinSubmitDialog(spec = ThemeState.skinSpec, onDismiss = { showSubmit = false })
    }
    removing?.let { skin ->
        WhaleConfirmSheet(
            title = "移除皮肤「${skin.name}」？",
            body = "移除后无法恢复；分享出去的 JSON 不受影响，需要时可以再导入。",
            confirmLabel = "移除",
            onConfirm = {
                ThemeState.removeCustomSkin(skin.id, persist = true)
                removing = null
            },
            onDismiss = { removing = null }
        )
    }
}

/** 导入一份皮肤 JSON（文件选择与剪贴板粘贴共用）；坏文件给明确文案，不静默回落。 */
private fun importSkinJson(text: String) {
    if (text.isBlank()) {
        showToast("剪贴板里没有皮肤 JSON")
        return
    }
    runCatching { ThemeState.addCustomSkin(text, applyNow = true, persist = true) }
        .onSuccess { showToast("已导入并套用「${it.name}」") }
        .onFailure { showToast("不是有效的皮肤文件（.whale-skin.json 解析失败）", long = true) }
}

@Composable
private fun SegmentLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 卡流：桌面 250dp 定宽卡自动换行（C 的两列网格口径），手机整宽一列。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** 皮肤中心空态（D2 拍板保留的代价：无投稿内容前它就是空的——把话说清楚，别装满）。 */
@Composable
private fun EmptyCenterCard(onSubmit: () -> Unit) {
    val p = LocalWhalePalette.current
    val shape = WhaleShapes.r2
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, p.hairline2, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "皮肤精选还在筹备中——投稿审核通过后会出现在这里，下载即套上",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSubmit) { Text("投稿") }
    }
}

/** 导入入口（自定义段末尾的虚位）：整块可点＝选文件；次动作＝粘贴剪贴板里的 JSON。 */
@Composable
private fun ImportTile(desktopPane: Boolean, onPickFile: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val p = LocalWhalePalette.current
    val shape = WhaleShapes.r2
    Column(
        Modifier
            .then(if (desktopPane) Modifier.width(200.dp) else Modifier.fillMaxWidth())
            .height(96.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, p.hairline2, shape)
            .clickable { onPickFile() }
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "导入 .whale-skin.json",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "点按选择文件",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { importSkinJson(clipboard.getText()?.text ?: "") }) {
            Text("从剪贴板粘贴导入")
        }
    }
}

// ── 大卡预览（C 的重复原语：预览卡＝迷你皮肤本体） ─────────────────────────────

/**
 * 预览卡样张的字体：目标皮肤的下载档**已就绪**才用真字体；皮肤点名平台衬线（`serif`）接衬线族；
 * 其余（未就绪 / 无绑定）回退平台默认——由状态行说真话，不假装字体已生效。
 */
internal fun skinPreviewFamily(spec: WhaleSkinSpec): FontFamily {
    SkinFontRepo.byId(spec.fonts.ui)?.let { f ->
        SkinFontRepo.readyPath(f.id)?.let { path ->
            if (isUsableFontFile(path)) Platform.ui.loadCustomFontFamily(path)?.let { return it }
        }
        return FontFamily.Default
    }
    // 与 `Theme.currentFontFamily` 的衬线档同一口径：平台没给具体字族时退回通用衬线族。
    // 原来写 `?.let { return it }`，Android 上（`serifFontFamily()` 默认 null）会**静默落到**
    // `FontFamily.Default`——深海那张卡的样张就不衬线了，卡自己说的和套上去的还不是一回事。
    if (spec.fonts.ui == "serif") return Platform.ui.serifFontFamily() ?: FontFamily.Serif
    return FontFamily.Default
}

/**
 * 大卡预览卡（DESIGN 合同 §4）：色带 → 名字＋字体名行 → 两行样张（目标皮肤字体）→
 * 气泡＋迷你 CTA（贴纸压卡角）→ 字体状态行 →（自定义卡）卡尾动作。
 * 内部颜色全取目标皮肤真值（`WhaleSkin.resolve`），卡圆角＝目标皮肤 r2、卡内小件＝r1——
 * 卡自己演示皮肤差异。选中＝accent 2dp 描边＋对钩。
 *
 * [footerActions]：自定义卡的「复制 JSON ／ 移除」（卡尾按钮，C 原型口径；破坏性动作走确认 Sheet）。
 */
@Composable
private fun SkinPreviewCardLarge(
    spec: WhaleSkinSpec,
    selected: Boolean,
    desktopPane: Boolean,
    onClick: () -> Unit,
    footerActions: (@Composable () -> Unit)? = null,
) {
    val dark = LocalWhaleDark.current
    val p = remember(spec, dark) { WhaleSkin.resolve(spec, dark) }
    // 读 state() 订阅下载状态：字体到位那一刻卡自己换真字体，状态行同步消失
    val fontFile = SkinFontRepo.byId(spec.fonts.ui)
    val fontState = fontFile?.let { SkinFontRepo.state(it.id) }
    val fontReady = fontFile != null && SkinFontRepo.readyPath(fontFile.id) != null
    val family = remember(spec.fonts.ui, fontState) { skinPreviewFamily(spec) }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(spec.shape.r2.dp)

    Box(
        Modifier
            .then(if (desktopPane) Modifier.width(250.dp) else Modifier.fillMaxWidth())
            .clip(shape)
            .background(p.paper)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = when {
                    selected -> p.accent
                    hovered -> p.ink3
                    else -> p.hairline
                },
                shape = shape,
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onClick() }
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            // 色带：paper / accent / hairline 三段，一眼读出纸面与主色
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(p.hairline2),
            ) {
                Box(Modifier.weight(1f).height(14.dp).background(p.paper))
                Box(Modifier.weight(1f).height(14.dp).background(p.accent))
                Box(Modifier.weight(1f).height(14.dp).background(p.hairline))
            }

            Text(
                spec.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = p.ink,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
            Text(
                fontLabelOf(spec),
                style = MaterialTheme.typography.labelSmall,
                color = p.ink2,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 1.dp),
            )
            Text(
                "雨停在十点零七分。天台的水泥地还亮着一层水光",
                fontFamily = family,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = p.ink,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(spec.shape.r1.dp))
                        .background(p.surface)
                        .border(1.dp, p.hairline, RoundedCornerShape(spec.shape.r1.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                ) {
                    Text("记得。你那天把伞给我了", fontSize = 12.sp, lineHeight = 17.sp, color = p.ink)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(spec.shape.r1.dp))
                        .background(p.accent)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text("说点什么", fontSize = 12.sp, color = p.onAccent)
                }
            }

            // 字体状态行：只报字体一件事；就绪不显示（DESIGN 合同 §6：不假装）
            val statusText = when {
                fontFile == null || fontReady -> null
                fontState is SkinFontRepo.State.Downloading -> "字体下载中…"
                fontState is SkinFontRepo.State.Failed -> "字体下载失败 · 点按重试"
                else -> "字体未下载 · ${"%.1f".format(fontFile.sizeBytes / 1024f / 1024f)} MB"
            }
            if (statusText != null) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (fontState is SkinFontRepo.State.Failed) p.dangerText else p.ink2,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                )
            }

            footerActions?.let { actions ->
                Row(Modifier.padding(start = 4.dp, top = 2.dp)) { actions() }
            }
        }

        // 选中对钩（压右上角；贴纸卡让位到它下方）
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 10.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(p.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "当前皮肤",
                    tint = p.onAccent,
                    modifier = Modifier.size(11.dp),
                )
            }
        }

        // 贴纸角（一物一纹口径：手账＝朱印「记」、书信＝邮戳双圈；贴纸是物件，不参与动效）
        when (spec.motif) {
            "sticker" -> CornerSeal(
                char = "记", p = p, double = false,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 12.dp),
            )
            "postmark" -> CornerSeal(
                char = "鲸", p = p, double = true,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 27.dp, end = 12.dp),
            )
        }
    }
}

/** 卡角贴纸：18dp 圆形、旋转 -9°、danger 相——手账朱印 / 书信邮戳的卡片版。 */
@Composable
private fun CornerSeal(char: String, p: WhalePalette, double: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .rotate(-9f)
            .clip(CircleShape)
            .border(1.5.dp, p.danger, CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .border(if (double) 1.dp else 0.dp, p.danger, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(char, fontSize = 9.sp, lineHeight = 9.sp, color = p.danger)
    }
}

/** 字体名行：信息不是装饰（DESIGN 合同 §6）——写清这套皮肤用的是什么字、从哪来。 */
private fun fontLabelOf(spec: WhaleSkinSpec): String {
    val f = SkinFontRepo.byId(spec.fonts.ui)
    return when {
        f != null -> "${f.label} · 动态下载"
        spec.fonts.ui == "serif" -> "系统衬线（宋体风格）"
        else -> "系统黑体"
    }
}
