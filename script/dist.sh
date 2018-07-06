#!/bin/bash

# Why is Java so difficult?
# Ant 1.10 / Java 8 do not support Unix file permissions on "copy"

pushd ../python
rm -rf ./dist/*
python2 ./setup.py sdist
popd

VER=3.0
ant -f ./dist.xml dist

REL=../dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

for f in $REL/xponents-solr/solr7-dist/bin/post \
	$REL/xponents-solr/solr7-dist/bin/solr ; do
  chmod u+x $f
done 


