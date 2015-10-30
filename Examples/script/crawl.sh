export LANG=en_US

# ant -f ./xtext-test.xml -Dinput=$1 -Doutput=$2 convert

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

#echo "Usage -- run as ./script/convert.sh"
echo JAVA_HOME =  $JAVA_HOME
echo See README_convert.txt  for more detail.

echo $*
logfile=$basedir/logs/xtext-stderr.log


WEBSITE=$1
OUTPUT=$2

java  -Dlog4j.configuration="file:${basedir}/script/log4j.properties" -Dhttp.proxyHost=${http_proxy} -Dhttp.proxyPort=80 -Dxtext.home="${basedir}" -Xmx512m  \
   -classpath "$basedir/lib/*" org.opensextant.xtext.test.WebCrawlTest -l $WEBSITE -o $OUTPUT


