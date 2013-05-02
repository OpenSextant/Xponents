

app=FlexPat 
fpver=1.3
export PATH=.:$PATH

  cd $app
  ant  
  ant javadoc

  cp build/flexpat-${fpver}.jar ../XCoord/lib
  cp build/flexpat-${fpver}.jar ../XTemporal/lib
  cp build/flexpat-${fpver}.jar ../XText/lib
  cd ..

app=XCoord 
  cd $app
  ./mkdist.sh
  cd ..

app=XTemporal 
  cd $app
  ./mkdist.sh
  cd ..

app=XText
  cd $app
  ./mkdist.sh
  cd ..

PKG=Xponents-release-src-v2
zip -r9 $PKG *.txt *.md *.xml

# add others...
for xponent in FlexPat XCoord XText XTemporal ; do
  zip -ru9 $PKG `find $xponent -type f | grep -v .svn | grep -v "/target/" | grep -v "lib/*.class" | grep -v .DS_Store `
done

if [ -d "./dist" ] ; then rm -r dist; fi;

mkdir -p dist

cp */target/*jar dist/


