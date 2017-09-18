#Xtax - Taxonomy, Phrase, and everything else tagger#

XTax is an application of the SolrTextTagger.  XTax is composed of the 
TaxonMatcher (org.opensextant.extractors.xtax), a solr "taxcat" catalog, 
and scripts to populate the catalog.

The XTax tagger is intended to for these situations:

* you have phrases or terminology (aka lexicon or taxonomy) you want to tag in free text or data
* you want to associate some amount of metadata with each tag, based on a reference catalog of metadata
* the terms and metadata come from many different reference data sets, but you want a single tagger.
* the terms and metadata are mixed hierarchical, taxonomic, and simple word lists

There is no taxcat catalog out of the box here -- See Xponents/Examples for ideas.
The taxcat catalog is created from *your data*.  JRCNames is used as an example data set.

Getting Started
===============
Components you will need:

* SolrTextTagger and Xponents Basics and Extraction JAR
    * A basic understanding of org.opensextant.extractors.xtax.TaxonMatcher API
* OpenSextant taxcat Solr core (Xponents/solr/)
* Python libs: src/main/python/, as well as  Open source: pysolr 3.2, chardet, requests 2.5, Python 2.7+
    * Available at: https://pypi.python.org/pypi/{chardet, pysolr, requests}
    * or the equivalent libraries to create and post data records to Solr.

NOTATION: In these notes, ./ or .\ relative paths refer to the current code base.
Paths that refer installed or other contexts will be noted explicitly.

Recipe:
* Setup scripts, libs, and Solr
* Create your catalog ingest script
* Use your ingest script to add data to the taxcat catalog
* Test using TaxonMatcher or Xponents/Examples code
* Refine and repeat.

Steps
==============

0. Build Xponents, 
    Note the version being used, and verify that is in the Xponents/solr/build-solr-support.xml 
    script where Xponents version is mentioned.
```

    cd Xponents
    mvn install 

    pushd Extraction/src/main/python
    python ./setup.py bdist_wheel --universal
    popd
    # Install built lib with dependencies to ./python
    pip install --target /path/to/piplib Extraction/src/main/python/dist/opensextant-VERSION.whl
    
    export PYTHONPATH=/path/to/piplib
    
    cp build.template build.properties 
    #  By default, ./solr/solr4 is where the actual Solr Home resides.

    cd Xponents/solr
    ant init


```

1. Install your solr core 

This is optional.  If you wish to keep your Solr indices separate from 
code and development, by all means do so.
```

    cp -r ./solr  /path/to/xponents-solr
    # This contains all the scripts, Jetty server, and the Solr Home 'solr4'
   
```

Java considerations:
```

	JVM argument -Dopensexant.solr=/path/to/xponents-solr/solr4
    JVM memory  -Xmx2g -Xms2g is a reasonable default for Gazetteer usage. Taxcat alone could use just 500m
    CLASSPATH  only the libraries in Xponents/lib/* are required. Again, for 
       XTax applications alone, you could use fewer JARs, based on Extraction/pom.xml
```

2. Start the Solr Server

In a separate terminal launch the Solr server that provisions the taxcat for your
python or other ingest scripting.

```

    ./myjetty.sh start &
     
```

Keep this JVM running the Solr daemon, as you ingest your data.
Note, if you use other Solr servers, then you will need to deconflict the "solr.solr.home" variable
You can certainly host multiple solr cores there along side taxcat.

In Production, this is intended to be used as an "Embedded Solr" resource, rather than as a tagger 
provisioned from HTTP/REST.  REST-ful mode is doable, but not very efficient for distributed processing.

Important -- IF you wish users to use your Solr TaxCat and Gazetteer indices, set them up as separate
copies apart from the Xponents Solr home set up for processing pipelines.


3. See Examples/XTax for guidance on creating a working catalog ingest.
 
The fundamentals of creating a catalog:
* manage a distinct solr record for each phrase variant
* taxon nodes, may have multiple variants
* use 'catalog' wisely -- as you ingest multiple data files, do they all fall under the same catalog?
* use 'valid'=true|false in a practical fashion -- mark valid=false phrases you 
wish to keep in the taxonomy, but do not want to use for tagging/matching. Often tagging ambiguous phrases
leads to false positives.  

False positives:  When to mark records as valid=false.
Consider the taxon
```

    name = 'rank.Commander'
    phrase = 'CDR'
   
```
Searching for CDR in text may reveal valid matches for a commander or a 
critical design review (CDR), or a call data record, or court data recorder, etc., for examples.
So when to mark such potentially ambiguous phrases as valid=false, is purely 
heuristics and empirical.

TacCat Schema
--------------

"taxcat" schema is quite simple, intentionally.
Your catalog in solr will help you coalesce many different taxonomies into one searchable, online catalog.
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

Use of tags and valid=True/False are optimizations. 
They are not required to make the XTax tagger work: only items marked valid=False will not be tagged.
Such items are maintained in the catalog as users may want to see all the contents of a lexicon,
even if you decide not to tag certain things in production.


TaxCat.py Library usage
--------------

First organize the required Python libs for this application and ensure your 
scripting/shell/IDE sees such variables.
```

    export PYTHONPATH=piplib
    # See note above regarding setting up python libraries.
    
```

Your main program might look like the following:

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

The "JRCnames" example (./examples/jrcnames.py) for 2014 has 600K rows;  in 2012 it had 500K.  
So growth is expected. Choose a catalog/id scheme that is larger than the # of rows of data you have.  
Have 100K rows of data, use 1 million, 2 million, etc as starting IDs.
Fit your smallest taxonomies in starting at 0.

All of this because row ID is Integer.

Invoke the example on JRCNames 'entities.txt' file as:
```
 
   python ./XTax/examples/jrcnames.py --starting-id 1000000 \
      --taxonomy ./entities.txt --solr http://localhost:7000/solr/taxcat

   # I changed the starting id just to show it as a argument you control.
```


Testing
=================

Tagging using Java

```
  /* Setup
   */
  // solr.solr.home or opensextant.solr are required JVM args for tagging
  TaxonMatcher tagger = new TaxonMatcher(); 
  tagger.configure()
  tagger.addCatalogFilter("citrus_fruit");
  
  /*
   * Extract
   */ 
  taxons = tagger.extract( "Marc bought eight pineapples given they were on sale.",  "test" )
```

Should find TaxonMatch, for "pineapples", and associate that as ```taxon={ "tropical.Pineapple", catalog="citrus_fruit" , attrs=....}```

```
  /* 
   * Review matches 
   */
  for (TextMatch tm : taxons) {
    TaxonMatch match = (TaxonMatch) tm;
    // The TaxonMatch holds the core match metadata: id, text, start/end offset
    //         
    // you may have various 'filters' to manage your false positives.        
    if (filterOut(match)) {  continue; }

      for (Taxon n : match.getTaxons()) {
        // Each taxon declares the taxonomy metadata for each matching node
      }
   } 
      
   /* Finally, all good Extractors run the .cleanup() method. 
    * traditionally, the cleanup() for SolrTextTaggers is required to statically 
    * shutdown Solr server threads, regardless if the server is http or embedded.
    * the SolrJ client needs to halt.
    */
   tagger.cleanup()   
```
   
