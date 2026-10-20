@echo off
title PulseCast Stream Miner (PC Pattern Training)
cd /d "%~dp0"

echo ==========================================================
echo  PulseCast Radio Stream Miner & Pattern Training Engine
echo  Gleichzeitiges Training fuer bis zu 30 Radiostreams
echo ==========================================================
echo.

python -m stream_miner.main --stations stream_miner/stations.json --max-streams 30

pause
