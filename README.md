Xponents
========

 Part of OpenSextant 2.0 
 Date: 25 JULY 2013

Xponents is a set of text extractor libraries with a light veneer of processing control, including: 

  * Document conversion using Tika;
  * Geographic name and coordinate extraction; 
  * Temporal extraction; 
  * Pattern-based extraction

The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.


#Dependencies#

FlexPat pattern-based extractors rely on Java 6+ regular expression API

XText depends on Tika 1.3+

Name Matching depends on: 

  OpenSextant Gazetteer; Depends on Pentaho Kettle
  https://github.com/OpenSextant/opensextant/tree/master/Gazetteer 

  OpenSextant SolrTextTagger
  https://github.com/OpenSextant/SolrTextTagger

