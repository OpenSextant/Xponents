Xponents and MapReduce
======================
CAVEAT:  This experiment was done with Xponents 2.9 and Solr 4.x.
We have no plans on maintaining it.

You have data that has text.
Your data also likely has been organized temporally or by some other means.
Once you dig in with Xponents you'll find lots of opportunities to
filter data by metadata, process the text and filter records based on that processing.
For example, find all data possibly related to Columbia by explicit mention or inferred
by timezone, city name, or well-known person (associated with the country).

To do this with bigdata, we demonstrate how to run Xponents taggers in MapReduce jobs.
The tagger resources can be large and complex, but hopefully this demonstration
illustrates the essential configuration and components.

```XponentsTaggerDemo``` is a main class for a Job. The Job here 
takes your execution parameters, filters, etc. and runs on input data, to produce some output (key,tags) tuples.

```GeoTaggerMapper``` and ```KeywordTaggerMapper``` are Mappers that process your input data and generate
the tags.  These demos output structured annotations in JSON.

```./script/ ``` Will have example invocations of the main job.



Hadoop Configuration
--------------------

See "build.sh" -- running this ONLY after you have successfully built the rest of Xponents is best.
Composing a lean, but accurate ZIP to ship over to your hadoop cluster took some effort, and this
build script is not optimal.  However it works.  

libjars - Create a 'libjars' folder (on your hadoop node where you kick off jobs).
This folder will contain JARS required by Xponents, Solr, etc. from the CLASSPATH and 
will be loaded by the various Class Loaders at runtime (that is,... when a job instance 
runs on the remote machine). The required JARS in libjars include:
- solr-core, solr-solrj
- lucene*
- spatial4j, jts
- any other JARs included in solr WAR except Hadoop libraries.


Running Xponents MR
--------------------

```

    mkdir xponents-mr; cd xponents-mr
	unzip ../xponents-mr-vXXXXXXX.zip 
	
	# Geotag content
	./script/xponents-mr.sh --in hdfs:///data/some/json/ --phase geotag

	# Tag other keywords and entities using XTax catalog
	./script/xponents-mr.sh --in hdfs:///data/some/json/ --phase xtax
	
	Where inputs (--in INPUT) are sequence files of the form:
		NULL {JSON OBJECT}
		
	In testing we happen to be using a null key.  'id' is in JSON
	For the purposes of this demonstration, Inputs from your "--in" path 
	are assumed to have the following properties:
		'id' and 'text' fields at the top level of your JSON
	
	Outputs will appear in HDFS under /user/you/xponents-mr-test
	Resulting sequence files will contain:
		ID	{JSON GEOCODINGS}
		ID  {JSON XTAX TAGS}
	for the respective phase run. 
```

Debugging MapReduce
-------------------

Use verbosity controls in script/xponents-mr.sh:

* use supplemental Log4J configuration: ``` --log4j-extra-config file:log4jsupplemental.xml```
* use mapreduce java options:  ```VERBOSITY=-Dlog4j.debug=true```, note that $VERBOSITY appears in job invocation
* override Hadoop Log4J using JVM options for job and mapreduce task: ```-Dlog4j.configuration=file:log4j.properties```
