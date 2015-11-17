if [ ! -d ./log ] ;  then
  mkdir log
fi

pushd gazetteer/
echo "Ensure you have downloaded the various Census names files or other name lists for exclusions..."
python ./script/assemble_person_filter.py 
popd

ant -f build-gazetteer.xml _index 

SERVER=localhost:7000

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
