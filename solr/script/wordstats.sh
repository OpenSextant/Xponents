#!/bin/bash

WORDSTATS=./tmp/wordstats

download(){
    mkdir -p $WORDSTATS
    for ALPHA in {a..z} ; do
       # rm -rf $WORDSTATS/*.gz
       URL="http://storage.googleapis.com/books/ngrams/books/googlebooks-eng-all-1gram-20120701-${ALPHA}.gz"
       wget $URL -P $WORDSTATS/
    done
}

assemble(){
    # Catalog is GA, GB, etc. "G" for google.
    for ALPHA in {a..z} ; do
        gzfile=($WORDSTATS/*-${ALPHA}.gz)
        python3 ./script/wordstats-collector.py G${ALPHA} $gzfile --db ./tmp/wordstats.sqlite
    done
}

while [ -n "$1" ]; do
  case $1 in
     "download")
       download
       shift
       ;;
     "assemble")
       assemble
       shift
       ;;
     *)
        echo "wordstats.sh [download] [assemble]"
        break;
        ;;
  esac
done