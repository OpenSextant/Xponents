Xponents
========

 Part of OpenSextant 2.0
 Date: 25 JULY 2013

Xponents is a set of text extractor libraries with a light veneer of processing control, including:

  * Document conversion using Tika;
  * Geographic name and coordinate extraction;
  * Temporal extraction;
  * Pattern-based extraction

The intent of Xponents is to provide the extraction without too much infrastructure, as you likely already have that.


#Dependencies#

Set JAVA_HOME to your JDK6 or JDK7 installation;  Maven javadoc plugin requires JAVA_HOME set

FlexPat pattern-based extractors rely on Java 6+ regular expression API

XText depends on Tika 1.3+

Name Matching depends on:

  OpenSextant Gazetteer;
  * Download a built gazetteer at  http://www.opensextant.org/
  OR
  * Build  your own using https://github.com/OpenSextant/opensextant/tree/master/Gazetteer, which Depends on Pentaho Kettle for creation

  OpenSextant SolrTextTagger
  https://github.com/OpenSextant/SolrTextTagger


#Using#

From a built distribution, try:

  ant -f ./script/testing.xml  test-XXXX     <args>

where XXXX is the name of a core component and args are the arguments to that component


To test XCoord coordinate extraction patterns this is pretty simple:

  ant -f ./script/testing.xml  test-xcoord

Other Ant tasks in "testing.xml" will allow you to process your own text file.


From source:

  ## Create the distribution
  ant -f ./script/dist.xml

  ## Now ./release/  contains a built version of XPonents that will run off of Ant from there on.

#Misc#

If you are building under OS X and receive an error like this:

    [ERROR] Failed to execute goal org.apache.maven.plugins:maven-jar-plugin:2.4:test-jar (attach-tests) on project opensextant-xponents: Error assembling JAR: java.lang.reflect.InvocationTargetException: Malformed input or input contains unmappable chacraters: /path/to/Xponents/Extraction/target/test-classes/unicode-filen?me.txt -> [Help 1]

Then you may need to set the environment variable LC_CTYPE="UTF-8" to allow building to work correctly. See here for a discussion: https://netbeans.org/bugzilla/show_bug.cgi?id=225743#c3
