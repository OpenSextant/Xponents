Back to [OpenSextant](http://opensextant.org)

Xponents
========
Xponents is a set of information extraction libraries including:

* Document conversion using Tika
* Geographic name and coordinate extraction
* Temporal extraction
* Pattern-based extraction
* Foundational data model for info extraction and text scrubbing utilities

The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.
The major sub-modules include:

* **Extraction**: Fully developed entity tagging, processing, filtering, and formatting API solutions for Geography, Time, Patterns, and Keywords.
* **Xponents Solr**: ./solr folder containing Solr 6.x configurations, scripts and resources for supporting fast, rich tagging with Solr and SolrTextTagger
* **XText**: Mature solutions for getting text and metadata from binary formats.
* **Basics** and **Patterns** are two supporting modules that are used as the Xponents lingua franca throughout the SDK.

As of 2017, this [Geocoder Handbook](./doc/Geocoder_Handbook.md) represents 
the Xponents methodology to geotagging and geocoding.  Nearly all of Xponents
and XText has some role in supporting the methodology.

Related sub-modules that employ these SDKs:
* **Examples**: A set of Java main programs that demonstrates the use of XText alongside Extraction to take raw data, process it, and output GIS formats in one shot!
* **Xlayer**: A functional skeleton design of REST microservices for Extraction project, featuring "PlaceGeocoder" class, Patterns and Keyword extraction.
* **MapReduce**: A demonstration of how to design, package, and deploy Extraction with a full Solr gazetteer


Developer Quick Start
--------------
This is primarily a Maven-based project, and so here are our Maven artifacts.
For those using other build platforms, you can find our published artifacts at 
[ OpenSextant Xponents on Maven ](http://search.maven.org/#search%7Cga%7C1%7Corg.opensextant)

```
  <!-- Pull in the Xponents Extraction project, and all dependencies -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents</artifactId>
    <version>2.10.1</version>
  </dependency>

  <!-- Work related to Coordinate, Date/Time and other explicit pattern extraction 
       Supports Extraction above.
    -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents-patterns</artifactId>
    <version>2.10.1</version>
  </dependency>

  <!-- Java data model (Country, Place, Text Entity, etc.), Text and File/Stream Utilites
       Supports all tool chains - Extraction, Patterns, XText, etc.
    -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents-basics</artifactId>
    <version>2.10.1</version>
  </dependency>


  <!-- XText:  Text &amp; Metdata Content extraction from data formats, PDF, Word Docs, etc. 
       Purely optional module.  This helps you acquire content from source data, e.g., Web, Folder crawl, or other.
    -->
  <dependency>
    <groupId>org.opensextant</groupId>
    <artifactId>opensextant-xponents-xtext</artifactId>
    <version>2.10.1</version>
  </dependency>

```

Additional prototypes such as Xponents MapReduce and Xlayer RESTFul extraction service examples are production prototypes
you have to check out and build.

Finally, in order to make use of the Xponents main Extraction project you either build your own Solr6 index ( Docs: [./solr](./solr/README.md) )
or download a pre-built index (Coming Soon -- hopefully November 2017)


Release History
---------------

    Xponents 2.10 Sept 2017 v2.10.1 - Solr 6 support, Java 8 only, SolrTextTagger 2.4;  Published to Sonatype and Maven Central
    Xponents 2.9, Jul 2017  v2.9.9  - Improved stop filters and other geotagging and KW tagging improvements
    Xponents 2.9, Apr 2017  v2.9.8  - refactored XLayer, added trivial match filters; updated versions of commons libs
    Xponents 2.9, Feb 2017  v2.9.7  - minor tests and additional filters added; minor tweak for Curacao
    Xponents 2.9, Jan 2017  v2.9.6
    Xponents 2.9, Oct 2016  v2.9.0
    Updated:  05 Dec  2015  v2.8
    Updated:  30 Nov  2015  v2.7.19
    Updated:  30 Oct  2015  v2.7.16
    Updated:  01 April2015  v2.7
    Updated:  01 June 2014  v2.5

Documentation
==============
When you build or use a release, see component documentation in ./doc/
* ./doc/Examples  shows some Examples of geo/temporal extractors and output formatters
* ./doc/Extraction provides the core library for geo, temporal and other pattern-based extraction
* ./doc/XText  documents the document conversion, text extraction library
* ./doc/Basics primarily used in Extraction, however there are utilities and reference data useful on its own.
* Javadoc for each module is provided in each folder
 
As well, the documentation for each module is in the respective source tree.

Dependencies
==============

Java
--------------
Set JAVA_HOME to your JDK installation;  Maven javadoc plugin requires JAVA_HOME set
Minimum Requirement: 
- Java 7  (Xponents 2.5 or later), Java 8 preferred
- Maven 3.2 to build and develop
- Ant 1.9 is useful for running release scripts
 
FlexPat pattern-based extractors rely on Java 6+ regular expression API

Other Libraries
---------------
- XText depends on Tika 1.13+
- XCoord depends on Geodesy (OpenSextant) for geodetic parsing and validation 
- Extraction makes usef of GISCore (OpenSextant) for output file formatting

Name matching depends on:

* OpenSextant Gazetteer; Download a built gazetteer flat file at  http://www.opensextant.org/ OR build your own
  using https://github.com/OpenSextant/opensextant/tree/master/Gazetteer, which depends on Pentaho Kettle 

* OpenSextant SolrTextTagger;  https://github.com/OpenSextant/SolrTextTagger v2.x (See project for maven details)
  * Xponents 2.5-2.9 == SolrTextTagger v2.0 w/Solr 4.10
  * Xponents 2.10    == SolrTextTagger v2.4 w/Solr 6.6+
  

Using
============

From a built distribution, try:

```
  ant -f ./script/testing.xml  test-XXXX     <args>
```

where XXXX is the name of a core component and args are the arguments to that component

To test XCoord coordinate extraction patterns this is pretty simple:

```
  ant -f ./script/testing.xml  test-xcoord
```

Other Ant tasks in "testing.xml" will allow you to process your own text file.


Set variable LC_CTYPE="UTF-8" for use in Linux/Mac shells when working with filenames or OS resources 
that may have Unicode characters. MORE: https://netbeans.org/bugzilla/show_bug.cgi?id=225743#c3


Developing
===============
From source, Maven is used as the primary build tool:

```
  ## Build
  mvn install 

  ## Create the distribution
  ant -f ./script/dist.xml

  ## Now ./Xponents-VER  contains a built version of XPonents that will run off of Ant from there on; Where VER=X.x

  
  # XText: Binary to text conversion tools
  cd XText; mvn install
  
  # Examples: All together - convert docs, extract, export findings in GIS formats
  cd Examples; mvn install

  # MapReduce: Build M/R deployable packages with full gazetteer, taggers, etc.
  cd MapReduce; mvn install

  # Microservices using Restlet framework
  cd Xlayer; mvn install
```

