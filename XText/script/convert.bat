set LANG=en_US

#ant -f .\xtext-test.xml  -Dinput=%1%  -Doutput=%2% convert
echo JAVA_HOME =  %JAVA_HOME%
cd ..

REM Add arguments to end of command line:
REM
REM   -i  "input dir or file"    Path to input item to convert or crawl
REM   -o  "output dir"           Path to output folder. It must exist.
REM   -e                         Conversions of all content will be cached with input
REM   -x  ".\export\folder"      Export PST or Zip archive contents to the named folder.
REM   -c                         Children objects will be stored with input
REM
REM   "-e" option will override "-o dir".  As "-e" commands output to be organized under -i input dir
REM   "-c" option will override "-x dir".  As "-c" saves binaries with input; -x exports to external dir.
REM
java -Xmx512m  -classpath ".\lib\*" org.opensextant.xtext.XText  -i "%1%" -o "%2%" 
