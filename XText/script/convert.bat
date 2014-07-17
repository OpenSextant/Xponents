set LANG=en_US

echo "Usage -- run as .\script\convert.bat"
echo JAVA_HOME =  %JAVA_HOME%
echo See README_convert.txt  for more detail.

java -Xmx512m  -classpath ".\etc;.\lib\*" org.opensextant.xtext.XText  -i %1 -o %2 -x %3
