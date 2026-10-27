@echo off
cd /d "%~dp0"
echo Requesting Cloudflare HTTPS Pack Proxy stop...
echo. > output\pack-https-20261026\stop.request
echo Stop request created. Helper will terminate gracefully.
pause
