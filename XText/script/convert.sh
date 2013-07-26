export LANG=en_US

ant -f ./testing.xml  -Dinput=$1 -Doutput=$2 convert
