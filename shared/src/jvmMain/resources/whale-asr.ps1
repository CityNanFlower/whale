# 鲸鱼桌面版 · 系统语音识别（离线识别一段 WAV，Windows 自带识别器，无需 API Key）
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File whale-asr.ps1 -Wav <文件> [-Culture zh-CN]
#   stdout：R|<识别文本>   识别到一句
#   stderr：E|<原因>       没装识别语言包 / 音频读不了 / 没识别到内容
#   退出码：0=识别到；2=没识别到；1=环境或音频不可用
#
# 为什么是"录完再离线识别"，而不是"起个进程边听边识别"（第 24 轮改）：
# 老版本让脚本 `[Console]::In.ReadLine()` 等父进程关 stdin 当停止信号，实测**在应用里不成立**——
# 这个子进程的 stdin 一开始就是 EOF，脚本启动约 1.8 秒后就自己走到 RecognizeAsyncStop + 等 1.2 秒，
# 然后以"没结果"退出；应用那侧收到的是 ERROR_NO_MATCH，用户看到的就是
# **"按住还没说话，三秒就自动结束"**（实测 2.96 秒，与用户反馈一致）。
# 离线识别没有"停止信号"这回事：进程起来 → 认一段文件 → 退出，全程确定性，
# 也与 Android 侧口径一致（那边同样是"录一段再交给识别引擎"）。
#
# 对应 Android 的 SpeechRecognizer：这条是"无需配钥匙的兜底识别"，
# 三家供应商识别（硅基流动 / 阿里 / 火山）走纯 HTTP，在主链路上。

param(
    [Parameter(Mandatory = $true)][string]$Wav,
    [string]$Culture = "zh-CN"
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Wav)) {
    [Console]::Error.WriteLine("E|音频文件不存在：" + $Wav)
    exit 1
}
if ((Get-Item -LiteralPath $Wav).Length -le 64) {
    [Console]::Error.WriteLine("E|音频是空的（没录到声音）")
    exit 2
}

try {
    Add-Type -AssemblyName System.Speech
} catch {
    [Console]::Error.WriteLine("E|System.Speech 不可用：" + $_.Exception.Message)
    exit 1
}

$rec = $null
try {
    # 想要的语种没装识别器时退到"本机任意一个可用识别器"（例如只装了 en-US 的机器上，
    # 英文说话仍能用），而不是直接报错——识别语种与朗读语种是两回事。
    try {
        $rec = New-Object System.Speech.Recognition.SpeechRecognitionEngine($Culture)
    } catch {
        $fallback = [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers() |
            Select-Object -First 1
        if ($null -eq $fallback) { throw }
        $rec = New-Object System.Speech.Recognition.SpeechRecognitionEngine($fallback.Culture)
    }
    $rec.LoadGrammar((New-Object System.Speech.Recognition.DictationGrammar))
    $rec.SetInputToWaveFile($Wav)

    # 同步识别整段音频：返回 $null = 整段都没听出词
    $result = $rec.Recognize()
    if ($null -eq $result -or [string]::IsNullOrWhiteSpace($result.Text)) {
        [Console]::Error.WriteLine("E|没有识别到内容")
        exit 2
    }
    [Console]::Out.WriteLine("R|" + $result.Text)
    [Console]::Out.Flush()
    exit 0
} catch {
    [Console]::Error.WriteLine("E|" + $_.Exception.Message)
    exit 1
} finally {
    if ($null -ne $rec) {
        try { $rec.Dispose() } catch { }
    }
}
