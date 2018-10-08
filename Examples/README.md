
OpenSextant Xponents: Example Processing Apps
=======================

Xponents demonstrations reside here: `Examples/src/main/java/org/opensextant/examples/` or in
`./src/test/java/`.  The main Java source is intended to be the official API, free of runtime decisions.
This set of demonstrations demonstrates how to use various APIs. 

- XponentsGazetteerQuery:  Explore API calls to navigate gazetteer data and utilities for data lookups. See ./doc/
- BasicGeoTemporalProcessing:  Process raw unstructured data/documents resulting in a GIS file of your choice.
- SocialDataApp: An illustration of how to use XCoord to parse coordinates and other metadata from tweets resulting in a GIS file of your choice.
- TaxonomicTagger: Demonstration of using XTax; Follow Extraction/XTax README on setting up examples or your own instance.  

The complete set of command line demos includes:

```
   ======================
      gazetteer  -- Gazetteer queries and experiments.
      xcoord     -- XCoord coordinate extraction tests
      geocoder   -- Geotagging and Geocoding
      geotemp    -- Geotagging and temporal extraction
      poli       -- Pattern-based extraction
      xtemp      -- Temporal extraction tests
      xtext      -- XText demonstration - crawl and convert (files to text) local folders
      web-crawl  -- Content crawl and convert. Advanced XText demo, e.g., feed convert and extraction pipeline.
      social-geo -- Social geo-inferencing on tweets (for now).
      ======================= 
      
      Usage:
        ./script/xponents-demo.sh   help                -- this menu above
        ./script/xponents-demo.sh   <APP> help          -- help for the app
        ./script/xponents-demo.sh   <APP>  <ARGS...>    -- execute APP
            
```

Additionally, these other scripts or mechanisms exist:

* Python `gazetteer.py` -- an illustration of how to interact with the Xponents Solr Gazetteer as a REST API
* XText `convert.sh (.bat)` -- is a full version of XText in its own bash or BAT script.
* XLayer -- a RESTful extraction API, demonstrating mainly geocoder and pattern extraction.

