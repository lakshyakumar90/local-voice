param([switch]$InitializeSigning)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
$signingDir = Join-Path $projectRoot 'signing'
$keyFile = Join-Path $signingDir 'localvoice-release.jks'
$credentialFile = Join-Path $signingDir 'release-password.xml'
if ($InitializeSigning) {
    if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath $credentialFile)) {
        throw 'Signing material already exists. Reuse it; do not replace the update identity.'
    }
    New-Item -ItemType Directory -Force $signingDir | Out-Null
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $signingDir -AclObject $acl
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($bytes)
    ConvertTo-SecureString $password -AsPlainText -Force | Export-Clixml -LiteralPath $credentialFile
    $env:LOCALVOICE_STORE_PASSWORD = $password
    & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -keystore $keyFile -storetype JKS -alias localvoice -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Local Voice, OU=Android Release' -storepass:env LOCALVOICE_STORE_PASSWORD -keypass:env LOCALVOICE_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Release key generation failed.' }
}
if (!(Test-Path -LiteralPath $keyFile) -or !(Test-Path -LiteralPath $credentialFile)) {
    throw 'No release key. Run once with -InitializeSigning.'
}
try {
    $secure = Import-Clixml -LiteralPath $credentialFile
    $credential = New-Object System.Net.NetworkCredential('', $secure)
    $env:LOCALVOICE_STORE_PASSWORD = $credential.Password
    $env:LOCALVOICE_KEYSTORE = $keyFile
    Push-Location $projectRoot
    try {
        & .\gradlew.bat '-Plocalvoice.arm64=true' :core:test :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release validation/build failed.' }
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $apk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
        $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
        try {
            $native = @($archive.Entries | Where-Object { $_.FullName -like 'lib/*.so' })
            if ($native.Count -eq 0 -or @($native | Where-Object { $_.FullName -notlike 'lib/arm64-v8a/*.so' }).Count -ne 0) {
                throw 'Release must contain ARM64 native libraries only.'
            }
        } finally { $archive.Dispose() }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:LOCALVOICE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:LOCALVOICE_KEYSTORE -ErrorAction SilentlyContinue
    $password = $null
    $credential = $null
}
