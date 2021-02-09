#!/bin/bash 
export PYTHONPATH=./piplib
PORT=$1
FILE=$2
URL=http://localhost:$PORT/xlayer/rest/process
# NOTE -- test data is in xlayer.py tester
python3 -m opensextant.xlayer --service-url "$URL" --inputfile "$2" --debug
