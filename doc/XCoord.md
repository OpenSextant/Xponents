XCOORD PATTERNS
===================

* **Author**: Marc. C. Ubaldino, MITRE Corporation
* **Date**: 2014-June; updated 2017-August
* Copyright MITRE Corporation, 2012-2017

*XCoord* is a geographic coordinate extractor. It finds the most common coordinate patterns in free text.
That is, if you want to geocode documents, chat messages, bulletins, etc that contain degrees/minute/seconds, decimal
degrees or military grids (MGRS) you will want to use something like XCoord.

Try the XCoord demo in `Examples` source folder or in the distribution, as below.  
The XCoord function is integrated fully with Xponents SDK and REST API by default.

```SYNOPSIS

    Input files that contain various coordinate patterns to see how the extraction behaves.
    
    ./xponents-demo.sh xcoord -h    

        TestXCoord  -f       -- system tests
        TestXCoord  -i TEXT  -- user test 
        TestXCoord  -t FILE  -- user test with file; one test per line
        TestXCoord  -t FILE  -- user test with file
        TestXCoord  -a       -- adhoc tests, e.g., recompiling code and testing
        TestXCoord  -h       -- help. 
```

## Coordinate Rule Library

The main program and class *XCoord* is
at [XCoord](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/java/org/opensextant/extractors/xcoord/XCoord.java)
while the accompanying patterns and rules file
is [geocoord_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/geocoord_patterns.cfg) .

For reference, review the XCoord DEFINES as you review RULES. There are subtle variations in field definitions.

For brevity sake, only true positive tests are included.  "FAIL" tests or true negatives are omitted.  
One test case per RULE is provided to illustrate each pattern. Sources of patterns are derived from
federal research projects performed by the MITRE Corporation.

These five families of patterns are supported:

* [MGRS pattern](#MGRS)
* [UTM pattern](#UTM)
* [DMS patterns](#DMS)
* [DM patterns](#DM)
* [DD patterns](#DD)

Conventions in pattern IDs. Each pattern is enumerated with the its family; Additional nomenclature includes:

* a = trailing hemisphere
* b = prefix hemisphere
* v = variable field length
* dot = use of period separator
* fs = fractional second variant
* deg = has explicit use of degree symbol, and others

**Table 1. Sample Listing of XCoord Patterns and Example Targets for Extraction**

| Family                                            | Pattern ID                                                            | Example                                                                                                                                                                                                                                                        |
|---------------------------------------------------|-----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <a name="MGRS" />**MGRS pattern**                 |                                                                       |                                                                                                                                                                                                                                                                |
| MGRS                                              | MGRS-01                                                               | 38SMB4611036560                                                                                                                                                                                                                                                |
| <BR> <BR>                                         |                                                                       |                                                                                                                                                                                                                                                                |
| <a name="UTM" />**UTM pattern**                   |                                                                       |                                                                                                                                                                                                                                                                |
| UTM                                               | UTM-01                                                                | `17N 699990 3333335` <BR>// Zone/Latitude band + northing + easting;  Optionally with units "m" <BR>// for meters and or N/E marker                                                                                                                            
| <BR> <BR>                                         |                                                                       |                                                                                                                                                                                                                                                                |
| <a name="DMS" />**Degree-Minute-Second patterns** |                                                                       |                                                                                                                                                                                                                                                                |
| DMS                                               | DMS-01fs-a, DMS-01fs-b                                                | `01°44'55.5"N 101°22'33.0"E`<BR>`N01°44'55.5" E101°22'33.0"`<br>// fractional second resolution, w/hash marks, with hemisphere                                                                                                                                 |
| DMS                                               | DMS-01fs-deg                                                          | `01°44'55.5" 101°22'33.0"`<BR>// fractional second resolution, w/hash marks, NO hemisphere                                                                                                                                                                     |
| DMS                                               | DMS-01dot-a, DMS-01dot-b                                              | `01.44.55N 055.44.33E`<BR>`N01.44.55 E055.44.33`<BR>// explicit dot separator                                                                                                                                                                                  |
| DMS                                               | DMS-02                                                                | `N42 18' 00" W102 24' 00"` // variable length fields with separators and hemisphere                                                                                                                                                                            |
| DMS                                               | DMS-01a, DMS-02a                                                      | `421800N 1022400W`<BR>`N421800 W1022400`<BR>// no field separators, D/M/S                                                                                                                                                                                      |
| DMS                                               | DMS-03a, DMS-03b                                                      | `4218001234N 10224001234W`<BR>`N4218001234 W10224001234`<BR>// no field separators; D/M/S.ss assummed                                                                                                                                                          |
| <BR><BR>                                          |                                                                       |                                                                                                                                                                                                                                                                |
| <a name="DM" />**Degree-Minute patterns**         |                                                                       |                                                                                                                                                                                                                                                                |
| DM                                                | DM-00                                                                 | `4218N-009 10224W-003`<BR>// obscure fractional minute notation                                                                                                                                                                                                |
| DM                                                | DM-01a, DM-01a-dash, DM-01a-dot; <BR> DM-01b, DM-01b-dash, DM-01b-dot | `42 18-009N 102 24-003W`<BR>`42-18-009N; 102-24-003W`<BR>`42.18.009N 102.24.003W`<BR>// Ambiguous fractional minute separator<BR>// is handled with distinct patterns<BR><BR>`N4218.009W10224.003`<BR>`N42 18-005 x W102 24-008`<BR>`N42.18.005 x W102.24.008` |
| DM                                                | DM-02a, DM-02b, DM-02b-dash                                           | `4218.009N 10224.003W`<BR>`N4218.0 W10224.0`<BR>`N4218-0018 W10224-0444`<BR> // 02a/b allows for fixed-width D/M without separators.                                                                                                                           |
| DM                                                | DM-03a, DM-03b                                                        | `4218009N10224003W`<BR>`N4218009W10224003`<BR>// Fixed-width patten for D/M.mmm                                                                                                                                                                                |
| DM                                                | DM-03-av, DM-03-av-deg, DM-03-av-decdm                                | `N42 18' W102 24'`<BR>`42° 18' 102° 24'`<BR>`42° 18.44' 102° 24.11'`<BR>// D/M pattern with explicit hashmarks and separators<BR>// 03-av-decdm is pattern with NO hemisphere                                                                                  |
| DM                                                | DM-03-bv                                                              | `42° 18'N 102° 24'W`<BR>// trailing hemisphere, minute resolution                                                                                                                                                                                              |
| DM                                                | DM-04a, DM-04b                                                        | `N4218 W10224`<BR>`4218N 10224W`<BR>// trivial DMH or HDM pattern.                                                                                                                                                                                             |
| DM                                                | DM-05                                                                 | `/4218N4/10224W5/`<BR>// Rare military format with checksum value.                                                                                                                                                                                             |
| DM                                                | DM-06                                                                 | OBE                                                                                                                                                                                                                                                            |
| DM                                                | DM-07                                                                 | `42 DEG 18.0N 102 DEG 24.0W`<BR>// 'DEG' spelled out.  fractional minute resolution                                                                                                                                                                            |
| DM                                                | DM-08                                                                 | `+42 18.0 x -102 24.0`                                                                                                                                                                                                                                         |
| <BR> <BR>                                         |                                                                       |                                                                                                                                                                                                                                                                |
| <a name="DD" />**Decimal Degree patterns**        |                                                                       |                                                                                                                                                                                                                                                                |
| DD                                                | DD-01                                                                 | `N42.3, W102.4`                                                                                                                                                                                                                                                | 
| DD                                                | DD-02                                                                 | ` 42.3N; 102.4W `                                                                                                                                                                                                                                              |
| DD                                                | DD-03                                                                 | `+42.3°;-102.4°` <BR>// explicit degree notation required, otherwise it is just a pair<BR>// of floating point numbers.                                                                                                                                        |
| DD                                                | DD-04                                                                 | `Latitude: N42.3° x Longitude: W102.3° `<BR>// Lat/Lon fields in text, decimal degree resolution                                                                                                                                                               |  
| DD                                                | DD-05                                                                 | `N42°, W102°`                                                                                                                                                                                                                                                  |
| DD                                                | DD-06                                                                 | `42° N, 102° W`                                                                                                                                                                                                                                                |
| DD                                                | DD-07                                                                 | `N42, W102`                                                                                                                                                                                                                                                    |
| END                                               |                                                                       |                                                                                                                                                                                                                                                                |

XCOORD RELEASE NOTES
====================

## XCoord 2.1 through 2.5  June 2014

* Numerous javadoc API updates
* MGRS filters for well known dates/months, lower case (default is to filter out lowercase), and Line endings in
  Latband/GZD
* MGRS exception reporting calmed down
* Version number for XCoord carrries with the rest of Xponents

## XCoord 2.0 2013-July Independence Release

* Xponents now fully maven built, set apart from the "opensextant" super project.
* Improved concept of precision/resolution
* Added more MGRS filters and pattern refinements
* Parsing unbalanced pattern matches better now, while being more flexible with allowed punctuation. Managing false
  positives from looser patterns is important. E.g., pattern with +/-DEG DEG MIN SEC would be an imbalanced lat/lon pair
  where longitude is specified to seconds, but latitude is degrees only. Such a case is very much a false positive.

## XCoord 1.6 2013-MAR St Patrick's release

* MGRS date filtering for various common dates in recent time; for example 03 MAR 12
* Allowing for "dashes" as separators between lat & lon, where in some cases it may appear as a hemisphere sign.
* Introduction of Maven

## XCoord v1.5 2013-JAN  MLK release

Major improvements

* Support for asymmetric coordinates; valid lat/lon formats that have varying resolution, e.g., 34:45N 44:55:10W. valid
  MGRS grids with typos, e.g., 483QR 443 55 ( easting has 3 digits, northing should be "550"); Grids with line breaks or
  interrupting whitespace, 483QR 44\n3 55; Grids with no whitespace, but invalid easting/northing, eg. 483QR44355 --
  both 443 / 550 and 440 / 355 are emitted as potentially correct interpretations
* Allow for multiple interpretations of a coordinate pattern, e.g., MGRS case above where easting/northing is ambiguous
  due to typos.
* Testing data included patterns from wikipedia geotagging conventions for wiki pages.
* Expanded test cases
* Improved date filtering for DMS patterns with odd punctuation, e.g. 2012-12-11 10:45:00 is a time not a coordinate.
  Two-digit year date/time patterns matched often.
* Consolidated patterns library primarily due to consideration of asymmetry in resolution. More smarts put into place in
  parsing/normalizing.

## XCoord v1.3, 2012-10-27 THANKSGIVING release

Minor improvements:

* added more DM/DMS patterns to ensure coordinates with DMS symbols only and no indication of hemisphere (-,+, ENSW) are
  detected and parsed
* added such tests to Truth data; see GeocoderEval project

## XCoord v1.2, 2012-10-31  HALLOWEEN release

Major improvements:

* Precision
* Test & Evaluation
* Rule order -- it is preserved and is currently MGRS UTM DMS DM DD
* Fixed many rules, added others

Details:

* Concepts added/adjusted (org.mitre.xcoord pkg):
    * GeocoordPrecision -- tracks number of digits mainly in DD, DM, DMS patterns, and the associated precision.
    * Hemisphere -- tracks hemisphere if it is +/-, Alpha or null.
    * PrecisionScales -- much improved assessment of inherent precision in any patter
      *Testing added
    * TestCase, TestScript added to capture input tests
    * TestUtility improved to report truth data if given, and the carry both truth and test results along with output
      for evaluation later.
* UTMParser hemisphere detection fixed.  "S" was allowed to be a Northern lat band, when it usually means South.
* DMSOrdinate now handles all logic for DD, DM, DMS parsing and calculation. Hemisphere parsing moved here (out of
  PatternManager)
* PatternManager --- ensure Rules order-of-appearance is preserved from configuration file
* GeocoordMatch -- formatting was a major concern, as with Java, printing a Float or Double allows near infinite decimal
  places (I mean a lot). Where you want 35.01, Java might print "35.00999989082911237", but you want "35.01" the string.
    * Use GeocoordMatch.formatLatitude() or formatLongitude() to get a printable string version of the calculated lat or
      lon


