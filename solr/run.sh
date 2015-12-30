if [ ! -d ./log ] ;  then
  mkdir log
fi
ant proxy start-jetty > ./log/server.log 
