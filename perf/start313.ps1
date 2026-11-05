param(
    [Parameter(Mandatory)][string]$BaseRoot,
    [Parameter(Mandatory)][string]$JavaHome
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BaseRoot = (Resolve-Path -LiteralPath $BaseRoot).Path
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $javaExecutable)) { throw 'Java 25 경로를 확인하세요' }
if ($BaseRoot -eq $repoRoot) { throw '부모 SHA의 별도 체크아웃이 필요합니다' }
$expectedBase = & git -C $repoRoot rev-parse '0137347^{commit}'
if ($LASTEXITCODE -ne 0) { throw '측정 기준 커밋을 찾을 수 없습니다' }
$actualBase = & git -C $BaseRoot rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $actualBase -ne $expectedBase) { throw 'BaseRoot는 측정 기준 0137347 체크아웃이어야 합니다' }
foreach ($checkout in @($BaseRoot, $repoRoot)) {
    $changes = & git -C $checkout status --porcelain -- src/main
    if ($LASTEXITCODE -ne 0 -or $changes) { throw "생산 코드에 미커밋 변경이 있습니다: $checkout" }
}
foreach ($port in @(18180, 18181, 18182, 18183, 18280, 18281, 18282, 18283)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "측정 포트 $port 사용 중입니다. 기존 하네스를 먼저 종료하세요"
    }
}
$overlay = @('build.gradle', 'src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessApplication.java',
    'src/mysqlIntegrationTest/java/eightjbbm/perf/PerfHarnessConfiguration.java',
    'src/test/java/eightjbbm/keepgo/integration/stub/AiServerStub.java')
foreach ($relative in $overlay) {
    Copy-Item -LiteralPath (Join-Path $repoRoot $relative) -Destination (Join-Path $BaseRoot $relative)
}
$environmentNames = @('JAVA_HOME', 'AI_STUB_PORT', 'AI_STUB_DELAY_MS', 'DB_HOST', 'DB_PORT', 'DB_NAME',
    'DB_USERNAME', 'DB_PASSWORD', 'PERF_RESET', 'PERF_MEMBERS', 'PERF_VIDEO_COUNT', 'PERF_VIDEO_PAGE_SIZE',
    'PERF_YOUTUBE_DELAY_MS', 'PERF_MAX_CONCURRENT_SYNCS', 'PERF_DB_POOL_SIZE', 'PERF_RECENT_RESULT_WINDOW',
    'PERF_SERVER_PORT', 'PERF_MANAGEMENT_PORT', 'PERF_ADMIN_PORT', 'PERF_TOKENS_FILE', 'PERF_KEY_DIR',
    'AI_SERVER_API_ADDRESS', 'KAKAO_REST_API_KEY')
$originalEnvironment = @{}
foreach ($name in $environmentNames) { $originalEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
$env:JAVA_HOME = $JavaHome
$runtimeRoot = Join-Path $repoRoot 'build/perf/313'
New-Item -ItemType Directory -Force -Path $runtimeRoot | Out-Null
foreach ($checkout in @($BaseRoot, $repoRoot)) {
    Push-Location -LiteralPath $checkout
    try {
        New-Item -ItemType Directory -Force -Path (Join-Path $checkout 'build/perf') | Out-Null
        $compileLog = Join-Path $checkout 'build/perf/313-compile.log'
        $previousPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & ./gradlew.bat perfRuntimeClasspath *> $compileLog
        } finally { $ErrorActionPreference = $previousPreference }
        if ($LASTEXITCODE -ne 0) { throw "컴파일 실패: $compileLog" }
    } finally { Pop-Location }
}
& docker compose -f (Join-Path $PSScriptRoot 'compose.yaml') up -d --wait mysql
if ($LASTEXITCODE -ne 0) { throw '측정 DB 기동 실패' }
$processes = @()
$ready = $false
try {
    foreach ($version in @('base', 'candidate')) {
        $checkout = if ($version -eq 'base') { $BaseRoot } else { $repoRoot }
        $port = if ($version -eq 'base') { 18100 } else { 18200 }
        $directory = Join-Path $runtimeRoot $version
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $classpath = Get-Content -Raw -LiteralPath (Join-Path $checkout 'build/perf/runtime-classpath.txt')
        $env:AI_STUB_PORT = [string]($port + 80)
        $env:AI_STUB_DELAY_MS = '1000'
        $env:DB_HOST = '127.0.0.1'
        $env:DB_PORT = '13316'
        $env:DB_NAME = "keepgo_perf_313_$version"
        $env:DB_USERNAME = 'root'
        $env:DB_PASSWORD = 'perf-only'
        $env:PERF_RESET = 'true'
        $env:PERF_MEMBERS = '40'
        $env:PERF_VIDEO_COUNT = '30'
        $env:PERF_VIDEO_PAGE_SIZE = '50'
        $env:PERF_YOUTUBE_DELAY_MS = '200'
        $env:PERF_MAX_CONCURRENT_SYNCS = '20'
        $env:PERF_DB_POOL_SIZE = '50'
        $env:PERF_RECENT_RESULT_WINDOW = '1s'
        $env:PERF_SERVER_PORT = [string]($port + 81)
        $env:PERF_MANAGEMENT_PORT = [string]($port + 82)
        $env:PERF_ADMIN_PORT = [string]($port + 83)
        $env:PERF_TOKENS_FILE = Join-Path $directory 'tokens.json'
        $env:PERF_KEY_DIR = Join-Path $directory 'keys'
        $env:AI_SERVER_API_ADDRESS = "http://127.0.0.1:$($port + 80)"
        $env:KAKAO_REST_API_KEY = 'perf'
        foreach ($entry in @(@('stub', 'eightjbbm.keepgo.integration.stub.AiServerStub'),
                @('app', 'eightjbbm.perf.PerfHarnessApplication'))) {
            $argumentFile = Join-Path $directory "$($entry[0])-args.txt"
            $argumentText = "-Xmx512m`n-cp`n`"$($classpath.Trim().Replace('\', '/'))`"`n$($entry[1])`n"
            [System.IO.File]::WriteAllText($argumentFile, $argumentText, [System.Text.UTF8Encoding]::new($false))
            $process = Start-Process -FilePath $javaExecutable -WindowStyle Hidden -PassThru -WorkingDirectory $checkout `
                -ArgumentList @("@`"$argumentFile`"") `
                -RedirectStandardOutput (Join-Path $directory "$($entry[0]).log") `
                -RedirectStandardError (Join-Path $directory "$($entry[0])-error.log")
            $processes += $process
        }
    }
    $deadline = (Get-Date).AddMinutes(3)
    foreach ($port in @(18183, 18283)) {
        while ($true) {
            if ($processes | Where-Object HasExited) { throw '측정 프로세스가 종료됐습니다. 로그를 확인하세요' }
            try {
                $health = Invoke-RestMethod "http://127.0.0.1:$port/health" -TimeoutSec 2
                if ($health.status -eq 'ok') { break }
            } catch { }
            if ((Get-Date) -gt $deadline) { throw '하네스 기동 시간 초과' }
            Start-Sleep -Seconds 1
        }
    }
    ConvertTo-Json -InputObject @($processes | ForEach-Object {
        [pscustomobject]@{ Id = $_.Id; StartTicks = $_.StartTime.ToUniversalTime().Ticks }
    }) | Set-Content (Join-Path $runtimeRoot 'processes.json')
    $ready = $true
    Write-Output 'BASE_AND_CANDIDATE_READY'
} finally {
    if (-not $ready) {
        $processes | Where-Object { !$_.HasExited } | Stop-Process -ErrorAction SilentlyContinue
    }
}
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $originalEnvironment[$name], 'Process')
    }
}
