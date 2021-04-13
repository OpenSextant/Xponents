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
(cd ./python  && rm ./dist/opensextant* &&  python3 ./setup.py sdist)

msg "Install Python resources"
# Install built lib with dependencies to ./python
pip3 install -U --target ./piplib ./python/dist/opensextant-1.3*.tar.gz
pip3 install -U --target ./piplib lxml bs4 arrow requests


msg "Assemble basic JAR resources"
. ./dev.env

pushd ./solr
  python3 ./script/assemble_person_filter.py
  ant  gaz-meta
popd




