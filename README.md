Xponents
========
    Xponents 2.8, Mar 2016  v2.8.13

    Date:     25 JULY 2013

    Updated:  05 Dec  2015   v2.8
    Updated:  30 Nov  2015   v2.7.19
    Updated:  30 Oct  2015   v2.7.16
    Updated:  01 April2015   v2.7
    Updated:  01 June 2014   v2.5

Xponents is a set of text extractor libraries with a light veneer of processing control, including:

* Document conversion using Tika
* Geographic name and coordinate extraction
* Temporal extraction
* Pattern-based extraction
* Foundational data model for info extraction and text scrubbing utilities

The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.

#Documentation#
When you build or use a release, see component documentation in ./doc/
* ./doc/Examples  shows some Examples of geo/temporal extractors and output formatters
* ./doc/Extraction provides the core library for geo, temporal and other pattern-based extraction
* ./doc/XText  documents the document conversion, text extraction library
* ./doc/Basics TBD -- primarily used in Extraction, however there are utilities and reference data useful on its own.
* Javadoc for each module is provided in each folder
 
As well, the documentation for each module is in the respective source tree.

#Dependencies#

##Java##
Set JAVA_HOME to your JDK7 installation;  Maven javadoc plugin requires JAVA_HOME set
Minimum Requirement: 
- Java 7  (Xponents 2.5 or later)
- Maven 3.2 to build and develop
- Ant 1.9 is useful for running release scripts
 
FlexPat pattern-based extractors rely on Java 6+ regular expression API

##Other Libraries##
- XText depends on Tika 1.8+
- XCoord depends on Geodesy (OpenSextant) for geodetic parsing and validation 
- Extraction makes usef of GISCore (OpenSextant) for output file formatting

Name Matching depends on:

  OpenSextant Gazetteer;
  * Download a built gazetteer at  http://www.opensextant.org/
  OR
  * Build  your own using https://github.com/OpenSextant/opensextant/tree/master/Gazetteer, which Depends on Pentaho Kettle for creation

  OpenSextant SolrTextTagger
  https://github.com/OpenSextant/SolrTextTagger v2.0
  ./solr/build.properties (copy of build.local.properties) allows you to set the solr-text-tagger 2.x version of your choice.
  Solr cores can be specific to the versions of lucene/solr/solr-text-tagger
  


#Using#

From a built distribution, try:

  ant -f ./script/testing.xml  test-XXXX     <args>

where XXXX is the name of a core component and args are the arguments to that component

To test XCoord coordinate extraction patterns this is pretty simple:

  ant -f ./script/testing.xml  test-xcoord

Other Ant tasks in "testing.xml" will allow you to process your own text file.


#Developing#
From source:

  ## Build
  mvn install 

  ## Create the distribution
  ant -f ./script/dist.xml

  ## Now ./Xponents-VER  contains a built version of XPonents that will run off of Ant from there on; Where VER=X.x

#Misc#

If you are building under OS X and receive an error like this:

    [ERROR] Failed to execute goal org.apache.maven.plugins:maven-jar-plugin:2.4:test-jar (attach-tests) on project opensextant-xponents: 
       Error assembling JAR: java.lang.reflect.InvocationTargetException: Malformed input or input contains unmappable chacraters: 
       /path/to/Xponents/Extraction/target/test-classes/unicode-filen?me.txt -> [Help 1]

Then you may need to set the environment variable LC_CTYPE="UTF-8" to allow building to work correctly. 
See here for a discussion: https://netbeans.org/bugzilla/show_bug.cgi?id=225743#c3
