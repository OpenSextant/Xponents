# Gazetteer Curation and Data Model

SEE [GAZETTEER_REPORT](../../GAZETTEER_REPORT.md) for source information and data rights.

Xponents SQLite ETL Schema
------------------

The Xponents SQLite schema represents the core ETL data to produce the converged and merged
Gazetteer. From here it is indexed into Solr as the production gazetteer/tagger.

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

Original Geonames Schema is available at http://download.geonames.org/export/dump/readme.txt

NaturalEarth
----------

A sampling of Natural Earth 10m boundary shapefile is printed here.  Still evaluating 
which resolution file to use.

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
