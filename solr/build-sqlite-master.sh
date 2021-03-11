#!/bin/bash

XPONENTS=../
export PYTHONPATH=$XPONENTS/python:$XPONENTS/piplib

TEST=0

if [ "$TEST" -eq 1 ] ; then 
  python3 ./script/gaz_opensextant.py ./tmp/MergedGazetteer.txt --max 300000
  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt  --max 300000
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp 
else
  # PRODUCTION
  python3 ./script/gaz_opensextant.py ./tmp/MergedGazetteer.txt
  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp

fi

