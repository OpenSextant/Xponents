#!/bin/bash
if [ ! -d ./log ] ;  then
  mkdir log
fi

SOLR_PORT=${SOLR_PORT:-7000}
SERVER=localhost:$SOLR_PORT
# Proxies can be sensitive, but at least we need NOPROXY
#unset http_proxy
#unset https_proxy
export noproxy=localhost,127.0.0.1
export NO_PROXY=$noproxy

cur=`dirname $0 `
XPONENTS=`cd -P $cur/..; echo $PWD`

export PYTHONPATH=$XPONENTS/python:$XPONENTS/piplib
GAZ_CONF=etc/gazetteer
SOLR_CORE_VER=solr7

if [ ! -d $XPONENTS/piplib ] ; then
   echo "install python first"
   echo "see README"
   exit 1
fi

index_taxcat () {
  SOLR_URL=$1
  TAXCAT=./etc/taxcat
  echo "Populate nationalities taxonomy in XTax"
  # you must set your PYTHONPATH to include required libraries built from ../python
  python3  ./script/gaz_nationalities.py  --taxonomy $TAXCAT/nationalities.csv --solr $SOLR_URL --starting-id 0
  sleep 1 

  echo "Populate core JRC Names 'entities' data file, c.2014"
  # These entries go in 3,000,000 to 5,000,000 range.
  JRC_SCRIPT=./script/taxcat_jrcnames.py
  python3 $JRC_SCRIPT --taxonomy $TAXCAT/data/entities.txt  --solr $SOLR_URL
  sleep 1

  # This file is manually curated, not sourced from JRC (EU). But the format is the same.
  # Start at 5,000,000 range.
  JRC_ADHOC=$TAXCAT/entities-adhoc.txt
  python3 $JRC_SCRIPT --taxonomy $JRC_ADHOC  --solr $SOLR_URL --no-fixes --starting-id 5000000
  sleep 1

  # Arbitrary row ID scheme, but we have various catalogs that should have reserved row-id space based on size.
  python3 script/taxcat_person_names.py   --solr $SOLR_URL --starting-id 20000
  sleep 1

  python3 script/taxcat_wfb.py --solr $SOLR_URL
  sleep 1
  # When done for the day,  optimize
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit%20expungeDeletes=\"true\"/>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<optimize/>"
}


index_gazetteer () {
  SOLR_URL=$1

  if [ ! -f "./tmp/master_gazetteer.sqlite" ]; then
    echo "Missing Master Gazetter SQLite file"
    exit 1
  fi

  # From SQLite master, Index
  python3 ./script/gaz_finalize.py --solr $SOLR_URL

  #
  echo "Alter some entries"
  # custom fixes: 'Calif.' abbreviation is not coded properly.
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<delete><query>name_tag:calif+AND+cc:US+AND+adm1:06</query></delete>"
  # ADHOC gazetter offers "washington dc" which confuses things.
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<delete><query>name:%22washington+dc%22+AND+place_id:(U531871+U1702382)</query></delete>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit/>"

  # When done for the day,  optimize
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit%20expungeDeletes=\"true\"/>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<optimize/>"
}

index_postal(){
  SOLR_URL=$1

  if [ ! -f "./tmp/postal_gazetteer.sqlite" ]; then
    echo "Missing Postal Gazetter SQLite file"
    exit 1
  fi

  # From SQLite master, Index
  python3 ./script/gaz_finalize.py --solr $SOLR_URL --postal --db ./tmp/postal_gazetteer.sqlite

  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit/>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<commit%20expungeDeletes=\"true\"/>"
  curl --noproxy localhost "$SOLR_URL/update?stream.body=<optimize/>"
}

do_start=0
do_clean=0
do_data=0
do_taxcat=1
do_gazetteer=1
do_postal=0
do_meta=0
proxy='' 
while [ "$1" != "" ]; do
 case $1 in 
  'data')
     do_data=1
     shift
     ;;
  'meta')
     do_meta=1
     shift
     ;;
  'start')
     do_start=1
     shift
     ;;
  'clean')
     do_clean=1
     shift
     ;;
  'proxy')
     proxy='proxy'
     shift
     ;;
  'taxcat')
     # One or the other. Default is both do taxcat and do gazetter
     do_gazetteer=0
     shift
     ;;
  'gazetteer')
     do_taxcat=0
     shift
     ;;
   'postal')
     do_postal=1
     do_gazetteer=0
     do_taxcat=0
     shift
     ;;
  esac
done

if [ ! -d $XPONENTS/target ]; then
  echo "In build mode, you must build the project libraries first" 
  exit 1
fi

if [ $do_data -eq 1 ] ; then 
  echo "Acquiring Census data files for names"
  ant $proxy get-gaz-resources

  echo "Harvesting World Factbook 'factoids'"
  python3 ./script/assemble_wfb_leaders.py
  python3 ./script/assemble_wfb_orgs.py
fi

if [ $do_meta -eq 1 ] ; then 
  python3 ./script/assemble_person_filter.py
  ant $proxy gaz-meta
fi

sleep 2 

if [ $do_start -eq 1 ]; then 
  # Stop first.
  ./mysolr.sh stop $SOLR_PORT

  if [ $do_clean -eq 1 ]; then 
    ant $proxy clean init
  fi

  echo "Starting Solr $SERVER"
  echo "Wait for Solr 7.x to load"
  ./mysolr.sh start $SOLR_PORT
  sleep 2
fi

if [ $do_data -eq 1 ] ; then 
  ant $proxy taxcat-jrc
fi
if [ $do_taxcat -eq 1 ]; then
  index_taxcat http://$SERVER/solr/taxcat
fi
if [ $do_gazetteer -eq 1 ]; then
  index_gazetteer http://$SERVER/solr/gazetteer
fi
if [ $do_postal -eq 1 ]; then 
  index_postal http://$SERVER/solr/postal
fi

echo "Gazetteer and TaxCat built, however Solr $SERVER is still running...." 
echo
echo "Use 'mysolr.sh stop $SOLR_PORT'"
