if [ ! -d ./log ] ;  then
  mkdir log
fi

SERVER=localhost:7000
unset http_proxy
unset https_proxy
export noproxy=localhost,127.0.0.1

pushd gazetteer/
echo "Ensure you have downloaded the various Census names files or other name lists for exclusions..."
python ./script/assemble_person_filter.py 

echo "Populate nationalities taxonomy in XTax"
# you must set your PYTHONPATH to include Extraction/XTax required libraries.
python  ./script/nationalities.py  --taxonomy ./conf/filters/nationalities.csv --solr http://$SERVER/solr/taxcat --starting-id 0
popd

ant -f build-gazetteer.xml _index 


# Finally add adhoc entries from JSON formatted files.
#
# Yes, this depends on curl. Could re-implement as Ant call.
# This could be: http://ant-contrib.sourceforge.net/tasks/tasks/post_task.html
#
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @./gazetteer/conf/additions/adhoc-US-city-nicknames.json
curl --noproxy localhost  "http://$SERVER/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @./gazetteer/conf/additions/adhoc-country-names.json


# When done for the day,  optimize
curl --noproxy localhost "http://$SERVER/solr/gazetteer/update?stream.body=<optimize/>"
