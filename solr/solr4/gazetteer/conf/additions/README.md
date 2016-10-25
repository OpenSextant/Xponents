This additions area allows any user to add additional
place name entries to the gazetteer.  The concept to start is pretty simple:

1. Use the existing geonames in the gazetteer find an entry that best lines up 
   with the name/location variant you are adding.  This works fine for adding
   nick names or other common names of places.

2. Copy the JSON or CSV version of the entry to a data file and manage your additions there.
   JSON is preferred, but CSV is possible.

3. Requirements:  'id' field must be an integer.  
- It must not collide with existing gazetteer name space of 0 .. 20,000,000
Possibly choose an id (based of the solr entry) add 20,000,000 to it. 
Then for any additional items increment that value.

- name_type = A or N for abbreviation or name.
- name_bias = non-zero 0.001 to 1.000, higher the value suggests it is weighted more (more likely an used name)


For example I added some common abbreviations for US cities.
After finding the entries for official places then just tweeked "name" and "name_type" fields
in adhoc-US-city-nicknames.json

Post the data to the server
curl --noproxy localhost  "http://localhost:7000/solr/gazetteer/update?commit=true" \
   -H Content-type:application/json --data-binary @./adhoc-US-city-nicknames.json


When done for the day,  optimize
  curl --noproxy localhost "http://localhost:7000/solr/gazetteer/update?stream.body=<optimize/>"
