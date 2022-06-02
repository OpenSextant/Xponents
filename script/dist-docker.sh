#!/bin/bash

echo "Building Docker images - Regular and Offline (Maven+Xponents)"

VERSION="3.5"
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


echo "Version Number of Image"
read IMG_VERSION

cd $TARGET/
if [ -e "maven-repo" ] ; then
  mv maven-repo ..
fi
docker build --tag opensextant:xponents-$IMG_VERSION .

