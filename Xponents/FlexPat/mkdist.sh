ant clean
ant
ant javadoc

PKG=FlexPat-release-12
cd ..
zip -r $PKG \
FlexPat/lib/*jar \
FlexPat/build.xml  \
FlexPat/READ* \
FlexPat/build/*jar 

for dir in FlexPat/doc FlexPat/src ; do 
  zip -ru9 ${PKG} `find $dir -type f | grep -v .svn`
done

