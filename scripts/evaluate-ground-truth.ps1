param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$apiBase = "$($BaseUrl.TrimEnd('/'))/api/v1"

$localEnv = @{}
$envPath = Join-Path $root ".env"
if (Test-Path $envPath) {
    foreach ($line in Get-Content $envPath) {
        if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)\s*$') {
            $localEnv[$Matches[1].Trim()] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
}

$username = [Environment]::GetEnvironmentVariable("LEDGERSYNC_OPERATOR_USERNAME", "Process")
$password = [Environment]::GetEnvironmentVariable("LEDGERSYNC_OPERATOR_PASSWORD", "Process")
if (-not $username) { $username = $localEnv["LEDGERSYNC_OPERATOR_USERNAME"] }
if (-not $password) { $password = $localEnv["LEDGERSYNC_OPERATOR_PASSWORD"] }
if (-not $username) { $username = "operator" }
if (-not $password) { $password = "operator" }

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$loginBody = @{ username = $username; password = $password } | ConvertTo-Json
$csrf = Invoke-RestMethod -Uri "$apiBase/auth/csrf" -Method Get -WebSession $session
Invoke-RestMethod -Uri "$apiBase/auth/login" -Method Post -ContentType "application/json" `
    -Headers @{ "X-XSRF-TOKEN" = $csrf.token } -Body $loginBody -WebSession $session | Out-Null

$latestRunPage = Invoke-RestMethod -Uri "$apiBase/finance/reconciliation/runs?page=0&size=1" -WebSession $session
if ($latestRunPage.content.Count -eq 0) {
    throw "No three-way reconciliation run is available. Load the demo dataset and run reconciliation first."
}
$run = $latestRunPage.content[0]
$runId = $run.id
$inputBatchIds = @()
if ($run.inputBatchIds) {
    $inputBatchIds = @($run.inputBatchIds | ConvertFrom-Json)
}
if ($inputBatchIds.Count -ne 3) {
    throw "Run '$runId' does not contain a complete three-source batch snapshot. Run reconciliation after applying the import-snapshot migration."
}
$matchesPage = Invoke-RestMethod -Uri "$apiBase/finance/reconciliation/runs/$runId/matches?page=0&size=500" -WebSession $session
$exceptionsPage = Invoke-RestMethod -Uri "$apiBase/finance/exceptions?runId=$runId&page=0&size=500" -WebSession $session
$recordsPage = Invoke-RestMethod -Uri "$apiBase/finance/transactions?page=0&size=500" -WebSession $session
$groundTruth = Get-Content (Join-Path $root "demo-data\ground_truth.json") -Raw | ConvertFrom-Json
if ($matchesPage.totalElements -gt $matchesPage.content.Count -or
    $exceptionsPage.totalElements -gt $exceptionsPage.content.Count -or
    $recordsPage.totalElements -gt $recordsPage.content.Count) {
    throw "Evaluation is limited to 500 records per API result. Refusing to report incomplete metrics."
}

$ledgerByReference = @{}
foreach ($record in $recordsPage.content) {
    if ($record.sourceType -eq "LEDGER" -and $inputBatchIds -contains $record.batchId) {
        if ($ledgerByReference.ContainsKey($record.recordReference)) {
            throw "Duplicate ledger reference '$($record.recordReference)' found. Evaluation requires an unambiguous dataset."
        }
        $ledgerByReference[$record.recordReference] = $record
    }
}

$matchesByLedgerId = @{}
foreach ($match in $matchesPage.content) {
    $ledgerId = $match.ledgerRecord.id
    if ($ledgerId) {
        if (-not $matchesByLedgerId.ContainsKey($ledgerId)) { $matchesByLedgerId[$ledgerId] = @() }
        $matchesByLedgerId[$ledgerId] += $match
    }
}

$exceptionsByLedgerId = @{}
foreach ($exception in $exceptionsPage.content) {
    $ledgerId = $exception.ledgerRecordId
    if ($ledgerId) {
        if (-not $exceptionsByLedgerId.ContainsKey($ledgerId)) { $exceptionsByLedgerId[$ledgerId] = @() }
        $exceptionsByLedgerId[$ledgerId] += $exception
    }
}

$results = foreach ($case in $groundTruth.cases) {
    $ledger = $ledgerByReference[$case.payment_ref]
    $caseMatches = @()
    $caseExceptions = @()
    if ($ledger -and $matchesByLedgerId.ContainsKey($ledger.id)) {
        $caseMatches = $matchesByLedgerId[$ledger.id]
    }
    if ($ledger -and $exceptionsByLedgerId.ContainsKey($ledger.id)) {
        $caseExceptions = $exceptionsByLedgerId[$ledger.id]
    }

    $hasCleanMatch = $false
    foreach ($match in $caseMatches) {
        if ($match.matchType -eq "EXACT_THREE_WAY" -and $match.matchStatus -eq "AUTOMATIC_CONFIRMED") {
            $hasCleanMatch = $true
            break
        }
    }
    if ($caseExceptions.Count -eq 0 -and $hasCleanMatch) {
        $predictedCategory = "CLEAN_3WAY_MATCH"
    } elseif ($caseExceptions.Count -eq 1) {
        $predictedCategory = $caseExceptions[0].type
    } elseif ($caseExceptions.Count -gt 1) {
        $predictedCategory = "MULTIPLE_EXCEPTIONS"
    } else {
        $predictedCategory = "UNCLASSIFIED"
    }
    $correct = $predictedCategory -eq $case.category

    [pscustomobject]@{
        paymentRef = $case.payment_ref
        category = $case.category
        expected = if ($case.expected_discrepancy) { $case.expected_discrepancy } else { "CLEAN_3WAY_MATCH" }
        predicted = $predictedCategory
        passed = [bool]$correct
    }
}

$categorySummary = $results | Group-Object category | ForEach-Object {
    $label = $_.Name
    $truePositive = @($results | Where-Object { $_.category -eq $label -and $_.predicted -eq $label }).Count
    $falsePositive = @($results | Where-Object { $_.category -ne $label -and $_.predicted -eq $label }).Count
    $falseNegative = @($results | Where-Object { $_.category -eq $label -and $_.predicted -ne $label }).Count
    $trueNegative = @($results | Where-Object { $_.category -ne $label -and $_.predicted -ne $label }).Count
    $falsePositiveCases = @($results | Where-Object {
        $_.category -ne $label -and $_.predicted -eq $label
    } | ForEach-Object {
        [pscustomobject]@{ paymentRef = $_.paymentRef; expected = $_.category; predicted = $_.predicted }
    })
    $falseNegativeCases = @($results | Where-Object {
        $_.category -eq $label -and $_.predicted -ne $label
    } | ForEach-Object {
        [pscustomobject]@{ paymentRef = $_.paymentRef; expected = $_.category; predicted = $_.predicted }
    })
    $precision = if (($truePositive + $falsePositive) -gt 0) { $truePositive / ($truePositive + $falsePositive) } else { $null }
    $recall = if (($truePositive + $falseNegative) -gt 0) { $truePositive / ($truePositive + $falseNegative) } else { $null }
    $f1 = if ($null -ne $precision -and $null -ne $recall -and ($precision + $recall) -gt 0) {
        2 * $precision * $recall / ($precision + $recall)
    } else { $null }
    [pscustomobject]@{
        category = $_.Name
        TP = $truePositive
        FP = $falsePositive
        FN = $falseNegative
        TN = $trueNegative
        precision = $precision
        recall = $recall
        f1 = $f1
        total = $_.Count
        falsePositiveCases = $falsePositiveCases
        falseNegativeCases = $falseNegativeCases
    }
}
$truePositive = @($results | Where-Object { $_.category -eq "CLEAN_3WAY_MATCH" -and $_.predicted -eq "CLEAN_3WAY_MATCH" }).Count
$falsePositive = @($results | Where-Object { $_.category -ne "CLEAN_3WAY_MATCH" -and $_.predicted -eq "CLEAN_3WAY_MATCH" }).Count
$falseNegative = @($results | Where-Object { $_.category -eq "CLEAN_3WAY_MATCH" -and $_.predicted -ne "CLEAN_3WAY_MATCH" }).Count
$cleanFalsePositiveCases = @($results | Where-Object {
    $_.category -ne "CLEAN_3WAY_MATCH" -and $_.predicted -eq "CLEAN_3WAY_MATCH"
} | ForEach-Object {
    [pscustomobject]@{ paymentRef = $_.paymentRef; expected = $_.category; predicted = $_.predicted }
})
$cleanFalseNegativeCases = @($results | Where-Object {
    $_.category -eq "CLEAN_3WAY_MATCH" -and $_.predicted -ne "CLEAN_3WAY_MATCH"
} | ForEach-Object {
    [pscustomobject]@{ paymentRef = $_.paymentRef; expected = $_.category; predicted = $_.predicted }
})
$trueNegative = @($results | Where-Object { $_.category -ne "CLEAN_3WAY_MATCH" -and $_.predicted -ne "CLEAN_3WAY_MATCH" }).Count
$precision = if (($truePositive + $falsePositive) -gt 0) { $truePositive / ($truePositive + $falsePositive) } else { $null }
$recall = if (($truePositive + $falseNegative) -gt 0) { $truePositive / ($truePositive + $falseNegative) } else { $null }
$f1 = if ($null -ne $precision -and $null -ne $recall -and ($precision + $recall) -gt 0) {
    2 * $precision * $recall / ($precision + $recall)
} else { $null }
$failed = @($results | Where-Object { -not $_.passed })
$report = [ordered]@{
    runId = $runId
    runStatus = $run.status
    evaluationScope = "IN_SAMPLE_SYNTHETIC_DEMO"
    heldOutEvaluation = $false
    groundTruthCases = $groundTruth.total_payments
    evaluatedCases = $results.Count
    automaticMatchRows = $matchesPage.totalElements
    detectedExceptionRows = $exceptionsPage.totalElements
    correctlyClassifiedCases = $results.Count - $failed.Count
    accuracy = if ($results.Count -gt 0) { ($results.Count - $failed.Count) / $results.Count } else { $null }
    cleanMatchDetection = [ordered]@{
        TP = $truePositive
        FP = $falsePositive
        FN = $falseNegative
        TN = $trueNegative
        precision = $precision
        recall = $recall
        f1 = $f1
        falsePositiveCases = $cleanFalsePositiveCases
        falseNegativeCases = $cleanFalseNegativeCases
    }
    failedCases = $failed
    categories = @($categorySummary)
}
$report | ConvertTo-Json -Depth 6
if ($failed.Count -gt 0 -or $results.Count -ne $groundTruth.total_payments) {
    exit 1
}
