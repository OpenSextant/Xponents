# Gazetteer ETL Production Report & API Usage

This report contains some of the basic techniques for reporting 
and validating the contents of the master gazetteer.  Not all of these
will work on subset databases or partial master gazetteers. 

* TODO: automate DB validation by source and country and generate this report.
* CAVEAT: The SQLite DB is available if you build the `./solr` gazetteer project data sources
  Or know someone who has and is kind enough to share.  The master gazetteer SQLite (or other 
  intermediary databases) are not shared -- You can build it, though.
* FINALLY: Please note that this is the debut of the pure Python Gazetteer ETL pipeline.  
  The Python API supporting this is an initial release, but well tested.  This documentation 
  will eventually migrate to a formal Python API document.  Please scan the entire page so 
  you understand the API functions and limitations.  Also, please file a git issue here if 
  you would like to see features or find bugs.

Thank you,
  The Management.


## Sources

**USA NGA Geographic Names Database**: is cited as the following as accessed from https://geonames.nga.mil/geonames/GNSHome/index.html

```
Toponymic information is based on the Geographic Names Database, containing
official standard names approved by the United States Board on Geographic Names and maintained by the
National Geospatial-Intelligence Agency. More information is available at the Resources link at http://www.nga.mil.
The National Geospatial-Intelligence Agency name, initials, and seal are protected by 10 United States Code § Section 425.
```

**Geonames.org**: Content referenced simply as "Geonames" or "Geonames.org" refers to the content from https://www.geonames.org/,
which provides this licensing message:

```
This work is licensed under a Creative Commons Attribution 4.0 License,
see https://creativecommons.org/licenses/by/4.0/
The Data is provided "as is" without warranty or any representation of accuracy, timeliness or completeness.
```

**Natural Earth Data**:  Opensextant Gazetteer contains data "Made with Natural Earth",
![NE Logo](https://www.naturalearthdata.com/wp-content/uploads/2009/08/NEV-Logo-color.png)
[Natural Earth Terms of Use](https://www.naturalearthdata.com/about/terms-of-use/)


**HumData Exchange**: Sources such as the Pakistan Admin-Level-3 gazetteer come from 
HumData (HDX) at https://data.humdata.org/dataset/cod-ab-pak.   Other sources to follow

**OpenSextant Metadata**: Derived mappings for aligning administrative boundary codings 
are cached from various builds of OpenSextant to support the internal data model.  In
2022 the NGA gazetteer was revamped entirely to use ISO alphabetic boundary codings 
entirely replacing their use of FIPS/ISO numeric codings.  These project sources help glue
together the critical administrative boundary hierarchy:

* `./solr/etc/gazetteer/global_admin1_mapping.json` - the final master mapping combining all component sources below
* `./solr/etc/gazetteer/nga_2021_admin1_mapping.json` - NGA codings as of 2021
* `./solr/etc/gazetteer/nga_2022_admin1_mapping.json` - NGA codings at the end of 2022
* `./solr/etc/gazetteer/xponents_v35_admin1_mapping.json` - Interim combined codings from Xponents v35

```text
   ISO     FIPS
   US.MA == US.25 == Massachussetts
   
   ISO      FIPS
   KE.01 == KE.10 == Baringo
   Reference: http://www.statoids.com/uke.html
```

Furthermore standards usage is not consistent:  Geonames uses a mix of FIPS and ISO 
accordingt to http://download.geonames.org/export/dump/readme.txt.   Countries US, CH, BE, ME are represented 
as ISO ADM1 coding.

## Sources and Standards

As of Xponents v3.5 FIPS (aka FIPS 10-4, or US GEC, etc) was the primary internal standard.
For Xponents v3.6 ISO 3166 will be the primary standard for ADM1 coding.  Here's a summary of 
standards in use by sources:

- **NGA**: as of 2022, ISO
- **Geonames**: FIPS, predominately except US, CH, BE and ME
- **NaturalEarth**: Any standards; both FIPS, ISO and historical codings 


## Library Details

The Python API `opensextant.gazetteer`  will be demonstrated later in this document.
That will help with these high-level coverage reports, but more importantly show you
how to integrate the API with the SQLite database into your pipeline.

The "USA & Territory Report" is just an exemplar report for commonly requested data.

## USA & Territory Report

This section reports on the coverage for USA and territories Puerto Rico (PR), Outlying Minor Islands (UM), and US Virgin Islands (VI).  The SQL criteria to get this subset is:

`... and cc in ('US', 'PR', 'UM', 'VI')`

There are MANY names for a given location or feature.  A "Name" or a "named place" is 
a single entry in the gazetteer. Distinct locations are designated by a unique "place ID".
Across naming systems and data sources, still multiple place IDs may refer to the same
physical location. 

Let's break this down into primarily administrative boundaries, cities, and then other.

Administrative Counts:
- 143 ADM1 boundaries ~ states, etc.
- 5000 approx names for those boundaries

City Count:
- 292K names for populated areas
- 220K distinct locations 

Postal Count:
- 41K postal codes -- for US these are strictly 5-digit ZIP codes.

The SQL statements below (which have equivalent means in the Python API) represent
how the above numbers were accomplished. ((Yes, the `cc in (...)` would be required, but 
is omitted here for clarity.))

Review the Data Model Reference below if you have questions about the SQL mechanics.
A more detailed schema is listed in [Source_Schema_Notes](./etc/gazetteer/Source_Schema_Notes.md)

```sqlite

    // Names & Location counts for "Province-level boundaries (Level-1 aka 'ADM1')"
  
    /* LIST */ 
    select * from placenames where feat_class = 'A' and feat_code = 'ADM1' 
      and name_group = '' and name_type = 'N';
  
    /* COUNT NAMES */
    select count(1) from placenames where feat_class = 'A' and feat_code = 'ADM1'  
      and name_group = '' and name_type = 'N' ;
    // COUNT: 4981
  
    /* COUNT LOCATIONS */
    select count(distinct(place_id)) from placenames where feat_class = 'A' and feat_code = 'ADM1' 
      and name_group = '' and name_type = 'N' ;
    // COUNT: 143
  
  
    // Names & Location counts for "Populated Places" ~ cities, towns, villages, etc.
  
    /* LIST */
    select * from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') and name_group = '' and name_type = 'N' ;
  
    /* COUNT NAMES */
    select count(1) from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') 
      and name_group = '' and name_type = 'N';
    // COUNT: 292559
    
    
    /* DISTINCT LOCATION COUNT (by place_id): */
    select count(distinct(place_id)) from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') 
      and name_group = '' and name_type = 'N' ;
    // COUNT: 227782
    
    // POSTAL CODES for US+ coverage.  NOTE:  Use the "postal_gazetteer.sqlite"
    select count(1) from placenames where feat_class = 'A' and feat_code = 'POST';
    // COUNT: 41676
  
```

### General Data Model Reference

Consider some basic nomenclature and conventions for the SQL data model in the 
gazetteer:

- `name_group` is one of '', 'ar' (arabic), or 'cjk' (chinese/japanese/korean ~ han writing system)
- `name_type`  is one of 'N', 'A', 'C' for name, abbreviation or code respectively
- `feat_class` is mainly 'P' or 'A' for populated places or administrative areas.  Other codes represent Terrain, Vegetation, Roads, etc.
- IDs and Sources:  'id' is an internal DB row-id; 'place_id' is a source identifier; 
  A place ID usually prefixes the source ID to properly attribute an entry.  Sources include:
  - G - geonames.org
  - GP - geonames.org postal data
  - U - USGS
  - N - NGA GNIS
  - X - Xponents-derived name
  - NE - Natural Earth consortium
  - others.
- Nota bene: with sqlite, use single quotes always. e.g., see string values in queries below.


## Opensextant Gazetteer Python API

OpenSextant provides a simple API to access a variety of types of geographic data and related stuff. The 
major types include:

* **Reference Gazetteer data:** relatively lean, tabulated information for high-level geography (Countries, Provinces, Major cities, etc). This category 
  is easily served in an API from flatfiles.  OpenSextant provides `opensextant.Place` and `.Country` classes to formally represent such things
* **Complete Gazetteer data:** bulky named points, where a single feature or location may have numerous names, abbreviations, codes in dozens of languages.  
  This data is best served from a database, which here is SQLite.
* **Non-Geographic data:** language codes, popular words, common census name data, and the like.

See the basic reference data in action here, which are demonstrated in the python test package
under [python/test/test_gazetteer_api.py](../python/test/test_gazetteer_api.py)

```python
from opensextant import load_major_cities, load_countries, get_country, load_us_provinces, load_provinces

# A list of countries:
data = load_countries()
print("API country list length:", len(data))

# Or look up by country code in ISO, FIPS or by name.
print("country: ", get_country("FR"))

# List major cities ~ according to Geonames.org:
data = load_major_cities()

# List major provinces worldwide accordingt to Geonames.org, USGS, and other sources.
# returns a dict { 'CC.ADM': Place() obj, ...}
data = load_provinces()

# List US States 
# returns a list of Place() obj
data = load_us_provinces()

```

Breaking away from the high-level reference data, let's get into the full, master gazetteer using `opensextant.gazetteer.DB`


```python
from opensextant.gazetteer import DB, get_default_db

# cd ./Xponents/solr,  and then get_default_db() works as it is a relative path.
# Otherwise use DB(dbfile) where dbfile is the path to your SQLite file.
#
db = DB(get_default_db())
names = db.list_admin_names()

# Some place in the USA -- This is a completely random location choice.
lat, lon = (44.321, -89.765)
for dist, geo in db.list_places_at(lat=lat, lon=lon):
    print("Distance", dist, "Place:", geo)

#-------------------------
## Results are:

DISTANCE in meters from the given lat, lon
GEO object is opensextant.Place


Distance 930 Place: Saratoga Church (historical), US @(44.31302,-89.76151)
Distance 1724 Place: Pioneer Cemetery, US @(44.3364877,-89.7643131)
Distance 1741 Place: Pioneer Cemetery, US @(44.33663,-89.76401)
Distance 1771 Place: Church of God, US @(44.31386,-89.74512)
Distance 1800 Place: Mckinley School (historical), US @(44.3133,-89.74512)
Distance 3674 Place: Columbia School (historical), US @(44.31413,-89.81012)
Distance 3976 Place: Bloody Run, US @(44.3433,-89.80401)
Distance 4123 Place: Fourmile Creek, US @(44.3474659,-89.801235)
Distance 4124 Place: Four Mile Creek, US @(44.34747,-89.80124)
#-------------------------

# Python
#-------------------------
# I think you get the picture.
lat, lon = (55.321, 27.765)
for dist, geo in db.list_places_at(lat=lat, lon=lon):
    print("Distance", dist, "Place:", geo)

#-------------------------
... 
Distance 371 Place: Luchayka, BY @(55.323,27.7697)
Distance 904 Place: Заборцы, BY @(55.3135,27.7705)
Distance 2317 Place: Дылевичи, BY @(55.3007,27.7731)
Distance 2439 Place: Sosnuvka, BY @(55.3,27.754)
Distance 2795 Place: Летники, BY @(55.3446,27.7499)
Distance 3029 Place: Gasperovshhina, BY @(55.3144,27.7186)
Distance 3310 Place: Кравцы, BY @(55.3492,27.7484)
Distance 3477 Place: Барсуки, BY @(55.343,27.726)
Distance 3492 Place: Soroki, BY @(55.3233,27.71)
Distance 3762 Place: Las'kiye, BY @(55.2872,27.7643)
```
 
