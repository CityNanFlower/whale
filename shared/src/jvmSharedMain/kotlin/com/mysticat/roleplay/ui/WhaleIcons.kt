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
                // 外轮廓：宝箱一体（圆顶从 y10.5 拱到 y3.5，箱身直边到底、底角圆角）
                moveTo(3f, 10.5f)
                curveTo(3f, 6.4f, 7f, 3.5f, 12f, 3.5f)
                curveTo(17f, 3.5f, 21f, 6.4f, 21f, 10.5f)
                lineTo(21f, 17.8f)
                curveTo(21f, 19.6f, 19.6f, 21f, 17.8f, 21f)
                lineTo(6.2f, 21f)
                curveTo(4.4f, 21f, 3f, 19.6f, 3f, 17.8f)
                close()
                // 洞 1：盖身之间的合页缝（留边 0.4 防止与外轮廓共边出毛刺）
                moveTo(3.4f, 11.4f)
                lineTo(20.6f, 11.4f)
                lineTo(20.6f, 12.2f)
                lineTo(3.4f, 12.2f)
                close()
                // 洞 2：箱身正中的小鲸鱼（面朝左，尾巴在右上方展开两片鳍叶）
                moveTo(5.8f, 16.6f)
                curveTo(5.8f, 14.9f, 7.6f, 13.7f, 9.8f, 13.7f)
                curveTo(11.9f, 13.7f, 13.5f, 14.4f, 14.3f, 15.4f)
                curveTo(14.8f, 14.5f, 15.7f, 13.8f, 16.9f, 13.6f)
                curveTo(16.6f, 14.6f, 16.6f, 15.4f, 17f, 16f)
                curveTo(17.8f, 15.9f, 18.4f, 16.2f, 18.7f, 16.8f)
                curveTo(17.5f, 17.7f, 16f, 17.8f, 14.8f, 17.3f)
                curveTo(13.9f, 18.3f, 12.1f, 18.9f, 10f, 18.9f)
                curveTo(7.7f, 18.9f, 5.8f, 18.2f, 5.8f, 16.6f)
                close()
            }
        }.build()
        _whaleTreasure = vector
        return vector
    }
