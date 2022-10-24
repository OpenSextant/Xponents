#!/bin/bash
echo
echo "Download NGA Geonames - World"
echo "==============================="
FILE=Whole_World.7z
TEST=tmp/$FILE
if [ !  -f "$TEST" ]; then 
    curl -k  https://geonames.nga.mil/geonames/GNSData/fc_files/$FILE -o $TEST
else
    echo "EXISTS - not overwriting"
fi

if [ -f "$TEST" ]; then
  echo "Unpacking $TEST"
  # On Mac:  7zz;  On Ubuntu: 7za
  UNZIP=7zz
  TARGET=./tmp/Whole_World.txt
  (cd ./tmp && $UNZIP e -so  $TEST | grep -v "The geographic names in this database" > Whole_World.txt)
  echo "NGA Geonames file is at $TARGET"
fi

echo
echo "Download USGS National File"
echo "==============================="
USGS_PRODDATE=20210825
FILE=NationalFile.zip
TEST=tmp/$FILE
if [ !  -f "$TEST" ]; then 
    curl -k "https://geonames.usgs.gov/docs/stategaz/$FILE" -o $TEST
else
    echo "EXISTS - not overwriting"
fi

if [ -f "$TEST" ]; then
  echo "Unpacking $TEST"
  UNZIP=unzip
  $UNZIP -d ./tmp  $TEST
  TARGET=./tmp/NationalFile.txt
  mv ./tmp/NationalFile_${USGS_PRODDATE}* $TARGET

  echo "USGS file is at $TARGET"
fi
