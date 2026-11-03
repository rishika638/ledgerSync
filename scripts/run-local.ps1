# scripts/run-local.ps1
# Starts local infrastructure (Postgres 16, Redis 7) and provides execution instructions

Write-Host "=== LedgerSync Local Setup Helper ===" -ForegroundColor Cyan

if (Get-Command docker -ErrorAction SilentlyContinue) {
    Write-Host "Starting PostgreSQL and Redis containers via Docker Compose..." -ForegroundColor Yellow
    docker compose up -d
    Write-Host "Containers started. PostgreSQL listening on 5432, Redis on 6379." -ForegroundColor Green
} else {
    Write-Host "Docker not found in PATH. Ensure PostgreSQL (port 5432) and Redis (port 6379) are running locally." -ForegroundColor Red
}

Write-Host "`nTo compile and run LedgerSync:" -ForegroundColor Cyan
Write-Host "  mvn clean spring-boot:run" -ForegroundColor White
Write-Host "`nTo execute automated test suites:" -ForegroundColor Cyan
Write-Host "  mvn clean test" -ForegroundColor White
Write-Host "`nSwagger UI / OpenAPI Documentation:" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/swagger-ui/index.html" -ForegroundColor White
Write-Host "`nActuator Metrics & Health:" -ForegroundColor Cyan
Write-Host "  http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  http://localhost:8080/actuator/metrics" -ForegroundColor White
