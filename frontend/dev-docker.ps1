$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

docker compose -f infra/docker-compose.yml up -d --build frontend nginx

Write-Host ""
Write-Host "Frontend is running:"
Write-Host "  http://localhost:5173"
Write-Host "  http://localhost:8088"
