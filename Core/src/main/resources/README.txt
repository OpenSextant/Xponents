
Core Resources
=============

FlexPat Patterns:

* XCoord patterns:  geo-coordinate formats found in the wild
* XTemporal patterns: date/time formats found in the wild, in file names, text, headings, etc.
* PoLi patterns:  Patterns of Life (PoLi) patterns can be extended and is relatively unbounded.  These are any 
  patterns that describe any aspect of life: money, digits, identifiers, etc


Resources (./resources) Contains data from these sources:

feature metadata - plain language descriptions for each of the Geonames feature classes and codes (AKA 
"Designation Codes").  Country names mapped to FIPS, ISO2, and ISO3 country codes.
*  SOURCE: https://geonames.nga.mil/geonames/GNSHome/reference.html
*  FILE: feature-metadata-2013.csv, feature-metadata-2022.csv

Country Codes: 

* FILE: country-names-2015.csv country-names-2021.csv
* SOURCES: geonames.org, ISO-3166, Wikipedia; NGA lists FIPS 10-4 codes for legacy use

Language metadata  -- mapping language trigraph/digraph to names. Using the ISO-639-2 standard.
Java language does not provide full list of languages for supported Locales.  IBM ICU4J does not do much better.

* FILE: ISO-639-2_utf-8.txt  
* SOURCE: Library of Congress, http://www.loc.gov/standards/iso639-2/php/English_list.php


US State Info: Locations, Codes and metadata is inspired by USGS

* FILE: us-state-metadata.csv
* SOURCE: https://www.usgs.gov/u.s.-board-on-geographic-names/download-gnis-data

Geonames.org timeZones.txt and and files under ./geonames.org folder:   

   A listing of Timezones per country/region/city.
   LICENSE

   This work is licensed under a Creative Commons Attribution 3.0 License (4.0 License as of 2017).  Both license versions apply.
   see https://creativecommons.org/licenses/by/4.0/legalcode
   see http://creativecommons.org/licenses/by/3.0/ 
   The Data is provided "as is" without warranty or any representation of accuracy, timeliness or completeness.

   The data format is tab-delimited text in utf8 encoding.



