package com.mysticat.roleplay.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * API Key 加密密钥的 Android Keystore 实现（「注入优先」模式）。
 * App 启动时注入：`Security.keyProvider = AndroidKeystoreKeyProvider`。
 * 桌面端不注入 → Security 走既有的「明文 + 界面警告」回退，加固阶段再接 DPAPI。
 */
object AndroidKeystoreKeyProvider : Security.KeystoreKeyProvider {

    private const val KEY_ALIAS = "mysticat_api_keys"
    private const val KEYSTORE = "AndroidKeyStore"

    /**
     * 缓存的 Keystore 密钥（原 Security 内的密钥缓存优化原样迁来）：
     * Keystore.load 是全量加载且 loadSettings() 会在主线程频繁触发加密解密，
     * 密钥在进程生命周期内不会变，双检锁缓存一份。
     */
    @Volatile
    private var cachedKey: SecretKey? = null

    override fun getOrCreateKey(): SecretKey {
        cachedKey?.let { return it }
        synchronized(this) {
            cachedKey?.let { return it }
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it.also { cachedKey = it } }
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            kg.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return kg.generateKey().also { cachedKey = it }
        }
    }

    /** 加解密失败时丢掉缓存，让下次重新读 Keystore（保持原自愈能力） */
    override fun resetKey() {
        cachedKey = null
    }
}
