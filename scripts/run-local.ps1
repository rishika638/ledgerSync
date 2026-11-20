$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker Desktop is required. Start Docker Desktop and rerun this script."
}

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Created .env from .env.example. Review its local-only passwords before use." -ForegroundColor Yellow
}

Write-Host "Starting PostgreSQL 16 and Redis 7 (existing named volumes are preserved)..." -ForegroundColor Cyan
docker compose up -d --wait postgres redis
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose failed to start PostgreSQL and Redis."
}

docker compose ps
Write-Host ""
Write-Host "Infrastructure is ready. In separate PowerShell terminals run:" -ForegroundColor Green
Write-Host "  .\scripts\start-backend.ps1"
Write-Host "  .\scripts\start-frontend.ps1"
