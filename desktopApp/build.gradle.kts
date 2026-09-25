// 桌面端入口与打包模块（桌面入口、托盘、jpackage 打包）。
//
// 只放"桌面专属"的东西：窗口壳、单实例锁、窗口尺寸持久化、平台实现注入。
// 业务 UI 与数据层全在 :shared（jvmSharedMain），这里不复制任何业务代码。
//
// M4 阶段只用 :desktopApp:run 本机直跑；打包（createDistributable / msi）是 M5 的事。
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    // 与 :shared 同一口径：不用 jvmToolchain（本机只有 JDK 21，Gradle 会去下载 17 而失败），
    // 用 JDK 21 编译、产出 17 字节码。
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// 版本元数据传给运行时：桌面的"应用信息"（关于页 / 更新检查）要读它。
// 直接 expand 一份 properties 资源，避免在 Kotlin 代码里再抄一遍版本号。
val buildInfoDir = layout.buildDirectory.dir("generated/whaleBuildInfo")
val generateBuildInfo by tasks.registering {
    val out = buildInfoDir
    val name = libs.versions.desktopVersionName.get()
    val code = libs.versions.desktopVersionCode.get()
    val aligned = libs.versions.desktopAlignedAndroid.get()
    inputs.property("name", name)
    inputs.property("code", code)
    outputs.dir(out)
    doLast {
        val f = out.get().file("whale-build.properties").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            # 由 :desktopApp 的 generateBuildInfo 任务生成，勿手改（版本来源＝gradle/libs.versions.toml）
            versionName=$name
            versionCode=$code
            alignedAndroid=$aligned
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.main {
    resources.srcDir(buildInfoDir)
    // .ico 只给 jpackage 出包用（它读的是文件路径），不必塞进运行时 jar——
    // 窗口图标那条路读的是同目录的 whale-icon.png。
    resources.exclude("icon/*.ico")
}

tasks.named("processResources") { dependsOn(generateBuildInfo) }

dependencies {
    implementation(project(":shared"))
    // Compose Desktop 运行时（Windows 版；跨平台打包时 currentOs 会按目标平台取）
    implementation(compose.desktop.currentOs)
    // :shared 把 Compose 依赖声明成 implementation，不会传给消费者——窗口壳自己用到的那几个要显式加
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutines.swing)
    // Coil3 全局加载器在桌面要手动设（Android 走 Application 接口自动挂）：图片卡/头像用
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
}

// 开发运行放行「本机 http 假服务端」+ 崩溃自测（打包产物不带这个属性，见 DesktopPlatformUi.isDebuggableBuild）。
// 用 matching 惰性匹配：Compose 插件的 run 任务不在这个时点注册，直接 named() 会报 "not found"。
tasks.matching { it.name == "run" }.configureEach {
    if (this is JavaExec) systemProperty("whale.debug", "true")
}

compose.desktop {
    application {
        mainClass = "com.mysticat.roleplay.desktop.MainKt"

        nativeDistributions {
            // ⚠ CMP 在 Windows 上只支持 Msi/Exe，两者都要 WiX（本机未装）——实际出包走
            // createDistributable + 自压 zip（CMP 的 Windows 打包只有 Msi/Exe 且都要 WiX，便携包靠自己压），
            // 这里保留 Msi 声明只是为了 createDistributable 能跑通（WiX 两个任务已在根项目禁用）。
            targetFormats(TargetFormat.Msi)
            packageName = "MysticatRoleplay"
            packageVersion = libs.versions.desktopVersionName.get()
            description = "鲸鱼 · 本地 AI 角色扮演"
            vendor = "Mysticat"
            // jlink 的模块清单：默认那套里没有 `jdk.httpserver`，而 `--smoke` 有 **7 项**自检要在
            // 进程内起一个本地假服务端（`com.sun.net.httpserver`）核对"聊天真的按卡的形态发出去"
            // 这类接缝（纯函数断言盖不住）。缺了它，这 7 项在**打包态恒 FAIL**（开发态有完整 JDK 所以
            // 全绿）——一个永远亮着 7 个红灯的验收闸门，等于把真回归淹掉。顺带说明：这条依赖只在
            // 自检代码里，生产路径不碰它（全项目 grep `com.sun.net.httpserver` 只有 Main.kt 的自检项）。
            // 依据：`runtime/bin/java.exe --list-modules | grep jdk.httpserver` 曾返回 0（第 89 轮实测）。
            modules("jdk.httpserver")
            // 出包图标（exe/开始菜单/任务栏）：jpackage 在 Windows 只吃 .ico，必须是多尺寸的
            // （16/32 那几档是资源管理器小图标视图用的，只塞 256 会糊）。
            // 生成方式见 tools/make-launcher-icon.py（与 Android mipmap 同一份母图）。
            windows {
                iconFile.set(project.file("src/main/resources/icon/whale-icon.ico"))
            }
        }
    }
}

// ─────────────────────────── M5：出便携 zip（零 WiX） ───────────────────────────

/** createDistributable 的产物目录（免安装 app image）：exe + app/ + runtime/ */
val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/MysticatRoleplay")

/**
 * 便携包版本标记：更新器下载 zip 后读**包内**这个文件反查真实版本。
 *
 * 为什么不用 zip 文件名或 version.json 里的号：那两个都可能与包里的代码不一致
 * （Android 侧踩过"漏改常量导致误报更新"），所以版本以"包自己的声明"为准——与
 * `readUpdatePackageVersion` 在 Android 侧读 APK Manifest 是同一个口径。
 */
val writePortableBuildInfo by tasks.registering {
    dependsOn("createDistributable")
    val outDir = appImageDir
    val name = libs.versions.desktopVersionName.get()
    val code = libs.versions.desktopVersionCode.get()
    val aligned = libs.versions.desktopAlignedAndroid.get()
    inputs.property("name", name)
    inputs.property("code", code)
    outputs.dir(outDir)
    doLast {
        val f = outDir.get().file("build-info.json").asFile
        f.parentFile.mkdirs()
        f.writeText(
            """
            {
              "packageName": "com.mysticat.roleplay",
              "versionName": "$name",
              "versionCode": $code,
              "alignedAndroid": "$aligned",
              "platform": "windows"
            }
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )
    }
}

/**
 * 精简 `material-icons-extended`：那份 jar 36 MB（压缩后仍 34 MB，**占整个便携包的 32%**），
 * 而我们真正用到的图标只有几十个（用户 2026-09-18 报"包太大传不上去"，量出来就是它）。
 *
 * 做法：从**我们自己编译出来的 class 文件**里扫出真正被引用的图标类，再重写那份 jar 只保留它们。
 * 不扫源码而扫字节码，是因为字节码里的常量池条目就是**编译器实际解析到的类名**——
 * 源码里写 `Icons.Filled.VolumeUp`、类加载时找的就是 `.../icons/filled/VolumeUpKt`，
 * 两边一一对应；而且扫描不会因为写法不同（import 别名、函数引用）漏掉。
 *
 * ⚠ 漏保留一个图标 = 用到它的那个界面在运行时 `NoClassDefFoundError`。
 * 所以这里**找不到就 fail**（宁可构建失败，也不要出一个点开某页就崩的包），
 * 并在打包后跑 `--smoke` + `--shot` 复核主要界面。
 */
val trimIconJar by tasks.registering {
    dependsOn("createDistributable")
    val imageDir = appImageDir
    // ⚠ 这两个路径必须在**配置期**算成 File：doLast 里再去碰 `rootProject` / `layout`
    // 会捕获 Gradle 脚本对象，配置缓存（gradle.properties 里开着）直接拒绝序列化并让构建失败。
    val sharedClasses = rootProject.layout.projectDirectory.dir("shared/build/classes/kotlin/jvmSharedMain").asFile
    val ownClasses = layout.buildDirectory.dir("classes/kotlin/main").get().asFile
    outputs.upToDateWhen { false }
    doLast {
        val appDir = imageDir.get().dir("app").asFile
        val iconJar = appDir.listFiles { f -> f.name.startsWith("material-icons-extended") && f.name.endsWith(".jar") }
            ?.firstOrNull() ?: return@doLast
        val before = iconJar.length()
        // 幂等保护：createDistributable 是"UP-TO-DATE"时不会重新拷 jar，此时这个文件已经是精简过的了，
        // 再按"未精简"去比对必然报缺失。用体积当判据（原 36 MB / 精简后 <10 MB）。
        if (before < 10L * 1024 * 1024) {
            logger.lifecycle("图标 jar 已是精简过的（${before / 1024 / 1024} MB），跳过")
            return@doLast
        }

        // 1) 扫出**被引用到的**图标类。扫三处，全部按字节正则扫常量池：
        //    ① 我们自己的 class 目录（:shared jvmSharedMain + :desktopApp）；
        //    ② app/ 里其余 jar —— 万一某个库内部引用了扩展图标，也能留住（不能只信自己的代码）。
        //    ⚠ 包名可能是**两段**（`automirrored/filled/MenuBookKt`），早期正则只认一段 ⇒
        //      automirrored 的图标一个都没被认成"被引用"、全被当没用到删掉 ⇒ 1.0.3 的 P0：
        //      点「用户」页 `NoClassDefFoundError: .../automirrored/filled/MenuBookKt`（用户实测）。
        val iconRef = Regex("androidx/compose/material/icons/((?:[a-z]+/)+)([A-Za-z0-9_]+)Kt")
        val needed = mutableSetOf<String>()
        fun scan(bytes: ByteArray) {
            iconRef.findAll(String(bytes, Charsets.ISO_8859_1)).forEach { m ->
                needed += "androidx/compose/material/icons/${m.groupValues[1]}${m.groupValues[2]}Kt"
            }
        }
        val classDirs = listOf(sharedClasses, ownClasses)
        classDirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach { scan(it.readBytes()) }
        }
        appDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") && f != iconJar }?.forEach { jar ->
            runCatching {
                ZipFile(jar).use { z ->
                    z.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .forEach { e -> z.getInputStream(e).use { scan(it.readBytes()) } }
                }
            }
        }
        // 自动镜像版与普通版成对保留（auto-mirrored 的实现会引用同名普通版）
        needed += needed.filter { it.contains("/automirrored/") }.map { it.replace("/automirrored/", "/") }

        // 2) 重写 jar：非图标条目原样带走，图标条目只留需要的。
        // ⚠ 判据必须是"**本 jar 里存在的**被引用图标"：像 Add / ArrowBack / AccountCircle 这些
        //   住在另一个 jar（material-icons-core）里，扫出来的名字在本 jar 中根本找不到——
        //    它们"缺失"是正常的，取交集才不会误报（首版就报出 245 个假的"缺失"）。
        val available = ZipFile(iconJar).use { z ->
            z.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") }
                .map { it.name.removeSuffix(".class") }
                .toSet()
        }
        val keep = needed.filter { it in available }.toSet()
        val tmp = File(iconJar.parentFile, iconJar.name + ".trimmed")
        ZipFile(iconJar).use { zin ->
            ZipOutputStream(tmp.outputStream().buffered()).use { zout ->
                for (e in zin.entries()) {
                    if (e.isDirectory) continue
                    val name = e.name
                    if (name.startsWith("androidx/compose/material/icons/") &&
                        name.removeSuffix(".class") !in keep
                    ) {
                        continue
                    }
                    val entry = ZipEntry(name)
                    // 时间戳沿用源条目的：新 ZipEntry 默认取"当前时间"，会让同一个包两次构建
                    // 哈希不同（实测 387c1e… vs ce38e1…），而更新器的 sha256 校验就靠可重复构建。
                    if (e.time > 0) entry.time = e.time
                    zout.putNextEntry(entry)
                    zin.getInputStream(e).use { it.copyTo(zout) }
                    zout.closeEntry()
                }
            }
        }
        // 3) 复核：要留的一个都不能少（写漏了 = 点开某页崩，宁可构建失败）
        val present = ZipFile(tmp).use { z ->
            z.entries().asSequence().map { it.name.removeSuffix(".class") }.toSet()
        }
        val missing = keep.filter { it !in present }
        if (missing.isNotEmpty()) {
            tmp.delete()
            throw GradleException("图标精简后缺少被引用的类（${missing.size} 个）：${missing.take(5)}")
        }
        val iconsInJar = available.count { it.startsWith("androidx/compose/material/icons/") }
        iconJar.delete()
        tmp.renameTo(iconJar)

        // 4) **总闸门**：所有被引用的图标类，必须能在**整个 app/ 镜像**里找到（本 jar ＋ icons-core ＋ 其余 jar）。
        //    为什么单靠上面第 2 步的 `keep` 不够：`keep = needed ∩ available` —— 只要一个图标在**源 jar 里就没有**
        //    （例如桌面端 material-icons-extended 1.7.3 整支 `automirrored/` 都不存在），它会被静默丢掉、
        //    连第 3 步的复核也看不见，最后表现成"装完点某页就崩"。1.0.3 的 P0 正是这么漏出去的。
        val shipped = mutableSetOf<String>()
        appDir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.forEach { jar ->
            runCatching {
                ZipFile(jar).use { z ->
                    z.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".class") }
                        .forEach { shipped += it.name.removeSuffix(".class") }
                }
            }
        }
        val nowhere = needed.filterNot { it in shipped }
        if (nowhere.isNotEmpty()) {
            throw GradleException(
                "这些图标类被代码引用、但**没有随包发出**（点开用到它的页面必崩）：${nowhere.sorted()}。" +
                    "多半是选了桌面端 material-icons-extended 里不存在的图标——该 artifact 只含部分图标、" +
                    "`automirrored/*` 一支完全没有；换成 material-icons-core 里的图标，或改用别处已经用过的图标。"
            )
        }
        logger.lifecycle(
            "图标 jar 精简：本 jar 内 ${iconsInJar} 个图标保留 ${keep.size} 个（其余未引用），" +
                "${before / 1024 / 1024} MB → ${iconJar.length() / 1024 / 1024} MB；" +
                "全镜像复核 ${needed.size} 个被引用图标一个不缺"
        )
    }
}

/**
 * 便携包里的**卸载程序**（用户 2026-09-18 要求：不能只有"删文件夹"这一条路）。
 *
 * 三条设计约束：
 * 1. **纯 ASCII 之外的文字用 GBK 写**：cmd 按系统码页解析批处理，UTF-8 的中文会被解析坏
 *    （本仓那条"bat 一律纯 ASCII"的规矩就是这么来的）。GBK 的中文对 cmd 是**正确编码**，
 *    且每个汉字两字节都 ≥0x81、不会撞上命令分隔符，所以在非中文系统上顶多显示乱码、逻辑照跑。
 * 2. **数据默认保留**：删数据要单独问一次（账号 / 角色卡 / 会话 / API Key 密文都没了，不可恢复）。
 * 3. **程序正在运行时先等它退出**：Windows 不允许覆盖/删除正在运行的程序文件。
 *
 * 自己所在目录的删除交给"%TEMP% 里的第二段"完成（批处理删不掉自己正被读取的那个文件）。
 */
val writeUninstaller by tasks.registering {
    // ⚠ 写进**独立目录**，不写 jpackage 的输出目录：那个目录是 createDistributable 的 outputs，
    //    往里塞文件会让它下一轮判定"输出被改过"而重跑，重跑时又把我们塞的文件擦掉
    //    （实测：第一轮包里有卸载程序、第二轮就没了，因为顺序上卸载程序先写、jpackage 后擦）。
    val outDir = layout.buildDirectory.dir("generated/uninstaller")
    // ⚠ 脚本正文必须声明成 **input**：只声明 outputs.dir 的话，Gradle 见输出还在就判 UP-TO-DATE、
    //    不重跑，改了正文也不会生效（实测踩过：改了三处护栏，出包出来的还是旧脚本）。
    val body = uninstallerScriptText()
    inputs.property("script", body)
    val fileName = "卸载鲸鱼.cmd"
    outputs.file(outDir.map { it.file(fileName) })
    doLast {
        val f = outDir.get().file(fileName).asFile
        f.parentFile.mkdirs()
        // GBK：见本任务注释第 1 条
        f.writeBytes(body.toByteArray(charset("GBK")))
        logger.lifecycle("卸载程序已写入：${f.absolutePath}（${f.length()} 字节，GBK）")
    }
}

/** 卸载程序正文（纯函数，便于声明成任务 input；正文里的 `|` 是 trimMargin 的边界符） */
fun uninstallerScriptText(): String = """
            |@echo off
            |setlocal
            |set "ROOT=%~dp0"
            |echo.
            |echo   ==========================================
            |echo    鲸鱼 · 卸载程序
            |echo   ==========================================
            |echo.
            |echo   程序目录：%ROOT%
            |echo.
            |
            |:wait
            |rem ⚠ 一律用 %SystemRoot%\System32 下的全路径调 Windows 自带工具：find/timeout 这类名字
            |rem 会被 PATH 里同名的第三方版本顶掉（实测在 Git Bash 环境下 find 与 timeout 都被 MSYS 的顶了，
            |rem 表现为 "find: '/I' No such file"、"timeout: invalid time interval"）。用户双击时 PATH 也许干净，
            |rem 但卸载程序不该赌环境。
            |%SystemRoot%\System32\tasklist.exe /FI "IMAGENAME eq MysticatRoleplay.exe" /NH 2>nul | %SystemRoot%\System32\findstr.exe /I /C:"MysticatRoleplay.exe" >nul
            |if not errorlevel 1 (
            |  echo   鲸鱼正在运行，请先关闭窗口（本程序会一直等）...
            |  %SystemRoot%\System32\timeout.exe /t 2 /nobreak >nul
            |  goto wait
            |)
            |
            |set "DATA=%APPDATA%\MysticatRoleplay"
            |set "CACHE=%LOCALAPPDATA%\MysticatRoleplay"
            |rem ⚠ 删除范围的三条护栏（用户 2026-09-18 专门问过"会不会把 C 盘别的东西也删了"）：
            |rem ① 程序目录先验三个标志物，不像鲸鱼的目录就**什么都不做**；
            |rem ② 只删鲸鱼自己的已知条目，**绝不 rd /S /Q 整个目录**（万一用户"解压到当前文件夹"到桌面/下载目录，
            |rem    整目录删除会把无关文件一起带走）；
            |rem ③ 数据/缓存目录各自也要有标志物（roleplay / cache 子目录）才删，且默认不删。
            |if not exist "%ROOT%MysticatRoleplay.exe" goto :notourdir
            |if not exist "%ROOT%app" goto :notourdir
            |if not exist "%ROOT%runtime" goto :notourdir
            |echo   将删除：程序文件（%ROOT%）
            |echo   数据文件默认保留：账号、角色卡、会话、API Key 都在
            |echo.
            |rem 用 set /p 而不是 choice：choice 读的是控制台按键，**管道输入喂不进去**（没法自动化测），
            |rem 而 set /p 可读管道；默认值一律是"不删"，误敲回车不会造成任何破坏。
            |rem 另有两个显式开关（给"静默卸载/重装脚本"用，也让这两条路径能被自动化验证）：
            |rem   卸载鲸鱼.cmd /yes                 只删程序、保留数据
            |rem   卸载鲸鱼.cmd /yes /deletedata     连数据一起删（不可恢复）
            |set "ANS="
            |set "ANS2="
            |if /I "%~1"=="/yes" (set "ANS=Y") else (set /p "ANS=  继续卸载程序？[输入 Y 继续 / 直接回车 = 取消] ")
            |if /I not "%ANS%"=="Y" goto :cancel
            |
            |echo.
            |echo   数据目录：%DATA%
            |echo   缓存目录：%CACHE%
            |if /I "%~1"=="/deletedata" set "ANS2=DELETE"
            |if /I "%~2"=="/deletedata" set "ANS2=DELETE"
            |if not defined ANS2 set /p "ANS2=  连数据一起删除吗？[输入 DELETE 确认 / 直接回车 = 保留数据] "
            |if /I not "%ANS2%"=="DELETE" goto :keepdata
            |echo   正在删除数据...
            |if exist "%DATA%\roleplay" rd /S /Q "%DATA%"
            |if exist "%CACHE%\cache" rd /S /Q "%CACHE%"
            |:keepdata
            |
            |echo   正在删除程序文件...
            |if exist "%ROOT%app" rd /S /Q "%ROOT%app"
            |if exist "%ROOT%runtime" rd /S /Q "%ROOT%runtime"
            |if exist "%ROOT%update-staging" rd /S /Q "%ROOT%update-staging"
            |if exist "%ROOT%build-info.json" del /Q "%ROOT%build-info.json"
            |if exist "%ROOT%MysticatRoleplay.exe" del /Q "%ROOT%MysticatRoleplay.exe"
            |rem 自己正被 cmd 读取，删不掉自己所在目录，所以交给 %TEMP% 里的第二段脚本（先切走当前目录，
            |rem 再删本目录里的 .cmd，最后只在目录已空时收掉目录本身）。
            |rem ⚠ 路径在这里**展开写死**：第二段脚本里的 %~dp0 指的是 %TEMP%，不是 %ROOT%。
            |> "%TEMP%\whale-uninstall-del.cmd" echo @echo off
            |>>"%TEMP%\whale-uninstall-del.cmd" echo cd /d "%TEMP%"
            |>>"%TEMP%\whale-uninstall-del.cmd" echo %SystemRoot%\System32\timeout.exe /t 2 /nobreak ^>nul
            |>>"%TEMP%\whale-uninstall-del.cmd" echo del /Q "%ROOT%*.cmd" 2^>nul
            |>>"%TEMP%\whale-uninstall-del.cmd" echo rd "%ROOT%" 2^>nul
            |>>"%TEMP%\whale-uninstall-del.cmd" echo del "%%~f0"
            |start "" /min cmd /c "%TEMP%\whale-uninstall-del.cmd"
            |echo.
            |echo   完成。程序文件将在几秒内删除（目录里若还有别的文件，目录本身会保留）。
            |%SystemRoot%\System32\timeout.exe /t 3 /nobreak >nul
            |exit /b 0
            |
            |:notourdir
            |echo.
            |echo   ⚠ 这个目录里找不到 MysticatRoleplay.exe / app / runtime，看起来不是鲸鱼的程序目录，
            |echo     为了避免误删别的文件，**什么都没做**。请手动删除本目录里的鲸鱼文件。
            |pause
            |exit /b 1
            |
            |:cancel
            |echo   已取消，什么都没有改。
            |pause
            |exit /b 0
        """.trimMargin().replace("\n", "\r\n")

/**
 * 一条命令出便携包：`:desktopApp:packagePortable`
 * → `dist/windows/鲸鱼-Windows-<桌面版本>-win.zip`，并打印 SHA-256（填进 `dist/发布用-version.json` 的 windows 节点）。
 */
val packagePortable by tasks.registering(Zip::class) {
    dependsOn(writePortableBuildInfo)
    dependsOn(trimIconJar)
    dependsOn(writeUninstaller)
    from(appImageDir)
    // 便携包里的卸载程序（生成在独立目录，见 writeUninstaller 的注释）
    from(layout.buildDirectory.dir("generated/uninstaller"))
    // 只压 app image 自身（exe + app/ + runtime/），不含上层目录名
    archiveFileName.set("鲸鱼-Windows-${libs.versions.desktopVersionName.get()}-win.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("dist/windows"))
    // 便携包要可重复构建：文件时间戳进 zip 头会让同一份内容哈希不同，sha256 校验就对不上了
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    doLast {
        val zip = archiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        zip.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        logger.lifecycle("便携包：${zip.absolutePath}")
        logger.lifecycle("大小：${zip.length() / 1024 / 1024} MB")
        logger.lifecycle("SHA-256：$hex")
        zip.resolveSibling(zip.name + ".sha256").writeText("$hex  ${zip.name}\n")
    }
}

// ─────────────────────────── M13：出安装程序 exe（第 42 轮接入链路） ───────────────────────────
//
// 路线 A（用户 2026-09-18 拍板，第 37 轮手工验证跑通）：jpackage --type exe + WiX 3.x。
// 本任务把当时"启动前 export PATH 前置 WiX"的手工步骤收进构建：jpackage 只认 PATH 里的
// candle/light，这里在子进程环境变量里前置 WiX 目录，不再依赖启动 Gradle 前的手工准备。
// 产物：dist/windows/鲸鱼-Windows-<桌面版本>-setup.exe（+ 同名 .sha256）。
// 便携 zip 与安装器**并存**（packagePortable 不动）：更新器按"应用目录可写性"分流（DesktopUpdater）。
val packageInstaller by tasks.registering {
    // 复用 packagePortable 的整条 app image 链（build-info / 图标精简 / 卸载程序），zip 顺带也出来——
    // 两个产物必须来自同一份 app image，版本才不会错位
    dependsOn(packagePortable)
    outputs.upToDateWhen { false }
    // ⚠ 一律先在**配置期**算成本地值（File/String/Provider），doLast 只许捕获这些普通对象——
    //   直接在 doLast 里碰脚本顶层属性 / libs / appImageDir 会捕获 Gradle 脚本对象，
    //   配置缓存直接拒绝序列化（实测两次 BUILD FAILED 才定位到；同 trimIconJar 注释里的坑）。
    val version = libs.versions.desktopVersionName.get()
    val outDir = layout.buildDirectory.dir("installer").get().asFile
    val distDir = rootProject.layout.projectDirectory.dir("dist/windows").asFile
    val imageDir = appImageDir
    val wixDir = (findProperty("whaleWixDir") as String?)
        ?: System.getenv("WHALE_WIX_DIR")?.takeIf { it.isNotBlank() }
        ?: "D:/tools/wix314"   // 第 37 轮就位的官方 wix314-binaries 免安装解压（免管理员）
    doLast {
        val appImage = imageDir.get().asFile
        if (!appImage.isDirectory) {
            throw GradleException("app image 不存在：${appImage.absolutePath}")
        }
        val jdkBin = System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() } ?: System.getProperty("java.home")
        val jpackage = File(jdkBin, "bin/jpackage.exe")
        if (!jpackage.isFile) {
            throw GradleException(
                "找不到 jpackage.exe（JAVA_HOME=$jdkBin）——打包必须用带 jpackage 的 JDK 21（如 jbr-21.0.11）"
            )
        }
        val wix = File(wixDir)
        if (!File(wix, "candle.exe").isFile) {
            throw GradleException(
                "WiX 不在 ${wix.absolutePath}（缺 candle.exe）。请自行安装 WiX v3 工具链（要含 candle.exe）；" +
                    "或 -PwhaleWixDir=… / 环境变量 WHALE_WIX_DIR 指定别处"
            )
        }
        outDir.mkdirs()
        logger.lifecycle("jpackage --type exe 打包中（WiX=${wix.absolutePath}）…")
        val proc = ProcessBuilder(
            jpackage.absolutePath, "--type", "exe",
            "--app-image", appImage.absolutePath,
            "--name", "MysticatRoleplay",
            "--app-version", version,
            "--vendor", "Mysticat",
            // 向导页（第 45 轮补，用户实测默认 exe 无任何 UI＝"UAC 之后没下文"）：
            // --win-dir-chooser＝选择安装目录；--win-menu＝开始菜单快捷方式（桌面快捷方式由首启引导页勾选创建）
            "--win-dir-chooser",
            "--win-menu",
            "--dest", outDir.absolutePath
        ).apply {
            // jpackage 只认 PATH 里的 candle/light：在子进程环境里前置 WiX 目录
            environment()["PATH"] = "${wix.absolutePath};${System.getenv("PATH") ?: ""}"
            redirectErrorStream(true)
        }.start()
        val output = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) {
            throw GradleException("jpackage 失败：\n${output.takeLast(3000)}")
        }
        val src = File(outDir, "MysticatRoleplay-$version.exe")
        if (!src.isFile) {
            throw GradleException("jpackage 报告成功但产物不存在：${src.absolutePath}\n${output.takeLast(2000)}")
        }
        val dest = File(distDir, "鲸鱼-Windows-$version-setup.exe")
        src.copyTo(dest, overwrite = true)
        // 安装器不承诺可重复构建（WiX 内嵌时间戳），SHA-256 以**本次**实际产物为准、随发随填清单
        val digest = MessageDigest.getInstance("SHA-256")
        dest.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        logger.lifecycle("安装程序：${dest.absolutePath}")
        logger.lifecycle("大小：${dest.length() / 1024 / 1024} MB")
        logger.lifecycle("SHA-256：$hex")
        dest.resolveSibling(dest.name + ".sha256").writeText("$hex  ${dest.name}\n")
    }
}
