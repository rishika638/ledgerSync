$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

if (Test-Path (Join-Path $root ".env")) {
    Get-Content (Join-Path $root ".env") | ForEach-Object {
        if ($_ -match '^\s*VITE_BACKEND_URL\s*=\s*(.*)\s*$') {
            $env:VITE_BACKEND_URL = $Matches[1].Trim().Trim('"').Trim("'")
        }
    }
}

if (-not $env:VITE_BACKEND_URL) {
    $env:VITE_BACKEND_URL = "http://localhost:8080"
}

Set-Location (Join-Path $root "frontend")
npm run dev -- --host=127.0.0.1
exit $LASTEXITCODE
