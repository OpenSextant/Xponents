if [ ! -d ./log ] ;  then
  mkdir log
fi
ant -f build-gazetteer.xml _index > log/build.log & 
