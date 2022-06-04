#!/bin/bash

echo "Building Docker images - Regular and Offline (Maven+Xponents)"

VERSION="3.5"
script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

REL=$basedir/dist/Xponents-$VERSION
if [ ! -d $REL ] ; then 
  echo "Distribution does not exist: $REL"
  echo "First build per BUILD.md"
  echo "Then run ./script/dist.sh "
  exit 1
fi

echo "              Xponents Docker                 "
echo "=============================================="


echo "Version Number of Image"
read IMG_VERSION

cp  $basedir/Examples/Docker/dockerignore $REL/.dockerignore
cd $REL &&  docker build --tag opensextant:xponents-$IMG_VERSION .

