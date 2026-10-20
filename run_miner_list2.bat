@echo off
title PulseCast Stream Miner - Liste 2 (Nonstop, 80s, 90s, Trance, SomaFM)
cd /d "%~dp0"

echo ==========================================================
echo  PulseCast Radio Stream Miner - LISTE 2 (30 Streams)
echo  Nonstop Channels, SomaFM, Top 100, Trance NL, 80s & 90s
echo ==========================================================
echo.

python -m stream_miner.main --stations stream_miner/stations_nonstop.json --max-streams 30

pause
