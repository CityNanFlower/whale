# 鲸鱼桌面版 · 系统朗读（一次一句，读完即退出）
#
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File whale-tts.ps1 -Rate <int> -PitchSt <int> -Culture zh-CN
#       朗读文本从 **stdin（UTF-8）** 读进来 —— 走 stdin 而不是命令行参数，
#       是为了彻底绕开 Windows 命令行引号与编码的坑（正文里什么标点都可能出现）。
#
# 为什么"一句话一个进程"：System.Speech 是同步阻塞的 Speak()，进程边界天然就是
# 队列边界 —— 停止朗读 = 杀进程，与 Android 的 TextToSpeech.stop() 语义一致，
# 也不会在应用进程里留下无法回收的 COM/语音线程。

param(
    [int]$Rate = 0,
    [int]$PitchSt = 0,
    [string]$Culture = "zh-CN"
)

[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Stop"

try {
    Add-Type -AssemblyName System.Speech
} catch {
    [Console]::Error.WriteLine("System.Speech 不可用：" + $_.Exception.Message)
    exit 1
}

$text = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($text)) { exit 0 }

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $want = $Culture
    $voice = $synth.GetInstalledVoices() |
        Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -like "$want*" } |
        Select-Object -First 1
    if ($null -ne $voice) { $synth.SelectVoice($voice.VoiceInfo.Name) }
    $synth.Rate = $Rate              # -10..10，0 为正常语速
    $synth.Volume = 100

    if ($PitchSt -eq 0) {
        $synth.Speak($text)
    } else {
        # System.Speech 没有"音高"属性，只能走 SSML 的 prosody；半音（st）是最贴近
        # Android setPitch() 那种"频率倍率"的标度（+12st = 升高一个八度）
        $sign = "+"
        if ($PitchSt -lt 0) { $sign = "" }
        $escaped = [System.Security.SecurityElement]::Escape($text)
        $ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
                "<prosody pitch='$sign$PitchSt" + "st'>$escaped</prosody></speak>"
        $synth.SpeakSsml($ssml)
    }
    exit 0
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    exit 1
} finally {
    $synth.Dispose()
}
