#!/bin/bash
MR=`dirname $0`
MR=`cd -P $MR; echo $PWD`



# Build project
echo "Building Project"
mvn install
cp target/xponents-mapreduce-0.1.jar xponents-mapreduce.jar

echo "Packaging JARs for Solr, Xponents, Gazetteer resources..." 
# Collect LIBJARs
#     Pay special attention to JARs required to run geotaggers.
# ----------------------------------
mkdir -p $MR/libjars
rm  $MR/libjars/*

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
# Conflict with Solr servlet API:
rm libjars/javax.servlet-api-3.0.1.jar
# GISCore not used; It supports formatting output and since we output only JSON, its not needed here.
rm libjars/giscore*jar 
# Logback for now interferes with choice of Logging package
rm libjars/logback*jar 

mvn dependency:copy-dependencies
for LIB in json-lib ezmorph commons-beanutils; do 
  cp target/dependency/$LIB*.jar ./libjars/
done
# ----------------------------------

echo "Zipping Final Distribution, in ./dist"

mkdir -p ./dist
rm -rf ./dist/*

DATE=`date +%Y%m%d`
zip -r dist/xponents-mr-v$DATE.zip libjars script log* xponents*jar

popd
popd
