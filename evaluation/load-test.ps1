# 对本机 AI ERP 做短时压测。先登录一次，再并发打列表和搜索接口。
param(
    [string]$BaseUrl = "http://127.0.0.1:8088",
    [string]$Username = "admin",
    [string]$Password = "QHerp@23",
    [int]$Workers = 20,
    [int]$Seconds = 20
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$resultDir = Join-Path $root "results"
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null

$loginBody = @{ username = $Username; password = $Password; code = ""; uuid = "" } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$BaseUrl/api/sys-api/login" -Method Post -Body $loginBody -ContentType "application/json" -TimeoutSec 20
if ($login.code -ne 200 -or -not $login.token) { throw "登录失败: $($login.msg)" }
$token = $login.token
Write-Host "登录成功"

$targets = @(
    @{ name = "goods-list"; method = "GET"; path = "/api/erp-api/goods/list?pageNum=1&pageSize=20" },
    @{ name = "sku-search"; method = "GET"; path = "/api/erp-api/goods/searchSku?keyword=DJ" },
    @{ name = "order-list"; method = "GET"; path = "/api/erp-api/order/list?pageNum=1&pageSize=20" },
    @{ name = "shop-list"; method = "GET"; path = "/api/oms-api/shop/list" },
    @{ name = "member-list"; method = "GET"; path = "/api/oms-api/shop/member/list?pageNum=1&pageSize=20" },
    @{ name = "inventory-page"; method = "GET"; path = "/api/erp-api/goods/sku_list?pageNum=1&pageSize=20" }
)

$script = {
    param($BaseUrl, $Token, $Targets, $Seconds)
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    $rows = New-Object System.Collections.Generic.List[object]
    while ([DateTime]::UtcNow -lt $deadline) {
        foreach ($target in $Targets) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $code = 0
            $ok = $false
            try {
                $req = [System.Net.HttpWebRequest]::Create($BaseUrl + $target.path)
                $req.Method = $target.method
                $req.Timeout = 15000
                $req.Headers.Add("Authorization", "Bearer $Token")
                $resp = $req.GetResponse()
                $code = [int]$resp.StatusCode
                $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
                $body = $reader.ReadToEnd()
                $reader.Close()
                $resp.Close()
                $ok = $code -eq 200 -and $body -match '"code"\s*:\s*200'
            } catch {
                $code = -1
                $ok = $false
            }
            $sw.Stop()
            $rows.Add([pscustomobject]@{ name = $target.name; ms = $sw.ElapsedMilliseconds; ok = $ok; code = $code })
        }
    }
    return $rows
}

$pool = [runspacefactory]::CreateRunspacePool(1, $Workers)
$pool.Open()
$jobs = @()
for ($i = 0; $i -lt $Workers; $i++) {
    $ps = [powershell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($script).AddArgument($BaseUrl).AddArgument($token).AddArgument($targets).AddArgument($Seconds)
    $jobs += [pscustomobject]@{ ps = $ps; handle = $ps.BeginInvoke() }
}
$all = @()
foreach ($job in $jobs) {
    $all += @($job.ps.EndInvoke($job.handle))
    $job.ps.Dispose()
}
$pool.Close()

function Summarize($name, $samples) {
    $sorted = @($samples | Sort-Object ms)
    if ($sorted.Count -eq 0) { return $null }
    $ok = @($sorted | Where-Object { $_.ok }).Count
    $p50 = $sorted[[Math]::Floor(($sorted.Count - 1) * 0.50)].ms
    $p95 = $sorted[[Math]::Floor(($sorted.Count - 1) * 0.95)].ms
    $max = $sorted[-1].ms
    $avg = [Math]::Round((($sorted | Measure-Object ms -Average).Average), 1)
    [pscustomobject]@{
        name = $name
        requests = $sorted.Count
        success = $ok
        errors = $sorted.Count - $ok
        avgMs = $avg
        p50Ms = $p50
        p95Ms = $p95
        maxMs = $max
    }
}

$report = @()
foreach ($target in $targets) {
    $report += Summarize $target.name @($all | Where-Object { $_.name -eq $target.name })
}

Write-Host "销售分析单次调用"
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$analysisOk = $false
try {
    $headers = @{ Authorization = "Bearer $token" }
    $analysis = Invoke-RestMethod -Uri "$BaseUrl/api/erp-api/ai/analysis/sales" -Method Post -Headers $headers -ContentType "application/json" -TimeoutSec 30
    $analysisOk = $analysis.code -eq 200
} catch { $analysisOk = $false }
$sw.Stop()
$report += [pscustomobject]@{ name = "sales-analysis-once"; requests = 1; success = [int]$analysisOk; errors = [int](-not $analysisOk); avgMs = $sw.ElapsedMilliseconds; p50Ms = $sw.ElapsedMilliseconds; p95Ms = $sw.ElapsedMilliseconds; maxMs = $sw.ElapsedMilliseconds }

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$out = Join-Path $resultDir "load-test-$stamp.json"
$payload = [pscustomobject]@{
    at = (Get-Date).ToString("s")
    baseUrl = $BaseUrl
    workers = $Workers
    seconds = $Seconds
    endpoints = $report
}
$payload | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 $out
$report | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "结果 $out"
