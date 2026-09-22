@echo off
cd /d D:\PLM-2\plm-algorithm
python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
