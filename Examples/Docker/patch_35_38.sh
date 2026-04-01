#!/bin/bash

SOLR_EXT=xponents-solr/solr7-dist/server/lib/ext
WEB_APP=xponents-solr/solr7-dist/server/solr-webapp/webapp/WEB-INF/lib

for JAR in \
    jts-core-1.15.0.jar \
    log4j-1.2-api-2.17.1.jar \
    log4j-api-2.17.1.jar \
    log4j-core-2.17.1.jar \
    log4j-slf4j-impl-2.17.1.jar \
    spatial4j-0.7.jar; do

    echo  $JAR
    if [ -e $SOLR_EXT/$JAR ]; then
      rm $SOLR_EXT/$JAR ;
    else
      echo "--------- Not Removed. $JAR"
    fi
done


if [ -d xponents-solr/solr7-dist/dist/ ] ; then
  rm -rf xponents-solr/solr7-dist/dist
fi

for JAR in $WEB_APP/zookeeper-3.4.14.jar $WEB_APP/spatial4j-0.7.jar ; do
if [ -e "$JAR" ] ; then
  rm $JAR
else:
  echo "No APP jar $JAR"
fi
done


V_LOG4J=2.25.4
for JAR in \
    jts-core-1.19.0.jar \
    jts-io-common-1.19.0.jar \
    log4j-1.2-api-${V_LOG4J}.jar \
    log4j-api-${V_LOG4J}.jar \
    log4j-core-${V_LOG4J}.jar \
    log4j-slf4j-impl-${V_LOG4J}.jar \
    spatial4j-0.8.jar ; do

    echo $JAR
    if [ -e ./lib/$JAR ]; then
        cp ./lib/$JAR $SOLR_EXT/$JAR;
    else
        echo "------ Missing $JAR"
    fi
done


# Copy over Jackson and other updated libs?
cp lib/zookeeper*jar $WEB_APP/
cp lib/spatial4j*jar $WEB_APP/

echo "REPORT ON JARS"
echo "============WEB APP===="
ls -l $WEB_APP/*jar
echo
echo
echo "============EXT===="
ls -l $SOLR_EXT/*jar