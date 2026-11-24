param([switch]$Clean)
$ErrorActionPreference = 'Stop'

$Root    = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'env.ps1')

$Proj    = Join-Path $Root 'app'
$Out     = Join-Path $Proj 'build'
$Src     = Join-Path $Proj 'src\main'
$Res     = Join-Path $Src 'res'
$Manif   = Join-Path $Src 'AndroidManifest.xml'
$Java    = Join-Path $Src 'java'
$Gen     = Join-Path $Out 'gen'
$Classes = Join-Path $Out 'classes'
$Dex     = Join-Path $Out 'dex'
$BT      = $env:BUILD_TOOLS
$AJ      = $env:ANDROID_JAR
$JdkBin  = Join-Path $env:JAVA_HOME 'bin'

function Log($m) { Write-Host ("[{0}] {1}" -f (Get-Date -Format 'HH:mm:ss'), $m) }

if ($Clean -and (Test-Path $Out)) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Out, $Gen, $Classes, $Dex | Out-Null

foreach ($f in @("$BT\aapt2.exe", "$BT\d8.bat", "$BT\apksigner.bat", "$BT\zipalign.exe", $AJ)) {
    if (-not (Test-Path $f)) { throw "missing build component: $f (run tools\bootstrap-toolchain.ps1 first)" }
}

Log 'aapt2 compile'
$resZip = Join-Path $Out 'res.zip'
& "$BT\aapt2.exe" compile --dir $Res -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Log 'aapt2 link'
$baseApk = Join-Path $Out 'base.apk'
& "$BT\aapt2.exe" link -o $baseApk -I $AJ --manifest $Manif -R $resZip --java $Gen --auto-add-overlay --min-sdk-version 26 --target-sdk-version 34
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Log 'javac'
$sources = @()
$sources += (Get-ChildItem -Path $Java -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += (Get-ChildItem -Path $Gen  -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($sources.Count -eq 0) { throw 'no java sources' }
$argFile = Join-Path $Out 'sources.txt'
$sources | ForEach-Object { '"' + ($_ -replace '\\', '\\') + '"' } | Set-Content -Path $argFile -Encoding ASCII
& "$JdkBin\javac.exe" --release 11 -encoding UTF-8 -nowarn -classpath $AJ -d $Classes "@$argFile"
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
Log ("classes: " + (Get-ChildItem -Path $Classes -Recurse -Filter *.class).Count)

Log 'jar + d8'
$classesJar = Join-Path $Out 'classes.jar'
& "$JdkBin\jar.exe" cf $classesJar -C $Classes .
& "$BT\d8.bat" --release --lib $AJ --min-api 26 --output $Dex $classesJar
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
$dexFile = Join-Path $Dex 'classes.dex'
if (-not (Test-Path $dexFile)) { throw 'no classes.dex produced' }
Log ("classes.dex = " + [math]::Round((Get-Item $dexFile).Length / 1KB, 1) + " KB")

Log 'packaging'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$unsigned = Join-Path $Out 'unsigned.apk'
Copy-Item $baseApk $unsigned -Force
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
try {
    $old = $zip.GetEntry('classes.dex')
    if ($old) { $old.Delete() }
    $entry = $zip.CreateEntry('classes.dex', [System.IO.Compression.CompressionLevel]::Optimal)
    $es = $entry.Open()
    $bytes = [IO.File]::ReadAllBytes($dexFile)
    $es.Write($bytes, 0, $bytes.Length)
    $es.Close()
} finally { $zip.Dispose() }

Log 'zipalign'
$aligned = Join-Path $Out 'aligned.apk'
& "$BT\zipalign.exe" -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

$ks = Join-Path $Proj 'keystore.jks'
if (-not (Test-Path $ks)) {
    Log 'keytool genkey'
    & "$JdkBin\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias qkb -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=QingKeBiao, OU=OpenSource, O=QingKeBiao, L=Beijing, ST=Beijing, C=CN"
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}

Log 'apksigner sign'
$finalApk = Join-Path $Out 'qingkebiao-signed.apk'
& "$BT\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --ks-key-alias qkb --min-sdk-version 26 --out $finalApk $aligned
if ($LASTEXITCODE -ne 0) { throw 'apksigner sign failed' }

Log 'apksigner verify'
& "$BT\apksigner.bat" verify --min-sdk-version 26 $finalApk
if ($LASTEXITCODE -ne 0) { throw 'verify failed' }

$dist = Join-Path $Root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
$distApk = Join-Path $dist 'qingkebiao-v3.1.apk'
Copy-Item $finalApk $distApk -Force

Log ("DONE -> " + $distApk + " (" + [math]::Round((Get-Item $finalApk).Length / 1KB, 1) + " KB)")
