Xponents Basics
===============

#Purpose#
Basics library provides some key object types used throughout Xponents.  As well, some commonly used utility classes are 
offered -- the utility classes are used under the hood of Xponent extraction, but are not required by API users otherwise.

#Objects and Utilities#
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

  * ./src/main/resources/country-names-2015.csv  // Original version: OpenSextant.org gazetteer ; updated here to reflect territories and other flags
  * ./src/main/resources/feature-metadata-2013.csv // OpenSextant.org gazetteer
  * ./src/main/resources/ISO-639-2_utf-8.txt  // Library of Congress (LoC) language listing 

Items moved to CLASSATH:
These are being refactored.  Geonames.org has files used in the build.
Yes, we can automate pulling these files in.  For now download them and put them in the designated location:
  * For build and test, make Basics/src/test/resources/geonames.org/
  * copy in cities15000.txt and countryInfo.txt
  * For production, also copy these items into ./solr/gazetteer/conf/geonames.org/


#Pairings with Extraction Tools#

* Geocoding, LatLon and GeodeticUtility are used heavily by XCoord (Extraction org.opensextant.extractors.xcoord)
* Place, PlaceName and Country are used by the SimpleGeocoder (TBD) (Extraction org.opensextant.extractors.geo)
* GeonamesUtility is optional for use with OpenSextant gazeteer records i.e, those stored in solr 'gazetteer' index OR for use with any geonames data.
* Taxon is used by TaxonMatcher aka XTax (Examples and Extraction org.opensextant.extractors.xtax)
* Language is used by TextUtils
