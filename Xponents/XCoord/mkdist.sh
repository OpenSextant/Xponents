mvn install 

ant clean
ant
# For now Javadoc is generated using Maven.
# ant javadoc
ant -f testing.xml

cp ./target/*javadoc.jar ./build/
cp ./target/*sources.jar ./build/


mkdir -p ./etc/
cp src/test/resources/log4j.properties ./etc
cp src/main/resources/geocoord_regex.cfg ./etc

PKG=XCoord-release-16
cd ..
zip -r $PKG \
XCoord/lib/*jar \
XCoord/etc/geocoord_regex.cfg \
XCoord/etc/log4j.properties \
XCoord/*.* \
XCoord/build/*jar 

for dir in XCoord/doc XCoord/results XCoord/src ; do 
  zip -ru9 ${PKG} `find $dir -type f | grep -v .svn`
done

