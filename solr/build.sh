if [ ! -d ./log ] ;  then
  mkdir log
fi

SERVER=localhost:7000
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

if [ ! -e ./solr4/lib/xponents-gazetteer-meta.jar ] ; then 
   ant gaz-meta
fi


export PYTHONPATH=$XPONENTS/piplib

for core in gazetteer taxcat ; do 
  if [ -d solr4/$core/data/index ] ; then
    rm solr4/$core/data/index/*
  else 
    mkdir -p solr4/$core/data/index
  fi
done

echo "Starting Solr $SERVER"
nohup ./myjetty.sh  start & 

echo "Wait for Solr / Jetty to load"
sleep 5

pushd solr4/gazetteer/
echo "Ensure you have downloaded the various Census names files or other name lists for exclusions..."
python ./script/assemble_person_filter.py 

echo "Populate nationalities taxonomy in XTax"
# you must set your PYTHONPATH to include Extraction/XTax required libraries.
python  ./script/nationalities.py  --taxonomy ./conf/filters/nationalities.csv --solr http://$SERVER/solr/taxcat --starting-id 0
popd


echo "Ingest OpenSextant Gazetteer... could take 1 hr" 
ant index-gazetteer


pushd solr4/gazetteer/
echo "Generate Name Variants"
python ./script/generate_variants.py  --solr http://$SERVER/solr/gazetteer --output ./conf/additions/generated-variants.json
popd


GAZ_CONF=solr4/gazetteer/conf

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
echo "Use 'myjetty.sh stop'"
