$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$toolsPath = "$sdkPath\build-tools\36.1.0"
$jdkPath = if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\javac.exe")) { "$env:JAVA_HOME\bin" } else { Split-Path (Get-Command javac.exe -ErrorAction Stop).Source }
function Check { if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o aplicativo.' } }
New-Item -ItemType Directory -Force build/classes,dist | Out-Null
& "$toolsPath\aapt2.exe" link -o build/base.apk --manifest AndroidManifest.xml -I "$sdkPath\platforms\android-35\android.jar"
Check
& "$jdkPath\javac.exe" --release 8 -encoding UTF-8 -classpath "$sdkPath\platforms\android-35\android.jar" -d build/classes src/br/local/superlite/MainActivity.java
Check
$classFiles = @(Get-ChildItem build/classes -Recurse -Filter *.class | ForEach-Object FullName)
& "$jdkPath\java.exe" -cp "$toolsPath\lib\d8.jar" com.android.tools.r8.D8 --lib "$sdkPath\platforms\android-35\android.jar" --min-api 26 --output build @classFiles
Check
Copy-Item build/base.apk build/unsigned.apk -Force
& "$jdkPath\jar.exe" uf build/unsigned.apk -C build classes.dex
Check
& "$toolsPath\zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
Check
if (!(Test-Path build/local.jks)) {
  & "$jdkPath\keytool.exe" -genkeypair -keystore build/local.jks -storepass android -keypass android -alias superlite -dname 'CN=SuperLite Local' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
  Check
}
& "$toolsPath\apksigner.bat" sign --ks build/local.jks --ks-pass pass:android --out dist/SuperLite.apk build/aligned.apk
Check
& "$toolsPath\apksigner.bat" verify dist/SuperLite.apk
Check
Write-Output "APK criado em $PSScriptRoot\dist\SuperLite.apk"
} finally {
Pop-Location
}
