package com.mysticat.roleplay.data

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 桌面端的密钥提供者（Android=AndroidKeystore，桌面=**Windows DPAPI**）。
 *
 * 做法：生成一把随机 AES-256 密钥，用 [Crypt32Util] 的 `CryptProtectData`（**用户作用域**）
 * 加密后存到 `%APPDATA%\MysticatRoleplay\secret.key`；此后 [Security] 用它做 AES/GCM 的
 * 那把钥匙，与 Android 侧的 Keystore 路径完全同构——共享层一行都不用分叉。
 *
 * 这层保护的实际含义（也是它相对"明文 + 警告"的收益）：密钥文件被拷到**别的 Windows 账号或别的机器**
 * 就解不开了（DPAPI 的主密钥由当前用户的登录凭据派生、由系统托管），所以 settings.json 里那串
 * `enc1:` 密文跟着备份文件流出也不会泄露 API Key。**它不等于"防住本机上的自己"**——
 * 同账号下运行的程序仍可调 DPAPI 解密（Android 的 Keystore 同理）。
 *
 * 为什么绕不开 JNA：Crypt32 是 Win32 native API，纯 JDK 没有出口（JDK 22 才把 FFM 转正，
 * 本工程按 JDK 21 / 字节码 17 编译）。JNA 只声明在 `jvmMain`，Android 侧完全看不见。
 */
class WindowsDpapiKeyProvider(private val keyFile: File) : Security.KeystoreKeyProvider {

    @Volatile
    private var cached: SecretKey? = null

    private val lock = Any()

    override fun getOrCreateKey(): SecretKey {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            return loadOrCreate().also { cached = it }
        }
    }

    /**
     * 只丢内存缓存，**不删密钥文件**。
     *
     * 与 Android 侧同一语义（那边也是丢缓存让下次重读 Keystore）：真正的密钥仍由 DPAPI 托管，
     * 缓存失效后重读即可自愈。删文件反而会把"共享层按约定调 reset 自愈"变成"用户 API Key 永久丢失"。
     */
    override fun resetKey() {
        cached = null
    }

    private fun loadOrCreate(): SecretKey {
        readExisting()?.let { return it }
        return createNew()
    }

    /** 读已有密钥；文件不存在/被破坏/DPAPI 解不开都返回 null（由调用方走"重建"分支） */
    private fun readExisting(): SecretKey? {
        if (!keyFile.isFile || keyFile.length() == 0L) return null
        return runCatching {
            val raw = Crypt32Util.cryptUnprotectData(keyFile.readBytes())
            if (raw.size != KEY_BYTES) {
                WhaleLog.w(TAG, "密钥文件长度异常（${raw.size}），按损坏处理并重建")
                raw.fill(0)
                return null
            }
            SecretKeySpec(raw, "AES").also { raw.fill(0) }
        }.getOrElse {
            // 常见原因：密钥文件是从别的 Windows 账号/机器拷过来的（DPAPI 解不开）
            WhaleLog.w(TAG, "DPAPI 解密密钥文件失败，将重建一把新密钥：${it.message}")
            null
        }
    }

    private fun createNew(): SecretKey {
        val raw = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        // SecretKeySpec 会复制入参（JDK 文档），所以拿到 key 之后就可以把裸密钥从内存里抹掉
        val key = SecretKeySpec(raw, "AES")
        writeAtomically(Crypt32Util.cryptProtectData(raw))
        raw.fill(0)
        WhaleLog.i(TAG, "已生成并托管新密钥（${keyFile.absolutePath}）")
        return key
    }

    /** 先写临时文件再原子改名：写到一半掉电不会把上一把还能用的密钥文件毁掉 */
    private fun writeAtomically(blob: ByteArray) {
        keyFile.parentFile?.mkdirs()
        val tmp = File(keyFile.parentFile, keyFile.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(keyFile)) {
            // 目标被占用等极端情况：退回直接写（放弃原子性，但不至于让加密整条链路失败）
            keyFile.writeBytes(blob)
            tmp.delete()
        }
    }

    companion object {
        private const val TAG = "WhaleSecurity"
        private const val KEY_BYTES = 32

        /**
         * 本平台能用就用，不能用返回 null（宿主据此保持"明文 + 界面警告"的旧回退）。
         * 判定方式是真调一次 DPAPI 往返，而不是看 os.name——JNA native 库缺失时
         * （jpackage 裁模块裁掉、或非 Windows）只有真调才会抛。
         */
        fun createIfAvailable(keyFile: File): WindowsDpapiKeyProvider? = runCatching {
            val probe = Crypt32Util.cryptUnprotectData(Crypt32Util.cryptProtectData(ByteArray(8)))
            check(probe.size == 8)
            WindowsDpapiKeyProvider(keyFile)
        }.getOrElse {
            WhaleLog.w(TAG, "本机不可用 Windows DPAPI（${it.message}），API Key 回退明文保存")
            null
        }
    }
}
