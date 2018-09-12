OpenSextant Solr Gazetteer
============================
The OpenSextant Gazetteer is a catalog of place names and basic geographic metadata, such 
as country code, location, feature codings.   In Xponents, Solr 7+ is used to index and provision 
the large lexicons such as gazetteer and taxonomies.

You are reading about the Xponents variant of the Solr Gazetteer.
(OpenSextantToolbox is a similar tagger solution with a variant of the Gazetteer.
Both libraries use the same Gazetteer "merged" flat file as a starting point)

Definitions: 
* OpenSextant "Gazetteer" is an ETL project that assembles the catalog into a single "merged" flat file.
* OpenSextant "Xponents Solr" is a particular Solr-based gazetteer implementation the provides 
 specific features to the Xponents API, taggers, etc.

You do NOT need know all about Solr or Lucene to make use of this, but it helps
when you need to optimize or extend things for new langauges.


Getting started
================================
You have a few options:

1. Download Xponents SDK release (libraries, docs, and pre-built Xponents Solr)
   * You just want the capability. No hassle.
   * https://github.com/OpenSextant/Xponents/releases will have library releases; Maven Central has JARs/javaodcs
   * https://github.com/OpenSextant/DataReleases will list full SDK releases for Xponents 3.0+
2. Checkout Xponents and Gazetteer projects and build from latest source and data.
   * You want the full experience.
3. Checkout Xponents build from latest source using an existing Gazetteer flat file.
   * https://github.com/OpenSextant/DataReleases -- lists Gazetteer quarterly releases
   * You want to dig into Xponents but most recent gazeeteer is not that important.

For option 1 download what you need from the links above.

For options 2 and 3 above, you'll follow the remainder of these instructions to build Xponents SDK with Solr indices populated.

Option 2.  Build Gazetteer From Scatch
---------------------------------------------

1. Checkout Gazetteer ETL project
   * http://opensextant.github.io/Gazetteer/ 
   * Follow instructions to install Pentaho Kettle 6+ and Ant
   * Tune the build.properties there.

Desired layout:

```
  ./Xponents/     (git project)
  ./Xponents/solr (this folder)
  ./Gazetteer/    (git project)
```

Now run these steps to acquire gazetteer data from USGS and NGA:

```
  cd Gazetter
  ant setProxy nga.data
  ant setProxy usgs.data

  # Remove setProxy if you have no http_proxy to worry about.
```

Separately run the ETL in the Gazetter project.
This Xponents script emulates the Gazetteer ant script, but allows 
for some tuning of JVM and other parameters, such as logging, etc.

```
  cd Xponents/solr
  ./build-gazetteer.sh 
```

**TIME:** Expect the above process to take 30-60 minutes once all software is installed and working.

**OUTPUT**: Now find the absolute path to the output `MergedGazetteer.txt`  (`Gazetteer/GazetteerETL/GeoData/Merged/MergedGazetteer.txt`)

Continue on with the rest of the instructions.
 

Option 3. Build Xponents Solr from Gazetteer Flat File
---------------------------------------------
First, copy Xponents/build.template as Xponents/build.properties

```
  gazetteer.data.file   -- set the absolute path to the MergedGazetteer.txt 

  solr.home             -- set the location of your solr home; the "gazetteer" Solr core is the output
                           Default: ./solr7  (as this is relative to the Xponents/solr/ dir)

  proxy                 -- set your HTTP proxy host, or leave blank if none. 
```


Honing Gazetteer Index
=================================
Size matters.  So does content.  Your gazetteer should contain named locations and other data
you want to use in your application.  For example, An application for a complete worldwide name 
search suggests you have a full gazetteer; An application of lightweight desktop geocoding suggests 
you have the basics plus some other data, but much less than the full version.

If you don't care about size move onto next section.
Regardless, this section is deprecated given this tuning is no longer supported:
* Xponents 2.9+ we got away from using Ant to RESTfully post data to Solr and invoke the update-script
 (java or javascript solution).  And with that the filtration on SplitCategory went with it.

Merged gazetteer file sizes:  
* 2.1 GB with 16.5 million entries.   (as of 2016)
* 2.3 GB with 18.5 million entries.   (as of 2018)

Proprotions of categories of entries -- which could help you decide how to balance SDK size with geographic coverage.

```
  SplitCategory
  --------------
  Full gazetteer: 100.0 %   
  General          30.0 %    Well-known + all administrative boundaries and populated places.
  Wellknown         1.0 %    Basic + major cities
  Basic          :  0.1 %    countries + territories + Level-1 provinces
  Rare             40.0 %    Uncommonly seen names, numeric entries, mostly unpopulated places or other features.
```

``` DEPRECATED FILTERS BELOW```

To adjust content (and therefore size), use the FILE: solrN/gazetteer/conf/solrconfig.xml 
Look at the 'update-script' 'params' section, which has an include_category parameter.  
The choices for this parameter are:

```
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

```

Running Xponents Solr
=================================

This is a stock instance of Solr 7.x with a number of custom solr cores.
For now the main cores are:  `taxcat` and `gazetteer`.  They are populated like this:

* `gazetteer`:  All the notes above on producing the flat file, but also additional sources of data and filters are 
  integrated by this `./solr/build.sh` script.
* `taxcat`:  `./solr/build.sh` conducts all the data downloads and loading.  See [XTax README](`./solr/etc/taxcat/README.md`)

These notes here are for the general situation just establishing Solr and iterating through common tasks.


Setup
----------

**Step 1.  Get Solr 7.x **

To get a fully working Solr instance running unpack the full Solr 7.x distribution here at ./solr7-dist;
This involves some extra steps, but is relatively well tested.

```
    wget http://archive.apache.org/dist/lucene/solr/7.4.0/solr-7.4.0.zip
    unzip solr-7.4.0.zip
    mv ./solr-7.4.0  ./solr7-dist

    # We could automate this sure. But you need only do it once and hopefully is not repetitive.
```


**Step 2. Build Some Prequisite Libs **

The gazetteer build scripts use some Ant, but mainly Python.
You'll see the Ant script just automates invocation of scripted steps.
The Python libraries provide a platform to help us add any type of
lexicon data to the Solr indexes for tagging.  These Python libs 
are used in ./build.sh and in any other scripts such as `./script/taxcat_jrcnames.py`

And as far as Xponents Java, just build the full project, `cd ../; mvn install`
Running these steps depends on the current version of Xponents Extraction and Basics.

From Source:
```
    pushd ../python
    python ./setup.py sdist
    popd
    pushd ../
    # Install built lib with dependencies to ./python
    pip install -U --target ./piplib ./python/dist/opensextant-1.1.9.tar.gz 
    popd
```

From Distribution:
```
    pip install -U --target ./piplib python/opensextant-1.1.9.tar.gz
```

NOTE: In Python Development mode where the opensextant libs are in development:
```
    export PYTHONPATH=/path/to/Xponents/piplib
```
  
**Step 3. Configure and Deployment Paths **

By default, you have this runtime environment in check-out or in distribution:
- ./Xponents/solr/solr7 will contain the Solr indicies
- ./Xponents/solr/solr7-dist will contain the Solr server that serves the indices

We refer to Xponents Solr informally as `XP_SOLR`, which is `./Xponents/solr`
Formally, the JVM argument `opensextant.solr` is set to `XP_SOLR/solr7`

In deployment you can choose XP_SOLR to be any path you want, as long as the `solr`
folder is kept intact once built.


**Step 4. Build Indices **

The build process can be brittle, so let's educate you and you can make decisions on your own. See comments on each option/directive
The ```build.sh``` script is the central brain behind the data assembly.

```
   ./build.sh  start clean data proxy 

   # clean  = Clean Solr indices and initialize library folders with copies of dependencies, etc.
   # start  = Start the Solr server on default Xponents port 7000.  Server is only used at build time, not at runtime
   # data   = Acquire additional data e.g., Census, Geonames.org, JRC entities, etc. These data sets are not cleaned by 'clean'
   # proxy  = IF you are behind a proxy, set your proxy in build.propertes

```

If you are really lucky, you would be done after this. 
But review the above build steps individually if you run into problems.



Index Step 1. Initialize
=================================

```
    UNIX:  build.sh [proxy] init

    Or: 
    # If you use a proxy, then include proxy command first in all your Ant invocations.
    # As well, set proxy.host and proxy.port in your build.properties above.
    #
    ant [proxy] init
```

Index Step 2. Get Supporting Data
--------------------------------

```
    build.sh [proxy] data 
```

This will pull down data sets used by Gazetteer and TaxCat taggers and resources using the Ant tasks:.
* ```ant get-gaz-resources ```
* ```ant taxcat-jrc ```


Index Step 3. Load Gazetteer 
--------------------------------
In this step, you can use:

```
    bash$  ./build.sh  [proxy] [start] 
```

which will build the Solr gazetteer index and add to the taxcat index.  If the Solr Server is not running
it will be started.  Access Solr URL is http://localhost:7000/solr


CLASSPATH NOTE: "Filters" are important to gazetteer tuning.   I refer to "/filters/"  resources in taggers
and data processing.  The filters are kept close by to the ./gazetteer index for now.  They could be
packed in their own JAR or with the Extractors JAR.   And then that JAR would be put in Jetty CLASSPATH
or your application CLASSPATH.

```
   
   # Copy Xponents gazetteer meta-files to runtime location
   #
   ant gaz-meta

   # NOTE: xponents-gazetteer.jar  JAR is required in your CLASSPATH at runtime.
   # You may find duplicate copies of this -- one copy is required in the Solr server class loader path, 
   # But then for client-side usage you may need a copy as well.
```

WHY?  These filters are data sets like source code. They are used 
by client side code or server side code;  Inside and outside of Solr processors and taggers.
So there is no single best place to locate them, and there is no single best answer for putting them in your CLASSPATH.
A JAR is more portable for deployment, but for development we just add the folder "...gazetteer/conf/"  to the CLASSPATH.


Index Step 4.  Load TaxCat 
--------------------------------
This step falls under the category of geotagger tuning.  E.g., see Extraction PlaceGeocoder class
as an implemenation of a full geotagging capability.  To negate false-positives we need a source
of known things that are not places, rules that guide us how to judge non-places, or some other 
means such as statistical models to do so.

XTax API uses TaxCat (./solr7/taxcat core).  This API supports the Gazetteer and Xponents taggers
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
