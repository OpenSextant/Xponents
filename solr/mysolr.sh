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

    echo "Now deleting local write locks"
    sleep 2

    for IDX in gazetteer taxcat postal; do 
      echo INDEX LOCK: $IDX
      rm -i $SOLR_HOME/$IDX/data/index/write.lock
    done
 ;;
 *)
   echo "Please just start or stop the Solr server"
esac
