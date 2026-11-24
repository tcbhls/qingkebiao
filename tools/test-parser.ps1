$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'env.ps1')

$base     = Join-Path $Root 'app\src'
$out      = Join-Path $Root 'app\build\test'
$d        = Join-Path $base 'main\java\com\qingkebiao\app'
$fixtures = Join-Path $base 'test\fixtures'

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$src   = Join-Path $base 'test\ParserTest.java'
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java  = Join-Path $env:JAVA_HOME 'bin\java.exe'

$files = @(Get-ChildItem -Path $d -Filter *.java | ForEach-Object { $_.FullName })
$files += $src

$rJava = Get-ChildItem -Path (Join-Path $Root 'app\build\gen') -Recurse -Filter R.java -ErrorAction SilentlyContinue | Select-Object -First 1
if ($rJava) { $files += $rJava.FullName }

& $javac -encoding UTF-8 -cp $env:ANDROID_JAR -d $out $files 2>&1 | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { Write-Host 'COMPILE FAILED'; exit 1 }

& $java '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -cp $out ParserTest $fixtures 2>&1 | ForEach-Object { Write-Host $_ }
exit $LASTEXITCODE
