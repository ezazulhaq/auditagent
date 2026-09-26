$ErrorActionPreference = 'Stop'

$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) {
    throw 'Run this script from inside the AuditAgent Git repository.'
}

Push-Location -LiteralPath $repoRoot
try {
    & git config --local core.hooksPath .githooks
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not configure the repository hook path.'
    }
    Write-Host 'AuditAgent Git hooks enabled from .githooks.'
} finally {
    Pop-Location
}
