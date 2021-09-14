#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

unset http_proxy
export NO_PROXY=localhost,127.0.0.1
export noproxy=$NO_PROXY

CMD=$1
XLAYER_PORT=$2 

case $CMD in

  'start')
    echo JAVA_HOME =  $JAVA_HOME
    echo $*
    cd $basedir
    logfile=$basedir/log/xlayer-stderr.log
    CLASSPATH="$basedir/etc:$basedir/lib/*"
    XPONENTS_SOLR=${XPONENTS_SOLR:-$basedir/xponents-solr}

    nohup java -Dopensextant.solr=$XPONENTS_SOLR/solr7 -Xmx4g -Xms4g \
        -XX:+UseParallelGC -server \
        -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$CLASSPATH" org.opensextant.xlayer.server.xgeo.XlayerServer $XLAYER_PORT  >$logfile 2>&1 &
  ;;


  'stop')
    RESTAPI=http://localhost:$XLAYER_PORT/xlayer/rest/control/stop
    # Using curl, POST a JSON object to the service.
    curl "$RESTAPI"
  ;;

esac
