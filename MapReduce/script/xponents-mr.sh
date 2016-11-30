#!/bin/bash

if [ -z "$1" ]; then 
  echo "Arguments are required"
  exit 1
fi

date1=20160101
date2=20161231

YEAR=2016
str=`ls  jars/*jar`
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

# CLASS:
hadoop jar ./xponents-mapreduce-0.1-SNAPSHOT.jar \
       -Dmapreduce.job.classloader=true \
       -Dmapreduce.map.java.opts="-Dsolr.solr.home=./xponents-solr.zip -Xmx512m -Dverbose:class -Xms256m -Djava.net.preferIPv4Stack=true" \
       -Dmapreduce.map.memory.mb=512 \
       -Dmapreduce.job.cache.archives=$SOLR_ARCHIVE \
       -Dlog4j.configuration=./log4j.properties \
       -libjars $JARS \
       -files=./log4j.properties \
       $*

#  Application args could be of the form:
#       --in "/data/path/to/my/data" \
#       --out "$JOB_OUT"
#       --phase xtax \            // Runs XTax TaxonMatcher
#       --phase geotag  \         // Runs PlaceGeocoder
#       --cc $country  \
#i      --date-range $date1:$date2  
