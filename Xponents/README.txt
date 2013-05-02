
Xponents README
===============
auth: Marc Ubaldino
date: 2013-04-26


         Xponents    extractor components used by OpenSextant.

         + XCoord    extract geographic coordinates
         + XTemporal extract well-defined date/time patterns
         + FlexPat   define and test regex patterns
         + XText     convert document formats, using Tika, etc.

OpenSextant extraction is based on these and other components. 
Xponents are largely regex-based. 
XText is a document converter that is Tika-based.
These components share a common, simple data model and utilities provided in OpenSextant Commons.
The projects started as and still are Ant-based builds. Support for Maven has been added recently.
We will support both indefinitely.


The general structure for these modules is:

  (module)/src/main/java    source code used by Ant or Maven
  (module)/lib              libraries used by Ant
  (module)/doc

  (module)/src/test/java, resources   Testing resources used by Ant or Maven


Generate Javadoc only using Maven. 

  mvn install 

Maven-specific outputs will appear at (module)/target/
Maven dependencies are defined in the POM (pom.xml)
A Maven install will compile code, resolve dependencies, test, generate javadoc and package the release.


The Ant build.xml script does these same things, but makes use of specific Ant tasks. The common ones are:

  ant 
  ant test-default






