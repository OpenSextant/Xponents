
# Use Installed Solr as "./solr6-dist"
SOLR_INSTALL=./solr6-dist

PORT=$2
case "$1" in 
 'start')
    $SOLR_INSTALL/bin/solr start  -p $PORT -s ./solr6 
    # -Dsolr.lock.type=none
 ;;
 'stop')
    $SOLR_INSTALL/bin/solr stop -p $PORT
 ;;
 *)
   echo "Please just start or stop the Solr server"
esac
