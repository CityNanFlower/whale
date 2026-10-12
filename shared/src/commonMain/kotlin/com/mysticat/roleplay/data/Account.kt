package com.mysticat.roleplay.data

import kotlinx.serialization.Serializable

/** 本地账号（无云端；可注册/切换）。nickname=用户名，account=登录账号，passwordHash=密码哈希 */
@Serializable
data class Profile(
    val id: String,
    val nickname: String,
    val account: String = "",
    val passwordHash: String = "",
    val avatarUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** profiles.json 的包装 */
@Serializable
data class ProfileList(
    val profiles: List<Profile> = emptyList()
)
