#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

unset http_proxy
export NO_PROXY=localhost,127.0.0.1
export noproxy=$NO_PROXY

XLAYER_PORT=$1

cd $basedir
CLASSPATH="$basedir/etc:$basedir/etc/*:$basedir/lib/*:$basedir/xlayer-lib/*"
XPONENTS_SOLR=${XPONENTS_SOLR:-$basedir/xponents-solr}

java -Dopensextant.solr=$XPONENTS_SOLR/solr7 -Xmx2g -Xms2g \
        -XX:+UseParallelGC -server \
        -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$CLASSPATH" org.opensextant.xlayer.server.xgeo.XlayerServer $XLAYER_PORT

