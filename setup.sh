#!/bin/bash

msg(){
  echo
  echo $1
  echo "========================="
}


msg  "../Xponents-Core checkout and build is required to get started"

unset PYTHONPATH
msg "Install Python resources"

PYLIB=`ls ../dist/xponents-core-3.*/python/opensextant-1.5.*.tar.gz`

if [ ! -e $PYLIB ]; then
  msg Locate $PYLIB first please
  exit
fi
 

# Install built lib with dependencies to ./python. First install here are 
# libraries used by Solr/ETL scripting:
pip3 install -U --target ./piplib lxml bs4 arrow requests pyshp pycountry $PYLIB

msg "Assemble basic JAR resources"
. ./dev.env

pushd ./solr
  python3 ./script/assemble_person_filter.py
  ant  gaz-meta
popd




