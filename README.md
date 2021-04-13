Back to [OpenSextant](http://opensextant.org)

Xponents, A Toolkit for Geotagging World-wide Geography
========
* **Author:** Marc Ubaldino, MITRE, (mubaldino@gmail.com, ubaldino@mitre.org). Spring 2021.
* **Citation:** Ubaldino, M (MITRE Corporation), __"OpenSextant Xponents: Geotagging Toolkit for World-wide Geography"__, 2019. https://opensextant.github.io/Xponents/
* **Lecture** **["Geographic Literacy in Text Analytics: Developing and Applying OpenSextant", Jan 2020](https://gis.harvard.edu/event/geographic-literacy-text-analytics-developing-and-applying-opensextant)**
* **Video:** **["Discoverying World Geography in Your Data"](https://youtu.be/44v2WljG1R0?t=1805)**
* **Docker:** [OpenSextant on Docker](https://hub.docker.com/r/mubaldino/opensextant) 

About our nomenclature -- **OpenSextant** is a family of projects 
for geotagging and other NLP and information extraction work.  **Xponents**
is an actively developed implementation of our OpenSextant mindset around 
geotagging: be accurate, be simple, be extensible and show the work.

**Xponents** is a set of information extraction libraries including to extract and normalize geographic entities, date/time patterns, keywords/taxonomies, and various patterns.  For example as depicted in Figure 1 where a tourist spots in the French country side are detected and geolocated.  That's easy 
when there is only one such known location with that name. It becomes challenging when we try to detect such names in any language in any part of the world.   Is "McDonald's" a farm or a restaurant?  
Which one is the right one -- there's thousands of restaurant locations by that name.

![General topics in our geotagging workflow](./doc/geocoding-workflow.png)

**Figure 1. A General Tagging and Coding Paradigm**

This table below loosely portrays the scenarios in which Xponents operates -- parsing 
and conditioning knowable geo/temporal references in text into usable data structures.

| input text|notional output with normalization|
|---|---|
|_"Boise, ID is fun!"_|**Place names, geocoded**:<br>`geo = { match:"Boise ID", adm1:"US.16",`<br>` lat=43.61, lon=-116.20,`<br>` feat_code:"PPL", confidence=78}` 
|_"Born on 30 DECIEMBRE 1990 ... "_ |**Normalized date/time**:<br>`date = { match="30 DECIEMBRE 1990",`<br>` date_norm="1990-12-30"}` 
|_"Epicenter at 01°44'N 101°22'E ..."_  |**Geo Coordinates**:<br>`coord = { match="01°44'N 101°22'E",`<br>` lat=1.733, lon=101.367,`<br>` pattern="DM-01"}`  
|_"The Swiss delegation..."_ | **Keywords:**<br>`taxon = { match="Swiss", id="nationality.CHI" }`
|_"User accessed IP 233.12.0.11"_|**Patterns:**<br>`pattern= { match="233.12.0.11", pattern_id="IPADDRESS" }`

Define your own patterns or compose your own Extractor apps.  As a Java API, the following application classes implement the extraction above:

* **PlaceGeocoder**: Tag and geocode named places, countries, coordinates, nationalities, all with some reasonable amount of disambiguation and noise filtration.
* **XCoord**: Tag and normalize geodetic coordinates of the forms Lat/Lon, MGRS, or UTM (30+ variations in all)
* **XTemporal**:  Tag and normalize date or date+time patterns
* **PoLi**: Patterns of Life ~ develop and test entity tagging based on regular expressions
* **TaxonMatcher**: Tag a list of known keywords or structured vocabularies aka taxonomic nomenclature.

Here are some fast-tracks for applying Xponents:

A. Geotagging and everything -- Deploy the Docker service as prescribed on our [Docker Hub](https://hub.docker.com/r/mubaldino/opensextant).  Consult at least the Python client `opensextant.xlayer` as noted in the Docker page and in the Python setup below.

B. Pattern extraction -- The Python library `opensextant.FlexPat` or its Java counterpart `org.opensextant.extractors.flexpat` -- offer a lean and effective manner to develop a regular-expression
pipeline.  In either case minimal dependencies are needed.  See Python setup below.

C. Geotagging and everything,.... but for some reason you feel that you need to build 
it all yourself. You'll need to follow the notes here in `BUILD.md` and in `./solr/README.md`. 
The typical approach is to deploy the docker instance of xponents and interact with it using the Python client, `opensextant.xlayer`.  The `xponents-service.sh` demonstrates how to run the 
REST service with or without Docker.


Video: Lucene/Solr Revolution 2017 Conference Talk
---------------------------------------
**["Discoverying World Geography in Your Data"](https://youtu.be/44v2WljG1R0?t=1805)**,
presented at Lucene/Solr Revolution 2017 in Las Vegas 14 September, 2017. In video, at minute 29:50. This is a 12 minute talk
<!-- https://www.youtube.com/watch?v=44v2WljG1R0  -->


Methodology
---------------------------------------
The **[Geocoder Handbook](./doc/Geocoder_Handbook.md)** represents 
the Xponents methodology to geotagging and geocoding that pertains to coordinate extraction and general geotagging, i.e., XCoord and PlaceGeocoder, respectively.


Code Examples
---------------------------------------

Using **XCoord** and **PlaceGeocoder** here are two examples of extracting 
geographic entities from this made-up text: 

```
    String text = "Earthquake epicenter occurred at 39.56N, -123.45W or "+
                  "an hour west of the Mendocino National Forest ";    
    
    // INIT
    //==================
    XCoord xcoord = new XCoord();    
    xcoord.configure();        
    SolrGazetteer gaz = new SolrGazetteer();
    
    // EXTRACT
    //==================
    List<TextMatch> coords = xcoord.extract( text );
    
    for (TextMatch match : coords) {
       /* if match instanceof GeocoordMatch do something. 
       */
       print("FOUND:" + match);
       print("Near named place " + gaz.placeAt(match));
    }
    
    /* "Do something" might produce this output:  print the location found could
     *  be reverse geocoded to Arnold, CA, US
     */
     -=-=-=-=-=-=-==-=-=-=-=-=-=
        FOUND: 39.56N, -123.45W @(33:49) matched by DD-02
        Near named place Arnold (ADM1=06, CC=US, FEAT=PPL)
     -=-=-=-=-=-=-==-=-=-=-=-=-=
```

Now with the same text as above, the second and more complex example applies the PlaceGeocoder:

```
    // INIT
    //==================
    tagger = new PlaceGeocoder();
    Parameters xponentsParams = new Parameters();
    xponentsParams.resolve_localities = true;
    tagger.setParameters(xponentsParams);
    tagger.configure();
    
    /* In this example, ""Mendocino National Forest" is found and is 
     * coded as { cc=US, adm1="06",...} representing that the forest 
     * is in California ("US.06"). To actually resolve "US.06" to a named 
     * province we need the resolve_localities flag ON. There is a small 
     * performance hit which adds up if you do this for every place found.
     */      
    
    // EXTRACT
    //==================
    List<TextMatch> allPlaces = tagger.extract( text );
    for (TextMatch match : allPlaces) {    
       /* PlaceGeocoder yields many types of entities!!
          if match instanceof GeocoordMatch, PlaceCandidate, TaxonMatch, etc.
              then do something. 
       */
    }
    
    /* These examples print to stdout, but imagine saving to a database, 
     * exporting KML or a spreadsheet on the fly
     */     
    -=-=-=-=-=-=-STDOUT==-=-=-=-=-=-=
    ....
    Name:Mendocino National Forest
    Rules = [DefaultScore]
        geocoded @ Mendocino National Forest (06, US, FRST), score=20.56 with conf=93
    MENTIONS DISTINCT PLACES == 1
    [Mendocino National Forest]
    MENTIONS COUNTRIES == 0
    []
    MENTIONS COORDINATES == 1
    [39.56N, -123.45W]
    ....
    -=-=-=-=-=-=-==-=-=-=-=-=-=
```

Java versus Python Libraries
----------------------------

Python and Java functionality overlaps but is still drastically differe.  The Core API resembles 
the Python library somewhat.

* `opensextant` (v1.3) Python API offers data utilities; Solr clients for TaxCat and Gazetteer;
  basic data models for text spans, place objects, etc.  Xponents REST client (`xlayer`) which interacts with the Java Xponents REST service.
* Xponents Core API (v3.4) Java library provides most of the functionality as in the Python library. 
  It offers more complete Unicode utilities and other metadata resources such as Country, Timezone, 
  and Language metadata
* Xponents SDK API (v3.4) provides the Solr and client/server integrations for Gazetteer, 
  TaxCat and PlaceGeocoder.


Setup
----------------------------
As mentioned above to work with just pattern extraction, the Core API is needed. 
But to do anything more, like geotagging, the SDK API is needed along with the instance of 
Solr as prescribed in the `./solr` folder.  Here are some useful pre-requisites:

- Java 8+ (Java 15+ preferred)
- Maven 3.5+ and Ant 1.10+
- Python 3.8+


**Maven**

Insert these dependencies into your POM depending on what you need.
```
  <!-- Xponents Core API -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents-core</artifactId>
    <version>3.4.0</version>
  </dependency>

  <!-- Xponents SDK API -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents</artifactId>
    <version>3.4.0</version>
  </dependency>
```

For reference: [OpenSextant Xponents on Maven](https://search.maven.org/search?q=a:opensextant-xponents).  For that matter, the only relevant artifacts in our `org.opensextant` group are:

* `geodesy 2.0.1`   - Geodetic operations and coordinate system calculations
* `giscore 2.0.2`  - GIS I/O
* `opensextant-xponents-core  3.4.*` - This Core API
* `opensextant-xponents       3.4.*` - This Solr-based tagger SDK
* `opensextant-xponents-xtext 3.4.*` - XText, the text extraction toolkit



**Python**

Someday we'll just post this to PyPi. 

```shell script

    pushd ./python
    python3 ./setup.py sdist
    popd

    # Install built lib with dependencies to ./piplib
    pip3 install -U --target ./piplib ./python/dist/opensextant-1.3*.tar.gz 
    pip3 install -U --target ./piplib lxml bs4 arrow requests
    
    # Note - if working with a distribution release, the built Python 
    # package is in ./python/ (not ./python/dist/)

    # * IMPORTANT *
    export PYTHONPATH=$PWD/piplib
    
    # Adjust the "--target piplib" and the PYTHONPATH according to how 
    # you like it. 
```

Demonstration
---------------------
See the [Examples](./Examples/README.md) material that you can use 
from within the Docker image or from a full checkout/build of the project.  Pipeline 
topics covered there are :

- Basic geo/temporal extraction
- XText file-to-text conversion tool. More info is at [`XText`](https://github.com/OpenSextant/XText/). XText is included in this distribution
- GIS map layer generation
- Language ID
- Social media geo-inferencing
- [Xponents REST](./doc/README_Xlayer_REST.md) interaction as described here   
- etc.


API Documentation and Developer Notes
--------------------------------------

These extractors are in the `org.opensextant.extractors` packages, and demonstrated in the Examples sub-project using Groovy.  These libraries and builds are located in Maven Central and in Docker Hub.  Here is a quick overview of the organization of the project:

- **`Core API`** ([Core JavaDoc](https://opensextant.github.io/Xponents/doc/core-apidocs/)):  covers pattern matching (coordinates, dates, etc), text utilities, simple classes around social media, languaged ID, and geographic metadata.  The data model classes are central to all things Xponents.
- **` SDK`** ([SDK JavaDoc](https://opensextant.github.io/Xponents/doc/sdk-apidocs/))) provides the advanced geoparsing, geotagger, keyword tagger, and any client/server integration. 


**Xponents Philosophy**:  The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.  This library tool chest contains the following ideas and capabilities:

* **Extractors for Text:** The focus of Xponents is working with unstructured text in any language: conditioning, extracting entities, tagging, coding it.  These extractors include:
  * `PlaceGeocoder` a geotagger/geocoder for ALL geographic entities in free-text of any size.
  * `XCoord`  a geographic coordinate extractor to pull out and geocode MGRS, UTM or latitude/longitude in degrees (decimal, minutes, seconds, etc)
  * `XTemporal` a date/time pattern extractor that identifies and codes practical well-known date and date/time formats
  * `XTax` a keyword extractor that allows you to associate important metadata and taxonomic information with keywords
  * `LangDetect` langid, using mainly CyboZu LangDetect
  * An extensible regular-expression entity tagger library, `FlexPat` (see `XCoord` and `XTemporal` packages for examples)
* **Output Data Model**: Keeping with our geographic theme, Xponents provides a simple set of data classes that act as the lingua franca for geographic reasoning:
  * `Country, Place, LatLon` are all examples of the `Geocoding` interface (see `org.opensextant.data`)
  * `Language` and `LangID` represents a simple but powerful and overlooked data: language codes and names.  Here we use language codes to drive various language-dependent features and then also to present language name to end users. (see ISO-339 standards; our source table is from Library of Congress)  
  * Entity Extraction and Matching:  `TextEntity` (a text span) and `TextMatch` (a span matched by a rule or extractor) represent the essential unit of data emitted by all Extractors in Xponents.  These include:  `GeocoordMatch`, `PlaceCandidate`, `DateMatch`, `TaxonMatch`, and others.
* **Geographic Gazetteers & Metadata:** under the hood are standard (ISO, USGS, etc) and defacto standard (geonames.org) data sets instrumented by these Java APIs.  The primary gazetteer is housed in an Apache Solr index, which can you can interact with during development. The [OpenSextant Gazetteer](https://github.com/OpenSextant/Gazetteer) project provides the data ETL. 
  * `SolrGazetteer` provides a clean API to query gazetteer data using Solr query mechanics and Xponents data classes. The index is optimized for full text search and geospatial queries
  * `GazetteerMatcher` provides a direct API around the text tagging capability (via [SolrTextTagger](https://github.com/OpenSextant/SolrTextTagger)) beneath the SolrGazetteer.
* **GIS Formatters:**  The immediate satisfaction of processing some challenging data and then producing a map visual from that is undeniable.  However, our GIS outputter options offer more than just the immediate map plot:  the output schema includes a decent combination of source information, match metadata, and geocoding so you can review  what was found while in the map view.  


Build
---------------------
See [BUILD.md](BUILD.md)

Release History and Versioning 
---------------
[RELEASES](./RELEASE.md)
