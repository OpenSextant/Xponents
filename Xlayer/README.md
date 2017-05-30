Xlayer:  Xponents REST service
==============================

You can pronounce this like "Zlayer" or X-Layer or Slayer.
Using primarily the Restlet Framework (http://restlet.org)
we have a very basic ability to provision the Xponent extraction 
as REST services.  

version 0.1
-----------
- Expose PlaceGeocoder capability: geocode text, yielding 
  places, coordinates, countries, and matched non-places organizations and person names


Execution
--------------

This is a server-side capability, but you can write your own BAT, Groovy, Ant or other script
to invoke the main Restlet server as shown in this script:

    ./script/xlayer.sh  8890 start 
  .... 
    ./script/xlayer.sh  8890 stop 

For now the script takes a port number, running a HTTP server on that port, with no security.
And then also the control command start or stop. 

Alternatives:  I considered making Solr request handlers to accompany the underlying
Solr Text Tagger (solr /tag handler).   However, that seemed limiting, because not all extraction
tools and techniques in Xponents are Solr-based.   Solr as a server, though, is quite strong.

Now the server is running, access it at:

    http://localhost:8890/xlayer/rest/process ? docid = .... & text = ...

GET or POST operations are supported.  With GET, use the parameters as noted above.
With POST, use JSON to formulate your input as a single JSON object, e.g., "{'docid':..., 'text':....}"
Additionally, features and tuning parameters will be supported.


Stopping Cleanly
------------------
    curl "http://localhost:8890/xlayer/rest/process?cmd=stop"

The recipe is ```SERVER/xlayer/rest/process?cmd=stop```
as shown in the xlayer.sh (or .BAT) script


Implementation
---------------
Please refer to Xponents Extraction module.  The tagging/extracting/geocoding is done by PlaceGeocoder Java API.
(https://github.com/OpenSextant/Xponents/blob/master/Extraction/src/main/java/org/opensextant/extractors/geo/PlaceGeocoder.java)

The general design of the RestLet applications here is depicted below in Figure 1. 
The XlayerServer is a container that manages the overall runtime environement.
The XlayerRestlet is an application inside the container.  A Restlet Application typically
has multiple services (ServerResources) mapped to URLs or URL patterns.

XponentsGeotagger is a wrapper around the PlaceGeocoder class. The wrapper manages the digestion of client requests 
in JSON, determines client's feature requests to hone processing and formatting, and finally produces a JSON formatted response.

![Figure 1](./doc/xlayer-xgeo-server-example.png "Extending Xlayer using Restlet")


When building an Xlayer application, client-side or server-side, please understand the general CLASSPATH needs:

* Xponents JARs -- APIs, Xlayer main and test code.  Use ``` mvn dependency:copy-dependencies``` and then see ./lib/opensextant-*.jar. The 
  essential items are listed in order of increasing dependency:
  * opensextant-xponents-basics-2.9.8.jar
  * opensextant-xponents-patterns-2.9.8.jar
  * opensextant-xponents-2.9.8.jar
  * opensextant-xponents-xlayer-0.1.jar
  * opensextant-xponents-xlayer-0.1-tests.jar
* Configuration items foldered in ```./etc``` or similar folder in CLASSPATH
* Logging configuration -- Logback is used in most Xponents work, but only through SLF4J. If you choose another logger implementation, 
  SLF4J is your interface.   Copy and configure ```Xlayer/src/test/resources/logback.xml``` in your install.  As scripted, ```./etc/``` is the location for this item.
* Geocoding metadata -- ./etc/ should contain xponents-gazetteer-meta.jar (result of normal full Xponents build. Specifically, ```ant -f ./solr/build.xml gaz-meta```). 
  This resource is required for Java Xponents usage or server-side development, but not client REST usage necessarily.


REST Interface
---------------
For example,  run the Python client to see how easy it is to call the service above.
Please note a Java version, XLayerClient, also exists in the src/main folder, with
test code in src/test

Required Python packages: requests and simplejson

    PYTHONPATH=/my/python 
    python  opensextant/xlayer.py  

INPUT:
* 'docid' - an optional identifier for this text
* 'text'  - UTF-8 text buffer

OUTPUT:
* 'response'     - status, numfound
* 'annotations'  - an array  of objects. 

Annotation schema 
* matchtext      - text span matched
* type           - type of annotation, one of 'place', 'country', 'coordinate', 'org', 'person'
* offset         - character offset into text buffer where text span starts
* length         - length of text span.  end offset = offset + length
* method         - method tag identifying the means by which this annotation was derived.

Geographic annotations additionally have:
* cc             - country code
* lat, lon       - WGS84 latitude, longitude
* prec           - precision inferred from text (coordinate digits) or gazetteer feature type
* adm1           - ADM1 boundary code, ideally ISO nomenclature, but still often FIPS
* feat_class     - Geonames class. One of A, P, H, R, T, S, L, V.  
* feat_code      - Geonames designation code. This is more specific than the class.
* confidence     - A relative level of confidence.  Subject to change; A scale of 0 to 100, where confidence < 20 is not reliable or lacks evidence.

Non-Geographic annotations have:
* catalog        - attribution to a data source or catalog containing the reference data
* taxon          - the ID of a normalized catalog entry for the match, e.g., `person = { text:'Rick Springfield', taxon:'person.Richard Springfield', catalog:'Rock-Legends'}`


References:
* NGA, http://geonames.nga.mil/gns/html/countrycodes.html
* Geonames.org, http://download.geonames.org/export/dump/featureCodes_en.txt
* Geonames.org, http://www.geonames.org/data-sources.html


Example JSON Output:
--------------------

```
   from opensextant.xlayer import XlayerClient

   # Ah,... Demo Client, really.
   xtractor = XlayerClient(serverURL)

   # Python call -- Send the text, process the text, print the JSON response to console.
   xtractor.process('test doc#1',
       'Where is 56:08:45N, 117:33:12W?  Is it near Lisbon or closer to Saskatchewan?'
       + 'Seriously, what part of Canada would you visit to see the new prime minister discus our border?'
       + 'Do you think Hillary Clinton or former President Clinton have an opinion on our Northern Border?')

   # NOTE -- This is draft 0.1;  We can certainly make a more complete Client API using our own 
   # python or Java data model and API.  This demo client demonstrates primarily the connectivity.
   #
```
 

	 {
	  "response": {
	    "status": "ok",
	    "numfound": 6
	  }
	  "annotations": [
	    {
	      /* A COORD 
	       * ~~~~~~~~~~~~~~~~~~~
	       */
	      /* common annotation items */
	      "text": " 56:08:45N, 117:33:12W",
	      "type": "coordinate",
	      "method": "DMS-01a",
	      "length": 22,
	      "offset": 8,      
	      /*  annotation-specific items: */
	      "cc": "CA",
	      "lon": -117.55333709716797,
	      "prec": 15,
	      "feat_code": "COORD",
	      "lat": 56.145835876464844,
	      "adm1": "01",
	      "feat_class": "S"
	    },
	    {
	      /* A PERSON 
	       * ~~~~~~~~~~~~~~~~~~~
	       */
	      "text": "Hillary Clinton",
	      "type": "taxon",
	      "offset": 185,
	      "length": 15,
	      /*  annotation-specific items: */
	      "taxon": "Person.Hillary Rodham Clinton",
	      "catalog": "JRC"
	    },
	    {
	      /* A PLACE 
	       * ~~~~~~~~~~~~~~~~~~~
	       */
	      "confidence": 60,
	      "cc": "PT",
	      "text": "Lisbon",
	      "lon": -9.13333,
	      "prec": 10000,
	      "length": 6,
	      "feat_code": "PPLC",
	      "offset": 44,
	      "lat": 38.71667,
	      "type": "place",
	      "adm1": "14",
	      "feat_class": "P"
	    },
	    {
	      "confidence": 73,
	      "cc": "CA",
	      "text": "Saskatchewan",
	      "lon": -106,
	      "prec": 50000,
	      "length": 12,
	      "feat_code": "ADM1",
	      "offset": 64,
	      "lat": 54,
	      "type": "place",
	      "adm1": "11",
	      "feat_class": "A"
	    },
	    {
	      /* A COUNTRY 
	       * ~~~~~~~~~~~~~~~~~~~
	       */
	      "cc": "CA",
	      "text": "Canada",
	      "length": 6,
	      "type": "country",
	      "offset": 101
	    },
	    {
	      "confidence": 93,
	      "cc": "SA",
	      "text": "Northern Border",
	      "lon": 42.41667,
	      "prec": 50000,
	      "length": 15,
	      "feat_code": "ADM1",
	      "offset": 252,
	      "lat": 30.25,
	      "type": "place",
	      "adm1": "15",
	      "feat_class": "A"
	    }
	  ]
	 }




Using Xlayer API and More
=========================


[XlayerClient demo](../Extraction/src/main/python/opensextant/xlayer.py "Xlayer demo client") provides the real 
basics of how a client calls the server.   A richer illustration of how to create a client and make use of 
Xponents APIs is here in the Java XlayerClient:

* [src/main/java/XlayerClientTest.java](src/test/java/XlayerClientTest.java) - Test main program. Compile and include ./target/*-tests.jar in CLASSPATH
* [src/main/java/org/opensextant/xlayer/XlayerClient.java](src/main/java/org/opensextant/xlayer/XlayerClient.java) - a basic Client, using Restlet
* [src/main/java/org/opensextant/xlayer/Transforms.java](src/main/java/org/opensextant/xlayer/Transforms.java) - a basic data adapter for getting REST response back into API objects.



