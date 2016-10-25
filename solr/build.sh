if [ ! -d ./log ] ;  then
  mkdir log
fi

SERVER=localhost:7000
unset http_proxy
unset https_proxy
export noproxy=localhost,127.0.0.1

cur=`dirname $0 `
XPONENTS=`cd -P $cur/..; echo $PWD`

if [ ! -d $XPONENTS/python ] ; then
   echo "install python first"
   echo "see README"
   exit 1
fi

export PYTHONPATH=$XPONENTS/python

for core in gazetteer taxcat ; do 
  if [ -d $core/data/index ] ; then
    rm ./$core/data/index/*
  else 
    mkdir -p $core/data/index
  fi
done

echo "Starting Solr http/7000"
nohup ./run.sh  & 


pushd gazetteer/
echo "Ensure you have downloaded the various Census names files or other name lists for exclusions..."
python ./script/assemble_person_filter.py 

echo "Populate nationalities taxonomy in XTax"
# you must set your PYTHONPATH to include Extraction/XTax required libraries.
python  ./script/nationalities.py  --taxonomy ./conf/filters/nationalities.csv --solr http://$SERVER/solr/taxcat --starting-id 0


echo "Generate Name Variants"
python ./script/generate_variants.py  --solr http://$SERVER/solr/gazetteer --output ./conf/additions/generated-variants.json

popd

ant index-gazetteer


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
   -H Content-type:application/json --data-binary @./gazetteer/conf/additions/adhoc-US-city-nicknames.json
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @./gazetteer/conf/additions/adhoc-world-city-nicknames.json
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @./gazetteer/conf/additions/adhoc-country-names.json

for f in ./gazetteer/conf/additions/generated*json ; do
    curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
       -H Content-type:application/json --data-binary @$f
done


# When done for the day,  optimize
curl --noproxy localhost "http://$SERVER/solr/gazetteer/update?stream.body=<optimize/>"


echo "Gazetteer and TaxCat built, however Solr http/7000 is still running...." 
