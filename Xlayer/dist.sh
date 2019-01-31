VER=3.0
REL=../dist/Xponents-$VER
TARGET_LIB=$REL/xlayer-lib/

# Prepare distribution, to add Xlayer
# ./lib/          -- has all Xponents libs
# ./xlayer-lib/   -- has only Xlayer specific libs
# ./doc/          README on Xlayer
# ./test          test scripts for xlayer
# ./script        Shell/BAT script for xlayer
#
mkdir -p $TARGET_LIB

# clean and gather dependencies.
mvn clean install
mvn dependency:copy-dependencies

cp ./script/*.* $REL/script/

# Copy configuration items to release
for CFG in xlayer-banner.txt logging.properties; do 
  cp ./etc/$CFG $REL/etc/
done

# Copy select libraries to release.
for lib in json-20160212.jar log4j* org.restlet* simple-5.1.6.jar ; do 
  cp ./lib/$lib $TARGET_LIB/
done

cp ./target/*.jar $TARGET_LIB/

# Documentation and testing.
cp -r ./doc $REL/
cp -r ./test $REL/
