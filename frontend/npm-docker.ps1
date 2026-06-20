$ErrorActionPreference = "Stop"

if ($args.Count -eq 0) {
  Write-Host "Usage: .\npm-docker.ps1 <npm args>"
  Write-Host "Example: .\npm-docker.ps1 run build"
  exit 1
}

docker run --rm -it `
  -v "${PSScriptRoot}:/workspace" `
  -w /workspace `
  -p 5173:5173 `
  -e VITE_API_PROXY_TARGET=http://host.docker.internal:8080 `
  -e VITE_WS_PROXY_TARGET=ws://host.docker.internal:8080 `
  node:24-alpine `
  npm @args
