param([string]$Serial = '')
$ErrorActionPreference = 'Stop'
$adbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { $adbPath = (Get-Command adb -ErrorAction Stop).Source }
$targetArgs = @()
if ($Serial) { $targetArgs = @('-s', $Serial) }
$state = & $adbPath @targetArgs get-state 2>&1
if ($LASTEXITCODE -ne 0 -or "$state" -notmatch '^device$') { throw 'Conecte e autorize um aparelho por ADB. Se houver vários, use -Serial.' }
$report = [System.Collections.Generic.List[string]]::new()
$report.Add('Raio: relatório local de abertura e memória')
$report.Add((Get-Date).ToString('o'))
$report.Add('O teste encerra e reabre o navegador; não mede bateria nem o total de todos os processos do WebView.')
& $adbPath @targetArgs shell am force-stop com.raio.browser
if ($LASTEXITCODE -ne 0) { throw 'Falha ao encerrar o aplicativo para o teste.' }
$launch = & $adbPath @targetArgs shell am start -W -n com.raio.browser/.MainActivity 2>&1
if ($LASTEXITCODE -ne 0 -or "$launch" -match 'Error:') { throw 'Instale o Raio antes de executar o teste.' }
$report.AddRange([string[]]$launch)
$memory = & $adbPath @targetArgs shell dumpsys meminfo com.raio.browser 2>&1
$report.AddRange([string[]]$memory)
$outputRoot = Join-Path (Split-Path $PSScriptRoot -Parent) 'dist'
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
$reportPath = Join-Path $outputRoot 'device-report.txt'
$report | Set-Content -LiteralPath $reportPath -Encoding utf8
Write-Host "Relatório salvo: $reportPath"
