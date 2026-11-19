$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Configure JAVA_HOME com um JDK 17 ou 21.' }
$gradleVersion = '8.9'
$gradleRoot = Join-Path $PSScriptRoot '.gradle-local'
$gradleCommand = Join-Path $gradleRoot "gradle-$gradleVersion\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradleCommand)) {
    New-Item -ItemType Directory -Force -Path $gradleRoot | Out-Null
    $archive = Join-Path $gradleRoot 'gradle.zip'
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip" -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $gradleRoot -Force
    Remove-Item -LiteralPath $archive
}
& $gradleCommand --no-daemon assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o APK.' }
New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot 'dist') | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk') -Destination (Join-Path $PSScriptRoot 'dist\Raio.apk') -Force
Write-Host 'APK gerado: dist\Raio.apk'
