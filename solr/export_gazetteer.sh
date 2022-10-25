#!/bin/bash

DB=$1
if [ -f $DB -a -e $DB ]; then 
  # Drop odd ball INDEXES; Delete duplicates and vacuum remaining.
  # Print out a count of remaining.
  sqlite3 $DB  << EOF
    delete from placenames where duplicate=1;
    drop index IF EXISTS so_idx ;
    drop index IF EXISTS dup_idx;
    drop index IF EXISTS plid_idx;
    VACUUM;

    select count(1) from placenames;
EOF

else 
  echo "Did not find sqlite file: '$DB'"
fi

