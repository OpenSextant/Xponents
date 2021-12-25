
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

5\. Distribution and Packaging: `ant -f ./script/dist.xml dist`

Producing API documentation updates: infrequently, only for major releases.
 
```
 # target ./doc folders already exist as folders known to git.
 cp -r target/apidocs/ doc/sdk-apidocs
 cp -r Core/target/apidocs/ doc/core-apidocs

 # When done, commit changes, as API docs appear on GitHub public site.
```

6\. Test.  Leverage the `Examples` projects to more fully test your build.

7\. Final Release.  With the properly compiled, tested packaging then create a distribution in full.

```
 ./script/dist.sh 

 # Note - this script call ant  package-dist which just packages the pre-built, tested libraries.
```

8\. Publish Java Libraries to Maven Central as below

9\. Build, Test and Publish Docker Images as described in `./Examples/Docker/`



All Together
----------------
Put it all together it might look like this, with notes about size of interim data:

```shell

  ./setup.sh

  # About here you head into ./solr and produce the Gazetteer accoring to those build notes.
  # 25 GB to compose SQlite gazetteer completely;  
  # Allow for another 5GB of working space for the Solr Gazetteer to run

  (cd ./Core && mvn install)
  mvn install 

  ant -f ./script/dist.xml build build-examples
  ant -f ./script/dist.xml dist

  # Test a bit, right?

  # Raw distro is 4 GB;  Docker image is 4.8 GB,  Offline Docker image is 5.0 GB
  # SubTotal - 15 GB
  ./script/dist.sh
  ./script/dist-docker.sh
  ./script/dist-docker-offline.sh  http://localhost:9000  $SONAR_TOKEN


  # TOTAL footprint is nearing 45 GB;  Allot 5 GB more for each saved version
```


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

