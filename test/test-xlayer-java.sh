script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

PORT=$1
URL=http://localhost:$PORT/xlayer/rest/process
FILE=$2

java -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$basedir/etc:$basedir/lib/*" XlayerClientTest $URL $FILE 
