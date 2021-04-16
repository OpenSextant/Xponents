Xponents REST Docker Image
===========================

NOTE: "Xlayer" name is being deprecated in favor of simply "Xponents REST".

This document contains Build, Run and Packaging sections.
Packaging will include topics such as image publishing and Sonarqube code scanning.

Building
------------

Copy this Dockerfile to the Xponents ./dist folder and build the docker image with the Xponents-X.x build in that folder.  
To use a full development-ready version of the image use the `Docker.offline` builder or its respective image build instead.


```shell script
# This below produces a build of Xponents with Xlayer server scripts packed in.
#
cd Xponents
./script/dist.sh

./script/dist-docker.sh

# Contents of the docker distribution script is below.
# We wanted to offer a "Maven Offline" capability, retroactively.
# Knowing that Maven offline can be this challenging, some of these components may be reworked.
# ====================

# After a succesful build (dist will be about 3.5 GB), go to the release and build docker images -- regular and then offline.
VERSION=3.3
cd ./dist/Xponents-$VERSION
docker build --tag opensextant:xponents-$VERSION .

# Build offline.
# ==============
# Objective is to pull ALL Maven plugins and resources for a full Maven capability offline
#     in addition to the direct dependencies for the project itself.
# Three layers Core API, SDK, and then Examples that pulls it all together along with XText.
# Then also to provide Maven dev tools we include FindBugs, Checkstyle and JavaDoc plugins.
# 
# Running any of those plugins naturally pulls them down -- we stash them in $LOCAL_REPO
LOCAL_REPO=maven-repo
# CORE
(cd Core && mvn install -Dmaven.repo.local=../$LOCAL_REPO)
# SDK build
mvn -U clean install -Dopensextant.solr=./xponents-solr/solr7  -Dmaven.repo.local=$LOCAL_REPO
# SDK miscellany
mvn checkstyle:check findbugs:check  -Dmaven.repo.local=$LOCAL_REPO
# Examples
(cd Examples ; mvn install -Dmaven.repo.local=../$LOCAL_REPO)
# Docker
docker build --tag opensextant:xponents-offline-$VERSION -f ./Dockerfile.offline .
```


Now Test It.

```
# With OFFLINE image, you must use this sort of invocation:

  docker run --rm -it --entrypoint /bin/sh mubaldino/opensextant:xponents-offline-3.3

  #> mvn -o test -Dmaven.repo.local=$PWD/maven-repo

# Otherwise...

  docker run --rm -it --entrypoint /bin/sh mubaldino/opensextant:xponents-3.3

  #> mvn test 



```

This default build provides only a running Xponents REST service.  The Solr Gazetteer (default port 7000) is not running, 
but see below if you wish to run them together.  For heavy production use, it is better to keep these separate -- 
data processing vs. rote reference data lookups.

Running
--------------------
**Run Xponents REST:**

Main points:
* Choose a port number -- XLAYER_PORT is the only argument to the internal "xlayer-docker.sh" script.
 The ports in the invocation below are related to:
  *  Xponents REST service, ala `-p HOST_PORT:PPPP ... -e XLAYER_PORT=PPPP`. Externally you use HOST_PORT in your URL.
  *  Solr Gazeteer REST service, ala ` -p HOST_PORT:SSSS`, where SSSS is the port you start Solr, if needed. It is OFF by default.
* Leave off `--detach` if you want to see console.
Use `docker logs NAME` to see the console, if it was a detached run. NAME is the docker container "--name" argument.

```sh

docker run -p 8888:8888 -p 7000:7000 -e XLAYER_PORT=8888 \
      --name xponents --rm --detach  opensextant:xponents-$VERSION
      
```

**Run Gazeteeer:**

```sh

docker exec -it xponents /bin/bash -c \
   "cd ./xponents-solr && ./solr7-dist/bin/solr start -p 7000 -s ./solr7 -m 3g -q -force"

```

Now that you have the Gazetteer running, you can query this in various ways below.  But first consult 
the [Gazetteer demo service](https://github.com/OpenSextant/Xponents/blob/master/Examples/doc/README_gazetteer.md) to see example queries.

* **Python client**, as illustrated by `gazetteer.py`: https://github.com/OpenSextant/Xponents/blob/master/Examples/script/gazetteer.py, 
which requires the service URL as the `--solr URL` argument.
* **Java API**, as illustrated in the Xponents demo in the SDK: `./script/xponents-demo.sh gazetteer --help`. This does not need to 
the Solr server running; just listing this here for completeness.
* **Direct Solr Gazetteer access**, which provides a standard Solr search experience. For example in this example the query is getting 
at "places with Boston in the name, within 50KM of the lat/lon (40, -71), listing results as CSV":
http://localhost:7000/solr/gazetteer/select?q=name%3ABoston%20AND%20{!geofilt%20sfield=geo%20d=50%20pt=40.0,-71.0}&wt=csv



Packaging
---------------

**Sonarqube Usage**

As noted on Sonarqube's page -- [install a server (from docker)](https://docs.sonarqube.org/latest/setup/install-server/) first.

1. Launch Sonarqube server from the docker-compose file provided. 
2. Access localhost:9000 (default Sonarqube admin page)
3. Create a project, defining a user and a user token
4. Establish your Global Settings (`$HOME/.m2/settings.xml`) as noted in [documentation](https://docs.sonarqube.org/latest/analysis/scan/sonarscanner-for-maven/)
5.  Insert your Sonarqube project token in place of `$SONAR_TOKEN` in examples below.
6. Optionally, add the `settings.xml` file to your global or user folder, i.e., `~/.m2/`.  The defaults work fine without it.

For example, with the sonarqube plugin I launch an analysis:

```
    SONAR_TOKEN=abcdef01234.... 

    pushd ./Xponents/Core
    mvn sonar:sonar \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents-core \
      -Dsonar.host.url=http://localhost:9000 \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.inclusions="**/*.java"
    
    popd
    pushd ./Xponents
    mvn sonar:sonar \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents \
      -Dsonar.host.url=http://localhost:9000 \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.inclusions="**/*.java"
      
    popd
    pushd ./XText
    mvn sonar:sonar \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xtext \
      -Dsonar.host.url=http://localhost:9000 \
      -Dsonar.login=$SONAR_TOKEN \
      -Dsonar.inclusions="**/*.java"
    popd    
```


