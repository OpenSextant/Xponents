OpenSextant Solr Gazetteer
============================
The OpenSextant Gazetteer is a catalog of place names and basic geographic metadata, such 
as country code, location, feature codings.   In Xponents, Solr 7+ is used to index and provision 
the large lexicons such as gazetteer and taxonomies.

Related:
- [Gazetteer Report](REPORT.md) lists some underlying raw statistics and SQL techniques for working with the 
  master gazetteer.  For example, Using SQLite to list all possible names, distinct location counts in the gazetteer
  by feature class, or how to use the `opensextant.gazetteer` API to query such things.

Definitions: 
* **OpenSextant "Xponents Solr"** is a particular Solr-based gazetteer implementation the 
  provides specific features to the Xponents API, taggers, etc.
* **"Gazetteer ETL"** - extract, transform and load - is the various conventions and routines to taking
  arbitrary source gazetteers and conditioning them for use as a geographic tagging model.  We are 
  concerned with balancing accuracy, thoroughness, usability and simplicity.  So the resulting data
  may not be pure or complete with regards to its source version because some interpretation, 
  conversion, or back-filling may be necessary to use the data at all.
  * Extract: the scripting assciated with the source data harvesting and parsing from flat files
  * Transform: the scripting associated with mapping source data to the `opensextant.Place` class
   and enrichening that with the internal text model and `PlaceHueristics` that produce name and location biasing based on general assumptions.
  * Load: the final scripting associated with parking entries in the `master_gazetteer.sqlite` and subsequently into the Solr `gazetteer` index
  * Repeat all above for the `postal` index ETL.
* **"Solr Index"** in this project Solr indices *store* reference data and provide a `/tag` 
  operation (`request handler`) to identify that reference data in an input argument to `/tag`.  The main indices are:
  * `gazetteer`
  * `taxcat`
  * `postal`

You do NOT need know all about SQLite, Solr or Lucene to make use of this, but it helps
when you need to optimize or extend things for new langauges.


Getting started
================================
You have a few options:

**Option 1.** Download Xponents SDK release (libraries, docs, and pre-built Xponents Solr)
* i.e., You just want the capability. No hassle.
* https://github.com/OpenSextant/Xponents/releases will have binary releases (no gazetteer data); Maven Central has JARs/javaodcs
* https://hub.docker.com/r/mubaldino/opensextant will have Xponents 3.x and later as a full running service.
* You may exit now -- there is not much more on this page you need to know.

**Option 2.** Checkout Xponents  projects and build from latest source and data.
* i.e., You want the full experience, all the pain of building from source. 
* follow the remainder of these instructions to build Xponents SDK, the master gazetteer and then
 populate the various Solr indices.  From a source checkout you will be guided through 
 installing Python dependencies, collecting the data for ETL and producing a working distribution.

Where Python is referred in any instructions we are referring to Python 3.8+ only.  
`python` and `pip` may be further qualified as `python3` and `pip3` in many scripts.  

The estimated disk space to build a complete distribution is on the order of 20 GB, with various temporary files and all.

**Expectations around Data**

|Source ID|Data Source|ETL Time|Place Name Count|
|-----|----|----|----|
|N|NGA GNIS|25 min|16.6 million|
|U|USGS `NationalFile`|5 min|2.3 million|
|U|US FIPS state postal/numeric codes and names|1 min|150+|
|G|Geonames|25 min|23+ million|
|NE|NaturalEarth Admin Boundaries|25 min|73K|
|ISO|ISO 3166|1 min|850+|
|X|Xponents Derived|5 min|235K|
|-|Geonames Postal|30 min|7 million|

**Distinct Place Names:** 24 million


**Build Setup - Python, Java, etc**

Managing public domain data sets pulled down, scraped, harvested, etc. involves additional Python libraries
that are not required by normal use of the `opensextant` package.  Add this Pip-installable items now from the 
Xponents root folder:

```shell script

    cd Xponents
    ./setup.sh 

    # Note - if working with a distribution release, the built Python package is in ./python/ (not ./python/dist/)
    # Note - chose any means you want to set your effective Python environment; I use the PYTHONPATH var

    export PYTHONPATH=$PWD/piplib

    # or 

    . ./dev.env
    
```

Linux/Mac kernel configuration related to Solr server usage also requires increasing certain "ulimit" limits
above defaults:

```
  # EXPERIMENTAL.

  # As root
  /sbin/sysctl fs.file-max=65536000
  # or temporarily
  ulimit -n 65536000
  
  # As user, increase user process max
  ulimit -u 8092

  # As root:
  sudo /sbin/sysctl -p
```



Option 2.  Build Gazetteer From Scatch
---------------------------------------------

Here is an overview of this data curation process:

 The main sources are ISO 3166, US NGA and USGS to cover world wide geography
- Secondary sources (Geonames.org, Natural Earth, Adhoc entries, Generated name variants)  are assembled from these Xponents scripts
- A one-time collection of `wordstats` is needed to identify common terms that collide with location names.
- With all data collected, each data set is loaded into SQLite with specific source identifiers
- With the master SQLite gazetteer complete entries can be de-duplicated, marked and optimized
- Finally, the master gazetter entries (non-duplicates) are funneled to the default Xponents Solr instance 

The steps here represent the journey of how to produce this behemoth -- a process we are constantly trying to streamline 
and automate.


**1. Data Collection**

```shell

    # cd ./solr

    ant gaz-resources
    ant gaz-stopwords
    ant gaz-sources
    
```

In parallel, run the wordstats collection ONCE.  This material does not change. You end up with about a 1.0 GB 
SQLite file with unigram counts from GooleBooks Ngrams project.

```shell
    ./script/wordstats.sh download
    ./script/wordstats.sh assemble
    
    # Once fully debugged this script may change to streaming or delete download files when done.
    # You may remove the ./tmp/wordstats/*.gz content once this script has completed.
    # Output: ./tmp/wordstats.sqlite
```

**2. Collect and Ingest Secondary Sources**

This SQLite master curation process is central to the Xponents gazetteer/geotagger.  
All of the metadata and source data is channeled through this and optimized. 
The raw SQLite master is approaching 10 GB or more containing about 45 million place names.
By contrast the resulting Solr index is about 3.0 GB with 25 million placenames. 
The optimization steps are essential to managing size and comprehensive coverage.

```shell script

  cd Xponents/solr
  ./build-sqlite-master.sh 
  
  # A simple test attempts to pull in only 100,000 rows of data from each source to see how things work.
  # ./build-sqlite-master.sh test   

```

**3. Postal Gazetteer**

The Postal gazetteer/tagger has its own sources (postal codes) but also pulls in metadata
for worldwide provinces from the master gazetteer. Make sure your master gazetteer (or test file)
completes successfully above. 

```shell

  ./build-sqlite-postal.sh 
  
```


Building and Running Xponents Solr
=================================

This is a stock instance of Solr 7.x with a number of custom solr cores.
The main cores are:  `taxcat`, `gazetteer`, and `postal`.  They are populated by the `build.sh` script
using their SQLite databases as the intermediate data:

* `gazetteer`:  99.9% of the `tmp/master_gazetteer.sqlite` distinct entries will be indexed into the Solr gazetteer.  A limited number of default filters omit odd names ~ short names, names of obscure hyrological features (wells, intermittent streams, etc).  Duplicate place names (feature + name + location + country) are not indexed.
* `taxcat`:  Taxcat will contain taxonomic entries such as well-known named entities, nationalities, generic person names, and other useful lexica.   See [XTax README](`./solr/etc/taxcat/README.md`)
* `postal`: The postal index is populated straight from `tmp/postal_gazetteer.sqlite`

These notes here are for the general situation just establishing Solr and iterating through common tasks.


Setup
----------

**Step 1. Get Solr 7.x**

To get a fully working Solr instance running unpack the full Solr 7.x distribution here 
at `./solr7-dist`.  This involves some extra steps, but is relatively well tested.
Using the latest Solr distribution would involve updating Maven POM, possibly, as well as
reviewing Solr index configurations.

```shell script
    wget http://archive.apache.org/dist/lucene/solr/7.7.3/solr-7.7.3.zip
    unzip solr-7.7.3.zip
    SOLR_DIST=./solr7-dist
    mv ./solr-7.7.3  $SOLR_DIST

    rm -rf $SOLR_DIST/example $SOLR_DIST/server/solr/configsets $SOLR_DIST/contrib $SOLR_DIST/dist/test-framework

    # We could automate this sure. But you need only do it once and hopefully is not repetitive.
    # NOTE If Solr 8.x is in use, the distro is ./solr8-dist.  Differences from Solr 7 to Solr 8 are still being 
    # investigated.
```


**Step 3. Configure and Deployment Paths**

By default, you have this runtime environment in check-out or in distribution:

- `./Xponents/solr/solr7` will contain the Solr indices
- `./Xponents/solr/solr7-dist` will contain the Solr server that serves the indices

We refer to **Xponents Solr** informally as `XP_SOLR`, which is `./Xponents/solr` in source tree, 
but in distribution it defaults to `./Xponents-VER/xponents-solr` to distinguish it from the raw source.
The typical release schedule for the `XP_SOLR` distribution is quarterly.
The OpenSextant/Xponents JVM argument used to set this index path is `opensextant.solr` which 
must be set to the `solr7` index folder, i.e. `XP_SOLR/solr7`.  This may be an absolute or relative path.

Keep the `./xponents-solr/` folder in tact, although only the `solr7` index folder is used at 
runtime -- The other folders provide a fully operational Solr Server.


**Step 4. Build Indices**

The build process can be brittle, so let's get educated so you can make decisions on your own. 

The `build.sh` script is the central brain behind the data assembly.  Use that script 
alone to build and manage indices, however if there are problems see the individual steps below
to intervene and redo any steps. 


**MAINTENANCE USE:**

To update the Gazetteer Meta resources review these few steps:

1. Follow notes above to setup.
2. Update stopwords and person names filter using the build script `meta` command
3. Install Maven project, as JAR contains resources from this ETL

```
  cd ./solr
  ./build.sh meta
  
  cd ..
  mvn install
```

NOTE: The `meta` step above gathers resources below and pushes them up to the Maven project `src/main/resources` so 
they become part of the CLASSPATH ( via `opensextant-xponents-*jar` or from file system). Resources include:

* `/lang/`  -- Lucene and other stopword sets
* `/filters/` -- exclusions for tagging and downstream tuning.
* other content possibly


**FIRST USE:** 
```shell script
    build.sh  meta
    build.sh  start clean data gazetteer
    build.sh  taxcat
    build.sh  postal
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
   # meta   = build and gather metadata resources 
   
   # proxy  = IF you are behind a proxy, set your proxy in build.propertes

   # gazetteer = regenerate only the gazetteer index
   
   # taxcat    = regenerate only the taxcat index
   
   # postal    = regenerate only the postal index

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

* `ant gaz-resources `
* `ant taxcat-jrc `


**Step 4.c Load Gazetteer & TaxCat** 

In this step, you can use:

```
    ./build.sh  [proxy] [start] 
```

which will build the Solr gazetteer index and add to the taxcat index.  If the Solr Server is not
running it will be started.  Access Solr URL is http://localhost:7000/solr


Expert Topics
================

Inspection of Filtered Out
--------------------------------

It is important to periodically look at terms and situations where a phrase is marked for avoiding tagging or something
that will prevent a tag from getting back to the user.  Filtration happens in two manners at least:

1. Phrase in raw reference data is excluded by ingest scripts for some reason
2. Phrase is determined to be not valid or "search_only" -- it is included in the Solr index, but not used in tagging by default.
3. Phrase is tagged, but post-tagging stop filters or other tests remove the phrase from output.  
  Most commonly this is usually done by using the TextMatch class  'filteredOut()' method (Javav) or attribute (Python)

Look at terms marked as search_only in the gazetter and not valid in taxcat:

* Gazetteer: http://localhost:7000/solr/gazetteer/select?facet.field=name&facet=on&q=search_only:true&rows=1000&fl=name,cc&facet.mincount=1&facet.limit=1000&facet.sort=count&wt=json
* TaxCat: http://localhost:7000/solr/taxcat/select?facet.field=phrase&facet=on&q=valid:false&rows=1000&fl=taxnode,phrase&facet.mincount=1&facet.limit=1000&facet.sort=count&wt=json

Set `"wt=csv"` to see CSV format.  This JSON output is setup to list facet patterns of most frequent terms.


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
