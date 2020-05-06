#!/bin/bash

echo "Building Docker images - Regular and Offline (Maven+Xponents)"

VERSION="3.3"
script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

TARGET=$basedir/dist/Xponents-$VERSION
if [ ! -d $TARGET ] ; then 
  echo "Distribution does not exist: $TARGET"
  echo "First build per BUILD.md"
  echo "Then run ./script/dist.sh "

  exit 1
fi

echo "              Xponents Docker                 "
echo "=============================================="

cd $TARGET/
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

echo "              Xponents Docker Offline         "
echo "=============================================="

# CORE
echo "++++++++++++++++ CORE ++++++++++++++++"
(cd Core && mvn install -Dmaven.repo.local=../$LOCAL_REPO)

# SDK build
echo "++++++++++++++++ SDK ++++++++++++++++"
mvn -U clean install -Dopensextant.solr=./xponents-solr/solr7  -Dmaven.repo.local=$LOCAL_REPO
# SDK miscellany
mvn checkstyle:check findbugs:check  -Dmaven.repo.local=$LOCAL_REPO

# Examples
echo "++++++++++++++++ EXAMPLES ++++++++++++++++"
(cd Examples ; mvn install -Dmaven.repo.local=../$LOCAL_REPO)

# Docker
echo "++++++++++++++++ DOCKER / Maven Offline ++++++++++++++++"
docker build --tag opensextant:xponents-offline-$VERSION -f ./Dockerfile.offline .
