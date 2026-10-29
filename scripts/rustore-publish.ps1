<#
.SYNOPSIS
    Публикует релиз SSH Mobile в RuStore через RuStore Public API.

.DESCRIPTION
    Полный цикл: создание черновика версии, загрузка APK, иконки и скриншотов,
    отправка черновика на модерацию. Тексты карточки берутся из store/rustore.json.

    Токен нигде не хранится в репозитории: он читается из параметра -Token,
    переменной окружения RUSTORE_TOKEN или файла во временной папке.

.PARAMETER Token
    Public-Token из консоли RuStore. Приоритет: параметр -> RUSTORE_TOKEN -> файл.

.PARAMETER TokenFile
    Файл с токеном (первая строка). По умолчанию %TEMP%\rustore-token.txt.

.PARAMETER Config
    JSON с текстами карточки и путями к файлам. По умолчанию store/rustore.json.

.PARAMETER VersionId
    Пропустить создание черновика и работать с уже существующей версией.

.PARAMETER SkipModeration
    Загрузить файлы и остановиться, не отправляя черновик на модерацию.

.PARAMETER DryRun
    Только проверить конфиг и наличие файлов, ничего не отправлять.

.EXAMPLE
    $env:RUSTORE_TOKEN = 'xxxxxxxx'
    .\scripts\rustore-publish.ps1

.EXAMPLE
    .\scripts\rustore-publish.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$Token,
    [string]$TokenFile,
    [string]$KeyId,
    [string]$KeyFile,
    [string]$Config,
    [int]$VersionId = 0,
    [switch]$SkipModeration,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }

$ApiBase = 'https://public-api-m.rustore.ru/public'
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $Config) { $Config = Join-Path $Root 'store\rustore.json' }

function Write-Step([string]$Text) { Write-Host "`n== $Text" -ForegroundColor Cyan }
function Write-Ok([string]$Text) { Write-Host "   OK: $Text" -ForegroundColor Green }

# --- авторизация -------------------------------------------------------------

$TmpDir = Join-Path $env:TEMP 'opencode'
$tokenCandidates = @((Join-Path $TmpDir 'rustore-token.txt'), (Join-Path $env:TEMP 'rustore-token.txt'))
$keyCandidates = @((Join-Path $TmpDir 'rustore-key.txt'), (Join-Path $env:TEMP 'rustore-key.txt'))
if ($TokenFile) { $tokenCandidates = @($TokenFile) + $tokenCandidates }

if (-not $Token) { $Token = $env:RUSTORE_TOKEN }
if (-not $KeyFile) { $KeyFile = $env:RUSTORE_KEY_FILE }
if (-not $KeyId) { $KeyId = $env:RUSTORE_KEY_ID }

# Файл с ключом RuStore содержит Base64 PKCS#8 (начинается с MII...), а не токен.
foreach ($f in ($tokenCandidates + $keyCandidates)) {
    if (-not (Test-Path $f)) { continue }
    $first = ''
    $line = Get-Content $f -TotalCount 1
    if ($line) { $first = ([string]$line).Trim() }
    if (-not $first) { continue }
    if ($first -match '^MII[A-Za-z0-9+/]{40,}={0,2}$') {
        if (-not $KeyFile) { $KeyFile = $f }
    } elseif (-not $Token) {
        $Token = $first
    }
}

if (-not $KeyId) {
    foreach ($f in @((Join-Path $TmpDir 'rustore-keyid.txt'), (Join-Path $env:TEMP 'rustore-keyid.txt'))) {
        if (Test-Path $f) {
            $line = Get-Content $f -TotalCount 1
            if ($line) { $KeyId = ([string]$line).Trim(); break }
        }
    }
}

function Get-RuStoreJwe([string]$KeyId, [string]$KeyFile) {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
        throw 'Для подписи запроса авторизации (SHA512withRSA) нужен Node.js.'
    }
    $helper = Join-Path $PSScriptRoot 'rustore-auth.js'
    $body = & node $helper $KeyId $KeyFile 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Не удалось подписать запрос авторизации: $($body -join ' ')" }
    $body = ($body -join '').Trim()

    $bodyFile = Join-Path $env:TEMP 'rustore-auth-body.json'
    [System.IO.File]::WriteAllText($bodyFile, $body, (New-Object System.Text.UTF8Encoding($false)))

    $raw = & curl.exe -sS -X POST "$ApiBase/auth" -H 'Content-Type: application/json' `
        -H 'Accept: application/json' --data-binary "@$bodyFile" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "curl не смог запросить токен: $raw" }

    $json = $raw | ConvertFrom-Json
    if ($json.code -ne 'OK' -or -not $json.body.jwe) {
        throw "RuStore не выдал токен: $($json.code) $($json.message)"
    }
    return [string]$json.body.jwe
}

if (-not $Token -and $KeyFile -and -not $DryRun) {
    if (-not $KeyId) {
        throw "Ключ RuStore найден ($KeyFile), но не указан keyId. Возьмите keyId в консоли RuStore и передайте -KeyId или сохраните в $TmpDir\rustore-keyid.txt."
    }
    Write-Step 'Авторизация по ключу'
    $Token = Get-RuStoreJwe -KeyId $KeyId -KeyFile $KeyFile
    Write-Ok "JWE-токен получен (keyId $KeyId, действует 15 минут)"
}

if (-not $Token -and -not $DryRun) {
    throw "Нет данных для авторизации. Нужен Public-Token (-Token / RUSTORE_TOKEN) или ключ RuStore с keyId (-KeyFile, -KeyId)."
}

# --- конфиг ------------------------------------------------------------------

Write-Step 'Конфигурация'
if (-not (Test-Path $Config)) { throw "Не найден конфиг: $Config" }
$cfg = Get-Content $Config -Raw -Encoding UTF8 | ConvertFrom-Json
$packageName = 'dev.sshmobile'

foreach ($f in @('appName', 'appType', 'shortDescription', 'fullDescription')) {
    if (-not $cfg.$f) { throw "В конфиге не заполнено обязательное поле '$f'." }
}
if ($cfg.developerContacts.email -match '^TODO@') {
    throw "Заполните developerContacts.email в $Config — RuStore не примет карточку без e-mail поддержки."
}
if ($cfg.shortDescription.Length -gt 80) { throw "shortDescription длиннее 80 символов ($($cfg.shortDescription.Length))." }
if ($cfg.fullDescription.Length -gt 4000) { throw "fullDescription длиннее 4000 символов ($($cfg.fullDescription.Length))." }
if ($cfg.whatsNew -and $cfg.whatsNew.Length -gt 5000) { throw "whatsNew длиннее 5000 символов." }
if ($cfg.moderInfo -and $cfg.moderInfo.Length -gt 180) { throw "moderInfo длиннее 180 символов ($($cfg.moderInfo.Length))." }

$files = @{ apk = $cfg.assets.apk; icon = $cfg.assets.icon }
foreach ($s in $cfg.assets.screenshots) { $files["shot_$($files.Count)"] = $s }
foreach ($k in $files.Keys) {
    $path = Join-Path $Root $files[$k]
    if (-not (Test-Path $path)) { throw "Не найден файл для '$k': $path" }
}
Write-Ok "$($cfg.appName), категория $($cfg.categories -join ', '), возраст $($cfg.ageLegal)"
Write-Ok "APK: $($cfg.assets.apk), иконка + $($cfg.assets.screenshots.Count) скриншота"
Write-Ok "Черновик: короткое описание $($cfg.shortDescription.Length)/80, полное $($cfg.fullDescription.Length)/4000, moderInfo $($cfg.moderInfo.Length)/180"

# --- HTTP --------------------------------------------------------------------

function Invoke-RuStore {
    param([string]$Method, [string]$Path, [string]$BodyFile, [string[]]$Form)

    $url = "$ApiBase$Path"
    $curlArgs = @('-sS', '-X', $Method, $url, '-H', "Public-Token: $Token", '-H', 'Accept: application/json')
    if ($BodyFile) { $curlArgs += @('-H', 'Content-Type: application/json', '--data-binary', "@$BodyFile") }
    foreach ($item in $Form) { $curlArgs += @('-F', $item) }

    if ($DryRun) {
        Write-Host "   [dry-run] $Method $url"
        foreach ($item in $Form) { Write-Host "   [dry-run] form $item" }
        return $null
    }

    $raw = & curl.exe @curlArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "curl завершился с кодом $LASTEXITCODE : $raw" }

    try { $json = $raw | ConvertFrom-Json } catch { throw "Не JSON в ответе: $raw" }
    $code = $null
    if ($json.PSObject.Properties['code']) { $code = $json.code }
    if ($code -and $code -ne 'OK') {
        $msg = ''
        if ($json.PSObject.Properties['message']) { $msg = $json.message }
        throw ("RuStore вернул $code : $msg`n$raw")
    }
    return $json
}

# --- черновик ----------------------------------------------------------------

if ($VersionId -eq 0) {
    Write-Step 'Создание черновика версии'
    $body = [ordered]@{
        appName          = $cfg.appName
        appType          = $cfg.appType
        categories       = @($cfg.categories)
        ageLegal         = $cfg.ageLegal
        shortDescription = $cfg.shortDescription
        fullDescription  = $cfg.fullDescription
        whatsNew         = $cfg.whatsNew
        moderInfo        = $cfg.moderInfo
        publishType      = $cfg.publishType
        developerContacts = [ordered]@{
            email   = $cfg.developerContacts.email
            website = $cfg.developerContacts.website
        }
    }
    $bodyFile = Join-Path $env:TEMP 'rustore-draft.json'
    [System.IO.File]::WriteAllText($bodyFile, ($body | ConvertTo-Json -Depth 8), (New-Object System.Text.UTF8Encoding($false)))

    $r = Invoke-RuStore -Method POST -Path "/v1/application/$packageName/version" -BodyFile $bodyFile
    if ($r) {
        $VersionId = [int]$r.content.versionId
        Write-Ok "versionId = $VersionId"
    }
} else {
    Write-Host "Используется существующая версия $VersionId" -ForegroundColor Yellow
}

if ($DryRun) {
    Write-Step 'Dry run завершён, запросы не отправлялись'
    return
}

# --- файлы -------------------------------------------------------------------

Write-Step 'Загрузка APK'
$r = Invoke-RuStore -Method POST -Path "/v1/application/$packageName/version/$VersionId/apk?isMainApk=true&servicesType=Unknown" `
    -Form @("file=@$(Join-Path $Root $cfg.assets.apk)")
Write-Ok 'APK загружен'

Write-Step 'Загрузка иконки'
$r = Invoke-RuStore -Method POST -Path "/v1/application/$packageName/version/$VersionId/image/icon" `
    -Form @("file=@$(Join-Path $Root $cfg.assets.icon)")
Write-Ok 'Иконка загружена'

Write-Step 'Загрузка скриншотов'
$ordinal = 1
foreach ($shot in $cfg.assets.screenshots) {
    $r = Invoke-RuStore -Method POST -Path "/v2/application/$packageName/version/$VersionId/image/screenshot/PORTRAIT/$ordinal/SCREENSHOT" `
        -Form @("file=@$(Join-Path $Root $shot)")
    Write-Ok "скриншот $ordinal — $(Split-Path $shot -Leaf)"
    $ordinal++
}

if ($SkipModeration) {
    Write-Step "Готово: версия $VersionId загружена, на модерацию не отправлялась"
    return
}

# --- модерация ---------------------------------------------------------------

Write-Step 'Отправка на модерацию'
$r = Invoke-RuStore -Method POST -Path "/v1/application/$packageName/version/$VersionId/commit?priorityUpdate=0"
Write-Ok 'Черновик отправлен на модерацию'

Write-Host @"

Дальше:
  • статус модерации — в консоли RuStore: https://console.rustore.ru/apps
  • при замечаниях выпустите новую версию с увеличенным versionCode,
    а не перезаписывайте APK
"@ -ForegroundColor Cyan
