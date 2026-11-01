$ErrorActionPreference = 'Stop'
$manifest = Join-Path $PSScriptRoot '../build/perf/313/processes.json'
foreach ($entry in (Get-Content -Raw -LiteralPath $manifest | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.Id -ErrorAction SilentlyContinue
    if ($process -and $process.ProcessName -eq 'java' -and $process.StartTime -eq [datetime]$entry.StartTime) {
        Stop-Process -InputObject $process
    } elseif ($process) {
        throw "PID $($entry.Id)가 다른 프로세스에 재사용됐습니다. 종료하지 않습니다"
    }
}
