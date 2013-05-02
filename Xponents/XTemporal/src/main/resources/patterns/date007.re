(?#)
(?# USE PYTHON REGEX SYNTAX, TweakRecon[in lib folder] or KODOS to CHECK )
(?# THE <MATCH_GROUP_NAME> and filename must be the same)
(?#)
(?# TARGET> MMM, YYYY  or Month, YYYY   comma optional. 4-digit year required)
(?#)
(?#FLAGS: VERBOSE,IGNORECASE)
(?#)
\b(?P<date007>(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\w*[\s,]+\d{4})\b