#!/bin/bash

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`
XPONENTS=$basedir
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
  esac
done

if [ "$do_data" -eq 1 ]; then
  ant postal-sources
fi

if [ -d "./tmp/postal" ]; then
  echo "Ready to Roll"
else
  echo "Some files may be missing - Please check ./tmp/postal and make sure required files are unpacked"
  ls -1 ./tmp/
  exit 1
fi

if [ "$do_test" -eq 1 ] ; then
  DB=./tmp/postal_test.sqlite
  python3 ./script/postal.py ./tmp/postal/allCountries.txt 0 --db $DB --max 1000
  python3 ./script/postal.py ./tmp/postal/CA_full.txt 2000000 --db $DB --max 1000 --country CA
  python3 ./script/postal.py ./tmp/postal/NL_full.txt 3000000 --db $DB --max 1000 --country NL
  python3 ./script/postal.py ./tmp/postal/GB_full.txt 4000000 --db $DB --max 1000 --country GB
  python3 ./script/postal.py XX 6000000 --db $DB --max 1000  --copy-admin

else
  # PRODUCTION
  DB=./tmp/postal_gazetteer.sqlite
  # start fresh:
  rm -f $DB
  echo POSTAL/GEONAMES       `date`
  python3 ./script/postal.py ./tmp/postal/allCountries.txt 0 --db $DB
  echo POSTAL/CANADA         `date`
  python3 ./script/postal.py ./tmp/postal/CA_full.txt 2000000 --db $DB --country CA
  echo POSTAL/NETHERLANDS    `date`
  python3 ./script/postal.py ./tmp/postal/NL_full.txt 3000000 --db $DB --country NL
  echo POSTAL/UK            `date`
  python3 ./script/postal.py ./tmp/postal/GB_full.txt 4000000 --db $DB --country GB
  echo POSTAL/ADMIN CODES   `date`
  python3 ./script/postal.py XX                       6000000 --db $DB --copy-admin --optimize

fi
