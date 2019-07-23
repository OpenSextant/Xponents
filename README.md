Back to [OpenSextant](http://opensextant.org)

Xponents
========
* **Author:** Marc Ubaldino, MITRE, (mubaldino@gmail.com, ubaldino@mitre.org). Summer 2019.
* **Docker:** https://hub.docker.com/r/mubaldino/opensexant  Xponents builds featuring Xlayer REST API and full Worldwide Gazetter

Xponents is a set of information extraction libraries including to extract and normalize geographic entities, date/time patterns, keywords/taxonomies, and various patterns.  For example as depicted in Figure 1:

![General topics in our geotagging workflow](./geocoding-workflow.png)

**Figure 1. A General Tagging and Coding Paradigm.**


| text|extracted entity|notional output with normalization|
|---|---|---|
|_"Boise, ID is fun!"_|Place names, geocoded| `geo = { match:"Boise ID",`<br>` adm1:"US.16",`<br>` lat=43.61, lon=-116.20,`<br>` feat_code:"PPL", confidence=78}` <br> And associated rules for the location resolution
|_"Born on 30 DECIEMBRE 1990 ... "_ |Normalized date and time| `date = { match="30 DECIEMBRE 1990",`<br>` date_norm="1990-12-30"}` 
|_"Epicenter at 01°44'N 101°22'E ..."_  |  Geo Coordinates| `coord = { match="01°44'N 101°22'E",`<br>` lat=1.733, lon=101.367,`<br>` pattern="DM-01"}`  
|_"The Swiss delegation..."_ | Keywords | `taxon = { match="Swiss", id="nationality.CHI" }`
|_"User accessed IP 233.12.0.11"_| Patterns | `pattern= { match="233.12.0.11", pattern_id="IPADDRESS" }`

Define your own patterns or compose your own Extractor apps.  As a Java API, the following application classes implement the extraction above:

* **PlaceGeocoder**: Tag and geocode named places, countries, coordinates, nationalities, all with some reasonable amount of disambiguation and noise filtration.
* **XCoord**: Tag and normalize geodetic coordinates of the forms Lat/Lon, MGRS, or UTM (30+ variations in all)
* **XTemporal**:  Tag and normalize date or date+time patterns
* **PoLi**: Patterns of Life ~ develop and test entity tagging based on regular expressions
* **TaxonMatcher**: Tag a list of known keywords or structured vocabularies aka taxonomic nomenclature.

These extractors are in the `org.opensextant.extractors` packages, and demonstrated in the Examples sub-project using Groovy.  These libraries and builds are located in Maven Central and in Docker Hub.  Here is a quick overview of the organization of the project:

- **`Core`:** The core Xponents API for pattern matching (coordinates, dates, etc), text utilities, simple classes around social media, languaged ID, and geographic metadata. Additionally tying this all together is an essential Xponents Data Model (`org.opensextant.data` [Core JavaDoc](https://opensextant.github.io/Xponents/doc/core-apidocs/))
- **`Xponents SDK`:** the root project here that provide the advanced geoparsing, geotagger, keyword tagger, Xlayer (REST API), and other application components. This is very dependent on understanding the fundamentals in Core. [SDK JavaDoc](https://opensextant.github.io/Xponents/doc/sdk-apidocs/)
- **`Examples:`** demonstrations of using various input/output solutions, e.g. `BasicGeoTemporalProcessing` is a command line app that uses [`XText`](https://github.com/OpenSextant/XText/) to crawl and grab text from your media, process geo entities, and then output them as Shapefile, KML, or CSV files.

To start using Xponents now, consider your use case:

* **Deploy a REST service** for all this geo/temporal entity extraction: Use the Docker Hub image here: https://hub.docker.com/r/mubaldino/opensexant 
* **Build your own Java 8+ app** against Xponents SDK: use Maven here:

```
  <!-- Xponents Core API -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents-core</artifactId>
    <version>3.2.0</version>
  </dependency>

  <!-- Xponents SDK API -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents</artifactId>
    <version>3.2.0</version>
  </dependency>
```

Video: Lucene/Solr Revolution 2017 Conference Talk
---------------------------------------
**["Discoverying World Geography in Your Data"](https://youtu.be/44v2WljG1R0?t=1805)**,
presented at Lucene/Solr Revolution 2017 in Las Vegas 14 September, 2017. In video, at minute 29:50. This is a 12 minute talk
<!-- https://www.youtube.com/watch?v=44v2WljG1R0  -->

Demonstration &amp; Download
---------------------------------------
So, you can download and try out a full build. But return here to read the rest of the story.  
The demonstrations only give you a sense of outputs 
for simple inputs.  A lot of actual usage will involve tuning your inputs (cleanup, language ID, etc)
and interpreting your outputs (e.g., filtering, cross-referencing, etc.).

Download the SDK, then walk through examples -- these resources are intended for developers with some Java or Python
experience, and some NLP or GIS background. But the examples and download should work with only the **Java 8+** 
installed on your system. **Python 2.7** is required for the few Python examples.

* See [Examples](./Examples/README.md).  Some examples here require a full SDK build.
* Download SDK builds -- to start using a full release, for now you have to acquire the binary from Docker Hub (2GB image as listed above) or build it yourself. 


Developing with Xponents
-----------------------
The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.  This library tool chest contains the following ideas and capabilities:

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
  
Methodology
---------------------------------------
The **[Geocoder Handbook](./doc/Geocoder_Handbook.md)** represents 
the Xponents methodology to geotagging and geocoding that pertains to coordinate extraction and general geotagging, i.e., XCoord and PlaceGeocoder, respectively.

Code Examples
---------------------------------------

Here are two examples of extracting geographic entities from this made-up text: 

```

    String text = "Earthquake epicenter occurred at 39.56N, -123.45W or "+
                  "an hour west of the Mendocino National Forest ";    
    
    // INIT
    //==================
    XCoord xcoord = new XCoord();
    
    /* configure() may take an optional patterns file;  default is rather comprehensive. */
    xcoord.configure(  );        
    
    // EXTRACT
    //==================
    List<TextMatch> coords = xcoord.extract( text );
    
    for (TextMatch match : coords) {
    
       /* if match instanceof GeocoordMatch
            do something. 
       */
    }
    
    /* "Do something" might produce this output:  print the location found could
     *  be reverse geocoded to Arnold, CA, US
     */
     -=-=-=-=-=-=-==-=-=-=-=-=-=
        FOUND: 39.56N, -123.45W @(33:49) matched by DD-02
        Coordinate at place named Arnold (ADM1=06, CC=US, FEAT=PPL)
     -=-=-=-=-=-=-==-=-=-=-=-=-=
```

Now with the same text as above, the second and more complex example applies the PlaceGeocoder:

```
    // INIT
    //==================
    tagger = new PlaceGeocoder();
    Parameters xponentsParams = new Parameters();
            
    /* Province ID helps convert raw coded info into plain language.
     * Its not enabled by default.  But, say we find "Mendocino National Forest"
     * the find is returned with cc=US, adm1="06"... 
     * the 'resolve_provinces' renders adm1="06" into adm1_name="California".  
     * This is a second lookup for every match, so it can be expensive.
     */
    xponentsParams.resolve_provinces = true;
    tagger.setParameters(xponentsParams);
    tagger.configure();
    
    ...
    
    // EXTRACT
    //==================
    List<TextMatch> allPlaces = tagger.extract( text );
    for (TextMatch match : allPlaces) {
    
       /* if match instanceof GeocoordMatch, PlaceCandidate, TaxonMatch, etc.
             PlaceGeocoder yields many types of entities!! then do something. 
       */
    }
    
    /* And now imagine the do something printed output to the console or saved 
     data to a database,  ... or exported the findings immediately to a map file....
     Below is just the raw console output from running.  There is a lot of metadata 
     for you to filter and work with.     
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

Modes of Integration
----------------------------------------

* **Java**: [Examples](./Examples) sub-project illustrates the essential Java API setup and classes. See Maven notes below. 
* **REST**:  See [Xlayer](./doc/README_Xlayer_REST.md) sub-project, which provides a wrapper around `PlaceGeocoder`, `LangDetect` and other items with some default settings.
* **Python**:  `./python/opensextant` provides a small subset of Xponent functionality as utilities and data classes that facilitate interacting the the gazetteer, RESTful Extractors, and other simple tasks. Extraction is not implemented in Python.
* **MapReduce**:  (Deprecated) For Xponents 2.9, some demonstrations were done to show how to package and create a Mapper to geotag social media, or any data record that had a "text" field.  See [MapReduce](./Examples/MapReduce)
* **Pipelines**:  For raw content (e.g., folders or other stream of data) consider using **XText** project which will help you render data to plain text that can be fed to Xponents Extractors.  See [Examples](./Examples)

Developer Quick Start
-----------------------
[Xponents Javadoc](./doc/apidocs/)

This is primarily a Maven-based project, and so here are our Maven artifacts.
For those using other build platforms, you can find our published artifacts at 
[OpenSextant Xponents on Maven](https://search.maven.org/search?q=a:opensextant-xponents)

* Java 8+, Maven 3+, and Ant 1.10+ are required. Maven Version 3.5 is highly recommended.  

For that matter, the only relevant artifacts in our `org.opensextant` group are:

* `geodesy 2.0.1`   - Geodetic operations and coordinate system calculations
* `giscore 2.0.2`  - GIS I/O
* `opensextant-xponents-core 3.2.0` - This Core API
* `opensextant-xponents  3.2.0` - This Solr-based tagger SDK
* `opensextant-xponents-xtext 3.2.0` - XText, the text extraction toolkit


Build
---------------------
See [BUILD.md](BUILD.md)

Release History and Versioning 
---------------
[RELEASES](./RELEASE.md)
