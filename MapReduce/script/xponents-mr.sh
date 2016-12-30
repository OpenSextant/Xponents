#!/bin/bash

if [ -z "$1" ]; then 
  echo "2 Arguments are required"
  echo "$0 <phase> <hdfs-input>"
  exit 1
fi

str=`ls  libjars/*jar`
JARS=`echo $str | sed -e 's: :,:g;'`
echo $JARS

phase=$1
input=$2

OUTPUT=xponents-mr-test-$phase
HDFS_USER=/user/$USER
JOB_OUT=$HDFS_USER/$OUTPUT
hdfs dfs -rm -r $JOB_OUT

# 1. Copy the resulting JAR from the maven build to 'xponents-mapreduce.jar'
#
# 2. Ahead of time you must push your Solr indices 'xponents-solr.zip' to HDFS:
#    First repack the solr index in a zip archive
#    cd Xponents/solr/solr4
#    zip -r xponents-solr4 ./
#    hdfs dfs -put  xponents-solr4.zip  /data/resources/xponents-solr4.zip
#
SOLR_ARCHIVE=xponents-solr4.zip
HDFS_SOLR_ARCHIVE=hdfs:///data/resources/$SOLR_ARCHIVE

# Note that log4j.properties can be overridden by the hadoop environment.
# The supplemental Log4J configuration file can be used to augment the
# logging configuration directly by our mapper implementations.

# Extra logging debug options.
# -Dlog4j.debug \
# -Dmapreduce.map.java.opts=" ......-Dverbose:class -Dlog4j.debug=true ....."
VERBOSITY="-Dverbose:class -Dlog4j.debug=true"
VERBOSITY="-Dlog4j.debug=false"
hadoop jar ./xponents-mapreduce.jar \
       -Dmapreduce.job.name=xponents-mr-test \
       -Dmapreduce.job.classloader=true \
       -Dmapreduce.map.java.opts="-Dsolr.solr.home=$SOLR_ARCHIVE -Xmx1200m -Xms512m -Djava.net.preferIPv4Stack=true $VERBOSITY -Dlog4j.configuration=file:log4j.properties" \
       -Dmapreduce.map.memory.mb=1024 \
       -Dmapreduce.job.cache.archives=$HDFS_SOLR_ARCHIVE \
       -Dlog4j.configuration=file:log4j.properties \
       -libjars $JARS \
       -files=./log4j.properties,./log4jsupplemental.xml \
       --log4j-extra-config file:log4jsupplemental.xml \
       --out $JOB_OUT \
       --phase $phase \
       --in $input

#  Application args could be of the form:
#       --in "/data/path/to/my/data" \
#       --out "$JOB_OUT"
#       --phase xtax \            // Runs XTax TaxonMatcher
#       --phase geotag  \         // Runs PlaceGeocoder
#       --log4j-extra-config file:log4jsupplemental.xml
