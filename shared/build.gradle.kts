// 共享模块（KMP：commonMain ← jvmSharedMain ← {androidMain, jvmMain}）：
//   commonMain      纯 Kotlin，零平台依赖
//   jvmSharedMain   JVM 共同层：全部 Compose UI + JVM 形状的数据层（能用 java.*/OkHttp/javax.crypto）
//   androidMain     Android 替身（Keystore / SAF / MediaStore / TTS / MediaRecorder / 入口）
//   jvmMain         桌面替身（JFileChooser / Skia·ImageIO / DPAPI / 窗口壳）
// 唯一硬约束：commonMain ← jvmSharedMain ← {androidMain, jvmMain}，下层看不见上层。
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // 注意：不用 jvmToolchain(17)——本机只有 JDK 21，Gradle 会去下载 17 而失败。
    // 只设 jvmTarget，用 JDK 21 编译、产出 17 字节码（M1 探针实测结论）。
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        // 自定义中间源集：对 android 与 jvm 两个 target 同时可见。
        val jvmSharedMain by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        // 第 60 轮：内置环境音的音源文件（真采样 WAV）。目录本体在 `shared/assets/bgm/`，
        // 只是一份——Android 与桌面各自把它当 **JVM 资源**打进包（见文件尾 resources.srcDir 两处）。
        // 为什么走"JVM 资源"而不是 Android 的 assets/：两个平台就能共用同一个读取口
        // （ClassLoader.getResourceAsStream，见 BgmSources.assetBytes），不必再为宿主加注入口。
        jvmSharedMain.resources.srcDir(rootProject.file("shared/assets"))

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.okhttp)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            // DPAPI（API Key 加密）要调 Win32 Crypt32 —— 纯 JDK 没有这条路，只能经 JNA。
            // 只声明在 jvmMain：androidMain 看不到，也不会被 KMP 解析成 Android 依赖。
            implementation(libs.jna.platform)
            // mp3spi：让 Java Sound 认识 MP3（供应商合成的音频格式），三件套由它的 POM 传递带入
            implementation(libs.soundlibs.mp3spi)
            // 第 60 轮：让 Java Sound 再认识 m4a/aac、flac、ogg（用户上传的音乐格式）。
            // 这三家都是 Java Sound 的 SPI（Apache-2.0），装上即生效——AudioSystem 自己会挑，
            // 播放代码一行不用改。⚠ 只给桌面：Android 的 MediaPlayer 原生就认这些格式。
            implementation(libs.javasound.aac)
            implementation(libs.javasound.flac)
            implementation(libs.javasound.vorbis)
        }

        // Android 替身要用 SAF 启动器（rememberLauncherForActivityResult）与返回键拦截
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.mysticat.roleplay.shared"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // 第 60 轮：同一份内置环境音也要进 APK。**必须挂在这里**——实测 `jvmSharedMain/resources`
    // 会被打进桌面 jar，但 AGP 只认 android 源集自己的 resources 目录，中间源集的东西不进 APK。
    sourceSets.getByName("main").resources.srcDir(rootProject.file("shared/assets"))
}
