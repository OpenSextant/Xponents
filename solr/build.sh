if [ ! -d ./log ] ;  then
  mkdir log
fi

CMD=$1
OPT=$2
SOLR_PORT=7000
SERVER=localhost:$SOLR_PORT
unset http_proxy
unset https_proxy
export noproxy=localhost,127.0.0.1

cur=`dirname $0 `
XPONENTS=`cd -P $cur/..; echo $PWD`

if [ ! -d $XPONENTS/piplib ] ; then
   echo "install python first"
   echo "see README"
   exit 1
fi

export PYTHONPATH=$XPONENTS/piplib
GAZ_CONF=etc/gazetteer
SOLR_CORE_VER=solr6

echo "Ensure you have downloaded the various Census names files or other name lists for exclusions..."
python ./script/gaz_assemble_person_filter.py 

if [ ! -e ./$SOLR_CORE_VER/lib/xponents-gazetteer-meta.jar ] ; then 
   # Collect Gazetteer Metadata: 
   ant -f ./solr6-build.xml gaz-meta
fi

sleep 2 

if [ "$CMD" = 'start' ]; then 
  if [ "$OPT" = 'clean' ]; then 
    ant -f ./solr6-build.xml clean init
  fi

  echo "Starting Solr $SERVER"
  echo "Wait for Solr 6.x to load"
  ./mysolr.sh stop $SOLR_PORT
  ./mysolr.sh start $SOLR_PORT
  sleep 2
fi

echo "Populate nationalities taxonomy in XTax"
# you must set your PYTHONPATH to include Extraction/XTax required libraries.
python  ./script/gaz_nationalities.py  --taxonomy $GAZ_CONF/filters/nationalities.csv --solr http://$SERVER/solr/taxcat --starting-id 0

sleep 2 

echo "Populate core JRC Names 'entities' data file, c.2014"
JRC_DATA=./etc/taxcat/data
JRC_SCRIPT=./script/taxcat_jrcnames.py
python $JRC_SCRIPT --taxonomy $JRC_DATA/entities.txt  --solr http://$SERVER/solr/taxcat

# Arbitrary row ID scheme, but we have various catalogs that should have reserved row-id space based on size.
python script/taxcat_person_names.py   --solr http://$SERVER/solr/taxcat --starting-id 20000

sleep 2 

echo "Ingest OpenSextant Gazetteer... could take 1 hr" 
ant -f solr6-build.xml index-gazetteer-solrj

sleep 2 
echo "Generate Name Variants"
python ./script/gaz_generate_variants.py  --solr http://$SERVER/solr/gazetteer --output $GAZ_CONF/additions/generated-variants.json

# Finally add adhoc entries from JSON formatted files.
#
# Yes, this depends on curl. Could re-implement as Ant call.
# This could be: http://ant-contrib.sourceforge.net/tasks/tasks/post_task.html
#
echo REMOVES
# custom fixes: 'Calif.' abbreviation is not coded properly.
curl --noproxy localhost "http://$SERVER/solr/gazetteer/update?stream.body=<delete><query>name_tag:calif+AND+cc:US+AND+adm1:06</query></delete>"
curl --noproxy localhost "http://$SERVER/solr/gazetteer/update?stream.body=<commit/>"

curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-US-city-nicknames.json
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-world-city-nicknames.json
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-country-names.json

for f in $GAZ_CONF/additions/generated-*.json ; do
    curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
       -H Content-type:application/json --data-binary @$f
done


# When done for the day,  optimize
curl --noproxy localhost "http://$SERVER/solr/gazetteer/update?stream.body=<optimize/>"

echo "Gazetteer and TaxCat built, however Solr $SERVER is still running...." 
echo
echo "Use 'mysolr.sh stop $SOLR_PORT'"
