
#2015#
* Xponents 2.7.8 - july 2015
 - Java 7 is the norm, but tested compilation and running on Java 8.
 - XText: improved semantics for found hyperlinks in web crawls
 - XText: Tika 1.8 is latest
 - Basics: fixed country code hash maps; added more text utility for handling unicode situations: Emojis and other language issues.
 - Basics: Enhanced the concept of a "Geocoding"  interface to include ADM1 Name in addition to ADM1 code
 - Extraction: Honed use of JRCNames as a keyword tagging resource in XTax
 - Extraction: Devised a rule set for a full range of geocoding ideas in PlaceGeocoder (coords, countries, places) while looking at filtering out terms and tokens for performance reasons.
 - Dist: Improved distribution packaging (script/dist.xml)
   

#2014#

* Xponents 2.5.1 - July 2014 
 - Java 7+ required now;  Java 6 source syntax supported, but release will be Java 7 binary
 - Javadoc cleanup
 - XText refactor, given added archive file support; concept of caching and crawling is optional and moved out of main conversion logic. 

* Xponents 2.4.3 - June 2014
 - Extraction: MGRS filters for well known dates/months, lower case (default is to filter out lowercase), and Line endings in Latband/GZD
 - XText bug fixes; check style review:  v1.5.4
 - POM cleanup and indentation; review unspecified compile time dependencies

*  Xponents 2.3  - May 2014
 - minor tweeks in APIs
 - added set_match_id(match, counter)  to FlexPat matchers

*  Xponents XText 1.5 - May 2014
 - numerous fixes in XText proper, and many path normalization fixes in ConvertedDocument
 - added Mail crawler and MessageConverter for handling email
 - many improvements to JPEG/EXIF conversion
