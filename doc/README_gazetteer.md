
Gazetteer Tasks: Using Xponents SDK to lookup place names.
================================
 
This is a demonstration of the APIs and techniques that support 
adhoc lookups, structured or unstructured. All of this is a work in progress.
We cover first the Java version of some query tools, and then the Python version.

Java Demonstration
------------------

__DEMO COMMAND USAGE:__
```

usage: org.opensextant.examples.XponentsGazetteerQuery [-g] [-h] [-l <arg>] [-p]
_______
 -h,--help

 -g,--geotag         Geotag as unstructured text
 -l,--lookup <arg>   Phrase to lookup (required)
 -p,--parse          Parse first.
_________

# The Xponents Solr index to use is the "solr6" folder in the
# full gazetteer distribution.  That is, in the release there is more than just solr 
#
XPONENTS_SOLR=xponents-solr/solr7

java -classpath  etc/*:dist/*:lib/* -Dopensextant.solr=$XPONENTS_SOLR -Xmx300m -Xms300m  \
  -Dlogback.configurationFile=./etc/logback.xml  \
   org.opensextant.examples.XponentsGazetteerQuery  --lookup "San Francisco, CA, USA"  

```


__JVM ARGS:__ 

Please use the provided `./script/xponents-demo.sh` groovy wrapper as a model
for how to run Xponents applications in general.  Here are some pointers:

* `CLASSPATH` should include `lib/opensextant*jar`. For this demo, the 
 essential library for testing is `opensextant-xponents-examples-<VER>-SNAPSHOT.jar`
* `opensextant.solr` variable is critical to this demonstration
* Logback is Logger implementation (etc/logback.xml), but SLF4J is API used throughout most of the code.
* Memory:
  * Tagger: 2.0 GB is good for one-off testing. Tagger can go as low as 1.2 GB.  For server or long runs, 3.0 GB or more is better.
  * Gazetteer: 0.3 GB -- to just query Solr index, not much memory is needed.
  
```

STRAIGHT LOOKUP
--------------------------------

  java ...XponentsGazetteerQuery --lookup "San Francisco" 

  /* Try parsing the given lookup as-is.  You get possibly 100s of matches -- it is not 
   * up to the API to choose which.
   */
INFO  o.o.examples.XponentsGazetteerQuery - NAME straight lookup: 'San Francisco'
INFO  o.o.examples.XponentsGazetteerQuery - =============================
INFO  o.o.examples.XponentsGazetteerQuery - 	first 10...
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (59, PH, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (21, PH, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (H2, PH, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (03, PH, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (N3, PH, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (08, SV, ADM2)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (08, SV, ADM3)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (06, US, ADMD)
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (33, CO, ADM2)


PARSING LOOKUP ASSUMING STRUCTURE
---------------------------------
  java ...XponentsGazetteerQuery --lookup "San Francisco, CA, USA" -parse

  /* This assumes a simple format of CITY, PROV, COUNTRY
   * See source code for details, as your parsing may differ.
   */
INFO  o.o.examples.XponentsGazetteerQuery - NAME parsed, then various lookups
INFO  o.o.examples.XponentsGazetteerQuery - =============================
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco (06, US, PPL) @ (37.7750,-122.4194)



GEOTAGGING FREE TEXT
--------------------------------

  java ...XponentsGazetteerQuery --lookup "San Francisco, CA, USA" --geotag

  /* This takes in the text as a blob of phrases and tries to find best possible matches.
   * Geocoding what it can.
   */ 

INFO  o.o.examples.XponentsGazetteerQuery - TEXT BLOB geotagged and all findings listed.
INFO  o.o.examples.XponentsGazetteerQuery - =============================
INFO  o.o.examples.XponentsGazetteerQuery - 	Francisco, Not a place. Type=taxon
INFO  o.o.examples.XponentsGazetteerQuery - 	San Francisco, Geocoded to San Francisco (06, US, PPL), score=8.18

```




A Python alternative 
===============================
Less code. But this demonstration is not as complete as the Java one.
Again, we are just demonstrating some possibilities with the APIs.
Here the Solr server will be running and we use pysolr to RESTfully query the gazetteer.
(Above, the Java API was using an internal Solr server.)

1. Install the Opensextant python lib.

```
   XPT=$PWD
   cd ./python/
   python3 ./setup.py sdist 

   # Or just install distribution:
   pip3 install --target $XPT/piplib ./dist/opensextant-1.x.x.tar.gz 

   # Set path
   export PYTHONPATH=$XPT/piplib

```
1. Run the Solr server,  `cd ./solr; mysolr.sh start 7000 `


1.  Run the demo script, `python3 -m opensextant.gazetteer`

```
python3 -m opensextant.gazetteer  --solr http://localhost:7000/solr/gazetteer --lookup "San Francisco, CA, US"  --parse

FOUND 10. Showing top 25
San Francisco (USGS273473), US, 34.4069, -118.6120
City of San Francisco (USGS2411786), US, 37.7782, -122.4425
San Francisco County (USGS277302), US, 37.7782, -122.4425
San Francisco Division (USGS1935284), US, 37.7782, -122.4425
South San Francisco Division (USGS1935322), US, 37.6450, -122.4120
Rancho San Francisco (USGS273473), US, 34.4069, -118.6120
S. San Francisco Division (USGS1935322), US, 37.6450, -122.4120
City of South San Francisco (USGS2411942), US, 37.6527, -122.3434
San Francisco De Las Llagas (USGS234626), US, 37.0741, -121.6227
Rancho San Francisco de las Llagas (USGS234626), US, 37.0741, -121.6227
```

