Xponents Basics

#Purpose#
Basics library provides some key object types used throughout Xponents.  As well, some commonly used utility classes are 
offered -- the utility classes are used under the hood of Xponent extraction, but are not required by API users otherwise.

The common objects cover the geography, date/time and taxonomic extraction.  They include:

* Geo: Country, Place, Geocoding,  which extend/implmement  GeoBase and LatLon.  
  A PlaceName is often used to represent a name entry in a gazetteer, however the GeoBase variants offer a basic name attribute.
* Language: Language object simply captures a language ID/name pair for use in extraction
* Term Extraction:  Taxon is a simple terminology node including the tuple of term ID/node/phrase/metadata.
* Pipeline input: TextInput and DocInput offer a convenient way of passing in a identified input; DocInput provides a filename and path

Utilities that facilitate extraction applications include these:

* FileUtility - dictionary loading, filename tests and tools.  Such things are beyond common resources such as Apache Commons. 
* GeodeticUtility - a convenience library for validating Basics objects and other primitives checks for XY-coordinate systems. 
  Limited support for geohashing, as the main use of geohashing in extraction work is inferring precision.
* GeonamesUtility - a library of tools for testing Geonames-like metadata (e.g., feature codes, types, etc). 
* TextUtils and UnicodeTextUtils - a wide range of text scrubbing routines not commonly found in other open source libraries.

#Behaviors#

The most common resources used are the language name mappings, Geonames features, and Geonames country list (curated by OpenSextant.org)
Staticly these files are loaded from the resource classpath (Basics JAR file):

  * ./src/main/resources/country-names-2013.csv  // OpenSextant.org gazetteer 
  * ./src/main/resources/feature-metadata-2013.csv // OpenSextant.org gazetteer
  * ./src/main/resources/ISO-639-2_utf-8.txt  // Library of Congress (LoC) language listing 

