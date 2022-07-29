Xlayer:  Xponents REST service
==============================

Xlayer (pr. "X Layer") is an older name for the Xponents geotagger web service.  We just call it "Xponents API" now. Under the hood the 
service is implemented in Java using Restlet framework and provides 
functionality described [here in README_REST_API.md](README_REST_API.md). The remainder of this page describes the Python client and more details related to the server development.  The REST README focuses on the docker instance and the web service specification. 


Execution
--------------

In the Xponents project or distribution you'll find the Xponents API server script:

```shell
    ./script/xlayer-server.sh  start 8080
    .... 
    ./script/xlayer-server.sh  stop 8080 
```

Alternatively, using Docker Compose:

```shell
   # In source tree you'll find docker-compose.yml 
   # You do not need to check out the project to use this.
   # Copy ../Examples/Docker/docker-compose.yml
   
   docker-compose up -d xponents
```

With the server running you will be able to test out the functions to 
`process` text and control the server (`ping` and `stop`).

    GET  http://localhost:8080/xlayer/rest/process ? docid= & text= & features =
    POST http://localhost:8080/xlayer/rest/process  JSON body { docid =, text =, features = }  
    GET  http://localhost:8080/xlayer/rest/control/ping
    GET  http://localhost:8080/xlayer/rest/control/stop


However you have started the API server, here are some test scripts to interact 
with it -- you'll need copies of the scripts from source tree here or from the distribution.  The docker image has copies of these scripts for testing.


* `./test/test-xlayer-curl.sh PORT FILE`  - requires cURL. **[script](https://github.com/OpenSextant/Xponents/blob/master/test/test-xlayer-curl.sh)** 
* `./test/test-xlayer-java.sh PORT FILE`  - requires Java and libraries in ./lib distro
* `./test/test-xlayer-python.sh PORT FILE`  - requires Python 3 `opensextant` module described below.  **[script](https://github.com/OpenSextant/Xponents/blob/master/test/test-xlayer-python.sh)** 

Xponents API Python Client
------------------

Please consider using this Python client to interact with the API server -- as a 
reference implementation of the data model and processing/extraction services it is
as complete as it needs to be.  If you need or want to work in another language and 
want to contribute your implementation please file an issue in the GitHub project for Xponents.
This page focuses on using the Python API to demonstrate the value of the Xponents
geotagging and extraction approach. 

[ **[Latest Release](https://github.com/OpenSextant/Xponents/releases/tag/python-v1.4.7)** ] [ **[Python API reference](../doc/pydoc/opensextant.xlayer.html)** ] [ **[Python Source](https://github.com/OpenSextant/Xponents/blob/master/python/opensextant/xlayer.py)** ]

Install it, `pip3  install opensextant-1.4.*.tar.gz`.  You now can make use of the `opensextant.xlayer` module. 
Here is a synopsis of the `XlayerClient` in action.  You can also refer to the client test code 
where [play_rest_api.py](../python/tests/play_rest_api.py) gets playful with the basics. 

```python

# Setup
from opensextant.xlayer import XlayerClient
client = XlayerClient(url)   # url = 'host:port'  or the full 'http://host:port/xlayer/rest/process'  URL.

# For each block of text:
textbuffer = " ..... "
result  = client.process("ab8ef7c...",             # document ID 
                         textbuffer,               # your raw text input
                         features=["geo",          # default is only "geo". If you want extracted features, add as you wish. 
                                   "postal", 
                                   "dates", 
                                   "taxons", 
                                   "filtered_out"])

# result is a simple array of opensextant.TextMatch
# where geotags are PlaceCandidate classes, which are subclasses of TextMatch

```

Let's take this step by step.  First, if you like looking at source code (see Source link above), 
`opensextant.xlayer` module is a full main program that provides additional test capabilities
for command line use or to craft your own post-processing script.

Here is a exposé of the key data classes - `TextMatch` and `PlaceCandidate`:

* TextMatch represents a span of text emitted by a particular NLP routine.  The core class attributes are:
  * `label`, `text`, `start`, `end`, `attrs`  (Describing an entity of type `label` and a value `text`; attributes may be empty)
  * `filtered_out` class attribute is True or False.  `filtered_out=True` indicates that the span was tagged, but 
  the API post-processed that tag as invalid or noise for some reason.
* PlaceCandidate is a subclass of TextMatch adding more geographic class attributes:
  * `place`, `confidence`, `attrs` -- A Place object (with lat/lon) was inferred with the confidence 
    and significant collection of gazetter attributes.  Other class attributes are available for expert use.


Below "Part A" is looping through all TextMatches generically. 
"Part B" is much more speicific to logic for geotags aka PlaceCandidate objects. 
For Part B, look at the advanced geoinferencing topics that follow.  More to come.  WHY?   Your objective with 
geotagging is usually to present these results in a spatial visualization or capture in a spatial database. All this
metadata work will help you do that effectively.


```python

from opensextant import TextMatch, PlaceCandidate, Place

# Let's say in the result array a TextMatch item had been created as such:
#
#   t = TextMatch("Sana'a Cafe", 55, 67)
#
# Name of a business mentioned at character span (55, 67)
# Additional metadata would be assigned internally.  See the loop example below:

# Part A -- Generic Text span interpretation
for t in result:
   # 
   if t.filtered_out:
     print("We'll ignore this tag.")
   else:     
     print(f"Match {t.id} of type {t.label}:  {t.text} at span ({t.start}, {t.end})")
     # 
     # NOTE:  For all TextMatch and subclasses `.attrs` field will contain additional metadata, including:
     #    - patterns -- ID of regex pattern
     #    - place    -- gazetter metadata and inferred precision, related geography, etc.
     #    - taxon    -- name of catalog and other metadata from taxonmomic key phrases
     # 
     print("\tATTRS", t.attrs)


# Part B -- Geo-inferencing interpretation. Looking at Countries, Placenames, Coordinates, and Postal entries
#
# Combine the loop logic as you need. This variation focuses on PlaceCandidate specifically 
for t in result:
  if not t.filtered_out and isinstance(t, PlaceCandidate):
    
    # PlaceCandidate is either a Country (or non-Coordinate location) or a Coordinate-bearing location.
    # Be sure to know the distinction. 
    if t.is_country:
      print("COUNTRY", t.text, t.place.country_code)
    else:
      print("GEOTAG", t.text)
      geo = t.place
      feature = f"{geo.feature_class}/{geo.feature_code}" 
      print(f"\tFEAT={feature} LL=({geo.lat}, {geo.lat}) in country {geo.country_code}")                  

```

## Expert Topics in Xponents Geoinferencing

These topics are addressed here because you as the 
consumer of the Xponents API output need to interpret what is found in text. 
This is the *inferencing* aspect of all
this -- if you don't take some action to interpret the output intelligently there really is no value or credibility
of the output downstream.  Review these topics for a flavor of that next level of inference follow.

### Feature Class Use

Be aware that all sorts of geographic references are returned by the API service along this spectrum of 
  about 10 M to 100 KM resolution: coordinates, postal, landmark, city, district, province, country and even 
  region or body of water.  Feature metadata (`feature_class`, `feature_code`) help distinguish types of features.

### Gazetteer Metadata Use
Feature geographic metadata is encoded to ISO-3166 standards, so make use of it on these fields as noted in the 
  schema below in REST Interface.

### Precision Error and Location Uncertainty

Consider these aspects of inferred locations that have `lat`, `lon` tuple or a valid `PlaceCandidate.place` object:

- **Precision** (`precision` attribute or `prec` key) -- the categorical error (in meters)  for the feature class. e.g., 
a province mention is typically on the order of +/- 50 KM although provinces vary wildly by size
- **Confidence** (`confidence` attribute or key) -- the relative confidence we have that the place mention 
  is indeed a place AND that we chose the correct location. 100 point scale where 20 represents a minimum threshold. 
  Less than 20 is typically filtered out as noise that has little corroborating evidence.
- **Location uncertainty or accuracy** (`location_accuracy()` method or `PlaceCandidate.location_certainty` attribute) 
  a logarithmic approach to rendering confidence and spatial precision error into a single number to enable you 
  to compare location mentions or present them in meaningful ways for users.  So if you have these situations in free text:
  - a coordinate with one decimal precision, that is very confident
  - a coordinate with six decimals of precision, that is very confident
  - a coordinate with twelve decimals of precision that was formatted with default floating point precision 
  - a small city with a common name, where we are not very confident it is right
  - a landmark or park
  - mention of a large country or region

The logarithmic approach helps distill about 6 orders of magnitude in geography down to a single useful number. So from
    10 meters up to 1000 KM (10^6 meters) it can be hard to compare the significance of places, let alone visualize them on a map.
      
Some [Examples in this test script](../python/tests/test_location_accuracy.py) for nailing down this idea of location 
accuracy issue as it is relatively important for inferrencing spatial entities:

```text

Important -- Look at the comments in code for each example. In the output see that 
the ACCURACY column is a result that typicallylands between 0.01 and 0.30 (on a 0.0 to 1.0 scale). 
This makes it easy to compare and visualize any geographic entity that has been inferred.

Accuracy 1.0 = 100% confident with a 1 meter of error.


EXAMPLES ....................	ACCURACY	CONF	PREC_ERR
Coord - 31.1N x 117.0W.           	0.100	90	10000
Coord - 31.123456N x 117.098765W. 	0.300	90	10
Coord - 31.123456789012N x 117.098765432101W. 	0.180	90	100
City Euguene, Oregon  .......... 	0.101	85	5000
Poblacion, ... Philippines  .... 	0.036	25	1000
Workshop of H. Wilson, Natick    	0.317	95	10
.....Khammouane.....in Laos..... 	0.058	60	50000

```


Xponents API Java Client
-------------------

Use the `opensextant-xponents` maven artifact and `org.opensextant.xlayer.XlayerClient(url)` 
gives you a starting point to invoke the `.process()` method. [API](../doc/sdk-apidocs/org/opensextant/xlayer/XlayerClient.html). 

```java
/* Note - import org.opensextant.output.Transforms is handling the JSON-to-Java 
 * object deserialization if for whatever reason that is wrong, you can adapt it as needed.  
 */
....

client = XlayerClient(url);
results = client.process(....);
/* Results is an array of TextMatch
   PlaceCandidate objects are subclass of TextMatch and will carry the geotagging details of geography, etc.
 */

```

Additional classes include:

* [XlayerClientTest.java](src/test/java/XlayerClientTest.java) - Test main program. Compile and include `./target/\*-tests.jar` in CLASSPATH
* [XlayerClient.java](src/main/java/org/opensextant/xlayer/XlayerClient.java) - a basic Client, using Restlet
* [Transforms.java](src/main/java/org/opensextant/output/Transforms.java) - a basic data adapter for getting REST response back into API objects.




Health Check
--------------

    curl "http://localhost:8080/xlayer/rest/control/ping"

Stopping Cleanly
------------------

    curl "http://localhost:8080/xlayer/rest/control/stop"


REST Interface
---------------
For example,  run the Python client to see how easy it is to call the service above.
Please note a Java version, XLayerClient, also exists in the src/main folder, with
test code in src/test


INPUT:

* `docid` - an optional identifier for this text
* `text`  - UTF-8 text buffer
* `features` - comma-separated string of features which will vary by app.  XponentsGeotagger (default app) class supports: 
   * `places, coordinates, countries` or `geo` to refer to all of those geographic entities
   * `postal` a special feature type invoking the postal code tagger
   * `patterns` - configured patterns. By default only date/time patterns are detected and normalized.  As other patterns are implemented, 
     this same REST API could be used without changing the call mechanisms -- your client would have to navigate the additional results though.
   * `persons`, `orgs`, `taxons` to refer to those non-geo entities.  
   * `filtered_out` will turn on noisy entities that were filtered out for some reason. The default is not return filtered-out items.
   
* `options`  - comma-separated string of options. Currently: 
   * `revgeo` - reverse geolocate any coordinates found in text input
   * `lowercase` - allow lowercase tagging
   * `clean_input` - attempt to sanitize input text which may contain angle or square bracket tags
    
OUTPUT:

* `response`     - `status`, `numfound` to indicate number of tags found.
* `annotations`  - an array  of objects. 

Annotation schema
 
* `matchtext`      - text span matched
* `match-id`       - match ID, usually of the form `type@offset` 
* `type`           - type of annotation, one of `place`, `country`, `coordinate`, `postal`, org`, `person`
* `offset`         - character offset into text buffer where text span starts
* `length`         - length of text span.  end offset = offset + length
* `method`         - method tag identifying the means by which this annotation was derived.
* `filtered-out`   - true or false if match was filtered by some rule or configuration setting. 
                   Filtered out items are therefore low-quality stuff.

Geographic annotations additionally have:

* `cc`             - country code
* `lat, lon`       - WGS84 latitude, longitude
* `prec`           - precision inferred from text (coordinate digits) or gazetteer feature type
* `adm1`           - ADM1 boundary code, ideally ISO nomenclature, but still often FIPS
* `feat_class`     - Geonames class. One of A, P, H, R, T, S, L, V.  
* `feat_code`      - Geonames designation code. This is more specific than the class.
* `confidence`     - A relative level of confidence.  Subject to change; A scale of 0 to 100, where confidence < 20 is not reliable or lacks evidence.
* `related_place_name` - IF a close by city can be associated with this coordinate the other metadata
  for ADM1, province name, and country code should reflect the coding. This place name identifies that city.
* `nearest_places` - An ARRAY of all such known places that could be landmarks, natural features, etc.
This is different than the `related_place_name` mainly by feature type: that field is a populated place (P/PPL) and this array is any feature type.

Derived Postal annotations additionally have:

* `related`         - an array of evidence that points to other matches in the input text (and response).
  as below:
  
```json
{
      "comment-only": "For an input 'Wellfleet, MA 02663' the individual matches will be given as normal, 
         but a composed match for the entire span will carry `related` section with 
         specific slots indicating the components of the postal match:
         
         'city', 'admin', 'country', 'postal'
       
         Each slot has the relevant `matchtext` and `match-id`. 
         Use the match-id to retrieve the full geocoding for that portion.
         The composed match here will usually carry the geocoding of the postal code.",
              
      "related": {
        "city": {
          "matchtext": "Wellfleet",
          "match-id": "place@0"
        },
        "admin": {
          "matchtext": "MA",
          "match-id": "place@11"
        },
        "postal": {
          "matchtext": "02663",
          "match-id": "postal@14"
        }
      },...
}

```

Non-Geographic annotations have:

* `catalog`        - attribution to a data source or catalog containing the reference data
* `taxon`          - the ID of a normalized catalog entry for the match, e.g., `person = { text:"Rick Springfield", taxon:"person.Richard Springfield", catalog:"Rock-Legends"}`



Example JSON Output:
--------------------

```
   from opensextant.xlayer import XlayerClient

   xtractor = XlayerClient(serverURL)

   # Python call -- Send the text, process the text, print the JSON response to console.
   xtractor.process("test doc#1",
     "Where is 56:08:45N, 117:33:12W?  Is it near Lisbon or closer to Saskatchewan?"
     + "Seriously, what part of Canada would you visit to see the new prime minister discus our border?"
     + "Do you think Hillary Clinton or former President Clinton have opinions on our Northern Border?")
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
	      "lon": -117.55333,
	      "prec": 15,
	      "feat_code": "COORD",
	      "lat": 56.145835,
	      "adm1": "01",
	      "feat_class": "S", 
	      "filtered-out":false	      
	    },
	    {
	      /* A COORD, with an indication of neighboring locales.
	       *   option "revgeo" or "resolve_localities" will evoke this output for Coordinates.
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
	      "lat": 56.145835,
	      "lon": -117.55333,
	      "prec": 15,
	      "feat_class": "S", 
	      "feat_code": "COORD",
	      "adm1": "01",
	      "filtered-out":false, 
	      "related_place_name": "Grimshaw",
	      "nearest_places": [
	         {
		      "name": "Provincial Park of Alberta", 
		      "cc": "CA",
		      "adm1": "01",
		      "lat": 56.16,
		      "lon": -117.54,
		      "feat_class": "S", 
		      "feat_code": "PARK",
		      "prec": 5000,             /* Precision (in meters) is an approximate radius around the point
		                                 * that represents the total area of the feature.
		                                 */
	          "distance": 3400	          /* Distance (in meters) from this nearby place to the found coordinate
	                                      */
	         }
	         /* UP to 5 different locations */
	      ]	      
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
	      "catalog": "JRC",
	      "filtered-out":false
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
	      "feat_class": "P",
	      "filtered-out":false
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
	      "feat_class": "A",
	      "filtered-out":false
	    },
	    {
	      /* A COUNTRY 
	       * ~~~~~~~~~~~~~~~~~~~
	       */
	      "cc": "CA",
	      "text": "Canada",
	      "length": 6,
	      "type": "country",
	      "offset": 101,
	      "filtered-out":false
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
	      "feat_class": "A",
	      "filtered-out":false
	    }
	  ]
	 }



Implementation
---------------

The key geocoders implemented in Xponents REST API are as follows:

- Main logic [XponentsGeotagger](https://github.com/OpenSextant/Xponents/blob/master/src/main/java/org/opensextant/xlayer/server/xgeo/XponentsGeotagger.java) , which wraps the following:
  -  [PlaceGeocoder]((https://github.com/OpenSextant/Xponents/blob/master/src/main/java/org/opensextant/extractors/geo/PlaceGeocoder.java)
  - [PostalGeocoder]((https://github.com/OpenSextant/Xponents/blob/master/src/main/java/org/opensextant/extractors/geo/PostalGeocoder.java)
  - [XTemporal]((https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/java/org/opensextant/extractors/xtemporal/XTemporal.java)
  


INSTALLATION
================
Essentials:

```
  # Install the Python library using Pip. Pip handles installing OS-specific python resources as needed. 
  cd Xponents/
  mkdir piplib 
  pip3 install --target piplib python/opensextant-1.x.x.tar.gz
  OR 
  pip3 install --user python/opensextant-1.x.x.tar.gz

  # Run server
  ./script/xlayer-server.sh 3535 start

  # In another window, Run test client using Python.
  ./test/test-xlayer-python.sh 3535 ./test/data/randomness.txt

  # Once done, run the Java client 
  ./test/test-xlayer-java.sh 3535 ./test/data/randomness.txt


These are limited examples.  If you want to demonstrate running client and server on 
different hosts which is more realistic, by all means adapt the shell scripts as needed.

Rather than use shell scripting, we have used Groovy and Ant to simplify these tests for Java.
As these are for demonstration only, we do not intend to generalize the scripting beyond this.

```

History 
---------
* version 1.4
  * Xponents 3.5.0, introducing postal geocoding and improved gazetteer sourcing
  
* version 0.8
  * Xponents 3.1.0, exposing reverse geocoding feature set
  
* version 0.6
  * Xponents 3.0 release integrated

* version 0.2
  * added features and options to allow caller to customize request
  * Expose PlaceGeocoder capability: geocode text, yielding 
    places, coordinates, countries, and matched non-places organizations and person names


