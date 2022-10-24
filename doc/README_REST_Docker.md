Xponents REST API
======================

Welcome to Xponents the geo/temporal tagging suite. 

* **[Xponents Home](https://opensextant.github.io/Xponents)** describes in more detail all information extraction capabillities.
* **[API Reference](https://opensextant.github.io/Xponents/doc/README_Xlayer_REST.html)**


Docker Image Variants: Offline vs. Normal
----------------------
* version `xponents-3.5` is a regular Xponents binary distribution with all the Gazetteer data and REST capabilities
* version `xponents-offline-3.5` is the same as above, but adds the full Apache Maven stack of libraries, plugins, and 
  Xponents dependencies with the Maven install.  Great for offline compilation of your own Java code.  Any new Java 
  dependencies must be provided by you to use development `offline` -- that is there is a `maven-repo` in the image which 
  you need to update `online` before you can use offline.  `¿Capisce?`
  
Services Included
------------------------
Xponents REST API is the default ENTRYPOINT.  That extracts geotags, patterns, etc.  Additionally you can run the 
underlying Solr Gazetteer for straight lookups of place metadata. This gazetteer service is not turned on by default. 
See how to invoke these options below.

Docker Get & Run
---------
In this example we pull down the Xponents docker image, re-tag it for simplicity sake. The tag used is `xponents:v3`.  
The running container is `xponents-api`.  If you need to query the gazetteer reference data regularly or casually you 
should consider launching separate docker containers, i.e., `xponents-api` and `xponents-gaz`.

```
docker pull mubaldino/opensextant:xponents-3.5

# Optionally tag a short name "xponents:v3" for the image which is used in the remainder of the examples.
#-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
docker tag mubaldino/opensextant:xponents-3.5  xponents:v3

# Run just Xponents REST:
#-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
docker run -p 8787:8787 -e XLAYER_PORT=8787 --name xponents-api --rm  --detach xponents:v3

OR....
# Run both Xponents REST and Xponents Gazetteer
#-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
docker run -p 7000:7000 -p 8787:8787 -e XLAYER_PORT=8787 \
    --name xponents-api --rm --detach xponents-v3

docker exec -it xponents-api /bin/bash -c \
    "cd ./xponents-solr && ./solr7-dist/bin/solr start -p 7000 -s ./solr7 -m 3g -q -force"
```

Use API
-----------
See notes in API. Quick test/healthcheck
```
# Its alive!
curl  http://localhost:8787/xlayer/rest/control/ping

# Process trivially.  In these few examples note the proper geolocation of `Boston, MA` vs. `Boston, OH`
#
* curl  -X POST http://localhost:8787/xlayer/rest/process --data '{"text":"Dude, you from Boston, too?"}' 
* curl  -X POST http://localhost:8787/xlayer/rest/process --data '{"text":"Dude, you from Boston, MA, too?"}' 
* curl  -X POST http://localhost:8787/xlayer/rest/process --data '{"text":"Dude, you from Boston, OH, too?"}' 

# Okay, making it real -- do this programmatically using Java or Python.  Details are here:
https://opensextant.github.io/Xponents/; see Xlayer link above.  Java and Python clients are documented there. 
We'll continue to make this simpler.  File a GitHub issue on the Xponents project to provide feedback.

```

Optionally pipe to "json_pp" to see output for this quick test.  Or better yet visit the API notes above, Java and Python 
Lib/client provided in Xponents source.

```
{
   "response" : {
      "numfound" : 1,
      "status" : "ok"
   },
   "annotations" : [
      {
         "offset" : 15,
         "province-name" : "Massachusetts",
         "cc" : "US",
         "geohash" : "drt2yzpgerej",
         "adm1" : "25",
         "confidence" : 30,
         "method" : "PlaceGeocoder v3.0",
         "type" : "place",
         "feat_class" : "P",
         "lat" : 42.35843,
         "feat_code" : "PPL",
         "length" : 6,
         "lon" : -71.05977,
         "prec" : 5000,
         "name" : "Boston",
         "matchtext" : "Boston",
         "filtered-out" : false
      }
   ]
}

```

Use Solr Gazetteer
================

When the Gazetteer is running, the default URL is this http://localhost:7000/solr/.  Then please consult the Apache Solr Admin manual on detailed use.
Gazetteer schema details will be mantained back at the Xponents Github site.  Currently not documented as a public API.



CHANGES
-------------
**Xponents v3.5.7:**

- Postal geocoder noise reduced

**Xponents v3.5.5:**

- Log4J remediation; Log4J 2.17+ is in use, thank you.
- API `postal`  feature now tags any valid, qualified postal sequence, e.g., "City, Province, PostalCode", in text.  
  `/xlayer/rest/process` operation takes `features="geo,postal,dates"` for example.
- API `lang` argument (Documentation coming) can be `ar` (for Arabic), `cjk` (for Chinese/Japanese/Korean), or a 
  ISO-639 language code to hone both geotagging and tag filtering.
- Expanded and updated Gazetteer data from Geonames.org, Natural Earth and others.  You may see other type of noise in output
- Memory leak fixed -- Still 3 GB is a good heap setting for the `JAVA_XMX` variable (or just use the current default)
