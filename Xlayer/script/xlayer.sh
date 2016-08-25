script=`dirname $0;`
basedir=`cd -P $script/..; echo $PWD`

echo JAVA_HOME =  $JAVA_HOME
echo $*
cd $basedir
logfile=$basedir/log/stderr.log
stdout=$basedir/log/xlayer.log
XPONENTS_SOLR=/mitre/xponents-solr

nohup java -Dopensextant.solr=$XPONENTS_SOLR -Xmx2g  -Djava.util.logging.config.file=./etc/logging.properties  \
   -classpath "$basedir/etc:$XPONENTS_SOLR/gazetteer/conf:$basedir/lib/*" org.opensextant.xlayer.server.XlayerServer   $*  2>$logfile > $stdout &


