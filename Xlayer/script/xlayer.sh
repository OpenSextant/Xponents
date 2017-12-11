script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

PORT=$1 
CMD=$2

CATALOG_VERSION=2017Q4
case $CMD in

  'start')
    echo JAVA_HOME =  $JAVA_HOME
    echo $*
    cd $basedir
    logfile=$basedir/log/stderr.log
    stdout=$basedir/log/xlayer.log
    XPONENTS_SOLR=${XPONENTS_SOLR:-/data/xponents-solr-$CATALOG_VERSION/solr6}

    nohup java -Dopensextant.solr=$XPONENTS_SOLR -Xmx2g   -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$basedir/etc:$basedir/etc/*:$basedir/lib/*" org.opensextant.xlayer.server.xgeo.XlayerServer   $*  2>$logfile > $stdout &
  ;;


  'stop')
    RESTAPI=http://localhost:$PORT/xlayer/rest/process
    # Using curl, POST a JSON object to the service.
    #  cmd = stop
    curl "${RESTAPI}?cmd=$CMD"
  ;;

esac
