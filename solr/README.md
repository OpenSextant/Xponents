OpenSextant Solr Gazetteer
============================
The OpenSextant Gazetteer is a catalog of place names and basic geographic metadata, such 
as country code, location, feature codings.   In Xponents, Solr 7+ is used to index and provision the large lexicons such as gazetteer and taxonomies.

You are reading about the Xponents variant of the Solr Gazetteer.
(OpenSextantToolbox is a similar tagger solution with a variant of the Gazetteer.
Both libraries use the same Gazetteer "merged" flat file as a starting point)

Definitions: 
* OpenSextant "Gazetteer" is an ETL project that assembles the catalog into a single 
  "merged" flat file.
* OpenSextant "Xponents Solr" is a particular Solr-based gazetteer implementation the 
  provides specific features to the Xponents API, taggers, etc.

You do NOT need know all about Solr or Lucene to make use of this, but it helps
when you need to optimize or extend things for new langauges.


Getting started
================================
You have a few options:

* **Option 1.** Download Xponents SDK release (libraries, docs, and pre-built Xponents Solr)
   * You just want the capability. No hassle.
   * https://github.com/OpenSextant/Xponents/releases will have library releases; Maven Central has JARs/javaodcs
   * https://hub.docker.com/r/mubaldino/opensextant will have Xponents 3.2 and later as a full running service.
* **Option 2.** Checkout Xponents and Gazetteer projects and build from latest source and data.
   * You want the full experience, all the pain of building from source.

For options 2 and 3 above, you'll follow the remainder of these instructions to build Xponents SDK 
with Solr indices populated.  Either way, where Python is referred in any instructions we are referring 
to Python 3.6+ only.  `python` and `pip` may be further qualified as `python3` and `pip3` in many scripts.


Option 2.  Build Gazetteer From Scatch
---------------------------------------------

A quick overview: Generate the raw gazetteer flat file `MergedGazetteer`, then load that 
into the Solr server along with other reference data.

1. Checkout Gazetteer ETL project
   * http://opensextant.github.io/Gazetteer/ 
   * Follow instructions to install Pentaho Kettle 6+ and Ant
   * Tune the build.properties there. Specifically set the dates of downloadable NGA GNS and USGS data sets.  The date
     in build.properties is `YYYYMMDD`:
       - USGS: https://www.usgs.gov/core-science-systems/ngp/board-on-geographic-names/download-gnis-data
       - NGA: http://geonames.nga.mil/gns/html/

Desired layout:

```
  ./Xponents/     (git project)
  ./Xponents/solr (this folder)
  ./Gazetteer/    (git project)
```

Now run these steps to acquire gazetteer data from USGS and NGA:

```shell script
  cd Gazetter
  ant setProxy nga.data
  ant setProxy usgs.data

  # Remove setProxy if you have no http_proxy to worry about.
```

Separately run the ETL in the Gazetter project.
This Xponents script emulates the Gazetteer's own ant script, but allows 
for some tuning of JVM and other parameters, such as logging, etc.

```shell script
  cd Xponents/solr
  ./build-gazetteer.sh 
```

**TIME:** Expect the above process to take 30-60 minutes once all software is installed and working.

**OUTPUT**: Now find the absolute path to the output `MergedGazetteer.txt`  (`Gazetteer/GazetteerETL/GeoData/Merged/MergedGazetteer.txt`)


Next, load this flat file into the Solr server. 
First, copy `build.template` as `build.properties`

```
  gazetteer.data.file   -- set the absolute path to the MergedGazetteer.txt 

  solr.home             -- set the location of your solr home; the "gazetteer" Solr core is the output
                           Default: ./solr7  (as this is relative to the Xponents/solr/ dir)

  proxy                 -- set your HTTP proxy host, or leave blank if none. 
```

Finally, walk through the following section on "Building and Running Xponents Solr", which provides
this last bit of configuration in 4 steps.


Building and Running Xponents Solr
=================================

This is a stock instance of Solr 7.x with a number of custom solr cores.
For now the main cores are:  `taxcat` and `gazetteer`.  They are populated like this:

* `gazetteer`:  All the notes above on producing the flat file, but also additional sources of data and filters are 
  integrated by this `./solr/build.sh` script.
* `taxcat`:  `./solr/build.sh` conducts all the data downloads and loading.  See [XTax README](`./solr/etc/taxcat/README.md`)

These notes here are for the general situation just establishing Solr and iterating through common tasks.


Setup
----------

**Step 1. Get Solr 7.x**

To get a fully working Solr instance running unpack the full Solr 7.x distribution here 
at `./solr7-dist`.  This involves some extra steps, but is relatively well tested.
Using the latest Solr distribution would involve updating Maven POM, possibly, as well as
reviewing Solr index configurations.

```shell script
    wget http://archive.apache.org/dist/lucene/solr/7.7.2/solr-7.7.2.zip
    unzip solr-7.7.2.zip
    SOLR_DIST=./solr7-dist
    mv ./solr-7.7.2  $SOLR_DIST

    rm -rf $SOLR_DIST/example $SOLR_DIST/server/solr/configsets $SOLR_DIST/contrib $SOLR_DIST/dist/test-framework

    # We could automate this sure. But you need only do it once and hopefully is not repetitive.
    # NOTE If Solr 8.x is in use, the distro is ./solr8-dist.  Differences from Solr 7 to Solr 8 are still being 
    # investigated.
```


**Step 2. Build Prerequisite Libraries**

The gazetteer build scripts use some Ant, but mainly Python.
You'll see the Ant script just automates invocation of scripted steps.
The Python libraries provide a platform to help us add any type of
lexicon data to the Solr indexes for tagging.  These Python libs 
are used in `./build.sh` and in any other scripts such as `./script/taxcat_jrcnames.py`

And as far as Xponents Java, just build the full project, `cd ../; mvn install`

From Source:

```shell script
    pushd ../python
    python ./setup.py sdist
    popd
    pushd ../
    # Install built lib with dependencies to ./python
    pip3 install -U --target ./piplib ./python/dist/opensextant-1.2*.tar.gz 
    popd
```

From Distribution:

```
    pip install -U --target ./piplib python/opensextant-1.2*.tar.gz
```

NOTE: In Python Development mode where the opensextant libs are in development:

```shell script
    export PYTHONPATH=/path/to/Xponents/piplib
```
  
**Step 3. Configure and Deployment Paths**

By default, you have this runtime environment in check-out or in distribution:

- `./Xponents/solr/solr7` will contain the Solr indices
- `./Xponents/solr/solr7-dist` will contain the Solr server that serves the indices

We refer to **Xponents Solr** informally as `XP_SOLR`, which is `./Xponents/solr` in source tree, 
but in distribution it defaults to `./Xponents-VER/xponents-solr` to distinguish it from the raw source.
Because Gazetteer and related metadata is constantly updated, this folder in distribution or in 
your runtime may be versioned as `/path/to/xponents-solr-YYYYQQ` for clarity.
The typical release schedule for the `XP_SOLR` distribution is quarterly.
The OpenSextant/Xponents JVM argument used to set this index path is `opensextant.solr` which 
must be set to the `solr7` index folder, i.e. `XP_SOLR/solr7`.  This may be an absolute or relative path.


Keep the `./xponents-solr/` folder in tact, although only the `solr7` index folder is used at 
runtime -- The other folders provide a fully operational Solr Server.


**Step 4. Build Indices**

The build process can be brittle, so let's educate you and you can make decisions on your own. See comments on each option/directive
The `build.sh` script is the central brain behind the data assembly.  Use that script 
alone to build and manage indices, however if there are problems see the individual steps below
to intervene and redo any steps. 

**Build Setup**
Managing public domain data sets pulled down, scraped, harvested, etc. involves additional Python libraries
that are not required by normal use of the `opensextant` package.  Add this Pip-installable items now from the 
Xponents root folder:

```shell script

  pip3 install -U --target ./piplib lxml bs4 arrow requests pycountry PyGeodesy

```

**MAINTENANCE USE:**

To update the Gazetteer Meta JAR (`xponents-gazetteer-meta.jar`) in between major quarterly releases, consider these few steps:

1. Follow notes above to setup.
2. Update Person names filter
3. Create JAR
4. Field new JAR with your runtime deployment in CLASSPATH

```
  cd ./solr
  python3 ./script/assemble_person_filter.py 
  ant gaz-meta
```


**FIRST USE:** 
```shell script
    build.sh  start clean data 
```
IF you have gotten to this step and feel confident things look good, this one invocation of `build.sh`
should allow you to run steps 4a, 4b, and 4c below all in one command.  STOP HERE.  If the above succeeded, check 
your running solr instance at http://localhost:7000/solr/ and inspect the different Cores.  If you don't know
Solr, please go learn a little bit about using that Solr Admin interface.  If you have about 20 million rows in the
gazetteer you are likely ready to go start using Xponents SDK.

**NEXT USES:** You should not need to reacquire data sources, clean or restart Solr after that first use of build.sh.
 Subsequent uses may be only `build.sh  gazetteer`, to focus on reloading the gazetteer, for example.  The rest of 
 this nonsense is to provide more transparency on the individual steps in the event something went wrong.

```
   Synopsis:
   ./build.sh  start clean data proxy gazeteer taxcat

   # clean  = Clean Solr indices and initialize library folders with copies of dependencies, etc.

   # start  = Start the Solr server on default Xponents port 7000.  
              Solr Server is only used at build time, not at runtime
   # data   = Acquire additional data e.g., Census, Geonames.org, JRC entities, etc. 
              These data sets are not cleaned by 'clean'
   # proxy  = IF you are behind a proxy, set your proxy in build.propertes

   # gazetteer = regenerate only the gazetteer index
   
   # taxcat    = regenerate only the taxcat index

```


**Step 4.a Initialize**

```shell script
    # If you use a proxy, then include proxy command first in all your Ant invocations.
    # As well, set proxy.host and proxy.port in your build.properties above.
    #
    ant [proxy] init
```

**Step 4.b Get Supporting Data**

```shell script
    build.sh [proxy] data 
```

This will pull down data sets used by Gazetteer and TaxCat taggers and resources using the Ant tasks:

* `ant get-gaz-resources `
* `ant taxcat-jrc `


**Step 4.c Load Gazetteer & TaxCat** 

In this step, you can use:

```
    ./build.sh  [proxy] [start] 
```

which will build the Solr gazetteer index and add to the taxcat index.  If the Solr Server is not
running it will be started.  Access Solr URL is http://localhost:7000/solr


CLASSPATH NOTE: "Filters" are important to gazetteer tuning.  I refer to "/filters/"  resources in 
taggers and data processing.  Filters are packed in the `xponents-gazetteer.jar` and is required for 
both running Solr-server gazetteer operations and normal Xponents library operations.  This JAR
must be available in the `CLASSPATH`

```shell script
   
   # Copy Xponents gazetteer meta-files to runtime location
   #
   ant gaz-meta
```


Expert Topics
================

TaxCat index ~ Taxonomic Catalog
---------------------------------
This step falls under the category of geotagger tuning.  E.g., see Extraction PlaceGeocoder class
as an implementation of a full geotagging capability.  To negate false-positives we need a source
of known things that are not places, rules that guide us how to judge non-places, or some other 
means such as statistical models to do so.

XTax API uses TaxCat (`./solr7/taxcat` core).  This API supports the Gazetteer and Xponents taggers
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

Feature Metrics
---------------------------------

Relative metrics on feature classes, followed immediately by some commentary on 
how such feature types as mentioned in most text are seen.   For example, 
we do not often hear about folks talking about Undersea features.  If an solid exact match for 
such a name is tagged and geocoded our confidence in that finding is based on a few aspects:

* length of the name (i.e., longer names are more explicit and less ambiguous)
* relative popularity of the feature class in available gazetteer data (an a-priori weighting may be used)
* likelihood of the term being used, aka `Mention Weight` is a relative weight applied to the Xponents confidence metric
* uniqueness of the name
* quality of specific gazetteer sources in terms of fairness of coverage and balance across the world \[Acheson, et al, 2017].

Approximate Feature Count from OpenSextant Gazetteer (2020)

    Feature Type   Count        Mention-Weight
    Places (P) - 9,000,000          1.0
    Hydro (H)  - 3,200,000          0.7 default
         H/STM*        50%          0.3
         H/LK*         10%
         H/RSV, SPNG, WLL 10%       0.3
         H/BAY, COVE    1% 
    Spot  (S)  - 2.700,000          0.8
    Terrain(T) - 2,300,000          0.8
    Land   (L) -   700,000          0.8
    Admin  (A) -   700,000          1.0
    Vegetation(V) - 85,000          0.8
    Roadways (R) -  65,000          0.7
    Undersea (U) -  12,000          0.5


This is an initial, experimental model for features based on intuition.
Populated Place and Administrative features 
are far more common in most data, but this depends on your domain.  This weighting will NOT omit
particular feature types, but it will help with disambiguating (choosing a most likely feature) 
and informing the confidence in that conclusion.  More test data is needed to objectively build 
a reasonable feature model.  

References:
* Acheson, De Sabbata, Purvesa   "A quantitative analysis of global gazetteers: Patterns of coverage for common feature types". 2017.  https://www.sciencedirect.com/science/article/pii/S0198971516302496 



Honing Gazetteer Index 
---------------------------------
Size matters.  So does content.  Your gazetteer should contain named locations and other data
you want to use in your application.  For example, An application for a complete worldwide name search suggests you have a full gazetteer; An application of lightweight desktop geocoding suggests 
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

