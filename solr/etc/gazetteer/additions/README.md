This additions area allows any user to add additional
place name entries to the gazetteer.  The concept to start is pretty simple:

1. Use the existing geonames in the gazetteer find an entry that best lines up 
   with the name/location variant you are adding.  This works fine for adding
   nick names or other common names of places.

2. Insert new rows in  the `adhoc-placenames.csv`

3. Requirements:  'id' field must be an integer.  
- It must not collide with existing gazetteer name space of 0 .. 20,000,000
Possibly choose an id (based of the solr entry) add 20,000,000 to it. 
Then for any additional items increment that value.

- name_type = A or N for abbreviation or name.
- name_bias = non-zero 0.001 to 1.000, higher the value suggests it is weighted more (more likely an used name)
- source = "X" for Xponents


4. Wait til you are ready to run full `build-sqlite-master.sh test`  OR attempt this 
 with the Python routine: `python3 ./script/gaz_generate_variants.py --db test.sqlite`

