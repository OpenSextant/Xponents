export LANG=en_US

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

#echo "Usage -- run as ./script/convert.sh"
echo JAVA_HOME =  $JAVA_HOME
echo See README_convert.txt  for more detail.

echo $*

# Debug logging: -Dlog4j.debug
#java  -Dlog4j.configuration="file:${basedir}/script/log4j.properties" -Dxtext.home="${basedir}" -Xmx512m  \
#   -classpath "$basedir/lib/*" org.opensextant.xtext.XText  --input="$input" --output="$output" --export="$crawl_output" > $logfile 2>&1

#java  -Dlog4j.configuration="file:${basedir}/script/log4j.properties" -Dxtext.home="${basedir}" -Xmx512m  \
#   -classpath "$basedir/lib/*" org.opensextant.xtext.XText   $* 

java  -Dxtext.home="${basedir}" -Xmx512m  -classpath "$basedir/etc:$basedir/lib/*" org.opensextant.xtext.XText   $* 


