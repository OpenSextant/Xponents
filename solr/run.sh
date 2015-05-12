if [ ! -d ./log ] ;  then
  mkdir log
fi
ant -f ./build-gazetteer.xml proxy start-jetty > ./log/server.log 
