RELEASES
==============
Visit https://github.com/OpenSextant/Xponents/releases for latest library releases and notes.

# 2025

**Xponents 3.8**
* Security patches: Log4J, SLF4J ~ v2.0; Logback ~ 1.5.19+, Zookeeper 3.7+; Apache Commons - io, codec, lang, text to latest
* Minor language filter updates
* Significant filtration in Chinese and Arabic geotags
* Major changes in Maven packaging - new Maven Central and plugin versions

# 2024

**Xponents 3.7**
* **Core API**: Convergence
  * Geodesy and GISCore geodetic libraries are now merged into the main Xponents Core
  * Tests and some data for G/G modules are copied over... however the primary test data remains in their respective
   repos `geodesy` and `giscore`.  
  * Substantial JavaLint and security fixes were made to these 10 yr old libraries.  Also dependencies such as FindBugs
  were replaced.
  * Migrated to a separate repo:  Find this now maintained separately at [`Xponents-Core` repo](https://github.com/OpenSextant/Xponents-Core)
  This is the first release ./Core/ folder will not appear in this source tree.
* **Xponents REST**: 
  * Noise filtering;  added `minlen` parameter to Xlayer (Xponents REST) service.  By default it filters out trivial stuff. About 3-5% reduction in 
   noise tags from codes, short names, etc, -- things typically not useful geocodings nor typically names of places.

**Xponents 3.6.7**: Springy
* **Core API**
  * Building up `TextInput` class to carry better metrics to enable filters and language-specific tuning.
  
* PlaceGeocoder tuning:
  * Refined tagging and filtering of CJK texts using mostly length and punctuation filters; Non-trival names
  and phrases will likely be grams of 3 or more characters and have no interceding space or punct.

# 2023

**Xponents 3.6.6**:  New Year Release

Patch focus -- handle acronym and short match noise from Postal codes and other artifacts.

* **Core API**
    * library calls for checking CJK or Middle-eastern scripts (Arabic, Hebrew, etc)
    * library call for checking if a match is an A.B.Breviation... loosely.
* PlaceGeocoder tuning:
    * Applied simpler abbreviation flagging on place candidate mentions, allowing `U.K.` and 
    `U.S.` and `E.E.U.U.`, etc. to be tagged properly and emitted as country match
    * Significant CJK/AR filtering as an interim solution for noise filtering and dealing with 
    a lack of language-specific tokenization and heuristics for match filtering or promoting.

**Xponents 3.6.5**

* PlaceGeocoder tuning:
    * Stop filters: Spanish, Arabic and Russian stopwords and non-places updated.
    * Libraries: Apache Commons libraries updated for IO, Compress, and Codec
    * Tagging filters: omit ADMIN level matches that are short codes, as well as
      avoid tagging bare country codes

**Xponents 3.6.4**

* PostalGeocoder tuning:
    * Optimization: separate tagger and geocoding passes into sepearate classes;
      consolidate postal code filters; Feed pre-tagging matches from PlaceGeocoder to PostalGeocoder
      as an option. PostalGeocoder would still run that internally if not fed externally
    * Tagging Omission Fixed: `POSTAL CITY`  or `CITY POSTAL` are now possible, only if a city represents an
      administrative area, such as `ADM2`-`ADM4` or `PPLA` gazetteer feature. These would be major
      metropolitan areas with their own set of multiple postal codes.
    * Invalid USGS data -- and other data with lat, lon = (0,0) aka 'Unknown' location info -- is filtered out by
      tagger.
    * Core API -- TextMatch class is sortable to allow for tagger applications to sort matches by appearance (offset low
      to high)

**Xponents 3.6.1**

* Taxon matching refinements and expansion. REST API now allows for geotagger to fully extract/report all taxons
* [#81](https://github.com/OpenSextant/Xponents/issues/81) Xponents Core: XCoord parsing of MGRS
* [#77](https://github.com/OpenSextant/Xponents/issues/77) Streamlined dependencies and now understand them better,
  e.g,. Log4J 2.20 updated. In Examples, XText + Tika 2.7 updated and dependencies slimmed down.
* Lots of code cleanup and documentation tweaks
* Python client library will be release in v3.7 with new gazetteer

# 2022

**Xponents 3.5.10**

* Maven Dependency review and scrub

**Xponents 3.5.9**

* Library versions: Post-Log4shell cleanup
    * SLF4J 1.7.36, Logback, 1.2.11, Log4J 2.17.2
    * Commons IO 2.11.0
    * Tika 1.28.3 (Examples and XText)
    * Spatial4J 0.8; JTS 1.x removed from dependencies, but still used in Solr distro
* JavaLint results from Sonarqube scans implemented - addressing the removal of Java 5, 6, 7 code style, code formatting
* **Geocoding**:
    * Postal code precision set at +/- 5000m instead of 50,000m
    * NameCodeRule rule - improved the validation for how "Name, Code" or "Name, AdminName" pairings are managed;
      Avoid repetitive lists such as "Country, Country, ..." or "Province, Province,... ";
      Avoid repetitive codes such as "CODE, CODE" as in `CM MA`
    * Gazetteer regenerated and substantial abbreviations and stop words added.

**Xponents 3.5.7 Postal Patience**

* **Geocoding**:
    - Following fixes related to `features=["postal",...]` in Python API (`XlayerClient`) or REST
      API (`/xlayer/rest/process`)
    - Omit postal tags where random punctuation appears amongst valid postal data. If it is valid,
      you should cleanse your text a bit. Postal codes and abbreviations are very common and
      can often be confused with other coded data
    - Postal tags left undecorated by the end of a call are omitted, ie., tagging for postal features  
      may detect a valid postal code (e.g., ZIP `90210`) -- but will only report it if it is qualified
      by a province or valid city (e.g., `CA 90210`, `Beverly Heights, 90210`). Bare digits are not reported.
    - Precision of postal feature `"A/POST"` was 50,000m; it is now 5,000m.
    - Use `features=["filtered",...]` to retrieve items that are tagged, but omitted

**Xponents 3.5.5 BeginAgain (Re-release)**

* **Geocoding**: Tamped down on acronym false-positives on UPPERCASE and lowercase
  documents given the added gazetteer data includes lots of codes.
    - Default behavior: country codes and province codes are NOT emitted although tagged.
      These are requested explicitly by caller using the `codes` feature. Right, so `USA`
      or `COD` or `MA` are not emitted by default although those bare tokens may represent
      countries or provinces. Such codes qualifying other placenames will be emitted.
    - Gazetteer tagging ommissions: numerous transliterated short names for Pacific/Asian islands `A xx`, `I-xx`
      and various other false-positive places are NOT tagged, although present in the gazetteer.
    - About 500 dictionary words in French, German and English were added to the stop-filter
      for tokens commonly not places. E.g., `amend`, `adept`, etc.
* **Bugs Fixed**:
    - Geocoder Rule `HeatMap` memory leak fixed
    - `German` is removed as a country -- its a nationality or an adjective
    - Tagger will throw `ExtractionException` if it tags 100,000 or more locations from gazetter
* **API Changes**:
    - `codes` feature can be requested in REST API: `features=geo,taxons,patterns,codes` for example.
      This will emit tagged acronyms for admin boundaries for now.
    - Xponents Core `TextUtils` now offers trivial text span testing for common punctuation.
      For example, to quickly test if `MARC __&amp;__ U` looks like a entity or is a false positive
      when tagging the phrase `Marc U` a common punct test was needed. These were fairly obvious
      pre-filters to employ just after tagging and before serious reasoning happens.

**Xponents 3.5.4 BeginAgain**

* **Full Evaluation**: internal evaluation work was redone start to finish to hone outlier gazetteer entries and
  patterns of rogue entries from new data sources. Evaluation work called out and fixed serious false-positive and
  recall
  errors
* **Log4J Remediation**:  While Log4J is not the primary choice of logging facility, it is a dependency that appears
  mainly in the Solr 7.x server distribution. Vulnerable Log4J JAR files were removed and latest ones were injected.
* **API Changes**:
    - `TextEntity` is a text span and requires a start, end offset pair. Only constructor
      requires that pair. Other subclasses can have a zero argument constructor by exception, such as `PoLiMatch`
    - `GeonamesUtility.isCountry()` now only returns true for `PCLI` entries others are historical country names or
      territories.
    - REST API now has `method` and `match-id` on most matches to be more consistent

# 2021

**Xponents 3.5.0 GiveThanks**

Release Objective: Sustainable & expanded gazetteer concepts. Improved geoinferencing rules and evaluation.

* **Features**: "postal" data from Geonames.org incorporated into its own tagger; Support tagging postal codes and
  abbreviations
  is available as a SDK tagger "PostalGeocoder" and also from the REST API, when using `features="postal"`
* **Gazetteer ETL**: continued to refactor data sources thoroughly adding a full SQLite curation pipeline
  for master gazetteer and postal data. Python API and `./solr/script` contain the bulk.
* **Gazetteer ETL**: employing Google Books wordstats (2012) to identify common words
* **Fuzzy Matching**: Honed the concept of phonetic and non-diacritic matching to help optimize gazetteer entry
  validation and tuning and tagger matching.
* **Gazetteer**: Added entries for historical countries, UAE, and numerous variants and nationalities
* **Geolocation**: converted gazetteer data and scoring to 100 point scale for ID bias and name bias (shed 50MB)
  from each data store due to not carrying around unnecessary floating point accuracy.
* **Python API**: `opensextant`, v1.4:
* **Java API**: `opensextant-xponents-core` v3.5:
* **Java API**: `opensextant-xponents` v3.5. Changes:
    * `ScoredPlace` no longer subclasses `Place`: It is a holder for `score` and `place`
    * `Parameters` no longer has `output_*` fields.  `tag_*` fields are used only to indicate user processing/output
      options
* **REST API**: Xponents Server v3.5:
* **Sonar Scan**: Java8 code style and compliance fixes throughout.

**Xponents 3.4.0 Vamp**

* NOT released on docker. Skip.
* Opensextant v1.3 python library updated and streamlined for Python3 conventions
* Gazetteer curation refactored and added serious sources such as Geonames.org and NaturalEarth
* Xponents Solr Gazetteer quarterly release, 2021-Q1
* Geolocation precision improvements based in GeocodeRules used in PlaceGeocoder
* Sonar Scan: Java8 code style and compliance fixes throughout.

**Xponents 3.3.6 Sonar**

* Added Docker notes on Sonarqube scanner and fixed basic javadoc and unit test issues

# 2020

**Xponents 3.3.5 Clarity**

* Maven offline support in a Docker offline image, now posted at https://hub.docker.com/r/mubaldino/opensextant/tags
* Bugs and JavaDoc consistency addressed
* XText brought up to v3.3.5

**Xponents 3.3.3 New Vision**

(( That is, "2020" = New Vision. Happy new year!))

* XCoord improvements - accommodating for missing degree and other symbols
* PlaceGeocoder uses feature class weighting using some common sense and less than obvious math.
  effect is to rank certain features higher than others. this could be data-driven, but for now
  is a implemented with a simple hash table of FEATURE CODES ==> WEIGHTS.

# 2019

**Xponents 3.3.0 Holly Jolly**

* Holiday 2019 release: Date/time pattern detection and normalization improvements.
* Python API improvements, mainly converting `XlayerClient` to return array of `TextMatch` objects, rather than raw
  python dicts.
* Minor gazetteer improvements

**Xponents 3.2.1 Dead of Night**

* Halloween 2019: script simplification, CLASSPATH and other cleanup. XCoord: Reduced ambiguity in some DM vs. DMS
  patterns

**Xponents 3.2.0 Dead Heat**

* July 2019: Refactoring to split a lighter-weight "Core API" from the heavier, more involved tagger SDK

**Xponents 3.1.0 Summer Solstice**

* Reverse geocoding added on request. Xlayer exposes the results of enriching found coordinates
  ** Use of Solr `{geofilt}` does not work with large number of rows of point data -- RPT wants to work with shapes and
  appearently tries to load resources
  to support more advanced shape queries. Ran out of memory with all invocationso of Solr Spatial mechanisms.
* OpenJDK 8 and 12 testing; Experiments on GC settings

# 2018

**Xponents 3.0.6 Pi Day/Equinox**

* Xtemporal now reduces matches filtering out submatches or duplicate date/time matches.

**Xponents 3.0.5 SuperBowl**

* Reviewed low recall due to name-code filters and rules. NAME,.....CODE will not filter out a CODE if CODE is a
  country.
* Solr/Lucene 7.6+
* Tika 1.19+ on XText

**Xponents 3.0.4 Columbus Day**

* Command line improvemnts on testing
* Consolidate all tests and examples under single Groovy script

**Xponents 3.0.3 Day of Rememberance (9/11)**

* Account for all decent stop word lists (see genediazjr "stopwords-iso" project); Stopwords for Tagalog, Urdu, Farsi,
  Chinese, Korean, etc, contributed there.
  These lists just make output less noisy when the language of text is known.
* More LanguageID-driven tests added for PlaceGeocoder
* NAME, CODE patterns teased apart
* Solr 7.4+ is required now; SolrTextTagger miraculously is embedded in Solr, so less has to be done externally to
  configure it all.
* Solr 7.4: removed deprecated Solr request optimizations, and other deprecated SolrJ usage.

**Xponents 3.0 Fourth of July**

* Refactor: all major libraries converged into one project: Basic, Patterns, Xponents are now just "Xponents"
* Refactor: XText is moved up to its own top level OpenSextant project
* Feature: Tweet geocoding was moved from Examples to a formal part of Xponents `org.opensextant.data.social`
  and ` org.opensextant.extractors.geo.social` represent the core functionality.
* Feature: Added Language ID API wrapping CyboZu LangDetect; Xponents `langid` extractor though adds a fair amount of
  wrapping using Xponents `Language` object to make language data (ISO639 codes, etc) easier to use. As well, where
  CyboZu LangDetect fails on short texts
  or other data, Xponents has some fall-back approaches to attempt alternate lang IDs for CJK languages (
  Chinese/Japanese/Korean).
* Versions: Solr 7.3 is core Solr/Lucene version
* Data: Formally support JSON through Jodd.org JSON package primarily with data transforms for "geocoding" data. This
  supports both REST (Xlayer project) and social media ingest and export.

DEPRECATED: 2.10 and earlier
-----------------------------

Xponents 2.10.4 thru 2.10.6:

* Bug fix: PlaceCandidate had opposite sort order (improper implementation of Comparable in ScoredPlace)
* Bug fix: SolrGazetteer.findPlaces() missing " AND " in solr clause
* Added demonstration code under Examples for work with gazetteer
* Fix span tag detection in MatcherUtils

# 2017 #

Xponents 2.10.x Revival

- Solr6 + Java8 support. Solr4 discontinued
- Province Names table: Geocoded matches now have Province Name along side ADM1
- Applied noise filters to TaxonMatcher
- MatcherUtils: utility class to help special cases in sifting through matches, e.g. matches
  on HTML or tagged data need more filtering.

Xponents 2.9.9 Fourth of July

- Much improved stop filters for nearly all Solr-supported languages (using lucene stopword resources)
- Reviewed geocoding rules; Assigned default confidence to country matches, instead of 0.
- Solr6 staging

Xponents 2.9.6

- Addition of a MapReduce (MR) experimentation area. Capability iterates over JSON data that
  has "text" and "id" fields; Geotags text for coordinates and place names.
- Filters: with advent of MR tokenization and false-positives in specific languages became glaringly obvious.
  Fixed: geotagging in Japanese (and Chinese, Korean, etc); As well, improved some common stop word lists
  for Vietnamese and Spanish leveraging stop word lists from Lucene and Carrot2.

Xponents 2.9.3

- Solr Gazetteer: non-places split into general and spanish as the major groupings.
- PlaceGeocoder: nonsense filter handles short matches for diacritic mismatch
- Filters:  CLASSPATH order is important, gazetteer/conf must appear first in classpath to override anything else.  
  Do NOT include test JARs in production setting or in evaluation -- If you do, then be aware of CLASSPATH.

# 2016 #

Xponents 2.9.0

- Fresh look at how resource files are pulled from CLASSPATH:  InputStream (getResourceAsStream()) is the primary entry
  point to
  pulling in any sort of config file or data resource. Getting File or URL should be left to the caller of APIs. If such
  things
  are offered in these APIs it is for mere convenience. Pulling items from JAR, file system, CLASSPATH, etc. seem to
  behave differently
  in different environemnts: e.g., HDFS, Server vs. Client Applications, etc.
- Solr 4.x refactoring:  Provision Solr from Jetty v9; No longer using the crippled jetty-runner v8 JAR.
- Patterns: Streamlined constructors given the resource file issue at top
- Extraction: PlaceGeocoder now weights findings against explictly mentioned countries to improve disambiguation. About
  1% improvement in F-score.

Xponents 2.8.18

- Extraction: NonsenseFilter added short name + number pattern to filter out unlikely name match,
  that is aimed at rare gazetteer entries. Alternatively, mark such things in Gazetteer as SearchOnly = true (to avoid
  tagging at all, by default)

Xponents 2.8.17

- Basics:  US State metadata for mapping FIPS/ISO and ADM1/Postal code pairings
- Extraction: NonsenseFilter added to deal with odd punctuation situations as a result of over-tagging or deep
  tokenization.

Xponents 2.8.16

- Basics:  Country data improvements include territories, timezones, languages spoken, etc; Backed by GeonamesUtility
  and GeodeticUtility
- Basics:  Place object is fleshed out more with population data, when available; ASCII and other name hueristic flags;
  Geohash options;
  overall improved Geocoding interface; Backed by techniques in TexUtils
- Patterns: XCoord and GeocoordMatch reimplement Geocoding interface; Date/Time pattern fixes
- Extraction: PlaceGeocoder added support for Arabic and CJK text parsing if given a language ID;
  Refactored rules stack and performance on scoring candidate names. Overall improvement in default score for a place
  match;
  Tweaked JRCNames to allow for better false-positive negation.
- XText 2.9.x:  Tika 1.13 upgrade; Improved Web/Sharepoint crawling logic (not perfect). Allows user to filter links
  worth capturing and converting
- XText 2.9.x:  TikaHTML parser/converter was not yielding reasonably obvious metadata tags (title, org, author, etc.)
  so I pulled in JerichoHTML to get tags.

# 2015 #

Xponents 2.8.5 - december 2015:

- adding timezone and language metadata;
- PlaceGeocoder: rules and tracing improved.
- PlaceGeocoder: Added nationality detection using XTax; inferred countries lightly rank candidates higher.

Xponents 2.8.x - november 2015: Long over due refactor

- Extraction/Geo: PlaceGeocoder now emitting reasonable choice for location of names; Still initial draft. Heavily
  involved in rules development in Java here. Evaluation of these features is still very much a personal/internal thing.
  -- TODO: document rules in plain language
  -- TOOD: someday opensource evaluation tools
- Patterns (*new*): Splintered off FlexPat-based libraries into this new module. If all a user wants is regex style
  patterns, they do not need Tika or Solr or any of that.
- Basics: TextUtils now has more text case checking tools
- MOVES:
  -- Basics 'flexpat' ---> Patterns
  -- Extraction 'xcoord','xtemporal','poli' --> Patterns

Xponents 2.7.19 - november 2015, bug fixes and fine tuning .16 patches

Xponents 2.7.16 - october 2015

- Extraction: 'PlaceGeocoder' saw a focused effort on improving how popular well-known
  entities can be used to negate gazetteer tagging. This solution makes better use of XTax as a naiive entity tagger.
  Overall, recall is maximized at the same time geo-tagging precision is maximized.
  As well, the foundation of "Geocode Rules" is established but needs further documentation.

Xponents 2.7.15 - october 2015

- Java 8: tested strict javadoc compilation and fixed errors. Warnings remain
- Basics: added timezone/UTC offset table to country objects (courtesty of geonames.org)
- packaging: removed deprecated code such as progress listeners
- Extraction: Retested Gazetteer spatial query, as certain standard solr spatial mechanisms force index to load into
  RAM, e.g., sort-by-dist
- Extraction: lower-case and case-insensitive matching enabled in GazetteerMatcher for odd cases like working with
  social media
- Extraction/Gazetteer:   added abillity to upload JSON form of gazetteer records, e.g. aliases for existing known
  gazetter entries

Xponents 2.7.8 - july 2015

- Java 7 is the norm, but tested compilation and running on Java 8.
- XText: improved semantics for found hyperlinks in web crawls
- XText: Tika 1.8 is latest
- Basics: fixed country code hash maps; added more text utility for handling unicode situations: Emojis and other
  language issues.
- Basics: Enhanced the concept of a "Geocoding"  interface to include ADM1 Name in addition to ADM1 code
- Extraction: Honed use of JRCNames as a keyword tagging resource in XTax
- Extraction: Devised a rule set for a full range of geocoding ideas in PlaceGeocoder (coords, countries, places) while
  looking at filtering out terms and tokens for performance reasons.
- Dist: Improved distribution packaging (script/dist.xml)

# 2014 #

Xponents 2.5.1 - July 2014

- Java 7+ required now; Java 6 source syntax supported, but release will be Java 7 binary
- Javadoc cleanup
- XText refactor, given added archive file support; concept of caching and crawling is optional and moved out of main
  conversion logic.

Xponents 2.4.3 - June 2014

- Extraction: MGRS filters for well known dates/months, lower case (default is to filter out lowercase), and Line
  endings in Latband/GZD
- XText bug fixes; check style review:  v1.5.4
- POM cleanup and indentation; review unspecified compile time dependencies

Xponents 2.3 - May 2014

- minor tweeks in APIs
- added set_match_id(match, counter)  to FlexPat matchers

Xponents XText 1.5 - May 2014

- numerous fixes in XText proper, and many path normalization fixes in ConvertedDocument
- added Mail crawler and MessageConverter for handling email
- many improvements to JPEG/EXIF conversion
