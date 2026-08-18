$ErrorActionPreference = 'Stop'

$repoOwner = 'QwQBiG'
$repoName = 'XianGeCe'
$tag = 'v0.0.1'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $repoRoot 'artifacts\xiangece.apk'
$manifestPath = Join-Path $repoRoot 'artifacts\update.json'

if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw "找不到 APK：$apkPath" }
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw "找不到更新清单：$manifestPath" }

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$hash = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($manifest.versionName -ne '0.0.1' -or [int]$manifest.versionCode -ne 2) {
    throw 'artifacts\update.json 不是当前 v0.0.1 测试发布清单。'
}
if ($manifest.sha256.ToLowerInvariant() -ne $hash) {
    throw 'update.json 中的 SHA-256 与 APK 不一致，已停止上传。'
}

$secureToken = Read-Host '请粘贴新的 GitHub Token（输入不会回显）' -AsSecureString
$token = $null
$tokenPtr = [IntPtr]::Zero
try {
    $tokenPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
    $token = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($tokenPtr)
    $headers = @{
        Authorization = "Bearer $token"
        Accept = 'application/vnd.github+json'
        'X-GitHub-Api-Version' = '2022-11-28'
        'User-Agent' = 'XiangeceReleasePublisher'
    }

    $apiBase = "https://api.github.com/repos/$repoOwner/$repoName"
    $me = Invoke-RestMethod -Method Get -Uri 'https://api.github.com/user' -Headers $headers
    Write-Host "Token 已验证：$($me.login)"

    $release = Invoke-RestMethod -Method Get -Uri "$apiBase/releases/tags/$tag" -Headers $headers
    if ($release.tag_name -ne $tag) { throw "找不到目标 Release：$tag" }
    Write-Host "目标 Release：$($release.name)（$($release.id)）"

    $assets = @((Invoke-RestMethod -Method Get -Uri "$apiBase/releases/$($release.id)/assets?per_page=100" -Headers $headers))
    $desired = @('update.json', 'xiangece.apk')

    foreach ($asset in $assets | Where-Object { $_.name -in $desired }) {
        Write-Host "替换旧资源：$($asset.name)"
        Invoke-RestMethod -Method Delete -Uri "$apiBase/releases/assets/$($asset.id)" -Headers $headers | Out-Null
    }

    $uploadBase = "https://uploads.github.com/repos/$repoOwner/$repoName/releases/$($release.id)/assets"
    foreach ($file in @($manifestPath, $apkPath)) {
        $name = Split-Path -Leaf $file
        Write-Host "上传：$name"
        Invoke-RestMethod -Method Post -Uri "$uploadBase?name=$([Uri]::EscapeDataString($name))" `
            -Headers $headers -ContentType 'application/octet-stream' -InFile $file | Out-Null
    }

    $after = @((Invoke-RestMethod -Method Get -Uri "$apiBase/releases/$($release.id)/assets?per_page=100" -Headers $headers))
    foreach ($name in $desired) {
        $asset = $after | Where-Object { $_.name -eq $name } | Select-Object -First 1
        if (-not $asset) { throw "上传后未找到资源：$name" }
        Write-Host "已确认：$name（$($asset.size) bytes）"
    }

    foreach ($asset in $after | Where-Object { $_.name -notin $desired }) {
        Write-Host "清理旧资源：$($asset.name)"
        Invoke-RestMethod -Method Delete -Uri "$apiBase/releases/assets/$($asset.id)" -Headers $headers | Out-Null
    }
    Write-Host '完成：v0.0.1 Release 已替换为当前 APK 和 update.json。' -ForegroundColor Green
}
finally {
    if ($tokenPtr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tokenPtr) }
    $token = $null
    $secureToken = $null
}
