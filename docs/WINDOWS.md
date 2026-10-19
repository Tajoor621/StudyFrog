# Windows decision: keep the web companion

Decision for 0.9.0: **no Windows native shell**. Keep the browser editor, private
loopback bridge and local Python engine. This is a scope/architecture decision,
not a measured claim that browser and native performance are equal.

| Consideration | Current decision |
| --- | --- |
| Inference | SD/ControlNet/LTX already run outside the browser in the local engine; a shell alone would not improve model compute. Actual hardware benchmarks remain pending. |
| Offline/privacy | Editor assets are cached; local models/tools are explicitly installed; bridge uses loopback and keeps the engine token out of browser JavaScript. |
| Maintenance | Preserve one web client while Android/iOS retain their native editors. Avoid adding another signing, installer and updater surface before hardware evidence. |
| Feature boundary | Windows currently has the web editor, not full Android vector/editor parity. Wrapping the same page would not add missing editor tools. |
| Reconsider a shell | Reproducible browser/OS limits block required file workflows, engine lifecycle or accessibility; prototype a thin shell and measure it before committing to a separate editor. |

No Store, Play Console, certificate purchase or paid distribution is introduced.
A native Windows fork remains a future decision requiring concrete unmet needs.

## First setup, free local tools

Install Python 3.11+ and a browser using their free installers. From the repository
root in PowerShell, create the environment and install the pinned local-engine
requirements. Optional model dependencies are much larger; install them only
when you intend to run your own SD/LTX weights. No model downloads are implicit.

```powershell
py -3 -m venv .venv
.venv\Scripts\python.exe -m pip install -r engine/requirements.txt
# Optional local trained-model runtime:
.venv\Scripts\python.exe -m pip install -r engine/requirements-models.txt
.\scripts\start-windows.ps1 -Mode Check
```

Use Python 3.11 or 3.12 for the pinned model runtime. FFmpeg is required for
computer video and Tesseract for OCR, separately installed free tools on PATH.
`Check` distinguishes missing optional tools from the usable editor. It does
not install software, change PowerShell execution policy, or download weights.
If local script policy blocks execution, inspect the script and follow your
own device's administrator policy; the app does not bypass it.

In one terminal create a private session token:

```powershell
$env:STUDYFORGE_TOKEN = (.venv\Scripts\python.exe -c "import secrets; print(secrets.token_urlsafe(32))")
```

Keep it private. Copy the same value to `STUDYFORGE_TOKEN` in a second terminal
without placing it in a URL, repository or command argument. Configure
`STUDYFORGE_MODEL` to your actual SD 1.x Diffusers folder and optional guidance/
LTX paths as described in the engine guide. A phone GGUF is not a Diffusers
folder. Start the two processes in their respective terminals:

```powershell
# Terminal with model configuration and shared token:
.\scripts\start-windows.ps1 -Mode Engine
# Second terminal with the same token:
.\scripts\start-windows.ps1 -Mode Web
```

Open `http://127.0.0.1:8766/`, select the local engine and check readiness. GitHub
Pages can serve the editor/help but cannot run this computer's Python engine.
For phones use the existing [private connection guide](../web/local-engine/README.md);
this launcher intentionally does not open a public/LAN listener. Stop each
process with Ctrl+C. The engine already prints its token in its local terminal;
never share terminal screenshots containing that token.

[Device validation](DEVICE_VALIDATION.md) covers actual model measurement,
Narrator/keyboard/font/contrast checks and encoder/memory sessions. Windows CI
runs the launcher preflight and local bridge/benchmark-tool tests, not trained
weights, a physical Windows workstation, Narrator or native-shell performance.
