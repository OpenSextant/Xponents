#!/bin/bash

scripts=`dirname $0`
XP=`cd -P $scripts/..; echo $PWD`

export CLASSPATH=$XP/etc:$XP/lib/*

SOLR_HOME=$XP/xponents-solr/solr7
if [ -d $XP/solr/ ] ; then
    # Use dir in development source tree.
    SOLR_HOME=$XP/solr/solr7
fi

proxy_args=
if [ -n "$http_proxy" -o -n "$https_proxy" ]; then 
    proxyHost=`echo $http_proxy | sed -e "s:http\://::g;" | sed -e "s:\:.$::;"`
    proxy_args="-Dhttp.proxyHost=${proxyHost} -Dhttp.proxyPort=80"
    proxy_args="-Dhttps.proxyHost=${proxyHost} -Dhttps.proxyPort=80 $PROXY"
fi

xponents_args=" -Dopensextant.solr=$SOLR_HOME -Xmx1500m -Xms1500m "
logging_args="-Dlogback.configurationFile=$XP/etc/logback.xml"
tika_args="-Dtika.config=$XP/etc/tika-config.xml"

java  $xponents_args $logging_args $tika_args $proxy_args -cp $CLASSPATH \
  org.codehaus.groovy.tools.GroovyStarter --main groovy.ui.GroovyMain \
  $XP/script/Xponents.groovy  "$@"
