script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

URL=http://localhost:$1/xlayer/rest/process
FILE=$2

java -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$basedir/etc:$basedir/lib/*" XlayerClientTest $URL $FILE 
