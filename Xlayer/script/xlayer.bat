set LANG=en_US

echo "Usage -- run as .\script\xlayer.bat"
echo JAVA_HOME =  %JAVA_HOME%
@echo off

REM Find current path to install
set scriptdir=%~dp0
set scriptdir=%scriptdir:~0,-1%
set basedir=%scriptdir%\..
set logconf=%scriptdir:\=/%
set XPONENTS_SOLR=%basedir%\..\xponents-solr\solr4

REM Default argument here is a port number. that is it.
REM Note -- on windows if you log out, this process will die.  
REM You are responsible for making a resident windows service out of it, if you like
REM Alternatively, we could deploy as a Tomcat or other webapp
java "-Dopensextant.solr=%XPONENTS_SOLR%" -Xmx2g "-Dlogback.configurationFile=%basedir%\etc\logback.xml"  -classpath "%basedir%\etc;%XPONENTS_SOLR%\gazetteer\conf;%basedir%\lib\*" org.opensextant.xlayer.server.xgeo.XlayerServer   %1  

pause
