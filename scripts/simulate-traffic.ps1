# scripts/simulate-traffic.ps1
# Simulates full reconciliation workflow locally

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$OperatorKey = "operator-secret-key-67890",
    [string]$AdminKey = "admin-secret-key-abcdef"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 1. Triggering On-Demand Reconciliation for STRIPE ===" -ForegroundColor Cyan
$recRun = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/run?provider=STRIPE" `
    -Method Post `
    -Headers @{ "X-API-Key" = $OperatorKey }

Write-Host "Reconciliation Run Result:" -ForegroundColor Green
Write-Host ($recRun | ConvertTo-Json)

Write-Host "`n=== 2. Inspecting Discrepancies ===" -ForegroundColor Cyan
$discrepancies = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/discrepancies?status=OPEN" `
    -Method Get `
    -Headers @{ "X-API-Key" = $OperatorKey }

Write-Host "Open Discrepancies Count: $($discrepancies.totalElements)" -ForegroundColor Yellow
if ($discrepancies.totalElements -gt 0) {
    $first = $discrepancies.content[0]
    Write-Host "Sample Discrepancy:" -ForegroundColor White
    Write-Host ($first | ConvertTo-Json)

    Write-Host "`n=== 3. Resolving Discrepancy as Admin ===" -ForegroundColor Cyan
    $resolveBody = @{
        status = "RESOLVED"
        resolutionNotes = "Verified via automated simulation script: manual sync approved"
    } | ConvertTo-Json

    $resolved = Invoke-RestMethod -Uri "$BaseUrl/api/v1/reconciliation/discrepancies/$($first.id)/resolve" `
        -Method Post `
        -Headers @{ "X-API-Key" = $AdminKey; "Content-Type" = "application/json" } `
        -Body $resolveBody

    Write-Host "Resolved Discrepancy Status: $($resolved.status)" -ForegroundColor Green
}

Write-Host "`n=== 4. Checking Dead-Letter Queue Status ===" -ForegroundColor Cyan
$dlq = Invoke-RestMethod -Uri "$BaseUrl/api/v1/queue/dead-letters" `
    -Method Get `
    -Headers @{ "X-API-Key" = $OperatorKey }

Write-Host "Dead Letters in Queue: $($dlq.totalElements)" -ForegroundColor Green
