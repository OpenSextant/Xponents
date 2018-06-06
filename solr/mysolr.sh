#!/bin/bash
# Use Installed Solr $SOLR_INSTALL
SOLR_INSTALL=./solr7-dist

PORT=$2
case "$1" in 
 'start')
    $SOLR_INSTALL/bin/solr start  -p $PORT -s ./solr6  -m 3g
 ;;
 'stop')
    $SOLR_INSTALL/bin/solr stop -p $PORT

    echo "Now deleting local write locks"
    sleep 2

    for IDX in gazetteer taxcat; do 
      echo INDEX LOCK: $IDX
      rm -i ./solr6/$IDX/data/index/write.lock
    done
 ;;
 *)
   echo "Please just start or stop the Solr server"
esac
