#!/bin/bash
#
#
VER=3.7

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`
BUILD_VER=`grep xponents.v  $basedir/build.properties |awk -F= '{print $2;}'`
REL=$basedir/../dist/Xponents-$VER
GAZ=$REL/xponents-solr
CORE=$basedir/../dist/xponents-core-$VER

msg(){
  echo
  echo $1
  echo "========================="
}

msg "Stop Solr 7.x before copying to distribution"
# ----------------------
cd $basedir/solr
./mysolr.sh stop 7000

msg "Make Python library"
msg " TODO: document using python lib from distro, as it is not fully installed." 
# ----------------------

pydist=`ls $CORE/python/opensextant-1*.tar.gz`
if [ -e  "$pydist" ] ; then
    pip3 install -U -t $REL/piplib $pydist
else
    echo "Python API lib is missing"
    exit
fi


msg "Prepare additional Java resources"
# ----------------------
export PYTHONPATH=$REL/piplib
cd $basedir/solr;
# resource files for person names
python3 ./script/assemble_person_filter.py
# copy to Maven project -- TODO:  final gaz-meta should have happened befoe mvn install or ant dist
ant gaz-meta


msg "Prepare Python API docs" 
# ----------------------
cd $basedir/doc/pydoc/
pydoc3 -w opensextant \
   opensextant.xlayer opensextant.utility \
   opensextant.phonetics  opensextant.advas_phonetics \
   opensextant.gazetteer opensextant.extractors \
   opensextant.TaxCat opensextant.FlexPat


cd $basedir

msg "Build and Package project"
# ----------------------
ant  dist

find $REL -type f -name "*.sh" -exec chmod u+x {} \; -print

msg "Patch Solr Server"
# ----------------------
# Patch Solr7 for Java16+
cp $basedir/solr/script/solr7-dist-bin-solr $GAZ/solr7-dist/bin/solr 
cp $basedir/solr/script/solr7-dist-bin-solr.cmd $GAZ/solr7-dist/bin/solr.cmd

for f in $GAZ/solr*-dist/bin/post $GAZ/solr*-dist/bin/solr ; do
  chmod u+x $f
done 


msg "Clean up distribution"
# ----------------------
rm -rf $GAZ/solr*-dist/server/logs/*
rm -rf $REL/log
mkdir -p $REL/log

# Dot Dir, Dot File
find $REL  -type f -name ".*" -exec rm -f {} \;
find $REL  -type f -name "*.iml" -exec rm {} \;
find $REL  -type d -name ".idea" -exec rm -rf {} \;
find $REL  -type d -name ".settings" -exec rm -rf {} \;
rm -rf $REL/.git 

# Media
rm $REL/doc/*.mp4
rm $REL/script/dist* 
# Library cleanup
rm -rf $GAZ/script/__pycache__
rm -ff $GAZ/solr7-dist/licenses/log4j*2.11* 

# Docker configuration
cp  $basedir/.gitignore $basedir/dev.env $REL/
(cd $basedir/Examples/Docker/; cp Dockerfile* README* settings.xml  docker-compose.yml $REL/)


# Distro has API docs in JAR files.
rm -rf $REL/doc/*apidocs/

msg "Create VERSION label"
cat <<EOF > $REL/VERSION.txt
Build:     $BUILD_VER
Date:      `date`
Gazetteer: Xponents Solr 2022-Q1
  Sources: NGA,  2022-APR
           USGS, 2021-AUG
           Geonames.org, 2021-AUG
           NaturalEarth, 2021-MAR
EOF
 
