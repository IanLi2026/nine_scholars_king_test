param(
    [string]$AdbPath = 'adb',
    [string]$Serial,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\diagnostics')
)

$ErrorActionPreference = 'Stop'
$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
$deviceState = & $AdbPath @deviceArgs get-state 2>&1
if ($LASTEXITCODE -ne 0 -or $deviceState -notcontains 'device') {
    throw 'Connect and authorize one Android device, or specify -Serial for the intended device.'
}
$reportDirectory = Join-Path $OutputDirectory (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
& $AdbPath @deviceArgs shell getprop ro.product.model |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'device-model.txt') -Encoding utf8
& $AdbPath @deviceArgs shell getprop ro.build.version.release |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'android-version.txt') -Encoding utf8
& $AdbPath @deviceArgs shell getprop ro.product.cpu.abilist |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'cpu-abis.txt') -Encoding utf8
& $AdbPath @deviceArgs shell dumpsys webviewupdate |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'webview-provider.txt') -Encoding utf8
& $AdbPath @deviceArgs shell dumpsys package com.example.nine_scholars_king_test |
    Select-String 'versionCode=|versionName=|minSdk=|targetSdk=' |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'app-version.txt') -Encoding utf8
& $AdbPath @deviceArgs logcat -d -b crash -v threadtime |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'crash-log.txt') -Encoding utf8
& $AdbPath @deviceArgs logcat -d -v threadtime 'ExamShell:E' '*:S' |
    Set-Content -LiteralPath (Join-Path $reportDirectory 'webview-startup-log.txt') -Encoding utf8
Write-Output "Diagnostics saved to $reportDirectory"
