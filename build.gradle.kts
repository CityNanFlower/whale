plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    // M3：shared（KMP）模块的插件
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // M4：desktopApp（桌面入口与打包）模块的插件
    alias(libs.plugins.kotlin.jvm) apply false
}

// ── M5 打包绕行：WiX（M1 实测，必踩）──
// CMP 在 Windows 上只认 Msi/Exe，两者都要 WiX；本机没装，且 `createDistributable` 也会把
// `:downloadWix` 拉进任务图去 github.com 下 wix311.zip —— 国内必超时。首版走"自压便携 zip"，
// 所以这里直接禁掉这两个任务。**必须写在根项目**：任务名是 `:downloadWix`，写在子模块不生效。
tasks.matching { it.name == "downloadWix" || it.name == "unzipWix" }.configureEach { enabled = false }
// 禁用之后 wixToolsetDir 仍被当"输入目录"校验，得给它一个存在的空目录
layout.buildDirectory.dir("wix311").get().asFile.mkdirs()
