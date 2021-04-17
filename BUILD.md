
Building Xponents 
==================

After checkout, first build Core API (opensextant-xponents-core)
Then you can begin working with the Tagger API (opensextant-xponents)

1\. Setup project. 

```
  ./setup.sh
```

2\. Setup Core API to build and test:

```
  (cd ./Core && mvn install)
```

3\. Build SDK:

There are some outstanding build tasks related to gazetteer metadata assembly that need automation.
But in general, you would make use of the `./solr/build.sh` script and consult the README there.

```
  mvn install
```

4\. Build a full Gazetteer from scratch.

`./solr/README.md` captures all the mechanics of building the Gazettter from checkout.

6\. Distribution and Packaging: `ant -f ./script/dist.xml dist`

Producing API documentation updates: infrequently, only for major releases.
 
```
 cp -r target/apidocs/ doc/sdk-apidocs/
 cp -r Core/target/apidocs/ doc/core-apidocs/

 # When done, commit changes, as API docs appear on GitHub public site.
```

7\. Test.  Leverage the `Examples` projects to more fully test your build.


Level of Effort.
----------------
First time to acquire all the software and build could be a day: You would pull in 4 GB of compressed files, 
unpack them all, configure software, etc.  
The Second time to walk through this all usually takes 2-4 hours, on a Macbook or Linux server with 4 CPU,
for example.

If this sounds like a bit much, then leverage the artifacts in Maven Central or Docker Hub.


Maven Publishing
----------------
```

  # Fix all versions to be release versions.
  # Ensure GPG key is known...
  # and OSSRH login is set in settings.xml

  cd ./Core
  mvn clean deploy -P release
  cd ..
  mvn clean deploy -P release
  

```

