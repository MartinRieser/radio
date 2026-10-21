@echo off
title PulseCast Stream Miner - Liste 3 (Europa Pop, Rock, Trance & Dance)
cd /d "%~dp0"

echo ==========================================================
echo  PulseCast Radio Stream Miner - EURO POP/ROCK/DANCE (60 Streams)
echo  Europa FM & DAB+ Streams: Pop, Rock, Trance, Dance, House, EDM
echo ==========================================================
echo.

python -m stream_miner.main --stations stream_miner/stations_euro_poprock.json --max-streams 60

pause
