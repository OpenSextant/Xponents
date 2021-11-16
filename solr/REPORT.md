

Find all names of provinces

GENERAL
=======================

- name_group is one of '', 'ar' (arabic), or 'cjk' (chinese/japanese/korean ~ han writing system)
- name_type  is one of 'N', 'A', 'C' for name, abbreviation or code respectively
- feat_class is mainly 'P' or 'A' for populated places or administrative areas.  Other codes represent Terrain, Vegetation, Roads, etc.
- IDs and Sources:  'id' is an internal DB row-id; 'place_id' is a source identifier; 
  A place ID usually prefixes the source ID to properly attribute an entry.  Sources include:
  - G - geonames.org
  - GP - geonames.org postal data
  - U - USGS
  - N - NGA GNIS
  - X - Xponents-derived name
  - NE - Natural Earth consortium
  - others.

- SQLITE:  with sqlite, use single quotes always. e.g., see string values in queries below.


LOCATIONS for UNITED STATES and Territories AKA cc in ('US', 'PR', 'UM', 'VI')):
======================

Administrative:
- 143 ADM1 boundaries ~ states, etc.
- 5000 approx names for those boundaries

Cities:
- 292K names for populated areas
- 220K distinct locations 

Postal:
- 41K postal codes



NAMES for "Province-level boundaries (Level-1 aka 'ADM1')"
======================

LIST - 
  select * from placenames where feat_class = 'A' and feat_code = 'ADM1' 
    and name_group = '' and name_type = 'N';

COUNT NAMES
  select count(1) from placenames where feat_class = 'A' and feat_code = 'ADM1'  
    and name_group = '' and name_type = 'N' ;
  4981

COUNT LOCATIONS
  select count(distinct(place_id)) from placenames where feat_class = 'A' and feat_code = 'ADM1' 
    and name_group = '' and name_type = 'N' ;
  143


NAMES for "Populated Places" ~ cities, towns, villages, etc.
============================================================
select * from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') and name_group = '' and name_type = 'N' ;

DISTINCT NAME COUNT:
select count(1) from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') 
  and name_group = '' and name_type = 'N';
292559


DISTINCT LOCATION COUNT (by place_id):
select count(distinct(place_id)) from placenames where feat_class = 'P' and feat_code in ('PPL', 'PPLC') 
  and name_group = '' and name_type = 'N' ;
227782



POSTAL CODES for worldwide coverage
============================================================

DISTINCT COUNT:
select count(1) from placenames where feat_class = 'A' and feat_code = 'POST';
41676
