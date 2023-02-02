#!/bin/bash
force=0 

echo "Where files EXIST -- please remove manually to refresh."

echo
echo
echo "Download NGA Geonames - World"
echo "==============================="
FILE=Whole_World.7z
TEST=tmp/$FILE
if [ !  -f "$TEST" ]; then 
    curl -k  https://geonames.nga.mil/geonames/GNSData/fc_files/$FILE -o $TEST
else
    echo "EXISTS $TEST"
fi

TARGET=./tmp/Whole_World.txt
if [ -f "$TEST" -a ! -e $TARGET ]; then
  echo "Unpacking $TEST"
  # On Mac:  7zz;  On Ubuntu: 7za
  UNZIP=7zz
  rm $TARGET
  $UNZIP e -so  $TEST | grep -v "The geographic names in this database" > $TARGET
  echo "NGA Geonames file is at $TARGET"
else
    echo "EXISTS $TARGET ?"
fi

echo
echo
echo "Download USGS National File"
echo "==============================="
USGS_PRODDATE=20210825
FILE=NationalFile.zip
TEST=tmp/$FILE
if [ !  -f "$TEST" ]; then 
    curl -k "https://geonames.usgs.gov/docs/stategaz/$FILE" -o $TEST
else
    echo "EXISTS $TEST"
fi

TARGET=./tmp/NationalFile.txt
if [ -f "$TEST" -a ! -e $TARGET ]; then
  echo "Unpacking $TEST"
  UNZIP=unzip
  $UNZIP -d ./tmp  $TEST
  mv ./tmp/NationalFile_${USGS_PRODDATE}* $TARGET

  echo "USGS file is at $TARGET"
else
  echo "EXISTS $TARGET ?"
fi


echo
echo
echo "==============================="
echo "Download HumData Exchange - PAK"
echo "==============================="
FILE=pak_adm_wfp_20220909_shp.zip
TEST=tmp/$FILE

if [ !  -f "$TEST" ]; then
  echo "Download https://data.humdata.org/dataset/cod-ab-pak"
  echo "  grab FILE $FILE"
  echo "  copy to ./tmp/ here"
  echo "READY?"
  read ans
  if [ "$ans" = "y" -a -f "$TEST" ]; then
    echo "Continuing ... "
    unzip -d tmp/pak_adm_wfp $TEST
  else
    echo "File not found"
  fi
fi

if [ -f $TEST ]; then
    unzip -d ./tmp/pak_adm_wfp/ $TEST
fi

