if [ ! -d ./log ] ;  then
  mkdir log
fi

SOLR_PORT=7000
SERVER=localhost:$SOLR_PORT
unset http_proxy
unset https_proxy
export noproxy=localhost,127.0.0.1

cur=`dirname $0 `
XPONENTS=`cd -P $cur/..; echo $PWD`

export PYTHONPATH=$XPONENTS/piplib
GAZ_CONF=etc/gazetteer
SOLR_CORE_VER=solr7


if [ ! -d $XPONENTS/piplib ] ; then
   echo "install python first"
   echo "see README"
   exit 1
fi

index_taxcat () {
  SOLR_URL=$1
  echo "Populate nationalities taxonomy in XTax"
  # you must set your PYTHONPATH to include required libraries built from ../python
  python  ./script/gaz_nationalities.py  --taxonomy $GAZ_CONF/filters/nationalities.csv --solr $SOLR_URL --starting-id 0
  sleep 1 

  echo "Populate core JRC Names 'entities' data file, c.2014"
  JRC_DATA=./etc/taxcat/data
  JRC_SCRIPT=./script/taxcat_jrcnames.py
  python $JRC_SCRIPT --taxonomy $JRC_DATA/entities.txt  --solr $SOLR_URL

  # Arbitrary row ID scheme, but we have various catalogs that should have reserved row-id space based on size.
  python script/taxcat_person_names.py   --solr $SOLR_URL --starting-id 20000

  sleep 1 
}


index_gazetteer () {
  SOLR_URL=$1
  echo "Ingest OpenSextant Gazetteer... could take 1 hr" 
  ant -f build.xml index-gazetteer-solrj

  sleep 2 
  echo "Generate Name Variants"
  python ./script/gaz_generate_variants.py  --solr $SOLR_URL --output $GAZ_CONF/additions/generated-variants.json

  # Finally add adhoc entries from JSON formatted files.
  #
  # Yes, this depends on curl. Could re-implement as Ant call.
  # This could be: http://ant-contrib.sourceforge.net/tasks/tasks/post_task.html
  #
  echo "Alter some entries"
  # custom fixes: 'Calif.' abbreviation is not coded properly.
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<delete><query>name_tag:calif+AND+cc:US+AND+adm1:06</query></delete>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit/>"

  echo "Add some others"
  curl --noproxy localhost  "$SOLR_URL/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-US-city-nicknames.json
  curl --noproxy localhost  "$SOLR_URL/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-world-city-nicknames.json
  curl --noproxy localhost  "$SOLR_URL/update?commit=true" \
   -H Content-type:application/json --data-binary @$GAZ_CONF/additions/adhoc-country-names.json

  for f in $GAZ_CONF/additions/generated-*.json ; do
    curl --noproxy localhost  "$SOLR_URL/update?commit=true" \
       -H Content-type:application/json --data-binary @$f
  done

  # When done for the day,  optimize
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<optimize/>"
}

do_start=0
do_clean=0
do_data=0
do_taxcat=1
do_gazetteer=1
proxy='' 
while [ "$1" != "" ]; do
 case $1 in 
  'data')
     do_data=1
     shift
     ;;
  'start')
     do_start=1
     shift
     ;;
  'clean')
     do_clean=1
     shift
     ;;
  'proxy')
     proxy='proxy'
     shift
     ;;
  'taxcat')
     # One or the other. Default is both do taxcat and do gazetter
     do_gazetteer=0
     shift
     ;;
  'gazetteer')
     do_taxcat=0
     shift
     ;;
  esac
done

pushd ../
mvn dependency:copy-dependencies
popd

if [ $do_data -eq 1 ] ; then 
  echo "Acquiring Census data files for names"
  ant -f build.xml $proxy get-gaz-resources
  ant -f build.xml $proxy taxcat-jrc
fi

python ./script/assemble_person_filter.py 

if [ ! -e ./$SOLR_CORE_VER/lib/xponents-gazetteer-meta.jar ] ; then 
   # Collect Gazetteer Metadata: 
   ant -f ./build.xml gaz-meta
fi

sleep 2 

if [ $do_start -eq 1 ]; then 
  if [ $do_clean -eq 1 ]; then 
    ant -f ./build.xml clean init
  fi

  echo "Starting Solr $SERVER"
  echo "Wait for Solr 6.x to load"
  ./mysolr.sh stop $SOLR_PORT
  ./mysolr.sh start $SOLR_PORT
  sleep 2
fi

if [ $do_taxcat -eq 1 ]; then
  index_taxcat http://$SERVER/solr/taxcat
fi
if [ $do_gazetteer -eq 1 ]; then
  index_gazetteer http://$SERVER/solr/gazetteer
fi


echo "Gazetteer and TaxCat built, however Solr $SERVER is still running...." 
echo
echo "Use 'mysolr.sh stop $SOLR_PORT'"
