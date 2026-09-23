package com.mysticat.roleplay.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * 本地安全性工具：
 * 1) 密码用带盐 PBKDF2WithHmacSHA256 存储（旧的无盐 SHA-256 可迁移）。
 * 2) API Key 用 AES/GCM 加密后再落盘（明文不再直接写进 settings.json）。
 *    密钥来自注入的 [KeystoreKeyProvider]：Android = AndroidKeystoreKeyProvider（AndroidKeyStore，App 启动时注入），
 *    桌面端暂不注入 → 走既有的「明文 + 界面警告」回退，加固阶段接 Windows DPAPI。
 */
object Security {

    private const val PREFIX = "enc1:"

    /** 平台密钥提供者；null = 本平台没有硬件级密钥库，encrypt 返回明文并留提示 */
    interface KeystoreKeyProvider {
        fun getOrCreateKey(): SecretKey
        fun resetKey() {}
    }

    @Volatile
    var keyProvider: KeystoreKeyProvider? = null

    /**
     * 密钥库的人话名称（Android="Android Keystore"、桌面="Windows DPAPI"），宿主注入 [keyProvider]
     * 时一并写。设置页/安全须知文案用它分端——null＝本平台没有密钥库，**界面不得声称"加密存储"**。
     */
    @Volatile
    var keyProviderName: String? = null

    /** 密钥库此刻可用（有 provider 且最近一次加解密没回退明文）——界面据此决定能否说"加密存储" */
    val encryptedStorageAvailable: Boolean
        get() = keyProvider != null && lastCryptoIssue == null

    /** 最近一次 Keystore 异常的人话描述（降级明文/解密失败），供设置页提示用户；null = 一切正常 */
    @Volatile
    var lastCryptoIssue: String? = null
        private set

    // ---- 密码：PBKDF2 + 盐 ----
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_LEN_BITS = 256

    fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        return "pbkdf2\$$ITERATIONS\$${b64(salt)}\$${b64(hash)}"
    }

    fun verifyPassword(password: String, stored: String): Boolean {
        val parts = stored.split("$")
        return if (parts.size == 4 && parts[0] == "pbkdf2") {
            val iters = parts[1].toIntOrNull() ?: ITERATIONS
            val salt = unb64(parts[2])
            val hash = unb64(parts[3])
            constantTimeEquals(pbkdf2(password.toCharArray(), salt, iters, KEY_LEN_BITS), hash)
        } else {
            // 旧格式：无盐 SHA-256（64 位十六进制）
            legacySha256(password) == stored
        }
    }

    private fun legacySha256(pw: String): String =
        MessageDigest.getInstance("SHA-256").digest(pw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun pbkdf2(chars: CharArray, salt: ByteArray, iterations: Int, lenBits: Int): ByteArray {
        val spec = PBEKeySpec(chars, salt, iterations, lenBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    // ---- API Key 加密：Android Keystore AES/GCM ----

    fun encrypt(value: String): String {
        if (value.isEmpty()) return value
        val provider = keyProvider ?: run {
            // 本平台没有密钥库（桌面端首版）：明文落盘 + 提示，不阻塞使用
            lastCryptoIssue = "安全模块暂不可用，API Key 将以明文保存在本机。功能不受影响。"
            return value
        }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, provider.getOrCreateKey())
            val out = PREFIX + b64(cipher.iv) + ":" + b64(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
            lastCryptoIssue = null // 加密成功说明密钥库已恢复，之前的降级提示可以撤掉
            out
        } catch (t: Throwable) {
            // 密钥库不可用时回退明文，保证不阻塞使用，但要留下提示
            provider.resetKey() // 缓存的密钥可能已失效，丢掉让下次重新读（保持自愈能力）
            lastCryptoIssue = "安全模块（Keystore）暂不可用，API Key 将以明文保存在本机。功能不受影响，但建议之后重新保存一次以恢复加密。"
            WhaleLog.w("WhaleSecurity", "Keystore 加密失败，回退明文：${t.message}")
            value
        }
    }

    fun decrypt(value: String): String? {
        if (value.isEmpty()) return value
        if (!value.startsWith(PREFIX)) return value // 旧明文，保持兼容
        return try {
            val body = value.substring(PREFIX.length)
            val parts = body.split(":", limit = 2)
            if (parts.size != 2) return null
            val iv = unb64(parts[0])
            val ct = unb64(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyProvider!!.getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            // 解密失败（例如密钥已丢）：返回 null，调用方按空串处理，但要留下提示
            keyProvider?.resetKey() // 同上：丢掉缓存，让下次重新读
            lastCryptoIssue = "本机安全密钥已失效，之前保存的 API Key 无法读取，需要重新填写一次。"
            WhaleLog.w("WhaleSecurity", "Keystore 解密失败，API Key 按丢失处理：${t.message}")
            null
        }
    }

    // （原 Security 内的 Keystore 密钥缓存/创建逻辑 P1-4 已整体迁到 androidMain 的
    //   AndroidKeystoreKeyProvider，含双检锁缓存与失败自愈，见该文件注释。）

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getMimeDecoder().decode(s)
}

/**
 * 这份设置里是否还存着**明文**密钥（第 63 轮安全审计）。
 *
 * 判定＝非空且不带 [Security] 的密文前缀。用于"当年加密不可用时存的明文，现在密钥库恢复了，
 * 静默升级成密文"这条自愈路径 —— 见 `Repository.loadSettings`。
 * 只看 providerKeys / imageProviderKeys / speechCredentials：旧的全局 key 字段在读设置时已归并进 providerKeys。
 */
fun AiSettings.hasPlaintextSecret(): Boolean {
    fun plain(v: String) = v.isNotBlank() && !v.startsWith("enc1:")
    if (providerKeys.values.any(::plain)) return true
    if (imageProviderKeys.values.any(::plain)) return true
    if (speechCredentials.values.any { fields -> fields.values.any(::plain) }) return true
    return plain(chatApiKey) || plain(imageApiKey)
}
