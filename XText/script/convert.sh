export LANG=en_US

# ant -f ./xtext-test.xml -Dinput=$1 -Doutput=$2 convert

script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

# echo $JAVA_HOME
#echo Using `which java`
#echo $basedir

input=$1
output=$2
shift
shift
# echo $*
java -Xmx512m  -classpath "$basedir/lib/*" org.opensextant.xtext.XText  -i "$input" -o "$output" $*
