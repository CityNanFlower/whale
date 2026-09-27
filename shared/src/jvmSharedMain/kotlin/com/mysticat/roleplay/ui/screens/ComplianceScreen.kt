package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.NoticeLine
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.ui.SectionCard

/**
 * 合规相关的**唯一一份**文案：免责三条 / 导入的合法来源提示 / 年龄与自愿声明。
 *
 * 为什么要收成一个对象：这几句在**两个地方**出现——「关于鲸鱼」那一页（随时可回看）
 * 与导入角色卡时的确认弹窗。分头各写一份，改一处漏一处就会出现两套口径，
 * 而这类文案是对用户的承诺，两个页面说法不一致比没有这段更糟。
 */
internal object Compliance {

    /** 年龄与自愿声明：勾选框与弹窗都显示这一句 */
    const val AGE_DECLARATION = "我已满 18 周岁，自愿使用本应用。"

    /**
     * 免责三句。措辞守住三条底线：① 应用只是本地工具，模型算力与内容由用户自己接的服务商提供；
     * ② 角色卡与剧情是虚构设定，不能当专业意见用；③ 违法违规内容不许导入或生成。
     */
    val DISCLAIMERS = listOf(
        "本应用是纯本地工具，不提供模型服务：对话内容由你自己填写的服务商生成，相关费用与合规义务在你与该服务商之间。",
        "角色卡、人设与剧情均为虚构设定，不构成医疗、法律、金融等任何专业意见，请勿据此做现实决策。",
        "请勿导入或生成违法违规内容；你自己导入的内容由你负责，并请遵守原作者的授权范围。"
    )

    /** 导入时的合法来源提示（导入角色卡那一步弹的那句） */
    const val IMPORT_SOURCE_NOTICE =
        "请确认你对要导入的角色卡拥有合法使用权：从社区拿到的卡，请遵守原作者的使用声明" +
            "（是否允许二改、商用、再分发）。"

    /** 弹窗里那句"为什么现在弹"的说明 */
    const val IMPORT_CONFIRM_HINT = "导入前先确认一次：勾选后不再重复弹这个确认。"
}

/**
 * 合规内容本体：**年龄与自愿声明**（可随时改，落盘按账号存）、**免责三句**、
 * **导入内容的来源**、**安全协议**（权限 / 加密 / 卸载这些"我们对用户承诺了什么"）。
 *
 * 它现在是「关于鲸鱼」整页里的几节（用户要求把两块并成一页），所以这里只出内容、
 * 不套外壳：页面外壳（顶栏返回 / 桌面上窄头部）与滚动容器都由调用方给。
 */
@Composable
internal fun ComplianceContent() {
    var accepted by remember { mutableStateOf(Repository.complianceAccepted()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(title = "年龄与自愿声明") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = {
                        accepted = it
                        Repository.setComplianceAccepted(it)
                    }
                )
                Text(
                    Compliance.AGE_DECLARATION,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            NoticeLine("这份声明按账号保存，随时可以在这里改。它不限制任何功能，只是导入角色卡时少弹一次确认。")
        }
        SectionCard(title = "免责声明") {
            Compliance.DISCLAIMERS.forEach { NoticeLine(it) }
        }
        SectionCard(title = "导入内容的来源") {
            NoticeLine(Compliance.IMPORT_SOURCE_NOTICE)
            NoticeLine("从网络直接拉取的角色卡由你的设备直连下载，不经过我们的服务器。")
        }
        SectionCard(title = "安全协议") {
            SecurityNotice()
        }
    }
}

/**
 * 导入角色卡前的合规确认（轻量：**只在还没勾选过时弹一次**）。
 *
 * 文案与「关于鲸鱼」那一页同源（[Compliance]），所以这里不重复写第二份。
 * 弹窗自己不管落盘：勾选由 [onAccept] 的调用方负责（它同时要接着把导入做完）。
 */
@Composable
internal fun ComplianceImportDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用须知") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Compliance.IMPORT_CONFIRM_HINT, style = MaterialTheme.typography.bodySmall)
                Compliance.DISCLAIMERS.forEach { NoticeLine(it) }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                    Text(
                        Compliance.AGE_DECLARATION,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    Compliance.IMPORT_SOURCE_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = checked) { Text("同意并导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 安全须知正文（与登录页那份口径一致，这里展开成完整说明）。
 *
 * ⚠️ 这里是**对用户的承诺**，必须与代码实际行为一致：
 *  - 旧文案写「导出的备份不含本地图片」，而 `Backup` 从 formatVersion 2 起就把头像/背景/聊天图
 *    **以 base64 内嵌进备份文件**（换机才能不断链）——一个专门讲安全的页面说反了，必须改；
 *  - 旧文案写「仅申请 INTERNET 权限」，而清单里还有 `REQUEST_INSTALL_PACKAGES`（应用内装更新包要用），
 *    少写一条会让用户误判风险面。
 */
@Composable
internal fun SecurityNotice() {
    // 分平台：权限、加密与卸载三段两端不同——桌面无 Android 权限概念、加密走 DPAPI
    // （WindowsDpapiKeyProvider）、MSI 卸载会连根清空安装目录。
    val isDesktop = Platform.ui.platformId() == "desktop"
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        NoticeLine("本应用没有云端服务器：账号、角色卡、聊天记录都以 JSON 存在本机应用目录。")
        if (isDesktop) {
            NoticeLine("应用不申请任何系统权限；联网仅用于调用你自己配置的模型接口与检查更新。")
        } else {
            NoticeLine("仅申请两项权限：INTERNET（调用你自己配置的模型接口）、REQUEST_INSTALL_PACKAGES（应用内下载后唤起安装器）。")
        }
        NoticeLine("登录密码使用带盐 PBKDF2（12 万次）哈希存储，不保存明文。")
        if (isDesktop) {
            NoticeLine("API Key 使用 Windows DPAPI 加密后落盘（密钥由你的 Windows 账号托管），不进备份文件；加密不可用时回退明文并在设置页明确提示。")
        } else {
            NoticeLine("API Key 使用 Android Keystore 的 AES/GCM 加密后落盘，不进备份文件。")
        }
        NoticeLine("接口地址强制 https，降低明文传输风险。")
        NoticeLine("对话内容、角色设定与 API Key 会发送到你填写的模型服务商，请注意其隐私政策。")
        if (isDesktop) {
            NoticeLine("卸载程序会清空安装目录（连同放在里面的任何文件）；数据默认存在用户目录下不受影响——请勿把数据目录设到安装目录内。")
        } else {
            NoticeLine("卸载应用会销毁 Keystore 密钥，重装后需重新填写 API Key。")
        }
        NoticeLine("「数据备份」导出的文件包含角色卡、会话记录与本地图片（base64 内嵌），请勿随意外发；API Key 不在其中。")
        NoticeLine("更新包校验只防传输损坏：sha256 与安装包走同一条下载通道，不构成对镜像的信任边界。")
    }
}
