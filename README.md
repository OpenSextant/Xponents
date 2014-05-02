Xponents
========

 Part of Xponents 2.x (OpenSextant v2.0)
 Date:     25 JULY 2013
 Updated:  01 MAY 2014

Xponents is a set of text extractor libraries with a light veneer of processing control, including:

  * Document conversion using Tika;
  * Geographic name and coordinate extraction;
  * Temporal extraction;
  * Pattern-based extraction
  * Foundational data model for info extraction and text scrubbing utilities

The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.


#Dependencies#

##Java##
Set JAVA_HOME to your JDK6 or JDK7 installation;  Maven javadoc plugin requires JAVA_HOME set
Minimum Requirement: 
  - Java 6 
  - Maven 3.2 to build and develop
  - Ant 1.9 is useful for running release scripts
 
FlexPat pattern-based extractors rely on Java 6+ regular expression API

##Other Libraries##
XText depends on Tika 1.3+
XCoord depends on Geodesy (OpenSextant)
Extraction makes usef of GISCore (OpenSextant)

Name Matching depends on:

  OpenSextant Gazetteer;
  * Download a built gazetteer at  http://www.opensextant.org/
  OR
  * Build  your own using https://github.com/OpenSextant/opensextant/tree/master/Gazetteer, which Depends on Pentaho Kettle for creation

  OpenSextant SolrTextTagger
  https://github.com/OpenSextant/SolrTextTagger


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

  ## Now ./release/  contains a built version of XPonents that will run off of Ant from there on.

