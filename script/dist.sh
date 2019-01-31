#!/bin/bash

# Why is Java so difficult?
# Ant 1.10 / Java 8 do not support Unix file permissions on "copy"

pushd ../python
rm -rf ./dist/*
python2 ./setup.py sdist
popd

VER=3.0
BUILD_VER=3.0.5
ant -f ./dist.xml dist

REL=../dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

for f in $REL/xponents-solr/solr7-dist/bin/post \
	$REL/xponents-solr/solr7-dist/bin/solr ; do
  chmod u+x $f
done 


rm $REL/script/dist.sh
rm $REL/script/dist.xml
rm $REL/script/setup-ant.xml

cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2019-Q1
  Sources: NGA,  2019-JAN
           USGS, 2018-DEC
EOF

