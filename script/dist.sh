#!/bin/bash
#
#
VER=3.5

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`
BUILD_VER=`grep build.v  $basedir/build.properties |awk -F= '{print $2;}'`

msg(){
  echo
  echo $1
  echo "========================="
}

msg "Stop Solr 7.x before copying to distribution"
# ----------------------
pushd $basedir/solr
./mysolr.sh stop 7000
popd

msg "Make Python library"
msg " TODO: document using python lib from distro, as it is not fully installed." 
# ----------------------
pushd $basedir/python
rm -rf ./dist/*
python3 ./setup.py sdist
pip3 install -U -t $basedir/piplib ./dist/opensextant-1.4*gz

msg "Prepare additional Java resources"
# ----------------------
export PYTHONPATH=$basedir/python:$basedir/piplib
pushd ../solr;
# resource files for person names
python3 ./script/assemble_person_filter.py
# copy to Maven project
ant gaz-meta
popd


msg "Prepare Python API docs" 
# ----------------------
pushd $basedir/doc/pydoc/
pydoc3 -w opensextant opensextant.xlayer opensextant.utility opensextant.phonetics  opensextant.advas_phonetics \
   opensextant.gazetteer opensextant.extractors opensextant.TaxCat opensextant.FlexPat
popd

msg "Build and Package project"
# ----------------------
pushd $basedir/script

# Pre-build the project before running this script.
ant -f ./dist.xml package-dist

REL=$basedir/dist/Xponents-$VER
find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

msg "Copy Solr indices in bulk"
# ----------------------
for f in $REL/xponents-solr/solr*-dist/bin/post \
	$REL/xponents-solr/solr*-dist/bin/solr ; do
  chmod u+x $f
done 

msg "Clean up distribution"
# ----------------------
rm -rf $REL/xponents-solr/solr*-dist/server/logs/*
rm -rf $REL/log
mkdir -p $REL/log

rm $REL/doc/*.mp4
rm $REL/script/dist* 
rm -r $REL/xponents-solr/retired
rm -r $REL/xponents-solr/script/__pycache__
rm -f $REL/xponents-solr/solr7-dist/licenses/log4j*2.11* 

cp -r $basedir/.gitignore $basedir/dev.env $basedir/Examples/Docker/* $REL/
rm -r $REL/Sonarqube

# Distro has API docs in JAR files.
rm -rf $REL/doc/*apidocs/

msg "Create VERSION label"
cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2022-Q1
  Sources: NGA,  2021-OCT
           USGS, 2021-AUG
           Geonames.org, 2021-AUG
           NaturalEarth, 2021-MAR
EOF
 
