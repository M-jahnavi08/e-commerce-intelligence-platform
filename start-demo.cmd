@echo off
cd /d "%~dp0"
python scripts\run-local.py
if errorlevel 1 pause
