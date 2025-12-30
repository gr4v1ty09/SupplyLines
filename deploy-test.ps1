# SupplyLines Test Deployment Script (PowerShell)
# Builds, deploys, and launches the mod in the PrismLauncher test instance

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$InstanceName = "CreateColoniesV2-DiagTest"
$ModsDir = "$env:APPDATA\PrismLauncher\instances\$InstanceName\minecraft\mods"
$PrismLauncher = "$env:LOCALAPPDATA\Programs\PrismLauncher\prismlauncher.exe"
$JarName = "supplylines-1.0.0.jar"

Write-Host "=== SupplyLines Test Deployment ===" -ForegroundColor Cyan
Write-Host ""

# Step 1: Format code
Write-Host "[1/4] Running Spotless formatting..." -ForegroundColor Yellow
Set-Location $ScriptDir
& .\gradlew.bat spotlessApply --quiet
if ($LASTEXITCODE -ne 0) { throw "Spotless formatting failed" }

# Step 2: Build
Write-Host "[2/4] Building mod JAR..." -ForegroundColor Yellow
& .\gradlew.bat build --quiet
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

# Step 3: Deploy
Write-Host "[3/4] Deploying to test instance..." -ForegroundColor Yellow
if (-not (Test-Path $ModsDir)) {
    throw "Mods directory not found: $ModsDir"
}
Copy-Item "build\libs\$JarName" -Destination $ModsDir -Force
Write-Host "    Deployed: $ModsDir\$JarName" -ForegroundColor Green

# Step 4: Launch
Write-Host "[4/4] Launching game..." -ForegroundColor Yellow
if (-not (Test-Path $PrismLauncher)) {
    # Try alternate locations
    $AltPaths = @(
        "$env:LOCALAPPDATA\PrismLauncher\prismlauncher.exe",
        "$env:ProgramFiles\PrismLauncher\prismlauncher.exe",
        "${env:ProgramFiles(x86)}\PrismLauncher\prismlauncher.exe"
    )
    foreach ($path in $AltPaths) {
        if (Test-Path $path) {
            $PrismLauncher = $path
            break
        }
    }
    if (-not (Test-Path $PrismLauncher)) {
        throw "PrismLauncher not found. Please update the script with the correct path."
    }
}
Start-Process $PrismLauncher -ArgumentList "--launch", $InstanceName

Write-Host ""
Write-Host "=== Deployment complete ===" -ForegroundColor Cyan
Write-Host "Game is launching. Check logs at:" -ForegroundColor White
Write-Host "  $env:APPDATA\PrismLauncher\instances\$InstanceName\minecraft\logs\latest.log" -ForegroundColor Gray
