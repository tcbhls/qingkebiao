# Build toolchain bootstrap: JDK 17 + Android cmdline-tools (no Android Studio needed)
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$Root = Split-Path -Parent $PSScriptRoot
$TC   = Join-Path $Root '.toolchain'
$Dl   = Join-Path $TC 'downloads'
New-Item -ItemType Directory -Force -Path $TC, $Dl | Out-Null

function Log($m) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) }

# ---------- JDK 17 ----------
$JdkHome = Join-Path $TC 'jdk-17'
if (-not (Test-Path (Join-Path $JdkHome 'bin\java.exe'))) {
    $jdkZip = Join-Path $Dl 'temurin17.zip'
    if (-not (Test-Path $jdkZip)) {
        Log 'downloading JDK 17 (Temurin)'
        $u = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'
        Invoke-WebRequest -Uri $u -OutFile $jdkZip -TimeoutSec 1800 -UseBasicParsing
    }
    $tmp = Join-Path $TC 'jdk-extract'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $jdkZip -DestinationPath $tmp -Force
    $inner = Get-ChildItem $tmp -Directory | Select-Object -First 1
    if (Test-Path $JdkHome) { Remove-Item $JdkHome -Recurse -Force }
    Move-Item $inner.FullName $JdkHome
    Remove-Item $tmp -Recurse -Force
}
Log "JAVA_HOME=$JdkHome"

# ---------- Android command line tools ----------
$Sdk = Join-Path $TC 'android-sdk'
$CmdTools = Join-Path $Sdk 'cmdline-tools\latest'
$SdkManager = Join-Path $CmdTools 'bin\sdkmanager.bat'
if (-not (Test-Path $SdkManager)) {
    $ctZip = Join-Path $Dl 'cmdline-tools.zip'
    if (-not (Test-Path $ctZip)) {
        Log 'downloading Android cmdline-tools'
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $ctZip -TimeoutSec 1800 -UseBasicParsing
    }
    $tmp = Join-Path $TC 'ct-extract'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $ctZip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path (Split-Path $CmdTools) | Out-Null
    if (Test-Path $CmdTools) { Remove-Item $CmdTools -Recurse -Force }
    Move-Item (Join-Path $tmp 'cmdline-tools') $CmdTools
    Remove-Item $tmp -Recurse -Force
}

$env:JAVA_HOME = $JdkHome
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:PATH = "$JdkHome\bin;$Sdk\platform-tools;$CmdTools\bin;$env:PATH"

Log 'accepting licenses'
$yes = ("y`n" * 80)
$yes | & $SdkManager --sdk_root=$Sdk --licenses 2>&1 | Out-Null

Log 'installing platform-tools, platforms;android-34, build-tools;34.0.0'
& $SdkManager --sdk_root=$Sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 |
    ForEach-Object { Write-Host "  $_" }

Log 'DONE'
