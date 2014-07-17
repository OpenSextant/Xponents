export LANG=en_US

# ant -f ./xtext-test.xml -Dinput=$1 -Doutput=$2 convert

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

#echo "Usage -- run as ./script/convert.sh"
echo JAVA_HOME =  $JAVA_HOME
echo See README_convert.txt  for more detail.

input=$1
output=$2
crawl_output=$3
shift
shift
# echo $*
java -Xmx512m  -classpath "$basedir/lib/*" org.opensextant.xtext.XText  -i "$input" -o "$output" -x "$crawl_output"
