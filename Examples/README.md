
OpenSextant Xponents: Example Processing Apps
=======================

The complete set of command line demos includes:

```
   ======================
      gazetteer  -- Gazetteer queries and experiments.
      xcoord     -- XCoord coordinate extraction tests
      geocoder   -- Geotagging and Geocoding
      geotemp    -- Geotagging and temporal extraction, with XText conversion of files.
      xtax       -- Keyword tagger, e.g., tagging for taxonomic word lists or other catalogs
      poli       -- Pattern-based extraction
      xtemp      -- Temporal extraction tests
      xtext      -- XText demonstration - crawl and convert (files to text) local folders
      web-crawl  -- Content crawl and convert. Advanced XText demo, e.g., feed convert and extraction pipeline.
      social-geo -- Social geo-inferencing on tweets (for now. Other data types later).
      ======================= 
      
      Usage:
        ./script/xponents-demo.sh   help                -- this menu above
        ./script/xponents-demo.sh   <APP> help          -- help for the app
        ./script/xponents-demo.sh   <APP>  <ARGS...>    -- execute APP
        
      .\script\xponents-demo.bat  exists as well for Windows/DOS.
      
      The underlying Xponents.groovy script is Groovy 2.4. Enjoy.
            
```

For example try the XCoord coordinate extraction on provided test data:

```
  ./script/xponents-demo.sh   xcoord -t ./test/Coord_Patterns_Truth_Text.txt
```

Xponents demonstrations reside here: `Examples/src/main/java/org/opensextant/examples/` or in
`./src/test/java/`.  The main Java source is intended to be the official API, free of runtime decisions.
This set of demonstrations demonstrates how to use various APIs.  Significant Java examples include:

- `gazeteer` = [XponentsGazetteerQuery](./src/main/java/org/opensextant/examples/XponentsGazetteerQuery.java) class:  Explore API calls to navigate gazetteer data and utilities for data lookups. See ./doc/
- `geotemp` = [BasicGeoTemporalProcessing](./src/main/java/org/opensextant/examples/) class:  Process raw unstructured data/documents resulting in a GIS file of your choice.
- `social-geo` = [SocialDataApp](../src/test/java/SocialDataApp.java) class: An illustration of how to use XCoord to parse coordinates and other metadata from tweets resulting in a GIS file of your choice.
- `xtax` = [TaxonomicTagger](./src/main/java/org/opensextant/examples/) class: Demonstration of using XTax; Follow Extraction/XTax README on setting up examples or your own instance.  

Other Items
================
Additionally, these other scripts or mechanisms exist:

* **Gazetteer interaction with Python** `gazetteer.py` -- an illustration of how to interact with the Xponents Solr Gazetteer as a REST API
* **Crawler/Converter**: XText `convert.sh (.bat)` -- is a full version of XText in its own bash or BAT script.
* **RESTify it all**: XLayer -- a RESTful extraction API, demonstrating mainly geocoder and pattern extraction.

