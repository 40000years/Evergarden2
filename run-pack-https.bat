@echo off
cd /d "%~dp0"
echo Starting Cloudflare HTTPS Pack Proxy...
start "" python tools\pack_https_proxy.py --settings output\pack-https-20261026\settings.json
echo Proxy started. Check output\pack-https-20261026\state.json for URL and status.
pause
