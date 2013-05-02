(?#)
(?# USE PYTHON REGEX SYNTAX, TweakRecon[in lib folder] or KODOS to CHECK )
(?# THE <MATCH_GROUP_NAME> and filename must be the same)
(?#)
(?# TARGET> DATE: MM/DD/YYYY )
(?#)
(?#FLAGS: VERBOSE,IGNORECASE)
(?#)
(DATE:\s*)?\b(?P<date004>([01]?\d)/([0-3]?\d)/([12]\d{3}))\b
