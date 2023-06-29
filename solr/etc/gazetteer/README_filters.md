# Exclusions of Placenames Homonyms
There are enough words in common language that coincide with names of places that
we have to make some practical choices about limiting the scope of phrase tagging.
The list of "non-placenames.csv" captures those common dictionary and other terms
that appear often and in almost all cases seen empirically, those terms are not 
referring to a location of any sort in context.

This list is used to mark gazetteer entries as search_only.
There is still opportunity to post-filter phrases that are found in the tagging phase.
Just as there is the option to re-tag a document ignoring the "search_only" criteria.

Most of these non-place names are marked with name_bias = 0 which does not distinguish
them from other valid place names with the name_bias = 0.

# Linguistic Data Sets By Source

## Middle Eastern titles were derived from various sources including:

 http://heraldry.sca.org/laurel/names/arabic-naming2.htm

## WordNet stop words were derived from Ted Pedersen,

   http://www.d.umn.edu/~tpederse/Group01/WordNet/words.txt

## US Census 2020 and 2010
US Census 2010 (http://census.gov) supplied the surnames data.
http://www.census.gov/topics/population/genealogy/data/1990_census/1990_census_namefiles.html

First names are located at:
  http://www2.census.gov/topics/genealogy/1990surnames/dist.male.first
  http://www2.census.gov/topics/genealogy/1990surnames/dist.female.first

Build script (build.sh) is default build for gazetteer.  Put US Census files above  in 
./conf/filters/census/ .  The Python script ./script/assemble_person_filter.py handles
these files to put together a final name list.

## Carrot2, Lucene and Snowball Stop Filters

Stop Filters:  ./etc/gazetteer/filters/lang/
* Some files for specific languages may be borrowed from Carrot2 if not already in the Lucene stop word set
*  Korean:  Carrot2/workspace/stopwords.ko     --> ./lang/carrot2-stopwords.ko
*  Chinese: Carrot2/workspace/stopwords.zh_cn  --> ./lang/carrot2-stopwords.zh
These are optional files, if you are not processing asian language data, this will not matter.
But if you are, then download Carrot2 and grab copies of these stop sets.

## WordStats

The wordstats data gathering leverages Google Books NGram Corpus, specifically 1-ngrams.
Citation:

```
@inproceedings{lin-etal-2012-syntactic,
    title = "Syntactic Annotations for the {G}oogle {B}ooks {NG}ram Corpus",
    author = "Lin, Yuri  and
      Michel, Jean-Baptiste  and
      Aiden Lieberman, Erez  and
      Orwant, Jon  and
      Brockman, Will  and
      Petrov, Slav",
    booktitle = "Proceedings of the {ACL} 2012 System Demonstrations",
    month = jul,
    year = "2012",
    address = "Jeju Island, Korea",
    publisher = "Association for Computational Linguistics",
    url = "https://aclanthology.org/P12-3029",
    pages = "169--174",
}
```


## Arabic Stopwords (Mohataher Github)

Source: https://github.com/mohataher/arabic-stop-words

```
The MIT License (MIT)

Copyright (c) 2016 Mohamed Taher Alrefaie

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
