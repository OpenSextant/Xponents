Geocoder's Handbook for Xponents
================================
**Author:** Marc Ubaldino

**Copyright** OpenSextant.org, 2017

**Updated** 2021

**Video**: **["Discoverying World Geography in Your Data"](https://www.youtube.com/watch?v=44v2WljG1R0)**,  presented at Lucene/Solr Revolution 2017 in Las Vegas 14 September, 2017. In video, at minute 29:50. This is a 12 minute talk

Contents:
- Tagging Conventions: what is extracted and considered in the tagger pipeline.
- Geocoding Rules: how discrete pieces of evidence are reasoned to arrive a location decision.
- **Examples.  Parsing and reasoning on _"San Antonio, TX"_

Welcome to Xponents.  We realize geocoding text or data can be tedious and mind numbing.
Hopefully this handbook will help you walk through the techniques defined in Xponents
and the rest of OpenSextant in a way that makes it obvious which rules will impact 
geocoding of your data.  One important thing to note is that in all this information/entity 
extraction performed here you will not see that much discussion of traditional 
natural language processing (NLP), e.g., parts of speech, co-references, sentence boundaries, etc.
Much of the language-specific processing is delegated to Solr and Lucene, which handle this 
reasonalbly well. Xponents APIs are then able to focus more on the critical extraction and encoding 
challenges.  In this regard when you have to do less tuning up front per-language, you can field
a decent geotagging capability faster and discover what you do not know.  You can refine language-specific
performance later.   In conclusion -- NLP mechanics and theory is very important, however as a developer
or integrator you need not be so concerned with it intially. 

There main topics to cover (Figure 1):

* input -- you should understand your input text.
* tagging -- first finding geographic mentions and supporting infomation in data
* coding -- parsing and justifying the location of a mention as precise as is appropriate.

![General topics in our geotagging workflow](./geocoding-workflow.png)

In either topic we will encounter the concept of **filters** that either negate or promote a finding.
And lastly **evidence** is any metadata that can be attached to a geotag or geocode to further back 
the choice of a location.   ```Look for pointers on Xponents solutions, aka Java classes, in each topic```

The primary implementation for this handbook is the Java package ```org.opensextant.extractors.geo```. 
The design of this package provides some good terminology to understand the methodolgy here:

* **Text Match:**  any tag found by Xponents Extractor or Patterns implementations
* **Place Candidate:** a tag representing a name, abbreviation, or code that matches something in the gazetteer
* **Place:** an named location, i.e., a place candidate has multiple places where the name appears.  
E.g., "Lebanon" is a country and name of towns around the world.  This is a straight representation of a 
gazetteer entry.
* **Scored Place:** a sub-class of place attached to a candidate. A scored place carries additional 
metadata such as a score, related to the geocoding workflow.

Tagging Conventions
===================
* **T1. Language of text, etc.** You should know the language and original character set of your text. 
Language ID helps direct tagging and filtering parts of speech found in texts, using such things as
word stemming, tokenization, treatment of diacritics and stopword filtering.  Xponents provides ```TextInput```
class to carry basic metadata like lang ID and a Unicode text buffer, however language detection must be done 
externally as this differs based on the type and length of text.  
* **T2. Coordinates or other hard location references.** Coordinates are typically used in geological, maritime, 
military, transportation, humanitarian and other industries. They offer excellent context to ground other
names or allusions to the geography at hand.  In Xponents, coordinate can be queried against the ```SolrGazetteer.placesAt()```
method to reveal nearest cities, the province and country containing the location, or the fact the coordinate is
not near anything (e.g., over water). **Xponents Patterns** project has ```XCoord``` extractor which detects and geocodes
the coordinate patterns listed here in the [XCoord Patterns reference manual](xcoord.html) reference manual.
* **T3. Names of populated places and administrative &amp; geopolitical boundaries.**
Any known geographic name can be tagged in any language (Xponents does not currently support the use of 
statistical models that can tag unknown names based on a learned model). **[SolrTextTagger](https://github.com/OpenSextant/SolrTextTagger/)**
is used along with Solr 4.x or 6.x to tag all known names, where the known geo-names are curated by the
 ```GazetteerMatcher``` class exposes the SolrTextTagger capability indirectly through calling a Solr request. 
The **[Gazetteer](https://github.com/OpenSextant/Gazetteer/)** is the primary source of consolidated gazetteer
data.
* **T4. Abbreviations, Aliases and Codes** are often overlooked in geotagging, but are incredibly helpful
in things such as detecting and qualifying business addresses, by line locations in news articles, and informal speak
such as the use of nicknames for cities. The solutions discussed above in convention **T3** are the same for tagging, 
however this class of tag is filtered and used differently than proper names. 
* **T5. Geopolitical and geographic context** such as nationalities.  Xponents maintains a list of nationalities
that infer a country code. There are certainly singular ethnicities within multiple countries that are not 
represented. A future enhancement would be to provide the mapping for such multiplicity.
* **T6. Non-Place entities** such as organizations and well-known persons. Identifying things that are 
likely not places helps filter out noise or amplify context.  For example, ```Detroit City Council``` is an 
organization name, that contains a city name. Whereas the ```Smithfield Group``` may be an institution not actually located
in a place called ```Smithfield```.  Either way it is important to be able to detect all such cases to work
with negation or amplification all at once. Xponents **Extraction** project provides ```TaxonMatcher```, aka XTax, which 
provisions a lexicon of such "well-known" non-places of your choosing or the default set used by Xponents.
* **T7. Time Zones** can be used to infer a general source of information and provide additional context. 
Xponents **Basiscs** has a ```Country``` class that makes use of a timezone table (source: Geonames.org) which 
helps infer one or more country codes for a given timezone.  Xponents **Patterns** ```XTemporal``` extractor can be used
to detect and code dates and times, if a date/time/timezone is not already provided.  The important part 
here is to recognize the **innate time and timezone in the original data**, not the UTC time. 

Geo-inferencing and Geocoding Rules 
===================
Rules are organized and fired by some main program, the reference implementation here is ```PlaceGeocoder``` in 
Xponents **Extraction** project.  Some rules are fired generically in order, while others are fired separately.
All rules (of type ```GeocodeRule```) are evaluated (```evaluate()```) after the tagging has occurred. Tagging
yields a list of ```PlaceCandidates``` which may have been filtered by the tagging phase.  Each candidate may
also have heuristics about the text, including if the text is all upper case, all lower case, pure ASCII vs. 
diacritic or non-ASCII.   As rules fire they contribute a rule label, a score increment and/or additional evidence
to each candidate.  

A final rule, a **Location Chooser**, assesses given evidence, context, rules and scores for each candidate. 
Ultimately the best score wins and a confidence (100 point scale) is associated with the choice to make it 
easier to compare geotagging and geocoding confidence across documents and data sets.

* **R1. Country Mentions (CountryRule).** Detect an mention of the name or reference of a country. Explicit 
mentions by name or abbreviation emit a country code (rule+score increment).  Nationalities add to context, 
but do not actually emit a country code.  Only select country codes seen in text might act as an explicit mention, 
e.g., "US", "JPN" or "CHN" (all caps) emit country mentions, however "COL" (Colombia) does not as it can easily
be confused with other abbreviations such as "colonel".
* **R2. Name, CODE Pattern (NameCodeRule).** Detect two candiates following each other where one is any 
place name followed by a CODE or name of an adminstrative boundary.  Example ```San Fran, CA``` 
* **R3. Names in general (NameRule).**  Use a limited set of qualifying phrases to increment the score of 
matching candidates. Specifically (in English currently) ```town of```, ```city of```, etc. preceeding the name
of populated places (feature types ```P/PPL```) score those candidates higher.  As well, ```province``` 
preceeding or following a name scores higher candidates that are Level-1 administrative boundaries. 
Could be enhanced by having table-driven rules per language.
* **R4. Coordinate Reverse-geocoding (CoordinateAssociationRule).** IFF coordinates are detected (or provided
some other means) those locations infer a particular containing province and country for each coordinate. Relevant
provinces provide context that scores higher other place candidates in those provinces.  ```SolrGazetteer.placesAt```
provides a simple recursive reverse lookup of location to containing boundaries or nearby places.  First 10 KM, then
30 KM radii are tried looking for closest match.
* **R5. Province Mentions (ProvinceAssociationRule).** Using any explicit administrative boundaries found,
loop over all candidates and increment score of any other candidates found to be contained geographically within 
those boundaries. The solution uses a the hierarchy of geographic names coding, e.g., ```COUNTRY.ADM1.ADM2```
is known as a hiearchical tree that represents containment of boundaries in a lexical string.  So, ``USA.06.4221``
is county (district) # 4221 in "California"(06) "USA".  A city in that district will have the same hiearchical coding, 
whereas a city of the same name in a different country would not. 
* **R6. Person Names (PersonNameFilter).**  Accounts for any qualified person (and organization) name patterns to negate (```filter out```)
tagged place names.  Specific rules such as R2. Name, CODE run ahead of this rule to ensure situations such
as "Eugene, OR" are not filtered out as person name (i.e., "Eugene") becuase it is a well-qualified place mention.
* **R7. Nonsense (NonsenseFilter).** Detect and filter out trivial matches that fall into a few categories: 
short phrases with diacritics (e.g., matching country codes or other abbreviations), numeric patterns in names, 
out of place punctuation, repeated short phrases ("boo boo"), obscure mixed case or lower case.  The effect
is to mark matching candidates as ```filtered out```
* **R8. Major Place Mentions (MajorPlaceRule).** Use population stats (source: geonames.org cities15000) 
to amplify score of populated place or administrative boundary matches.
* **R9. Place mentioned in Organization (ContextualOrganizationRule).**  Seek to reverse a ruling
that a candiate was filtered out because it was an organization name.  If a relevant province is inferred
so far, and the candidate has a location in that province, then score that location higher.
* **R10. Location Choosing (LocationChooserRule).**
  * Assess non-filtered out candidates
  * Choose the highest scoring location for each candidate
  * Assign confidence to each choice based on document scoped evidence as well as those rules attached to the particular mention

Examples
=========

TestPlaceGeocoder is a test routine that helps execute discrete test and evaluation activies, for example:
* process a single file,
* process block of text or 
* run through the `/data/placename-tests.txt`. (in `src/test/resources`). 

All of these are means of feeding the geotagger to find out -- in detail --
how decisions are made, what is missed and what false positives are emitted.  There may be serious, systematic errors in rules or just missing reference gazetteer data.   All of these scenarios need to be 
assessed with library and reference data changes.

Let's look at the style of output in debug mode.  `logback.xml` 
controls logging: By default geocoding and geotagging classes are in 
`DEBUG` mode.  

Okay.  Look at the text `San Antonio, TX`. 

Consider variants that may change your mindset around decisions:

* `S. Antonio, TX`
* `SAN ANTONIO TX`
* `SAN ANTONIO TEXAS`
* `San Antonio, Texas, Mexico`
* `San Antonio near the Texas-Mexico border`
* `San \n    Antonio, Austin and other cities in southern Texas`
* `san antonio, texas`
* `SanAnt`
* etc. 

Return back to the variant mentioned above: `San Antonio, TX`. 
Xponents teases this apart with an evolving set of rules. The important notes include:

* **Name-Code rule:** `NAME, ADMIN-CODE` where the administrative boundary code represents the place that contains the named place, `NAME`.
* **Major Place rule:** Cities named `San Antonio` number in the range of 200, but the city in Texas, USA has a significant population
* **Feature rule:** Populated places (`P/PPL` coding for example) and administrative boundaries (`A/ADM1` for `TX` or `Texas, USA`). 
* **Collocation rules:** while not present in this example, the mention of other related or nearby places such as `Austin` helps improve the confidence around the connection between cities or sites located in the same district or province or other spatial proximity.
* **Text rules:** Case, punctuation, whitespace (or lack of), abbreviations
and other situations are all considered to either filter in/out mentions or to change the confidence in our location decision.
* **Co-reference or entity collisions**: Names, names, names.  Where person or organization names appear *in* the location name, we can ignore them safely, e.g., `Antonio`. But if the location name appears as a subset of the tag, then we should ignore the location tag, e.g. `San Antonio Pharma Group` is a likely company possibly with no specific geographic reference.

The list goes on.  The list is always growing as more opportunities are 
observed.  Lots of those opportunities come with additional meta-data or
reference data.  The raw output of the `TestPlaceGeocoder` evaluation is below:

```

MENTIONS ALL == 3
Name:Antonio, Type:taxon
	(filtered out: Antonio)
Name:San Antonio, TX, Type:generic
Rules = [
  Contains.PersonName, 
  AdminCode, 
  DefaultScore, 
  MajorPlace.Population, 
  CollocatedNames.boundary, 
  Feature, 
  Location.InAdmin]
	geocoded @ San Antonio (48, US, PPL), score=26.31 with conf=81, at [29.4241,-98.4936]
	geocoded @ San Antonio (24, MX, PPL), score=15.54 second place
Name:TX, Type:generic
Filtered Out.  Rules = [DefaultScore]
MENTIONS DISTINCT PLACES == 1
[San Antonio, TX]
MENTIONS COUNTRIES == 0
[]
MENTIONS COORDINATES == 0
[]

```

  
