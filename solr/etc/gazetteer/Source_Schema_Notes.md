# Gazetteer Curation and Data Model

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

Xponents SQLite ETL Schema
------------------

```shell

sqlite TABLE placenames (

	id              -- integer row ID used here and in Solr index 
	place_id        -- source place ID        
	name	        -- the place name, abbreviation or code. Any text, really	
	name_type       -- type of name N, A, C, respectively        
	name_group	    -- coarse grouping by language script/tokenization model. 
	                  general (""), Arabic ("ar"), and Chinese/Japanese/Korean ("cjk")	
	source	        -- source catalog ID
	feat_class      -- geonames-based feature class 
	feat_code       -- geonames-based feature coding
 	cc              -- ISO 3166 country code (2-alpha) or "*"
	FIPS_cc         -- FIPS 10-4 country code (2-alpha) or "*"
	adm1            -- ISO-based Admin Level 1 boundary code. FIPS enumerations are remapped to US postal ADM1
	adm2            -- Admin Level 2 boundary code. Not always provided
	lat, lon        -- WGS84 decimal latitude/longitude 
	geohash         -- 6-character geohash
	duplicate       -- distinct base names are default as duplicate=0; 
	                   duplicates on the KEY=<country+name+feature+geohash> are marked as duplicate=1
	name_bias       -- Internal name bias from -100 to 100 indicating if a name is worth tagging or for searching only
	id_bias         -- Internal place ID bias indicating *our* preference for this particular name+location pair 
	                   when that name appears 
	search_only     -- Flag to indicate this geography should be carried along with Gazetteer, but not used for tagging


INDICES on columns above help speed curation and Solr indexing:

*  place_id
*  source
*  cc
*  adm1
*  name
*  name_type
*  name_group
*  feat_class
*  feat_code
*  duplicate
*  search_only


```


Geonames
----------

Geonames Schema is available at http://geonames.org/export/dump/

```

geonameid         : integer id of record in geonames database
name              : name of geographical point (utf8) varchar(200)
asciiname         : name of geographical point in plain ascii characters, varchar(200)
alternatenames    : alternatenames, comma separated, ascii names automatically transliterated, convenience attribute from alternatename table, varchar(10000)
latitude          : latitude in decimal degrees (wgs84)
longitude         : longitude in decimal degrees (wgs84)
feature class     : see http://www.geonames.org/export/codes.html, char(1)
feature code      : see http://www.geonames.org/export/codes.html, varchar(10)
country code      : ISO-3166 2-letter country code, 2 characters
cc2               : alternate country codes, comma separated, ISO-3166 2-letter country code, 200 characters
admin1 code       : fipscode (subject to change to iso code), see exceptions below, see file admin1Codes.txt for display names of this code; varchar(20)
admin2 code       : code for the second administrative division, a county in the US, see file admin2Codes.txt; varchar(80)
admin3 code       : code for third level administrative division, varchar(20)
admin4 code       : code for fourth level administrative division, varchar(20)
population        : bigint (8 byte int)
elevation         : in meters, integer
dem               : digital elevation model, srtm3 or gtopo30, average elevation of 3''x3'' (ca 90mx90m) or 30''x30'' (ca 900mx900m) area in meters, integer. srtm processed by cgiar/ciat.
timezone          : the iana timezone id (see file timeZone.txt) varchar(40)
modification date : date of last modification in yyyy-MM-dd format

AdminCodes:
Most adm1 are FIPS codes. ISO codes are used for US, CH, BE and ME. UK and Greece are using an additional level between country and fips code. The code '00' stands for general features where no specific adm1 code is defined.
The corresponding admin feature is found with the same countrycode and adminX codes and the respective feature code ADMx.
```
NaturalEarth
----------
Natural Earth 10m boundary shapefile

```
# As printed out using _schema()
NATEARTH_SCHEMA = """
DeletionFlag C
featurecla C
scalerank N
adm1_code C
diss_me N
iso_3166_2 C
wikipedia C
iso_a2 C
adm0_sr N
name C
name_alt C
name_local C
type C
type_en C
code_local C
code_hasc C
note C
hasc_maybe C
region C
region_cod C
provnum_ne N
gadm_level N
check_me N
datarank N
abbrev C
postal C
area_sqkm N
sameascity N
labelrank N
name_len N
mapcolor9 N
mapcolor13 N
fips C
fips_alt C
woe_id N
woe_label C
woe_name C
latitude N
longitude N
sov_a3 C
adm0_a3 C
adm0_label N
admin C
geonunit C
gu_a3 C
gn_id N
gn_name C
gns_id N
gns_name C
gn_level N
gn_region C
gn_a1_code C
region_sub C
sub_code C
gns_level N
gns_lang C
gns_adm1 C
gns_region C
min_label N
max_label N
min_zoom N
wikidataid C
name_ar C
name_bn C
name_de C
name_en C
name_es C
name_fr C
name_el C
name_hi C
name_hu C
name_id C
name_it C
name_ja C
name_ko C
name_nl C
name_pl C
name_pt C
name_ru C
name_sv C
name_tr C
name_vi C
name_zh C
ne_id N
"""
```