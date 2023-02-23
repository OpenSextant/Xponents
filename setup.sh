#!/bin/bash


msg(){
  echo
  echo $1
  echo "========================="
}


# Project setup -- if any of these resources change at all you re-run the setup 
# to refresh the environment.
msg "Setup Project resource data"
ant setup

# Python setup.
msg "Build Python libraries"

unset PYTHONPATH
if [ -d "./python/dist" ]; then
  rm -f ./python/dist/*
fi
(cd ./python  &&  python3 ./setup.py sdist)

msg "Install Python resources"
# Install built lib with dependencies to ./python. First install here are 
# libraries used by Solr/ETL scripting:
pip3 install -U --target ./piplib lxml bs4 arrow requests pyshp pycountry
pip3 install -U --target ./piplib ./python/dist/opensextant-1.4*.tar.gz

msg "Assemble basic JAR resources"
. ./dev.env

pushd ./solr
  python3 ./script/assemble_person_filter.py
  ant  gaz-meta
popd




