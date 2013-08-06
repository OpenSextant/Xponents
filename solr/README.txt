OpenSextant Solr Gazetteer
============================
The OpenSextant Gazetteer is a catalog of place names and basic geographic metadata, such 
as country code, location, feature codings.  It is currently indexed and stored using 
Solr 4.2+ (http://lucene.apache.org/solr)

This Solr-based index for the 'gazetteer' core was finalized with a Solr 4.2.1 configuration.   
These notes are written based on that version of Solr.

Gazetteer ingest into Solr is done using an Embedded Solr loader akin to the CSV data import handler.
The OpenSextant Gazetteer project digests raw gazetteer data from various sources, generating
a single merged TAB-delimited data file.  That merged data file is the input to build 
this Gazetteer index, which is used by applications at runtime.


Honing Gazetteer Index
=================================

Size matters.  So does content.  Your gazetteer should contain named locations and other data
you want to use in your application.  For example, Complete worldwide name search suggests you 
have a full gazetteer; Lightweight desktop geocoding suggests you have the basics plus some other 
data, but much less than the full version.

Merged gazetteer file:  1.8 GB with 13.5 million entries.  

From this merged data set, we can filter the rows of data by making use of some simple categories.  
Places and Place names may be well-known or rare, or some where in-between.

Solr Gazetteer Sizes Approximately:
  Full gazetteer:  1.6 GB  (v1.4 or v1.5 OpenSextant)
  General         ~600 MB
  Wellknown        ~20 MB
  Basic gazetteer:  ~1 MB 

To adjust content (and therefore size), use the FILE: solr/gazetteer/conf/solrconfig.xml 
Look at the 'update-script' 'params' section, which has an include_category parameter.  
The choices for this parameter are:

 // NO filtering done within Solr; NOTE: Your Gazetteer ETL output may have already filtered records
 // So, the term 'all' here is relative to what you send into Solr
 // 
 include_category = all

      OR

 include_category = [cat list] 

 where cat list is one or more of these in a comma-separated list. Case matters.

    Basic           countries and provinces (ADM1)
    Wellknown       major cities and all admin boundaries
    general         unspecified 'SplitCategory', i.e., empty column
    NonLatin        non-Latin scripts and languages

 update-script params format:
          <!-- A comment here about your inclusions -->
          <str name="include_category">[cat,cat,cat,...]</str>


Deploying with a Web App server
=================================

The easiest way to interact with your Solr Index is to provision your own web-app 
server locally or on a server. The basics:

  copy solr.war to server 'webapps' folder
  add some JARs to server 'libs'
  set JVM args, such as -Dsolr.solr.home=/path/to/your/solr


For example, Jetty:

   download a version of Jetty (v8.1 works with Java6; v9 works with Java7)
   unzip jetty-distribution-8.1.7.v20120910.zip
   //
   // move jetty-... folder to ./jetty for simplicity sake

   copy solr.war to jetty/webapps/
   Start jetty --- ant -f run-ant.xml jetty


Customization
================

Phonetics

As of OpenSextant 1.5 (July 2013), the use of phonetics codecs to provide a phoneme version of a place name
was removed, as it had not been used.   The last few name field types that allowed for phonetic encoding were as follows:


Classpath
===============

If your solr invocation -- embedded or server -- is not working right due to Class not found issues, then try 
setting the JAR libraries and classpath elements directly in solrconfig.xml for the respective solr cores.

  E.g.,  as of v1.4 we had libraries configured in solrconfig.xml, but found this to be better managed from your own classpath.
  Web servers and other tools likely want to have extra libs (sometimes lib/ext or in <base_install>/lib) so they can provision
  global classes to applications in containers.   JTS in particular will not be found soon enough if deployed in a web-app where
  Solr Embedded might be used:  it needs to be copied to the global classpath.

  Example solrconfig.xml "libs"  section:

  <!--  Add the following libraries to your Classpath (DEFAULT)  OR configure them here.

     In order of importance:
     (required)The text tagger,
     (required)OpenSextant phonetics encoder - limited use currently; Packaged in OpSx Toolbox JAR
     (optional)Java Topo Suite (JTS) for geospatial search against gazetteer.  Schema field geo is linked to use of JTS.
     (optional)Java Servlet API - for use outside of servlet container
    -->
  <!--
  <lib path="${opensextant.home}/lib/solr-text-tagger-1.2.jar" /> -->
  <!--
  <lib path="${opensextant.home}/lib/OpenSextantToolbox.jar" /> -->
  <!--  JTS is loaded only via CLASSPATH, but noted here as it gazetteer schema is geospatially searchable using both spatial4j + JTS
  <lib path="${opensextant.home}/lib/jts-1.13.jar" /> -->
  <!--
  <lib path="${opensextant.home}/lib/javax.servlet-api-3.0.1.jar" /> -->
