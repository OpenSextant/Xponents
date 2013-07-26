ant clean
ant
ant jar
#ant javadoc

mvn install

cp ./target/*javadoc.jar ./build/
cp ./target/*sources.jar ./build/

mkdir -p ./etc/
cp src/test/resources/log4j.properties ./etc

PKG=XText-release-10
cd ..
zip -r $PKG \
XText/lib/*jar \
XText/build/*jar \
XText/*.* 

for dir in XText/doc XText/src  XText/etc  XText/script; do 
  zip -ru9 $PKG `find $dir -type f | grep -v .svn`
done

