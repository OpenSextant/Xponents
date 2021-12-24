#!/bin/bash

echo "Building Docker images - Regular and Offline (Maven+Xponents)"

VERSION="3.5"
script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

SONAR_URL=http://localhost:9000
if [ -n "$1" ]; then 
  SONAR_URL=$1
fi

TARGET=$basedir/dist/Xponents-$VERSION
if [ ! -d $TARGET ] ; then 
  echo "Distribution does not exist: $TARGET"
  echo "First build per BUILD.md"
  echo "Then run ./script/dist.sh "
  echo "... Then run ./script/dist-docker.sh  to see that the normal docker image builds"

  exit 1
fi
echo SONAR_TOKEN = 
echo "IF none is provided, Sonar scan is skipped"
read SONAR_TOKEN

# Build offline.
# ==============
# Objective is to pull ALL Maven plugins and resources for a full Maven capability offline
#     in addition to the direct dependencies for the project itself.
# Three layers Core API, SDK, and then Examples that pulls it all together along with XText.
# Then also to provide Maven dev tools we include FindBugs, Checkstyle and JavaDoc plugins.
# 
# Running any of those plugins naturally pulls them down -- we stash them in $REPO
REPO=maven-repo

echo "              Xponents Docker Offline         "
echo "=============================================="

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
if [ -n "$SONAR_TOKEN" ]; then 

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
      "-Dsonar.inclusions=**/*.java"
    
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


# Docker
echo "++++++++++++++++ DOCKER / Maven Offline ++++++++++++++++"
cd $TARGET
docker build --tag opensextant:xponents-offline-$VERSION -f ./Dockerfile.offline .
