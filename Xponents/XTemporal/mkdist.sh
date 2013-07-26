#test both Ant and Maven builds
ant clean
ant
# ant javadoc
mvn install 

cp ./target/*javadoc.jar ./build/
cp ./target/*sources.jar ./build/


mkdir -p ./etc/
cp src/test/resources/log4j.properties ./etc
cp src/main/resources/*.cfg ./etc


PKG=XTemporal-release-10
cd ..
zip -r $PKG \
XTemporal/lib/*jar \
XTemporal/etc \
XTemporal/build.xml  \
XTemporal/READ* \
XTemporal/build/*jar

# for dir in XTemporal/doc/api-java ; do 
#  zip -ru9 $PKG `find $dir -type f | grep -v .svn`
#done

