#!/bin/bash 
export PYTHONPATH=./piplib:./python
URL_PREFIX=$1
FILE=$2
URL=$URL_PREFIX/xlayer/rest/process
# NOTE -- test data is in xlayer.py tester
python3 -m opensextant.xlayer --service-url "$URL" --inputfile "$FILE" --debug
