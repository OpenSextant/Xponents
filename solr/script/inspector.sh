#!/bin/bash
echo "Admin Code helper"

if [ ! -d "$XPONENTS/solr" ] ; then
  echo "Please load ../dev.env  and or set the XPONENTS variable to your checkout"
  exit
fi
DB=$XPONENTS/solr/tmp/master_gazetteer.sqlite
countries=`sqlite3 $DB  "select distinct(cc) from placenames;"`
for cc in  $countries ; do 
   echo "COUNTRY=$cc"
   sqlite3 $DB "select adm1,count(1) as CNT from placenames where cc='$cc' group by adm1;"
done
