#!/bin/bash

XPONENTS=../
export PYTHONPATH=$XPONENTS/python:$XPONENTS/piplib
export PYTHONUNBUFFERED=1

do_test=0
do_data=0
while [ "$1" != "" ]; do
 case $1 in
  'data')
     do_data=1
     shift
     ;;

  'test')
    do_test=1
    shift
    ;;
done

if [ "$do_data" -eq 1 ]; then
  ant gaz-sources
fi

if [ -f "./tmp/MergedGazetteer.txt"
     -a -f "./tmp/allCountries.txt"
     -a -d "./tmp/ne_10m_admin_1_states_provinces" ]; then
  echo "Ready to Roll"
else
  echo "Some files may be missing - Please check ./tmp and make sure required files are unpacked"
  ls -1 ./tmp
  exit 1
fi

if [ "$do_test" -eq 1 ] ; then
  DB=./tmp/test.sqlite
  python3 ./script/gaz_opensextant.py ./tmp/MergedGazetteer.txt --max 300000 --db $DB
  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt  --max 300000 --db $DB
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp  --db $DB
  python3 ./script/gaz_generate_variants.py --db $DB
  python3 ./script/gaz_finalize.py  --dedup --optimize
else
  # PRODUCTION
  python3 ./script/gaz_opensextant.py ./tmp/MergedGazetteer.txt
  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp
  python3 ./script/gaz_generate_variants.py
  python3 ./script/gaz_finalize.py  --dedup  --optimize
fi
