#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

#TODO: Test as ant class?

CLASS=$1
shift

CLASSPATH="$basedir/etc:$basedir/target/*:$basedir/lib/*"
XPONENTS_SOLR=./solr

java -Dopensextant.solr=$XPONENTS_SOLR/solr7 -Xmx3g -Xms3g \
        -XX:+UseParallelGC  \
        -Dlogback.configurationFile=$basedir/etc/logback.xml \
        -classpath "$CLASSPATH" $CLASS $*
