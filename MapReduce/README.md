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