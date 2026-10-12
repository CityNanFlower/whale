# 鲸鱼桌面版 · 系统能力探测（对应 M1 探针第 8/9 条）
#
# 输出格式（每行一条，| 分隔）：
#   SPEAK|<culture>|<voiceName>     已安装且启用的朗读音色
#   RECOG|<culture>|<id>            已安装的系统语音识别器
#   ERRSPEAK|... / ERRRECOG|...     该子系统不可用及原因
#   END                             正常跑完
#
# 为什么要它：JDK 里没有任何 TTS/ASR，Windows 上唯一"免费离线"的路子是 .NET 的
# System.Speech（Windows PowerShell 5.1 自带）。这里只做"有没有"的枚举，不发声、不收音。

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$ErrorActionPreference = "Continue"

try {
    Add-Type -AssemblyName System.Speech
} catch {
    [Console]::Out.WriteLine("ERRSPEAK|Add-Type System.Speech 失败：" + $_.Exception.Message)
    [Console]::Out.WriteLine("END")
    exit 0
}

try {
    $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
    foreach ($v in $synth.GetInstalledVoices()) {
        if ($v.Enabled) {
            [Console]::Out.WriteLine("SPEAK|" + $v.VoiceInfo.Culture.Name + "|" + $v.VoiceInfo.Name)
        }
    }
    $synth.Dispose()
} catch {
    [Console]::Out.WriteLine("ERRSPEAK|" + $_.Exception.Message)
}

try {
    foreach ($r in [System.Speech.Recognition.SpeechRecognitionEngine]::InstalledRecognizers()) {
        [Console]::Out.WriteLine("RECOG|" + $r.Culture.Name + "|" + $r.Id)
    }
} catch {
    [Console]::Out.WriteLine("ERRRECOG|" + $_.Exception.Message)
}

[Console]::Out.WriteLine("END")
