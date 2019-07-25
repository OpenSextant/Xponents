#!/bin/bash
#
#
VER=3.2
BUILD_VER=3.2

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

# Why is Java so difficult?
# Ant 1.10 / Java 8 do not support Unix file permissions on "copy"

unset PYTHONPATH

pushd $basedir/python
rm -rf ./dist/*
python2 ./setup.py sdist

pushd $basedir/script

ant -f ./dist.xml package-dist

REL=$basedir/dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

for f in $REL/xponents-solr/solr7-dist/bin/post \
	$REL/xponents-solr/solr7-dist/bin/solr ; do
  chmod u+x $f
done 

rm $REL/script/dist.* 
cp -r $basedir/Examples/Docker ./dist/

cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2019-Q3
  Sources: NGA,  2019-MAY
           USGS, 2019-MAY
EOF

