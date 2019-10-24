set LANG=en_US

echo "Usage -- run as .\script\xponents-demo.bat"
@echo off

REM Find current path to install
set scriptdir=%~dp0
set scriptdir=%scriptdir:~0,-1%
set basedir=%scriptdir%\..
set logconf=%scriptdir:\=/%
set XP=%basedir%

set SOLR_HOME=%XP%\xponents-solr\solr7

logging_args="-Dlogback.configurationFile=%basedir%\etc\logback.xml "
tika_args=" -Dtika.config=%basedir%\etc\tika-config.xml "
xponents_args=" -Dopensextant.solr=%SOLR_HOME% -Xmx1200m -Xms1200m "

java %xponents_args% %tika_args% %logging_args%  -cp "%XP%\etc;%XP%\lib\*" ^
  org.codehaus.groovy.tools.GroovyStarter --main groovy.ui.GroovyMain ^
  %XP%\script\Xponents.groovy  %*
  
pause
