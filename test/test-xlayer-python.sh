#!/bin/bash 
export PYTHONPATH=./piplib:./python
# URL is host:port or the full prefix
URL=$1
FILE=$2
# NOTE -- test data is in xlayer.py tester
python3 -m opensextant.xlayer --service-url "$URL" --debug "$FILE"
