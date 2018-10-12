Back to [OpenSextant](http://opensextant.org)

Xponents
========
Xponents is a set of information extraction libraries including to extract and normalize geographic entities, date/time patterns, keywords/taxonomies, and various patterns.  For example:

| text|routine|notional output with normalization|
|---|---|---|
|_"Boise, ID is fun!"_|PlaceGeocoder| `geo = { matchtext:"Boise ID", adm1:"US.16", <br>lat,lon: [43.61,-116.20], feat_code:"PPL", <br>confidence=78}` <br> And associated rules for the location resolution
|_"Born on 30 DECIEMBRE 1990 ... "_ |XTemporal  | `date = { matchtext="30 DECIEMBRE 1990", <br>date_norm="1990-12-30"}` 
|_"Epicenter at 01°44'N 101°22'E ..."_  |  XCoord | `coord = { matchtext="01°44'N 101°22'E", <br>lat=1.733, lon=101.367, pattern="DM-01"}`  

Define your own patterns or compose your own Extractor apps. 


* Geographic name and coordinate extraction, along with a rich set of rules and metadata for geographic reasoning.  Such reasoning methods are essential for geoinferencing and disambiguating place names, e.g., 
* Temporal extraction
* Pattern-based extraction
* Keyword extraction
* Foundational data model for info extraction and text scrubbing utilities

Video: Lucene/Solr Revolution 2017 Conference Talk
---------------------------------------
**["Discoverying World Geography in Your Data"](https://youtu.be/44v2WljG1R0?t=1805)**,
presented at Lucene/Solr Revolution 2017 in Las Vegas 14 September, 2017. In video, at minute 29:50. This is a 12 minute talk
<!-- https://www.youtube.com/watch?v=44v2WljG1R0  -->


Development Kit Contents
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


Demonstration
---------------------------------------
So, you can download and try out a full build. But return here
to read the rest of the story.  The demonstrations only give you a sense of outputs 
for simple inputs.  A lot of actual usage will involve tuning your inputs (cleanup, language ID, etc)
and interpreting your outputs (e.g., filtering, cross-referencing, etc.).

Download the SDK, then walk through examples:

* See [Examples](./Examples/README.md)
* Download: [Xponents SDK 3.0](https://github.com/OpenSextant/DataReleases) 


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
             PlaceGeocoder yields many types of entities!!!
          then 
            do something. 
       */
    }
    
    /* And now imagine the do something printed output to the console or saved 
     data to a database,  ... or exported the findings immediately to a map file....
     Below is just the raw console output from running.  There is a lot of metadata 
     for you to filter and work with.     
     */
     
    -=-=-=-=-=-=-==-=-=-=-=-=-=
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
* **REST**:  See [Xlayer](./Xlayer) sub-project, which provides a wrapper around `PlaceGeocoder` with some default settings.
* **Python**:  A small set of libraries, utilities and data classes are provided in Python to facilitate interacting the the gazetteer, RESTful Extractors, and other simple tasks. Extraction is not implemented in Python.
* **MapReduce**:  (Deprecated) For Xponents 2.9, some demonstrations were done to show how to package and create a Mapper to geotag social media, or any data record that had a "text" field.  See [MapReduce](./MapReduce)
* **Pipeline**:  For raw content (e.g., folders or other stream of data) consider using **XText** project which will help you render data to plain text that can be fed to Xponents Extractors.  See [Examples](./Examples)

Developer Quick Start
-----------------------
[Xponents Javadoc](./doc/apidocs/)

This is primarily a Maven-based project, and so here are our Maven artifacts.
For those using other build platforms, you can find our published artifacts at 
[ OpenSextant Xponents on Maven ](http://search.maven.org/#search%7Cga%7C1%7Corg.opensextant)

* Java 8+ is required
* Maven 3+ is required. Maven Version 3.5 is highly recommended.  Xponents artifact profile:

```
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents</artifactId>
    <version>3.0.4</version>
  </dependency>
```



Build
---------------------

Building a full Xponents release is involved.  

* Ant 1.9+ and cURL are required in addition to developer items above. 

The order of things is:

1. copy `./solr/build.template` to `./solr/build.properties`.  Edit according to comments there.f
2. `ant -f ./script/setup-ant.xml`
3. `mvn compile`
4. `mvn install`

Separately acquire the Gazetteer "Merged Gazetteer" data file:
* Download from GitHub: https://github.com/OpenSextant/DataReleases OR
* Build from source https://github.com/OpenSextant/Gazetteer

5. Next, follow the instructions in `./solr/` to generate your copy of a working Solr index
6. Distribution/Packaging: `ant -f ./script/dist.xml dist`

Release History 
---------------
[RELEASES](./RELEASE.md)

Other Libraries
---------------
- XText depends on Tika 1.13+
- XCoord depends on Geodesy (OpenSextant) for geodetic parsing and validation 
- Extraction makes usef of GISCore (OpenSextant) for output file formatting

Name matching depends on:

* OpenSextant Gazetteer; Download a built gazetteer flat file at  http://www.opensextant.org/ OR build your own
  using https://github.com/OpenSextant/Gazetteer, which depends on Pentaho Kettle 

* OpenSextant SolrTextTagger;  https://github.com/OpenSextant/SolrTextTagger v2.x (See project for maven details)
  * Xponents 2.5-2.9 == SolrTextTagger v2.0 w/Solr 4.10 w/ Java7
  * Xponents 2.10    == SolrTextTagger v2.4 w/Solr 6.6+ w/ Java8
  * Xponents 3.0     == SolrTextTagger v2.5 w/Solr 7.3+ w/ Java8
  * Xponents 3.0.4   == Solr 7.4+ w/ Java8. SolrTextTagger was migrated into Solr 7.4 formally as "TextTagger" request handler.
  
