#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`
XPONENTS=$basedir
export PYTHONPATH=$XPONENTS/python:$XPONENTS/piplib
export PYTHONUNBUFFERED=1

do_test=0
while [ "$1" != "" ]; do
 case $1 in
  'test')
    do_test=1
    shift
    ;;
  *)
    echo "Bad argument"
    exit 1
    ;; 
 esac
done

# GLOB NationalFile*
USGS_FILE=`ls ./tmp/NationalFile_202*`

for f in  $USGS_FILE \
    "./tmp/Countries.txt" \
    "./tmp/allCountries.txt" \
    "./tmp/ne_10m_admin_1_states_provinces" \
    "./tmp/wordstats.sqlite"; do
  if [ -e "$f" ]; then
    echo "All is good, resource exists: $f"
  else
    echo "Missing resource, $f"
    echo "See README on sources and wordstats"
    exit 1
  fi
done

if [ "$do_test" -eq 1 ] ; then
  DB=./tmp/test.sqlite
  python3 ./script/gaz_popstats.py --db $DB
  python3 ./script/gaz_usgs.py ${USGS_FILE} --max 100000 --db $DB
  python3 ./script/gaz_nga.py ./tmp/Countries.txt --max 100000 --db $DB

  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt  --max 100000 --db $DB
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp  --db $DB --max 100000
  python3 ./script/gaz_fix_country_coding.py "US" --db $DB
  python3 ./script/gaz_generate_variants.py --db $DB
  python3 ./script/gaz_country_meta.py --db $DB
  python3 ./script/gaz_finalize.py  adjust-id --db $DB
  python3 ./script/gaz_finalize.py  adjust-bias --db $DB
  python3 ./script/gaz_finalize.py  dedup --optimize --db $DB
else
  # PRODUCTION  -- RAW SOURCES
  # ==========================
  # start fresh:
  DB=./tmp/master_gazetteer.sqlite
  rm -f $DB

  datekey=`date +%Y%m%d`

  echo GEONAMES POPULATION STATS
  python3 ./script/gaz_popstats.py

  # Geonames is FIRST, because it has better consistent coverage for all names
  echo GEONAMES       `date`
  LOG=./tmp/gaz_geonames_${datekey}.log
  python3 ./script/gaz_geonames.py ./tmp/allCountries.txt > $LOG

  # Note - USGS does not provide ADM1 names for US States in NationalFile.
  echo USGS      `date`
  LOG=./tmp/gaz_usgs_${datekey}.log
  python3 ./script/gaz_usgs.py $USGS_FILE > $LOG


  echo NGA GNIS  `date`
  LOG=./tmp/gaz_nga_${datekey}.log
  python3 ./script/gaz_nga.py ./tmp/Countries.txt > $LOG

  echo ADMIN CODES    `date`
  LOG=./tmp/gaz_administrative_codes_${datekey}.log
  python3 ./script/gaz_administrative_codes.py ./tmp/ne_10m_admin_1_states_provinces/ne_10m_admin_1_states_provinces.shp > $LOG

  # OMISSIONS
  echo OMISSIONS: Remove selected uncommon/problematic name variations
  python3 ./script/gaz_exclusions.py ./etc/gazetteer/filters/exclude-features.csv

  # DERIVATIONS
  # ==========================
  echo US STATE CODES `date`
  LOG=./tmp/gaz_fix_country_coding_${datekey}.log
  python3 ./script/gaz_fix_country_coding.py "US" > $LOG

  echo VARIANTS       `date`
  LOG=./tmp/gaz_generate_variants_${datekey}.log
  python3 ./script/gaz_generate_variants.py > $LOG

  echo COUNTRIES      `date`
  LOG=./tmp/gaz_country_meta${datekey}.log
  python3 ./script/gaz_country_meta.py > $LOG

  echo ADJUST        `date`
  LOG=./tmp/gaz_adjustments_${datekey}.log
  python3 ./script/gaz_finalize.py  adjust-id > $LOG
  python3 ./script/gaz_finalize.py  adjust-bias >> $LOG
  echo `date`

  echo OPTIMIZE/DEDUP  `date`
  LOG=./tmp/gaz_dedup_${datekey}.log
  python3 ./script/gaz_finalize.py  dedup  --optimize > $LOG
  echo `date`

fi
