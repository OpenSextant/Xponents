Xponents and MapReduce
======================

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

.. WORK IN PROGRESS .. 


Hadoop Configuration
--------------------

libjars - Create a 'libjars' folder (on your hadoop node where you kick off jobs).
This folder will contain JARS required by Xponents, Solr, etc. from the CLASSPATH and 
will be loaded by the various Class Loaders at runtime (that is,... when a job instance 
runs on the remote machine). The required JARS in libjars include:
- solr-core, solr-solrj
- lucene*
- spatial4j, jts
- any other JARs included in solr WAR except Hadoop libraries.
```
antlr-runtime-3.5.jar
asm-4.1.jar
asm-commons-4.1.jar
commons-cli-1.2.jar
commons-codec-1.10.jar
commons-configuration-1.6.jar
commons-fileupload-1.2.1.jar
commons-io-2.4.jar
commons-lang-2.6.jar
concurrentlinkedhashmap-lru-1.2.jar
dom4j-1.6.1.jar
guava-14.0.1.jar
hppc-0.5.2.jar
httpclient-4.3.1.jar
httpcore-4.3.jar
httpmime-4.3.1.jar
log4j-1.2.17.jar
lucene-analyzers-common-4.10.4.jar
lucene-analyzers-kuromoji-4.10.4.jar
lucene-analyzers-phonetic-4.10.4.jar
lucene-codecs-4.10.4.jar
lucene-core-4.10.4.jar
lucene-expressions-4.10.4.jar
lucene-grouping-4.10.4.jar
lucene-highlighter-4.10.4.jar
lucene-join-4.10.4.jar
lucene-memory-4.10.4.jar
lucene-misc-4.10.4.jar
lucene-queries-4.10.4.jar
lucene-queryparser-4.10.4.jar
lucene-spatial-4.10.4.jar
lucene-suggest-4.10.4.jar
noggit-0.5.jar
org.restlet-2.1.1.jar
org.restlet.ext.servlet-2.1.1.jar
protobuf-java-2.5.0.jar
slf4j-api-1.7.5.jar
slf4j-log4j12-1.7.21.jar
solr-core-4.10.4.jar
solr-solrj-4.10.4.jar
wstx-asl-3.2.7.jar
zookeeper-3.4.6.jar

spatial4j-0.4.1.jar -- not provided with Solr 4.x
jts-1.13.jar -- not provided with Solr 4.x

```
