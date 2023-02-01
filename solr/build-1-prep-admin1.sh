#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`
XPONENTS=$basedir
export PYTHONPATH=$XPONENTS/python:$XPONENTS/piplib
export PYTHONUNBUFFERED=1

date

# All of this is to produce a foundational mapping of ISO to FIPS administrative boundary codes.
# Unfortunately we have to do this empirically so we know this mapping is consistent with our source data
# AND to know if this source data has oddities.

# Damn... You'd need this old copy of NGA geonames c.2021:
python3 ./script/gaz_nga.py ./tmp/Countries.txt --adm1

# Use the latest NGA geonames:
python3 ./script/gaz_nga.py ./tmp/Whole_World.txt --adm1

# Pull in the Geonames.org content
python3 ./script/gaz_geonames.py ./tmp/allCountries.txt  --adm1

# Stitch it all together:
./script/gaz_admin_exporter.py


echo "Find resulting output here:"

ls -l  etc/gazetteer/global_admin1_mapping.json

echo "Intermediate files:"
ls -l  etc/gazetteer/*csv

date

echo "Run  python tests in python/tests -- specifically adapt test_gazetteer_api.py on this result"
echo "When testing fine then copy up to Core/src/main/resources/"

