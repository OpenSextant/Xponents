OpenSextant Solr Gazetteer
============================
The OpenSextant Gazetteer is a catalog of place names and basic geographic metadata, such 
as country code, location, feature codings.  It is currently indexed and stored using 
Solr 4.2+ (http://lucene.apache.org/solr).  Currently Solr 4.10+ is used.

You are reading about the Xponents variant of the Solr Gazetteer.

Definitions: 
* OpenSextant "Gazetteer" is an ETL project that assembles the catalog into a single "merged" flat file.
* OpenSextant "Xponents Solr" is a particular Solr-based gazetteer implementation the provides 
 specific features to the Xponents API, taggers, etc.

You need not know all about Solr or Solr 4.x to use this.


Getting started
================================

Gazetteer data is generally best downloaded from here:
    http://opensextant.github.io/Gazetteer/ see "latest release" to get processed Gazetteer flat file.

Or checkout the project there and build it yourself.

Once you have that "merged" flat file, configure a copy of Xponents/build.template as Xponents/build.properties

  gazetteer.data.file   -- set the path to the MergedGazetteer.txt (TAB-delimited data file) as your input
                           NO default.

  solr.home             -- set the location of your solr home; the "gazetteer" Solr core is the output
                           Default: ./solr4  (as this is relative to the Xponents/solr/ dir)


Honing Gazetteer Index
=================================

Size matters.  So does content.  Your gazetteer should contain named locations and other data
you want to use in your application.  For example, Complete worldwide name search suggests you 
have a full gazetteer; Lightweight desktop geocoding suggests you have the basics plus some other 
data, but much less than the full version.

Merged gazetteer file:  2.1 GB with 16.5 million entries.  

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

Please note working from a distribution vs. from checkout should be about the same.
To get a fully working Solr instance running in a web server, such as the default Jetty9 will take
some extra steps, but is relatively well tested.


0. Build Some Prequisite Libs
=============
The gazetteer build scripts use some Ant, but mainly Python.
You'll see the Ant script just automates invocation of scripted steps.
The Python libraries provide a platform to help us add any type of
lexicon data to the Solr indexes for tagging.  These Python libs 
are used in ./build.sh and in any other scripts such as XTax/examples/jrcnames.py

From Source:
    pushd Extraction/src/main/python
    python ./setup.py bdist_wheel --universal
    popd
    # Install built lib with dependencies to ./python
    pip install --target ./piplib Extraction/src/main/python/dist/opensextant-VERSION.whl 

From Distribution:
    pushd lib/python/
    python ./setup.py bdist_wheel --universal
    popd
    pip install --target piplib lib/python/dist/opensextant-VERSION.whl

In Python Development mode where the opensextant libs are in development:
    export PYTHONPATH=/path/to/lib/python   or /path/to/Xponents/Extraction/src/main/python
    # Hmm.. note you still have to install python dependencies.
  
1. Configure 
=============

    Decide where your final solr server data will be managed, e.g. 
        XP_SOLR = /myproject/resources/xponents-solr/      
    This folder should contain all the scripts and Jetty v9 server stuff from Xponents/solr
    The JVM arg "opensextant.solr"  is then XP_SOLR/solr4
    You properties arg "solr.home"  is also this same path.
    (XP_SOLR is not a real variable, just short hand for the sake of brevity)

    TODO: these two variable names will eventually converge and just be 'opensextant.solr'

2. Initialize
=============

    # If you use a proxy, then include proxy command first in all your Ant invocations.
    # As well, set proxy.host and proxy.port in your build.properties above.
    #
    ant proxy init
    OR 

    # Otherwise, if you have NO proxy, then simply drop that command from any tasks below.
    ant init


3. Load Gazetteer 
=============
In this step, you can use:

    bash$  ./build.sh 

which will build the Solr gazetteer index and add to the taxcat index
Alternatively, you could run the Jetty server separately and adapt a copy of the build.sh script 
for your own uses.
 
    bash$  ./myjetty.sh start & 

And then interact with the Solr server from Python, Curl, Java, or your browser. As noted 
in the various scripts,  default Solr URL is http://localhost:7000/solr


4.  Load TaxCat 
===============
This step falls under the category of geotagger tuning.  E.g., see Extraction PlaceGeocoder class
as an implemenation of a full geotagging capability.  To negate false-positives we need a source
of known things that are not places, rules that guide us how to judge non-places, or some other 
means such as statistical models to do so.

XTax API uses TaxCat (./solr4/taxcat core).  This API supports the Gazetteer and Xponents taggers
with lexicons of various types.  Like the GazetteerMatcher tagger, XTax tagger uses the TaxCat 
catalog to markup documents with known entities in the catalogs.

Some terms: 
* lexicon:  a flat file, spreadsheet or other original data source you wish to use as entities you want to find
* TaxCat catalog:  the normalized version of your lexicon as it sits in the Solr index.
* XTax: the Java API and related resources for managing the catalog and using it as a tagger.
* JRC lexicon:  JRCNames is a public domain data set that represents Persons, Organizations, and things.
* XTax JRC catalog:  A very specific interpretation of the JRC lexicon for the purpose of treating geotagging false-positives.

Note, as XTax JRC (and other catalogs you add) tag text you naturally find lots of additional entities.
Some of them can be used to negate false-positives in geotagging, .... other entities found are just 
interesting -- you should save them all as a part of your pipeline.

Okee dokee. Now let's give it a shot.

   # Reference:  See Xponents/XTax/ for the full documentation on XTax and JRC, as an example data set.
   #
   export PYTHONPATH=/path/to/my/piplib
   solr=http://localhost:7000/solr/taxcat
   JRC_SCRIPT=/path/to/Xponents/Extraction/XTax/examples/jrcnames.py

   # JRC Entities - global multilingal identies
   #
   python $JRC_SCRIPT --taxonomy /path/to/JRCNames/entities-2014.txt  --solr $solr

   ... 5-10 minutes later these entities are now in your ${opensexant.solr}/solr4/taxcat core ready to go.



Customization
================

Phonetics

As of OpenSextant 1.5 (July 2013), the use of phonetics codecs to provide a phoneme version of a place name
was removed, as it had not been used.   The last few name field types that allowed for phonetic encoding were as follows:

