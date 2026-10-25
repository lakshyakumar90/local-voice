param([switch]$Release)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath 'vendor\whisper.cpp\include\whisper.h')) { & "$PSScriptRoot\bootstrap-native.ps1" }
    $candidate='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
    if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath "$candidate\bin\javac.exe")) { $env:JAVA_HOME=$candidate }
    if ($env:JAVA_HOME) { $env:PATH="$env:JAVA_HOME\bin;"+$env:PATH }
    $target=if($Release) {':app:assembleRelease'} else {':app:assembleDebug'}
    & .\gradlew.bat $target :core:test :app:testDebugUnitTest :app:lintDebug --console=plain
    if($LASTEXITCODE -ne 0) {throw 'Android build or tests failed'}
} finally {Pop-Location}
