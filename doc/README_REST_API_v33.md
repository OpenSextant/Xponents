Xponents REST API
======================

Welcome to Xponents the geo/temporal tagging suite. 
See the **[Xponents home page](../README.md)** for a better sense of the feature 
set of information extraction capabillities. 


API:
-----
https://opensextant.github.io/Xponents/doc/README_Xlayer_REST.html

Image Variants: Offline vs. Online
----------------------
* version`xponents-3.3` is a regular Xponents binary distribution with all the Gazetteer data and REST capabilities
* version `xponents-offline-3.3.` is the same as above, but adds the full Apache Maven stack of libraries, plugins, and Xponents dependencies with the Maven install.  Great for offline compilation of your own Java code.  As long as you do not introduce new dependencies you can do Java development `offline`.

Services Included
------------------------
Xponents REST API is the default ENTRYPOINT.  That extracts geotags, patterns, etc.  Additionally you can run the underlying Solr Gazetteer for straight lookups of place metadata. This gazetteer service is not turned on by default. See how to invoke these options below.

Docker Get & Run
---------
In this example we pull down the Xponents docker image, re-tag it for simplicity sake. The tag used is `xponents:v3`.  The running container is `xponents-api`.  If you need to query the gazetteer reference data regularly or casually you should consider launching separate docker containers, i.e., `xponents-api` and `xponents-gaz`.

```
docker pull mubaldino/opensextant:xponents-3.3

# Optionally tag a short name "xponents:v3" for the image which is used in the remainder of the examples.
#-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
docker tag mubaldino/opensextant:xponents-3.3  xponents:v3

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

Optionally pipe to "json_pp" to see output for this quick test.  Or better yet visit the API notes above, Java and Python Lib/client provided in Xponents source.

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

