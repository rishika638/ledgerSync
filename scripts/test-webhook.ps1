# scripts/test-webhook.ps1
# Deterministic local webhook signature generator and tester

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$StripeSecret = "whsec_test_stripe_secret_key_abc123",
    [string]$ApiKey = "operator-secret-key-67890"
)

$ErrorActionPreference = "Stop"

Write-Host "=== 1. Ingesting Valid Stripe Payment Intent Succeeded ===" -ForegroundColor Cyan
$eventId = "evt_local_demo_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)
$paymentId = "pi_local_demo_" + [System.Guid]::NewGuid().ToString().Substring(0, 8)

$payloadObj = @{
    id = $eventId
    type = "payment_intent.succeeded"
    created = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    data = @{
        object = @{
            id = $paymentId
            amount = 15000
            amount_received = 15000
            currency = "usd"
            customer = "cus_demo_user_1"
        }
    }
}
$rawJson = $payloadObj | ConvertTo-Json -Depth 5 -Compress
$rawBytes = [System.Text.Encoding]::UTF8.GetBytes($rawJson)
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

# Compute HMAC SHA256 of "{timestamp}.{rawJson}"
$signedPayload = "$timestamp.$rawJson"
$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($StripeSecret)
$hash = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($signedPayload))
$sigHex = -join ($hash | ForEach-Object { "{0:x2}" -f $_ })
$stripeHeader = "t=$timestamp,v1=$sigHex"

$resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/webhooks/stripe" `
    -Method Post `
    -Headers @{ "Stripe-Signature" = $stripeHeader; "Content-Type" = "application/json" } `
    -Body $rawJson

Write-Host "Response: $($resp | ConvertTo-Json)" -ForegroundColor Green

Write-Host "`n=== 2. Testing Idempotent Duplicate Delivery ===" -ForegroundColor Cyan
$dupResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/webhooks/stripe" `
    -Method Post `
    -Headers @{ "Stripe-Signature" = $stripeHeader; "Content-Type" = "application/json" } `
    -Body $rawJson

Write-Host "Duplicate Response: $($dupResp | ConvertTo-Json)" -ForegroundColor Yellow
if ($dupResp.duplicate -eq $true) {
    Write-Host "SUCCESS: Duplicate acknowledged idempotently without secondary effects." -ForegroundColor Green
}

Write-Host "`n=== 3. Testing Invalid Signature Rejection ===" -ForegroundColor Cyan
try {
    $badResp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/webhooks/stripe" `
        -Method Post `
        -Headers @{ "Stripe-Signature" = "t=$timestamp,v1=bad_signature_0000000000000000"; "Content-Type" = "application/json" } `
        -Body $rawJson
} catch {
    Write-Host "SUCCESS: Request rejected as expected with HTTP $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Green
}

Write-Host "`n=== 4. Polling Processed Payment Transaction ===" -ForegroundColor Cyan
Start-Sleep -Seconds 3

try {
    $payment = Invoke-RestMethod -Uri "$BaseUrl/api/v1/payments/provider/STRIPE/$paymentId" `
        -Method Get `
        -Headers @{ "X-API-Key" = $ApiKey }
    Write-Host "Payment Record:" -ForegroundColor Green
    Write-Host ($payment | ConvertTo-Json)
} catch {
    Write-Host "Payment query note: $($_.Exception.Message)" -ForegroundColor Yellow
}
