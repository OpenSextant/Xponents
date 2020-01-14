#!/bin/bash
#
echo "OpenSextant Gazetter Execution"
echo "NOTES: https://github.com/OpenSextant/Xponents/,  see ./solr/README.md"

cd ../../Gazetteer
export JAVA_HOME=${JAVA_HOME:-/opt/java8}
export PATH=$JAVA_HOME/bin:$PATH

DATE=`date +%Y%m%d`
GAZETTEER=`pwd`
LOGPATH=$GAZETTEER/GazetteerETL/Logs/gaz-build,${DATE}.log
ERR_LOGPATH=$GAZETTEER/GazetteerETL/Logs/gaz-build,${DATE},errors.log

# USE BASH, direct:
# Replicated build.properties for Kettle here.
KETTLE_HOME=${KETTLE_HOME:-/opt/Kettle6}
SCRIPT=kitchen.sh
export PENTAHO_DI_JAVA_OPTIONS="-Xmx3g -Xms3g"
$KETTLE_HOME/$SCRIPT -level Minimal -file $GAZETTEER/GazetteerETL/BuildMergedGazetteer.kjb  > $LOGPATH

