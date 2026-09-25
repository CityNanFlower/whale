package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/** 用户预设设定（传达给对话中的角色，让角色按你的设定与你互动） */
@Serializable
data class UserSetting(
    val id: String,
    val name: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** user_settings.json 包装 */
@Serializable
data class UserSettingList(
    val settings: List<UserSetting> = emptyList()
)
