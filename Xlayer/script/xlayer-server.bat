set LANG=en_US

echo "Usage -- run as .\script\xlayer.bat"
echo JAVA_HOME =  %JAVA_HOME%
@echo off

REM Find current path to install
set scriptdir=%~dp0
set scriptdir=%scriptdir:~0,-1%
set basedir=%scriptdir%\..
set logconf=%scriptdir:\=/%
set XPONENTS_SOLR=%basedir%\xponents-solr

set COMMAND=%1
set XLAYER_PORT=%2

REM Default argument here is a port number. that is it.
REM Note -- on windows if you log out, this process will die.  
REM You are responsible for making a resident windows service out of it, if you like
REM Alternatively, we could deploy as a Tomcat or other webapp

if "%COMMAND%" == "start" (
    echo "START XLAYER SERVICE"
    java -Dopensextant.solr="%XPONENTS_SOLR%\solr7" -Xmx2g -Xms2g ^
          -Dlogback.configurationFile="%basedir%\etc\logback.xml" ^
          -classpath "%basedir%\etc;%basedir%\etc\*;%basedir%\lib\*" ^
          org.opensextant.xlayer.server.xgeo.XlayerServer   %XLAYER_PORT% 
    pause
)

if "%COMMAND%" == "stop" (
    echo "STOP XLAYER SERVICE"
    echo "Launching a browser and running this... "
    start  "" "http://localhost:%XLAYER_PORT%/xlayer/rest/control/stop"
)


