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


Getting started
================================

Gazetteer data is generally best downloaded from here:
   http://opensextant.org/downloads.html (download files coming....)
   For example, 
   http://opensextant.org/downloads/gazdata/gazdata-20130811.zip

   (Under the OpenSextant/Gazetteer project you can try your hand at building the 
    gazetteer catalog from scratch, if you need fine tuning and know what you are doing.)

Customize your build.properties file, first copying the build.local.properties as a template.

  gazetteer.data.file   -- set the path to the MergedGazetteer.txt (TAB-delimited data file) as your input
                           NO default.

  solr.home             -- set the location of your solr home; the "gazetteer" core is the output
                           Default: ./
 


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


Running Xponents Solr
=================================

This is a stock instance of Solr 4.x with a number of custom solr cores.
For now the main cores are:  taxcat and gazetteer.

To start from raw data for Gazetteer, see gazetteer/README* for those staging details to generate a gazetteer.
To start from raw data for Taxcat / XTax,  see Extraction/XTax/README.md

These notes here are for the general situation just establishing Solr and iterating through common tasks.

0. Build Some Prequisite Libs
-----------------------------

    pushd Extraction/src/main/python
    python ./setup.py bdist_wheel --universal
    popd
    # Install built lib with dependencies to ./python
    pip install Extraction/src/main/python/dist/*whl --target ./python


  

1. Configure 
=============

   Decide where your final solr server data will be managed, e.g. 
      /myproject/resources/xponents-solr/      ### &lt;--- resulting ./Xponents/solr solr-home

   This path is referred to as JVM arg "opensextant.solr"

   copy build.local.properties to build.properties
   follow instructions in that config file.

   You should use Maven and build or checkout Xponents artifacts:  
    
       bash$  cd Xponents; mvn install

   The init phase next copies JAR artifacts from your Maven repo, ~/.m2/repository

2. Initialize
=============

    # If you use a proxy, then include proxy command first in all your Ant invocations.
    # As well, set proxy.host and proxy.port in your build.properties above.
    #
    ant -f build-gazetteer.xml proxy init
    OR 

    # Otherwise, if you have NO proxy, then simply drop that command from any tasks below.
    # For example:
    ant -f build-gazetteer.xml init


3. Load Gazetteer 
=============

In this step 
 
    bash$  ./run.sh 

This step hopefully shows that you are ready to load the gazetteer or a taxcat catalog.
Note, you need to use the respective Ant script for init and build.  But to just 
run the solr home either script will do.  First run the Solr server (port is configured in build.properties)

    ant -f build-gazetteer.xml proxy start-jetty

    

  



Customization
================

Phonetics

As of OpenSextant 1.5 (July 2013), the use of phonetics codecs to provide a phoneme version of a place name
was removed, as it had not been used.   The last few name field types that allowed for phonetic encoding were as follows:

