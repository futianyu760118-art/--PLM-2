@echo off
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d D:\PLM-2\plm-backend
java -jar target\plm-backend.jar
