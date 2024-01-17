Xponents Patterns
===================

Regular Expression (REGEX) patterns is a common solution to detect
relatively concrete sequences characters and symbols. REGEX, however,
is not a complete solution to help validate what is found: for example,
finding a 10-digit sequence of numbers does not alone imply you found a
phone number; Finding a tuple of digits separated by slashes is not alone
to imply a date format. Additional validation is important ... usually.

Xponents FlexPat is a general methodology for developing REGEX-based extractors.
Consider alternative solutions, such as [YARA](https://yara.readthedocs.io/en/stable/) --
very powerful, but much more intricate and tailored specifically toward malware/cyberforensics.
FlexPat provides a more general abstraction around REGEX, specifically:

- to improve the testability of patterns
- to iterate on variations,
- to streamline validation, and
- to externalize the patterns, concepts and test cases for all to see

FlexPat currently operates in Java or Python with the same patterns file syntax. On the last
point above, the intent of FlexPat was to get the patterns out of language specific source code
and into a readable form that all team members could comprehend and weigh in on test cases.

To date Xponents FlexPat extractors include **XCoord** for geo-coordinate patterns, **XTemporal** for date/time
patterns,
and **PoLi** for general tutorial/demonstration purposes using simple patterns like email and telephone numbers.  
They are described in more detail with the actual files and supporting material below.

XCoord
---------
`XCoord` is a geographic coordinate extractor and normalizer that finds latitude/longitude pairs or grids such
as `MGRS` or `UTM`. The patterns are either decimal degrees, minutes seconds, and/or fractional parts along
with hemisphere symbology. Patterns include these: [Coord Patterns, 2013-2017](./XCoord.html), as
implemented in this patterns
definition [geocoord_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/geocoord_patterns.cfg).
This was drafted and operationalized
in Java here and has not yet been ported to Python (In actuality the first extractor implementation before OpenSextant
was
in Python not using the FlexPat approach). Coordinate patterns include:

- DD (decimal degrees) ~ 39.0N, -117.2W
- DM, DMS (degrees/minutes/seconds) ~ N39º 0' x W117º 12'
- MGRS ~ Quad/Zone/Easting/Northing
- UTM ~  Zone/Band/Easthing/Northing

**FILES**:  **[Coord Patterns](./xcoord.html)**, Patterns Config: *
*[geocoord_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/geocoord_patterns.cfg)
**.

XTemporal
-----------
`XTemporal` a date/time extractor and normalizer that finds dates and date/time patterns, implemented
with this patterns
definition [datetime_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/datetime_patterns.cfg).
Patterns include

- MDY ~ Month/Day/Year, e..g, `Sept 22nd, 2017` or `09/22/2017`
- DMY, DMYT ~ Day/Month/Year/Time ~ `22 SEPT 2017 0700Z`
- YMD ~ Year/Month/Day ~ `2017-09-22`
- DTM ~ Date+Time ~ `2017-09-22T0700-0500`

**FILES**: Patterns Config: *
*[datetime_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/datetime_patterns.cfg)
**

PoLi
-----------
`PoLi` or patterns-of-life demonstration, which includes well-known patterns like telephone numbers, email address,
and money. Those patterns are contained
in [poli_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/poli_patterns.cfg).
As a demonstration of FlexPat,
this set of patterns was provided only to show the development process of additional patterns. It is here for
illustration. On other projects we have implemented such patterns in much more depth, albeit such things are
not always open sourced. These patterns include:

- Telephone patterns - with country code prefixes; validation includes confirming valid country + exchange pairings.
- Email and usernames - any user handle of the form `"user@domain"`
- URLs and IP Addresses - Internet locations are parsed for prototcol, domain and addresses are resolved to a city
  or ISP if possible.

**FILES**: Patterns Config: *
*[poli_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/poli_patterns.cfg)**

Developing with FlexPat
-----------------------
While **XCoord** and **XTemporal** above are complex regarding their parsing, they are relatively well-contained and
easy.
Patterns tackled using the more general solutions demonstrated in **PoLi** show that the REGEX detection is just the
first
part of the problem, and the user has to bring in that sense of validation.

That validation is implemented (in Python) by subclassing `opensextant.FlexPat.PatternMatch` and implementing a
`normalize()` function to validate the detected pattern and groups. We'll get into that more below with the
optional `CLASS` directive.

First here is the outline of the standard FlexPat patterns configuration file -- which should be language independent
(_yes, until you specify the optional `CLASS`, which is the name of your custom class which may vary depending on your
programming language_).

This FlexPat uses a "patterns configuration" file, which contains the clauses for `DEFINE`, `RULE`, `TEST`, and `CLASS`
-- the essential ingredients for a pattern extractor pipeline. Outlining these more:

- `DEFINE`: define discrete groups or "sub-patterns" that recur in your patterns
- `RULE`: an actual REGEX, defined with named groups only identified by your `DEFINEs` or any other valid REGEX syntax
- pattern family: a logical grouping of like patterns that may have subtle variations, e.g.,  
  `MDY` has about 6 total month-day-year patterns. But all such variants are easily referenced by naming the pattern
  family `MDY`.
- `TEST`:  is a single example of the pattern+variant to be detected, parsed and/or normalized by the `RULE`.
  By convention a `FAIL` comment trailing the test is used to denote a test case that should NOT be detected by
  the `RULE`. Alternatively, the pattern may detect a match, but through the `CLASS` `.normalize()` implementation the
  `RULE` will yield a pattern match that is _filtered out_. In conclusion, there are two chances to succeed here
  -- (a) `RULE` detect or not detect the match, and/or (b) `CLASS` normalization validates or invalidates the match.  
  This is the value of filling out as many `TEST` test cases as possible to touch on variants.
- `CLASS`: An optional custom class that subclasses `PatternMatch` to carry REGEX subgroups and allow for additional
  normalization, validation, etc.

The **PoLi** example is provided as a template for starting your own set of patterns:
[poli_patterns.cfg](https://github.com/OpenSextant/Xponents/blob/master/Core/src/main/resources/poli_patterns.cfg


Code References and Examples
=========

* Java:
    * [FlexPat overview in Java](https://opensextant.github.io/Xponents/doc/core-apidocs/org/opensextant/extractors/flexpat/package-summary.html).
* Python:
    * [FlexPat API](https://opensextant.github.io/Xponents/doc/pydoc/opensextant.FlexPat.html)
* Testing:
    * XCoord example: Invoked
      using `./script/xponents-demo.sh`, [TestXCoord.java](https://github.com/OpenSextant/Xponents/blob/master/Core/src/test/java/org/opensextant/extractors/test/TestXCoord.java)
    * [FlexPat example](https://github.com/OpenSextant/Xponents/blob/master/python/test/test_flexpat.py)

