[CmdletBinding()]
param([ValidateSet('Check', 'Engine', 'Web')][string]$Mode = 'Check')
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$python = Join-Path $repo '.venv\Scripts\python.exe'
if (-not (Test-Path $python)) { throw 'Create .venv with Python 3.11+ and install engine/requirements.txt. See docs/WINDOWS.md.' }
& $python -c 'import sys; assert sys.version_info >= (3,11); import PIL'
if ($LASTEXITCODE -ne 0) { throw 'Python 3.11+ and the pinned Pillow dependency are required.' }
if ($Mode -eq 'Check') {
    foreach ($tool in @('ffmpeg', 'tesseract')) {
        if (Get-Command $tool -ErrorAction SilentlyContinue) { Write-Host "$tool available" }
        else { Write-Host "$tool missing (only its video/OCR features are unavailable)" }
    }
    Write-Host 'Web editor is usable without a model. Image generation needs local SD 1.x weights; AI motion additionally needs local LTX weights/CUDA.'
    Write-Host 'Use the same STUDYFORGE_TOKEN in the Engine and Web terminals. This script never sends it or installs software.'
    exit 0
}
if ([string]::IsNullOrWhiteSpace($env:STUDYFORGE_TOKEN)) { throw 'Set a private STUDYFORGE_TOKEN in both terminals first. Do not put it in a URL, repository, or command argument.' }
Push-Location $repo
try {
    if ($Mode -eq 'Engine') { $env:STUDYFORGE_BIND = '127.0.0.1'; & $python engine/server.py }
    else { & $python web/local-engine/bridge.py }
    if ($LASTEXITCODE -ne 0) { throw "$Mode stopped with exit code $LASTEXITCODE" }
} finally { Pop-Location }
