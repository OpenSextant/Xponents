#!/bin/bash
#
#
VER=3.4
BUILD_VER=3.4.0
SOLR_DIST=./solr7-dist

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

msg(){
  echo
  echo $1
  echo "========================="
}

msg "Stop Solr 7.x before copying to distribution"
pushd $basedir/solr
./mysolr.sh stop 7000
popd

msg "Make Python library"
pushd $basedir/python
rm -rf ./dist/*
python3 ./setup.py sdist
pip3 install -U -t $basedir/piplib ./dist/opensextant-1.3*gz

msg "Prepare additional Java resources"
# ----------------------
export PYTHONPATH=$basedir/piplib
pushd ../solr;
# resource files for person names
python3 ./script/assemble_person_filter.py
# copy to Maven project
ant gaz-meta
popd

msg "Build and Package project"
pushd $basedir/script

# Pre-build the project before running this script.
ant -f ./dist.xml dist

REL=$basedir/dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

msg "Copy Solr indices in bulk"
for f in $REL/xponents-solr/solr*-dist/bin/post \
	$REL/xponents-solr/solr*-dist/bin/solr ; do
  chmod u+x $f
done 

msg "Clean up distribution"
rm -rf $REL/xponents-solr/solr*-dist/server/logs/*
rm -rf $REL/log
mkdir -p $REL/log

rm $REL/doc/*.mp4
rm $REL/script/dist* 
cp -r $basedir/dev.env $basedir/Examples/Docker/* $REL/


# cp -r $basedir/src $basedir/pom.xml $basedir/Core $REL/

msg "Create VERSION label"
cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2021-Q2
  Sources: NGA,  2021-MAR
           USGS, 2021-JAN
           Geonames.org, 2021-MAR
           NaturalEarth, 2021-MAR
EOF

