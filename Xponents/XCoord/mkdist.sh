mvn install 

ant clean
ant
# For now Javadoc is generated using Maven.
# ant javadoc
ant -f testing.xml

cp ./target/*javadoc.jar ./build/
cp ./target/*sources.jar ./build/

cp src/main/resources/geocoord_regex.cfg ./doc

PKG=XCoord-release-16
cd ..
zip -r $PKG \
XCoord/lib/*jar \
XCoord/*.* \
XCoord/build/*jar 

for dir in XCoord/doc XCoord/results XCoord/src ; do 
  zip -ru9 ${PKG} `find $dir -type f | grep -v .svn`
done

