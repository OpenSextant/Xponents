#!/bin/bash 

JETTY_BASE=$PWD
SOLR_HOME=$PWD/solr4
PORT=7000
STOP_PORT=7007
basedir=$JETTY_BASE
export JETTY_HOME=$basedir/jetty9
JAR=$JETTY_HOME/start.jar

case "$1" in
  'start')

       $JAVA_HOME/bin/java -Xmx3g -Xms3g -Djava.awt.headless=true \
             -Dsolr.solr.home=${SOLR_HOME} -Dsolr.enableRemoteStreaming=true  -jar $JAR \
             STOP.PORT=${STOP_PORT} STOP.KEY=xponents-solr  \
             jetty.home=$JETTY_HOME jetty.base=$JETTY_BASE  -daemon  
        ;;

   'stop')
        java  -jar $JAR  STOP.PORT=${STOP_PORT} STOP.KEY=xponents-solr --stop
        ;;

    *)
        echo "Usage: server-jetty.sh start  | stop  "
        java  -jar $JAR --help
        ;;
esac
