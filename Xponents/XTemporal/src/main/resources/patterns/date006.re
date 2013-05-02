(?#)
(?# USE PYTHON REGEX SYNTAX, TweakRecon[in lib folder] or KODOS to CHECK )
(?# THE <MATCH_GROUP_NAME> and filename must be the same)
(?#)
(?# TARGET> MMM DD, YYYY   or MMM DD YYYY,  MMM DD, YY, etc.)
(?#)
(?#FLAGS: VERBOSE,IGNORECASE)
(?#)
\b(?P<date006>(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*\s+[0-2]?\d[,\s]+\d{4})\b
