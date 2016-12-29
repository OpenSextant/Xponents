#!/bin/bash
MR=`dirname $0`
MR=`cd -P $MR; echo $PWD`



# Build project
echo "Building Project"
mvn install
cp target/opensextant-xponents-mapreduce-0.1-SNAPSHOT.jar xponents-mapreduce.jar

echo "Packaging JARs for Solr, Xponents, Gazetteer resources..." 
# Collect LIBJARs
#     Pay special attention to JARs required to run geotaggers.
# ----------------------------------
mkdir -p $MR/libjars
rm  $MR/libjars/*

# Alternatively get all base JARS from Solr 4.10 WAR
# mkdir -p /tmp/solr-webapp/
# rm -rf /tmp/solr-webapp/*
# pushd /tmp/solr-webapp; 
# jar xf $MR/../solr/webapps/solr.war 
#
# SOLR WAR JARs
# cp ./WEB-INF/lib/*jar $MR/libjars
# REMOVE Hadoop, Joda and other conflicting libs found in Solr 4.x
# rm  libjars/hadoop*jar
# rm  libjars/org.restlet*
# rm  libjars/joda*jar

# RUNTIME JARS:  JTS, Spatial4J, Logging
cp $MR/../solr/lib/ext/*jar $MR/libjars

# Primary dependencies come from Xponents Extraction POM
#  Xponents support JARS; Get current Xponents JARS as well as all dependencies.
cp $MR/../solr/solr4/lib/*jar $MR/libjars
pushd $MR/../Extraction
rm ./lib/*
mvn dependency:copy-dependencies
cp lib/*jar $MR/libjars

# Collect JAR for Gazetteer metadata and filters.
pushd $MR/../solr/solr4/gazetteer/conf;
jar cf $MR/libjars/xponents-gazetteer-meta.jar ./filters/*.* 

cd $MR
rm libjars/javax.servlet-api-3.0.1.jar

# ----------------------------------

echo "Zipping Final Distribution, in ./dist"

mkdir -p ./dist
rm -rf ./dist/*

DATE=`date +%Y%m%d`
zip -r dist/xponents-mr-v$DATE.zip libjars script log* xponents*jar

popd
popd
