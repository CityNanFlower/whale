package com.mysticat.roleplay.ui

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * App 自绘图标（Material 图标库里没有的形状）。与 `Icons.Filled.*` 同样用法：填色由导航栏 tint 统一上，
 * 所以这里一律用纯黑 fill 占位。
 *
 * **体量口径（2026-09-25 按实测校准）**：Material 图标的实际内容区是 **20×20**（24 视口四周各留 2）
 * ——`icon-measure` 工具对 发现/聊天记录/用户 三个图标的实测全是 [2,22]×[2,22]。
 * 自绘图标的主体一律画满这个框，别再按"宽 18 格"对齐（口径，实测偏小一圈）。
 */

private var _whaleTreasure: ImageVector? = null

/**
 * 「宝库」tab 的鲸鱼宝箱：一只规整的圆顶宝箱，箱身正中镂空一头小鲸鱼。
 * 外轮廓＝宝箱（圆顶盖＋直边箱身，盖身之间刻一道缝）；鲸鱼＝EvenOdd 镂空洞，
 * 底色从洞里透出来，剪影才清楚。填色由导航栏 tint 统一上。
 */
val Icons.Filled.WhaleTreasure: ImageVector
    get() {
        _whaleTreasure?.let { return it }
        val vector = ImageVector.Builder(
            name = "WhaleTreasure",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
                // 外轮廓：宝箱一体（圆顶拱到 y2，箱身直边到底、底角圆角；主体占满 [2,22]，与 Material 同框）
                moveTo(2f, 10f)
                curveTo(2f, 5.31f, 6.44f, 2f, 12f, 2f)
                curveTo(17.56f, 2f, 22f, 2f, 22f, 10f)
                lineTo(22f, 18.34f)
                curveTo(22f, 20.4f, 20.44f, 22f, 18.44f, 22f)
                lineTo(5.56f, 22f)
                curveTo(3.56f, 22f, 2f, 20.4f, 2f, 18.34f)
                close()
                // 洞 1：盖身之间的合页缝（留边 0.4 防止与外轮廓共边出毛刺）
                moveTo(2.44f, 11.03f)
                lineTo(21.56f, 11.03f)
                lineTo(21.56f, 11.94f)
                lineTo(2.44f, 11.94f)
                close()
                // 洞 2：箱身正中的小鲸鱼（面朝左，尾巴在右上方展开两片鳍叶）
                moveTo(5.11f, 16.97f)
                curveTo(5.11f, 15.03f, 7.11f, 13.66f, 9.56f, 13.66f)
                curveTo(11.89f, 13.66f, 13.67f, 14.46f, 14.56f, 15.6f)
                curveTo(15.11f, 14.57f, 16.11f, 13.77f, 17.44f, 13.54f)
                curveTo(17.11f, 14.69f, 17.11f, 15.6f, 17.56f, 16.29f)
                curveTo(18.44f, 16.17f, 19.11f, 16.51f, 19.44f, 17.2f)
                curveTo(18.11f, 18.23f, 16.44f, 18.34f, 15.11f, 17.77f)
                curveTo(14.11f, 18.91f, 12.11f, 19.6f, 9.78f, 19.6f)
                curveTo(7.22f, 19.6f, 5.11f, 18.8f, 5.11f, 16.97f)
                close()
            }
        }.build()
        _whaleTreasure = vector
        return vector
    }

private var _whaleUser: ImageVector? = null

/**
 * 「用户」tab 的实心人形：头（圆）＋肩（梯形弧底），占满 [2,22]×[2,22]，无外圈。
 * 替换 Material 的 AccountCircle（环形＋镂空，着墨率 41% 偏"空"）——实心剪影与
 * 聊天记录／宝库的实心观感一致。比例照 Material Person（头部圆略大于肩宽的 1/2）放大到同框。
 */
val Icons.Filled.WhaleUser: ImageVector
    get() {
        _whaleUser?.let { return it }
        val vector = ImageVector.Builder(
            name = "WhaleUser",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // 头：圆心 (12,7) 半径 5（圆用 0.5523·r 的控制柄近似）
                moveTo(12f, 2f)
                curveTo(14.76f, 2f, 17f, 4.24f, 17f, 7f)
                curveTo(17f, 9.76f, 14.76f, 12f, 12f, 12f)
                curveTo(9.24f, 12f, 7f, 9.76f, 7f, 7f)
                curveTo(7f, 4.24f, 9.24f, 2f, 12f, 2f)
                close()
                // 肩：从 y14.5 的两侧下摆收到 (2,19.5)，底部平边到 y22
                moveTo(12f, 14.5f)
                curveTo(8.66f, 14.5f, 2f, 16.18f, 2f, 19.5f)
                lineTo(2f, 22f)
                lineTo(22f, 22f)
                lineTo(22f, 19.5f)
                curveTo(22f, 16.18f, 15.34f, 14.5f, 12f, 14.5f)
                close()
            }
        }.build()
        _whaleUser = vector
        return vector
    }
