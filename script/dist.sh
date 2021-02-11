#!/bin/bash
#
#
VER=3.3
BUILD_VER=3.3.5
SOLR_DIST=./solr7-dist

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

# Why is Java so difficult?
# Ant 1.10 / Java 8 do not support Unix file permissions on "copy"

unset PYTHONPATH

pushd $basedir/python
rm -rf ./dist/*
python3 ./setup.py sdist

pushd $basedir/script

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
cp $basedir/Examples/Docker/* $REL/

# cp -r $basedir/src $basedir/pom.xml $basedir/Core $REL/

cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2020-Q1
  Sources: NGA,  2020-JAN
           USGS, 2019-NOV
EOF

