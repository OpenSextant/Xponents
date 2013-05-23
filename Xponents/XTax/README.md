#Xtax - Taxonomy, Phrase, and everything else tagger#

DRAFT
this is an application of the SolrTextTagger.  
The intent is to be able to re-use the technique behind the Solr PlaceMatcher in OpenSextant
to be able to tag virtually anything else other than place names for these situations:

 * you have phrases or terminology you want to tag in free text or data
 * you want to associate some amount of metadata with each tag, based on a reference catalog of metadata
 * the terms and metadata come from many different reference data sets, but you want a single tagger.
 * the terms and metadata are mixed hierarchical, taxonomic, and simple word lists

If all you need is a general purpose tagging mechism that matches phrases to a taxon, then possibly look at the
emerging general purpose tagger in opensextant.toolbox GATE processing package.


This is not a buildable component.  Currently it is a recipe for creating a taxonomic tagger using:

  SolrTextTagger
  OpenSextant Gazetteer "solr home"
  Python "pysolr"
  OpenSextant opensextant.extraction.TaxonMatcher API

1. create your solr core; extending gazetteer multicore in solr.xml
   Default name for this core will be "taxcat", short for Taxonomy Catalog

2. write your reference data ingester in python

3. Test your index by extending/wrapping/calling TaxonMatcher.tagText() with some 
test data.  The test data should contain mentions of phrases or terms in your catalog


## Schema ##

"taxcat" schema is quite simple, intentionally. 
Your catalog in solr will help you coalesce many differnt taxonomies into one searchable, online catalog.
Not all entries need to be used for tagging -- that is if you think a phrase is particularly erroneous or will
produce noise, then set valid=false.  It can remain in your catalog, though.

   catalog  -- a catalog ID that you identifies the source of reference data.
   taxnode  -- a taxon ID, that helps you associate N phrases with a given taxon
   phrase   -- a textual phrase in the reference data
   id       -- catalog row ID
   valid    -- if phrase entry should really be used for tagging. 
   attrs    -- optional.  Any amount of metadata you wish to carry forward with your tagging. This is a string, no format.



  catalog = "citrus_fruit",
  taxnode = "tropical.Pineapple"
  phrase = { "pineapple", 
             "la piña", 
             ...}   // Each phrase would be its own row in Solr; but they all carry the same taxonid and cat id

  id  = (you define;  using taxcat.py API, you name a starting offset for the catalog

Use of attrs and valid=T/F are optimizations. 


## Tagging ##

   TaxMatcher.tagText( "Marc bought eight pineapples given they were on sale.",  "test" )

   Should find TaxonMatch, for "pineapples", and associate that as taxon={ "tropical.Pineapple", catalog="citrus_fruit" , attrs=....}




## Running ## 


  Do whatever environment setup you need to do to set ANT_HOME, JAVA_HOME, SOLR_HOME
  Also:

  PYTHONPATH=./lib:./src/main/python

  # Add all that here:
  . setup.env

  # solr_home is set locally here in build.properties
  ant -f  ./build.xml  create-solr-home
  #
  
  #
  run your python app that leverages the taxcat.py API
  
  ant -f  build.xml  build-fst

