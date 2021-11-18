#!/bin/bash
# Use Installed Solr $SOLR_INSTALL
SOLR_INSTALL=./solr7-dist
SOLR_HOME=./solr7

PORT=$2
case "$1" in 

 'start')
    $SOLR_INSTALL/bin/solr start  -p $PORT -s $SOLR_HOME  -m 3g -q
 ;;

 'stop')
    $SOLR_INSTALL/bin/solr stop -p $PORT
    # TODO: Deletion of locks forcibly may be an issue.
    # For now we prefer to not be destructive -- if locks remain there is another issue.
 ;;
 *)
   echo "Please just start or stop the Solr server"
esac
