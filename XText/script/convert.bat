set LANG=en_US

echo "Usage -- run as .\script\convert.bat"
echo JAVA_HOME =  %JAVA_HOME%
echo See README_convert.txt  for more detail.
@echo off

REM Find current path to install
set scriptdir=%~dp0
set scriptdir=%scriptdir:~0,-1%
set basedir=%scriptdir%\..
set logconf=%scriptdir:\=/%
set logfile=%basedir%\logs\xtext-stderr.log

REM -Dlog4j.debug
REM -Dlog4j.configuration="file:/%logconf%/log4j.properties"
java -Dxtext.home="%basedir%"  -Xmx512m  -classpath "%basedir%\lib\*;%basedir%\etc" org.opensextant.xtext.XText  --input=%1 --output=%2 --export=%3 
REM To quell the console logging, add stdout redirects below:
REM >%logfile% 2>&1 
