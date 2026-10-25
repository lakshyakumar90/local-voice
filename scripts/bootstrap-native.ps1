$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$nativePath = Join-Path $projectRoot 'vendor\whisper.cpp'
$revision = '231638a0717b7822784297e5980965ffcec05810'
if (-not (Test-Path -LiteralPath (Join-Path $nativePath '.git'))) {
    git clone --no-checkout https://github.com/ggml-org/whisper.cpp.git $nativePath
    if ($LASTEXITCODE -ne 0) { throw 'Could not clone whisper.cpp' }
}
git -C $nativePath fetch --depth 1 origin $revision
if ($LASTEXITCODE -ne 0) { throw 'Could not fetch pinned whisper.cpp revision' }
git -C $nativePath checkout --detach $revision
if ($LASTEXITCODE -ne 0) { throw 'Could not select pinned whisper.cpp revision' }
