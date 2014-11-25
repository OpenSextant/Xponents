#Xtax - Taxonomy, Phrase, and everything else tagger#

XTax is an application of the SolrTextTagger.  XTax is composed of the 
TaxonMatcher (org.opensextant.extractors.xtax), a solr "taxcat" catalog, 
and scripts to populate the catalog.

The XTax tagger is intended to for these situations:

 * you have phrases or terminology you want to tag in free text or data
 * you want to associate some amount of metadata with each tag, based on a reference catalog of metadata
 * the terms and metadata come from many different reference data sets, but you want a single tagger.
 * the terms and metadata are mixed hierarchical, taxonomic, and simple word lists

There is no taxcat catalog out of the box here -- See Xponents/Examples for ideas.
The taxcat catalog is created from *your data*.


Getting Started
===============
Components you will need:

 * SolrTextTagger and Xponents Basics and Extraction JAR
 * OpenSextant taxcat Solr core (Xponents/solr/taxcat)
   * A basic understanding of org.opensextant.extractors.xtax.TaxonMatcher API
 * Python libs: src/main/python/opensexant;  Open source: pysolr 2.x, chardet, Python 2.7+
   * or the equivalent libraries to create and post data records to Solr.

NOTATION: In these notes, ./ or .\ relative paths refer to the current code base.
Paths that refer installed or other contexts will be noted explicitly.

NOTE: Pysolr 3.2 has additional dependencies; use Pysolr 2.1 for now.

Recipe:
* Setup scripts, libs, and Solr
* Create your catalog ingest script
* Use your ingest script to add data to the taxcat catalog
* Test using TaxonMatcher or Xponents/Examples code
* Refine and repeat.

0. Build Xponents, 
    mvn install 
    Note the version being used, and verify that is in the Xponents/solr/build*xml scripts
```
    cd Xponents/solr
    ant -f build-taxcat.xml init
```

1. Install  your solr core, e.g.,  
```
    /my/xponents/solr/solr.xml, 
    /my/xponents/solr/taxcat/, 
```

    JVM var "solr.solr.home" will be set to /my/xponents/solr in this case.
    Also, you then have Xponents/solr/lib/  runtime dependencies

    copy Xponents/solr/lib  --> ${solr.solr.home}/lib

2. Set your scripting environment

    export PYTHONPATH=/my/app/lib
    # Copy relevant Python libs here; and ensure your scripting/shell sees such variables.

    Secondly, start the Solr server that includes the catalog 
    
```
    cd Xponents/solr

    // Edit the "build.properties" or copy "build.local.properties" 
    // Change the solr.home setting to match what you use above in step 1.

    // Using Winstone:  ant -f build-taxcat.xml start-solr
    //   I found problems making this work reliably.
    //
    // Using Jetty:
    ant  -f  build-taxcat.xml print-start-jetty
    //
    //   Run the answer printed to console:

        java  -Xmx2G -Djava.awt.headless=true -Dsolr.solr.home=/my/xponents/solr  \
              -Dlog4j.configuration=log4j.properties  \
              -Djava.util.logging.config.file=logging.properties  -jar build/jetty-runner-8.1.9.v20130131.jar \
              --lib ./build/containerLib  --port 7000 --path /solr build/solr-4.10.1.war
```

Keep this JVM running the Solr daemon, as you ingest your data.
Note, if you use other Solr servers, then you will need to deconflict the "solr.solr.home" variable
You can certainly host multiple solr cores there along side taxcat.


3. See Examples/XTax for guidance on creating a working catalog ingest.
 
The fundamentals of creating a catalog:
* manage a distinct solr record for each phrase variant
* taxon nodes, may have multiple variants
* use 'catalog' wisely -- as you ingest multiple data files, do they all fall under the same catalog?
* use 'valid'=true|false wisely -- mark valid=false phrases you wish to keep in the taxonomy, but do not want to use for tagging/matching


Python library, TaxCat, usage
```
    builder = TaxCatalogBuilder(server="http://localhost:7000/solr/taxcat")

    node = Taxon()
    node.name =  'tropical.Pineapple'
    node.phrase =  'pineapple'
    node.id = 1
    builder.add( 'citrus_fruit',  node )

    node = Taxon()
    node.name =  'tropical.Pineapple'
    node.phrase =  'la piña'
    node.id = 2

    # Customize taxon with additional metadata:
    node.tags = [ 'lang=spanish', 'usage=latin american' ]

    builder.add( 'citrus_fruit',  node )

    ...
    build.save(flush=True)

    # optimize only if you have many thousands of records, or if solr core appears not optimized.:
    build.optimize()
```


CAVEATS:
* Solr TextTagger uses strictly Integer row ID; schema.xml 'id' field is an int type.
* Hence, you need to manage your own mapping of catalog/taxon "ID"-to-solr ID yourself.
You likely want one taxcat for all of your data;  makes life easier.  But, as catalogs can be
ingested independent of each other you need to manage the mapping of catalog entries to solr row IDs.  For example, 
if catalog A has 100 rows and starts at row 1, and catalog B has 50 rows, then B must start at row 101 and increase
from there.   As you add rows to solr,  map each catalog to a starting ID and allow each block 
of row ID space to accomodate the data for each catalog. More Examples:

```
 my_catalogs = 
 {
   'A' : 0,       # If A has 100 entries, then it has growing room to 999, inclusive.
   'B' : 1000,    # has less than a thousand entries, 1001-1999, inclusive.
   'C' : 2000,    # has about  a thousand entries?
   'D' : 100000,  # has ~ million rows or less.  Range 1,000,001 to 2 million?
 }
```

JRC, 2014 has 600K rows;  in 2012 it had 500K.  So growth is expected.
Choose a catalog/id scheme that is larger than the # of rows of data you have.  
Have 100K rows of data, use 1 million, 2 million, etc as starting IDs.
Fit your smallest taxonomies in starting at 0.

All of this because row ID is Integer.


4. Testing. 

## Schema ##

"taxcat" schema is quite simple, intentionally.
Your catalog in solr will help you coalesce many differnt taxonomies into one searchable, online catalog.
Not all entries need to be used for tagging -- that is if you think a phrase is particularly erroneous or will
produce noise, then set valid=false.  It can remain in your catalog, though.

    catalog  -- a catalog ID that you identifies the source of reference data.
    taxnode  -- a taxon ID, that helps you associate N phrases with a given taxon
    phrase   -- a textual phrase in the reference data
    id       -- catalog row ID
    valid    -- true = if phrase entry should be used for tagging.
    tags -- optional.  Any amount of metadata you wish to carry forward with your tagging. This is a string, no format.

Example:

    catalog = "citrus_fruit",
    taxnode = "tropical.Pineapple"
    phrase =  "pineapple"
    id  =  46    # (solr row ID you define and manage. Must be Integer. )

Use of tags and valid=T/F are optimizations.  They are not required to make the XTax tagger work.

## Tagging ##

    TaxMatcher.tagText( "Marc bought eight pineapples given they were on sale.",  "test" )

Should find TaxonMatch, for "pineapples", and associate that as taxon={ "tropical.Pineapple", catalog="citrus_fruit" , attrs=....}

