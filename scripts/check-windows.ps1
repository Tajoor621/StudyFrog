$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
python -m venv .venv
if ($LASTEXITCODE -ne 0) { throw 'Python venv failed' }
& .venv\Scripts\python.exe -m pip install -r engine/requirements.txt
if ($LASTEXITCODE -ne 0) { throw 'Dependency setup failed' }
& .venv\Scripts\python.exe -m unittest discover -s engine -p test_benchmark.py -v
if ($LASTEXITCODE -ne 0) { throw 'Benchmark tooling tests failed' }
& .venv\Scripts\python.exe -m unittest discover -s web/tests -v
if ($LASTEXITCODE -ne 0) { throw 'Web bridge tests failed' }
& scripts/start-windows.ps1 -Mode Check
