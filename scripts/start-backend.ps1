$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path ".env")) {
    throw "Missing .env. Run .\scripts\run-local.ps1 first to create it from .env.example."
}

Get-Content ".env" | ForEach-Object {
    $line = $_
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)\s*$') {
        $name = $Matches[1].Trim()
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and
            (($value.StartsWith('"') -and $value.EndsWith('"')) -or
             ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

& ".\mvnw.cmd" spring-boot:run
exit $LASTEXITCODE
