# Gazetteer ETL Production Reporting Techniques

This report contains some of the basic techniques for reporting 
and validating the contents of the master gazetteer.  Not all of these
will work on subset databases or partial master gazetteers. 

* TODO: automate DB validation by source and country and generate this report.
* CAVEAT: The SQLite DB is available if you build the `./solr` gazetteer project data sources
  Or know someone who has and is kind enough to share.


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

Breaking away from the high-level reference data -- which is just backed by flat files -- 
let's get into the full, master gazetteer using `opensextant.gazetteer.DB`


```python
from opensextant.gazetteer import DB, get_default_db

# cd ./Xponents/solr,  and then get_default_db() works as it is a relative path.
# Otherwise use DB(dbfile) where dbfile is the path to your SQLite file.
#
db = DB(get_default_db())
names = db.list_admin_names()

# Some place in the USA -- This is a completely random location choice.
lat, lon = (44.321, 89.765)
for dist, geo in db.list_places_at(lat=lat, lon=lon):
    print("Distance", dist, "Place:", geo)

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


# I think you get the picture.
lat, lon = (55.321, 27.765)
for dist, geo in db.list_places_at(lat=lat, lon=lon):
    print("Distance", dist, "Place:", geo)
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
 