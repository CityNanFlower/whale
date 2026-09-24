package com.mysticat.roleplay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.mysticat.roleplay.data.AiClient
import com.mysticat.roleplay.data.AndroidKeystoreKeyProvider
import com.mysticat.roleplay.data.BgmPlayer
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.data.Security
import com.mysticat.roleplay.data.Voice
import com.mysticat.roleplay.ui.AndroidPlatformUi
import com.mysticat.roleplay.ui.Platform
import com.mysticat.roleplay.data.AndroidAudioPlayer
import com.mysticat.roleplay.data.AndroidSpeechRecognizer
import com.mysticat.roleplay.data.AndroidTtsEngine
import com.mysticat.roleplay.data.AndroidVoiceRecorder

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        // M3 去 Context：注入宿主目录（数据仍在 files/roleplay/，行为不变），
        // 并注入 Keystore 密钥提供者（桌面端不注入 → API Key 走明文+警告回退）
        Repository.init(filesDir, cacheDir)
        Security.keyProvider = AndroidKeystoreKeyProvider
        Security.keyProviderName = "Android Keystore"
        // M3 第四步：平台服务注入（SAF/Toast/相册/分享/权限/剪贴板/编解码 + 语音三件套）。
        // 桌面端在 desktopApp main 里注入各自的实现。
        Platform.ui = AndroidPlatformUi(this)
        Voice.ttsEngineFactory = { AndroidTtsEngine(this) }
        Voice.audioPlayerFactory = { AndroidAudioPlayer() }
        Voice.recorderFactory = { AndroidVoiceRecorder(this) }
        Voice.recognizer = AndroidSpeechRecognizer(this)
        // 背景音乐：MediaPlayer 支持的容器/编码比桌面宽得多（ogg/m4a/flac 都能放），
        // 所以上传闸门按这里的声明放行；桌面只有 mp3/wav（见 installDesktopVoice）。
        Voice.supportedAudioExtensions =
            setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus", "amr")
        // 退到后台就把 BGM 停掉（第 59 轮）。没有前台服务也没有通知，放着不管的话用户离开 App
        // 之后它会在后台一直响、还没法关——那不是"环境音"，是失控。
        // 计数口径 = "有几个 Activity 处于 started"：页面间跳转/旋转都不归零，只有真退到后台才停。
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                if (started == 1) BgmPlayer.setAppForeground(true)
            }

            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) BgmPlayer.setAppForeground(false)
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        // 上次开着 BGM 的话，这次启动接着放（开关是持久化的用户意图，不该每次重开 App 都要再点一下）
        BgmPlayer.syncFromSettings()
        // 崩溃守卫（2026-09-15，第 14 轮）：把偶发崩溃从「App 直接死掉」变成
        // 「记录现场 + 重启回首页」。装在最前面，后面任何一次异常都能被接住。
        CrashGuard.install(this)
        // 调试包放行「本机 http 假服务端」（release 恒为 false）：用于在不花钱、不发真实请求的前提下
        // 验证网络链路（真流式 / 重试 / 兜底解析）。见 AiClient.requireHttps 的说明。
        AiClient.allowInsecureLoopback =
            (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * 全局 Coil 配置：限内存缓存、渐显，避免大图解码挤爆内存（#14 加固）。
     *
     * 拦截器把**公网 http 图片地址**升级成 https：卡片/会话里可能存着服务端给的 http 链接
     * （OSS 预签名等），targetSdk 34 的明文策略会直接拦掉，表现成"图片莫名加载不出来"。
     * 私网与回环地址保持原样（本地部署的图床本来就该走 http），判断口径与 [AiClient.httpsUrl] 同一份。
     */
    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.20).build() }
            // coil3 不再有 loader 级 crossfade（改为请求级 coil3.transition.crossfade），
            // 为保持依赖换血轮最小改动先移除；渐显属可接受的细微视觉差异。
            // coil3 3.5 没有 .okHttpClient 扩展了——自定义 OkHttp 走 OkHttpNetworkFetcherFactory 注入
            // （sources jar 实测：顶层函数，@JvmName("factory")；loader 级 crossfade 已移除，属可接受视觉差异）。
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            okhttp3.OkHttpClient.Builder()
                                .addInterceptor { chain ->
                                    val req = chain.request()
                                    val original = req.url.toString()
                                    if (req.url.scheme == "http") {
                                        val upgraded = AiClient.httpsUrl(original)
                                        if (upgraded != original) {
                                            return@addInterceptor chain.proceed(req.newBuilder().url(upgraded).build())
                                        }
                                    }
                                    chain.proceed(req)
                                }
                                .build()
                        },
                        cacheStrategy = { coil3.network.CacheStrategy.DEFAULT }
                    )
                )
            }
            .build()
}
