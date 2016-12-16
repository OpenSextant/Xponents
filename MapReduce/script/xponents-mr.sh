#!/bin/bash

if [ -z "$1" ]; then 
  echo "Arguments are required"
  exit 1
fi

date1=20160101
date2=20161231

YEAR=2016
str=`ls  libjars/*jar`
JARS=`echo $str | sed -e 's: :,:g;'`
echo $JARS

OUTPUT=myJob
HDFS_USER=/user/$USER
JOB_OUT=$HDFS_USER/$OUTPUT
hdfs dfs -rm -r $JOB_OUT

# Ahead of time you must push your Solr indices 'xponents-solr.zip' to HDFS:
# 
# hdfs dfs -put  xponents-solr.zip  /data/resources/xponents-solr.zip

SOLR_ARCHIVE=hdfs:///data/resources/xponents-solr.zip

# Note that log4j.properties can be overridden by the hadoop environment.
# The supplemental Log4J configuration file can be used to augment the
# logging configuration directly by our mapper implementations.

# CLASS:
hadoop jar ./xponents-mapreduce-0.1-SNAPSHOT.jar \
       -Dmapreduce.job.classloader=true \
       -Dmapreduce.map.java.opts="-Dsolr.solr.home=./xponents-solr.zip -Xmx1024m -Dverbose:class -Xms256m -Djava.net.preferIPv4Stack=true -Dlog4j.debug=true -Dlog4j.configuration=file:log4j.properties" \
       -Dmapreduce.map.memory.mb=1024 \
       -Dmapreduce.job.cache.archives=$SOLR_ARCHIVE \
       -Dlog4j.configuration=./log4j.properties \
       -Dlog4j.debug \
       -libjars $JARS \
       -files=./log4j.properties,./log4jsupplemental.xml \
       $*

#  Application args could be of the form:
#       --in "/data/path/to/my/data" \
#       --out "$JOB_OUT"
#       --phase xtax \            // Runs XTax TaxonMatcher
#       --phase geotag  \         // Runs PlaceGeocoder
#       --cc $country  \
#       --date-range $date1:$date2 \
#       --log4j-extra-config file:log4jsupplemental.xml
