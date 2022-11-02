#!/bin/bash

echo "Operating on a Copy of master db?  "
read CHECK

if [ $CHECK != "y" ]; then
  echo "Answer y to proceed... exiting."
  exit
fi

DB=$1
if [ -f $DB -a -e $DB ]; then 
  # Drop odd ball INDEXES; Delete duplicates and vacuum remaining.
  # Print out a count of remaining.
  sqlite3 $DB  << EOF
    delete from placenames where duplicate=1;
    DROP index IF EXISTS so_idx ;
    DROP index IF EXISTS dup_idx;

    ALTER TABLE "placenames" drop column "name_bias";
    ALTER TABLE "placenames" drop column "id_bias";
    ALTER TABLE "placenames" drop column "search_only";
    ALTER TABLE "placenames" drop column "duplicate";

    VACUUM;

    ALTER TABLE "placenames" add column "duplicate" BIT DEFAULT 0;
    ALTER TABLE "placenames" add column "name_bias" INTEGER DEFAULT 0;
    ALTER TABLE "placenames" add column "id_bias" INTEGER DEFAULT 0;
    ALTER TABLE "placenames" add column "search_only" BIT DEFAULT 0;

    select count(1) from placenames;
EOF

else 
  echo "Did not find sqlite file: '$DB'"
fi

