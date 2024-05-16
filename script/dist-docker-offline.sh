#!/bin/bash

echo "Building Docker images - Regular and Offline (Maven+Xponents)"

VERSION="3.5"
script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`


run_sonar=0
SONAR_URL=http://localhost:9000
if [ -n "$1"  ]; then 
  SONAR_URL=$1
  if [ -n "$2" ] ; then 
    SONAR_TOKEN=$2
    # run sonar only if command line args are given
    run_sonar=1
  else 
    echo "Usage:   $0  SONAR_URL  SONAR_TOKEN"
    echo
    echo "If using Sonar scan; otherwise scan is not performed"
    echo

    exit
  fi
fi

REL=$basedir/../dist/Xponents-$VERSION
if [ ! -d $REL ] ; then 
  echo "Distribution does not exist: $REL"
  echo "First build per BUILD.md"
  echo "Then run ./script/dist.sh "
  echo "... Then run ./script/dist-docker.sh  to see that the normal docker image builds"

  exit 1
fi

# Build offline.
# ==============
# Objective is to pull ALL Maven plugins and resources for a full Maven capability offline
#     in addition to the direct dependencies for the project itself.
# Three layers Core API, SDK, and then Examples that pulls it all together along with XText.
# Then also to provide Maven dev tools we include FindBugs, Checkstyle and JavaDoc plugins.
# 
# Running any of those plugins naturally pulls them down -- we stash them in $REPO
pushd $REL
REPO=maven-repo

echo "              Xponents Docker Offline         "
echo "=============================================="

echo "Version Number of Image"
read IMG_VERSION

# Previously cached.  Kill this off 
if [ -e "../maven-repo" ] ; then
  mv ../maven-repo  .
fi

cp  $basedir/Examples/Docker/dockerignore.offline $REL/.dockerignore


# CORE
echo "++++++++++++++++ CORE ++++++++++++++++"
(cd Core && mvn install -Dmaven.repo.local=../$REPO)

# SDK build
echo "++++++++++++++++ SDK ++++++++++++++++"
mvn -U clean install -Dopensextant.solr=./xponents-solr/solr7  -Dmaven.repo.local=$REPO
# SDK miscellany
# findbugs:check is no longer maintained.
mvn checkstyle:check -Dmaven.repo.local=$REPO

# Examples
echo "++++++++++++++++ EXAMPLES ++++++++++++++++"
(cd Examples ; mvn install -Dmaven.repo.local=../$REPO)


# Code scan using Sonarqube:
if [ $run_sonar -eq 1 ]; then 

    git init
    git add .gitignore pom.xml Core/pom.xml Core/src src script python 
    git commit -m "Trivial Sonar staging: All files must reside in git for Sonar"

    echo "Sonar scanning Core API"
    (cd Core &&   mvn  sonar:sonar \
      -Dmaven.repo.local=../$REPO \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents-core \
      -Dsonar.host.url=$SONAR_URL \
      -Dsonar.login=$SONAR_TOKEN \
      "-Dsonar.inclusions=**/*.java" )
    
    echo "Sonar scanning Xponents SDK"
    echo "--This is done OFFLINE to prove dependencies were acquired in pass above"
    mvn -o sonar:sonar \
      -Dmaven.repo.local=$REPO \
      -Dsonar.sourceEncoding=UTF-8 \
      -Dsonar.projectKey=opensextant-xponents \
      -Dsonar.host.url=$SONAR_URL \
      -Dsonar.login=$SONAR_TOKEN \
      "-Dsonar.inclusions=**/*.java"
fi



# One last time: go-offline
#  -- Remove cache files from any Internet downloads
#  -- Verify one last time we can make the project "go-offline"
find ./$REPO  -name "*.sha1"  -exec rm {} \;
find ./$REPO  -name "*.repositories"  -exec rm {} \;
mvn dependency:go-offline -Dmaven.repo.local=$REPO

# Log4J cleanup -- No, this happens on in Docker after offline image is built; then you can delete Log4J
for log4jdir in `find ./maven-repo -type d | grep log4j | grep "2.11"`; do 
  echo "Remove $log4jdir"
  rm -rf $log4jdir
done


# Docker
echo "++++++++++++++++ DOCKER / Maven Offline ++++++++++++++++"
cd $REL
docker build --tag opensextant:xponents-offline-$IMG_VERSION -f ./Dockerfile.offline .
