#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

XLAYER_PORT=$1

cd $basedir
CLASSPATH="$basedir/etc:$basedir/lib/*"
XPONENTS_SOLR=${XPONENTS_SOLR:-$basedir/xponents-solr}

java -Dopensextant.solr=$XPONENTS_SOLR/solr7 -Xmx${JAVA_XMX} -Xms${JAVA_XMS} \
        -XX:+UseParallelGC -server \
        -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$CLASSPATH" org.opensextant.xlayer.server.xgeo.XlayerServer $XLAYER_PORT


