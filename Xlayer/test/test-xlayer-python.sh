#!/bin/bash 
export PYTHONPATH=./piplib
PORT=$1
FILE=
# NOTE -- test data is in xlayer.py tester
python ./piplib/opensextant/xlayer.py --service-url http://localhost:$PORT/xlayer/rest/process --inputfile $2
