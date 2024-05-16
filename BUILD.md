
Building Xponents 
==================

After checkout, first build Xponents-Core API (opensextant-xponents-core)
Then you can begin working with this extractor SDK (opensextant-xponents)

1. Setup project. 

```
  # Set up Core API with independent reference data -- If you are attempting a build
  # you should also have checked out Xponents-Core repo and understand how to build that first
  # as a precursor to this build, which depends on Core.

  # Set the main SDK API with the Gazetteer data unique to this project:
  cd solr && ./build.sh meta
```


3. Build a full Gazetteer from scratch.

`./solr/README.md` captures all the mechanics of building the Gazettter from checkout.
The SDK is dependent on having one of the folders `./solr` source or `./xponents-solr` built.
Since you are reading thie BUILD documentation you'll want to try building the `./solr` folder from source.

4. Build SDK:

There are some outstanding build tasks related to gazetteer metadata assembly that need automation.
But in general, you would make use of the `./solr/build.sh` script and consult the README there.

```
  mvn install  


  # To try out demos, tests, etc.  This step invokes `mvn install` on the desired projects.
  ant build build-examples
```

`Examples` sub-project makes use of Core, the main SDK, and the OpenSextant XText project. 
XText libraries are  published in Maven Central.


5. Distribution and Packaging: `ant dist`

Producing API documentation updates: infrequently, only for major releases.
 
```
 # target ./doc folders already exist as folders known to git.
 cp -r target/apidocs/ doc/sdk-apidocs

 # When done, commit changes, as API docs appear on GitHub public site.
```

6. Test.  Leverage the `Examples` projects to more fully test your build.

7. Final Release.  With the properly compiled, tested packaging then create a distribution in full.

```
 ./script/dist.sh 

 # Note - this script call ant  package-dist which just packages the pre-built, tested libraries.
```

8. Publish Java Libraries to Maven Central as below

9. Build, Test and Publish Docker Images as described in `./Examples/Docker/`



All Together
----------------
Put it all together it might look like this, with notes about size of interim data:

```shell

  # About here you head into ./solr and produce the Gazetteer accoring to those build notes.

  # Java modules
  # ----------------------
  # Ant script below automates these Maven routines. So to build there are options
  # Depending on what you are doing.
  # Option -- Maven to install
  # mvn install 
  # (cd ./Examples && mvn install)

  ant compile 
  # Once tested and good -- publish a release to Maven Central 

  # Use Ant to wrap around Maven to site, package and prep distro
  ant dist

  # Test a bit, right?

  # Complete distribution
  # ----------------------
  # Produce the  full Xponents SDK, then build docker images from that.
  # Raw distro is 4 GB;  Docker image is 4.8 GB,  Offline Docker image is 5.0 GB
  # SubTotal - 15 GB
  ./script/dist.sh
  ./script/dist-docker.sh
  
  # Prior to doing anything with Sonar make sure it is accessible. 
  # If using Examples/Docker/Sonarqube -- go there now and run `docker-compose up -d`.  Configured port is 9900
  ./script/dist-docker-offline.sh  http://localhost:9900  $SONAR_TOKEN

```

Code Practices
----------------

Three (3) or more different software quality practices are in play here:

- Use Sonarqube to track SQE over time across the project
- Use Checkstyle to assess code statically
- Use Docker scan + Snyk to monitor dependencies and vulnerabilities
- Use Findbugs legacy to cover a lot overlap on the first three.

This project overall converted from using checkstyle and findbugs to just using Sonarqube. 
See the deployment of Sonarqube in [Examples/Docker/Sonarqube](./Examples/Docker/Sonarqube)

For historical purposes here is a POM snippet for using findbugs, which is no longer maintained. 
In a similar reduction of dependencies, checkstyle plugin functions are replaced by Sonar scanning:

```xml

<project>
  ...    
  <pluginManagement>
        <plugin>
          <groupId>org.codehaus.mojo</groupId>
          <artifactId>findbugs-maven-plugin</artifactId>
          <version>3.0.5</version>
        </plugin>
  </pluginManagement>
  <plugins>
      <!-- run explicitly with: mvn findbugs:check -->
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>findbugs-maven-plugin</artifactId>
        <configuration>
          <xmlOutput>true</xmlOutput>
          <!--<threshold>High</threshold> -->
        </configuration>
      </plugin>
      
      <!-- run explicitly with: mvn checkstyle:check -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>

        <artifactId>maven-checkstyle-plugin</artifactId>
        <configuration>
          <configLocation>checkstyle.xml</configLocation>
          <propertyExpansion>checkstyle.indentChars=4</propertyExpansion>
          <suppressionsLocation>checkstyle-suppressions.xml</suppressionsLocation>
          <consoleOutput>true</consoleOutput>
          <failOnViolation>false</failOnViolation>
        </configuration>
      </plugin>
  </plugins>
</project>
```

For Docker Scan and Snyk usage, you will need to review those emerging services.  They seem to work 
in an insecure mode, but have issues with secure operation behind firewalls.  With a vulnerability 
report in hand you can adjust Maven POM or other aspects before publishing.

```shell
  # Get a vulnerability report
  snyk auth
  snyk test  --insecure


  # Ditto, using Docker
  docker scan opensextant:xponents-offline-3.5

```

Sizing
----------------

Overall a complete build of gazetteer and production docker images will consume 45 GB. 
Docker images are compressable to about 2.0 GB.  The breakdown is below:

- Checkout and build libraries -  0.3 GB
- Compose SQLite Gazetteers from raw data - 25.0 GB
- Assemble Solr Gazetteer(s)  - 3.5 GB
- Package final distribution - 4.0 GB
- Package docker normal - 4.0 GB
- Package docker offline - 4.2 GB


Level of Effort.
----------------
First time to acquire all the software and build could be a day: You would pull in 4 GB of compressed files, 
unpack them all, configure software, etc.  
The Second time to walk through this all usually takes 2-4 hours, on a Macbook or Linux server with 4 CPU,
for example.

If this sounds like a bit much, then leverage the artifacts in Maven Central or Docker Hub.


Maven Publishing
----------------
-  Fix all versions to be release versions. No SNAPSHOTS, no broken javadoc, etc. Go through entire packgaging phase.
-  Ensure GPG key is known...
-  and OSSRH login is set in settings.xml
-  Prerequsite items include a `pinentry` and Gnu PGP utilities. On Mac, `brew install gnupg pinentry-mac`, for example.
-  Review [Maven Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/) for details

```
  mvn clean deploy -P release

```

