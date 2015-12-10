export LANG=en_US

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

#echo "Usage -- run as ./script/convert.sh"
# echo JAVA_HOME =  $JAVA_HOME
echo "Some cautionary points:"
echo "If using proxy, set http_proxy, default port is 80"  
echo "If link you are using is a folder, you must add trailing slash, eg., -l http://a.b.com/folder/ " 

echo $*
logfile=$basedir/logs/xtext-stderr.log
# Get proxy from http_proxy
proxyHost=`echo $http_proxy | sed -e "s:http\://::g;" | sed -e "s:\:.$::;"`

WEBSITE=$1
OUTPUT=$2
# Omit proxy argument if you don't use a proxy/firewall.

java  -Dlog4j.configuration="file:${basedir}/etc/log4j.properties" \
   -Dhttp.proxyHost=${proxyHost} -Dhttp.proxyPort=80 -Dxtext.home="${basedir}" -Xmx512m  \
   -classpath "$basedir/lib/*" org.opensextant.examples.WebCrawl -l $WEBSITE -o $OUTPUT -d
