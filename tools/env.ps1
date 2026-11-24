$Root = Split-Path -Parent $PSScriptRoot
$TC   = Join-Path $Root '.toolchain'
$Jdk  = Join-Path $TC 'jdk-17'
$Sdk  = Join-Path $TC 'android-sdk'

$env:JAVA_HOME        = $Jdk
$env:ANDROID_HOME     = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:BUILD_TOOLS      = Join-Path $Sdk 'build-tools\34.0.0'
$env:ANDROID_JAR      = Join-Path $Sdk 'platforms\android-34\android.jar'
$env:PLATFORM_TOOLS   = Join-Path $Sdk 'platform-tools'
$env:PATH             = "$Jdk\bin;$Sdk\platform-tools;$Sdk\cmdline-tools\latest\bin;$env:PATH"
