#!/bin/bash
#
#
VER=3.3
BUILD_VER=3.3.7
SOLR_DIST=./solr7-dist

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

# Why is Java so difficult?
# Ant 1.10 / Java 8 do not support Unix file permissions on "copy"

unset PYTHONPATH

# Stop default Solr before copying.
pushd $basedir/solr
./mysolr.sh stop 7000
popd

# Make Python library
pushd $basedir/python
rm -rf ./dist/*
python3 ./setup.py sdist

# Package project
pushd $basedir/script

# Pre-build the project before running this script.
ant -f ./dist.xml package-dist

REL=$basedir/dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

for f in $REL/xponents-solr/solr*-dist/bin/post \
	$REL/xponents-solr/solr*-dist/bin/solr ; do
  chmod u+x $f
done 

rm -rf $REL/xponents-solr/solr*-dist/server/logs/*
rm -rf $REL/log
mkdir -p $REL/log

rm $REL/doc/*.mp4
rm $REL/script/dist* 
cp -r $basedir/dev.env $basedir/Examples/Docker/* $REL/


# cp -r $basedir/src $basedir/pom.xml $basedir/Core $REL/

cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2021-Q2
  Sources: NGA,  2021-MAR
           USGS, 2021-JAN
           Geonames.org, 2021-MAR
           NaturalEarth, 2021-MAR
EOF

