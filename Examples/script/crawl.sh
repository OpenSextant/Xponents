export LANG=en_US

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

echo "Some cautionary points:"
echo "If using proxy, set http_proxy, default port is 80"  
echo "If link you are using is a folder, you must add trailing slash, eg., -l http://a.b.com/folder/ " 

echo $*
logfile=$basedir/log/xtext-stderr.log
# Get proxy from http_proxy
proxyHost=`echo $http_proxy | sed -e "s:http\://::g;" | sed -e "s:\:.$::;"`

WEBSITE=$1
OUTPUT=$2
# Omit proxy argument if you don't use a proxy/firewall.

OPTS="-Dlogback.configurationFile=${basedir}/etc/logback.xml"
PROXY="-Dhttp.proxyHost=${proxyHost} -Dhttp.proxyPort=80"
java $OPTS $PROXY  -Dxtext.home="${basedir}" -Xmx512m  \
   -classpath "$basedir/lib/*" org.opensextant.examples.WebCrawl -l $WEBSITE -o $OUTPUT -d
