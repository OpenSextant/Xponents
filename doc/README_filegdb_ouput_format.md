
GIS File Input/Output
=====================

OpenSextant (Xponents, specifically) has had the ability to output ESRI FileGDB directly
for many years.  However, its supportability is limited. You can see the Esri FileGDB native
library referenced below -- this was work done c.2012-2015.  These binaries may still work.

What has worked well, though, is support for GeoJSON, GeoCSV (CSV with lat/lon), CSV, KML, 
and Shapefile.   The notes below focus more on the FileGDB support here with Xponents. 
The [README Examples](./README_Examples.md) details how to call the demo to output these 
file formats.

Consult the [GISCore](https://github.com/OpenSextant/giscore) developer page for the 
complete details on setting up your runtime environemnt. 

**Bottom Line:** FileGDB support is not maintained or well-tested. But the rest is. Good luck.


**Invocation**

In the Xponents Examples, you'll be working towards this type of invocation:

```shell

	./script/xponents-demo.sh geotemp  <ARGS>
	
	# Under the hood you'll need to pay attention to additional jvmArgs noted below
	#               geotemp                    <ARGS>...
	java jvmArgs ...BasicGeoTemporalProcessing -f FileGDB -i /my/dir  -o /output
```


**Environ:**

```shell

	export FILEGDB=RELEASE
	export DYLD_LIBRARY_PATH=/path/to/Xponents/Examples/filegdb/osx/build/Release

	JVM tuning: Add jvmArg as follows:
        -Djava.library.path=/path/to/Xponents/Examples/filegdb/osx/build/Release
	
```

**Working Dir:**

GISCore appears to have some use of `user.dir` if your lib path above is not absolute.
So, in that case you have to have "filegdb/" in  your current working dir.


**Libraries:** 

```
OSX contents:

filegdb/osx/build/Release/libfilegdb.dylib          -- GISCore build
filegdb/osx/build/Release/libfgdbunixrtl.dylib      -- ESRI FileGDB API
filegdb/osx/build/Release/libFileGDBAPI.dylib       -- ESRI FileGDB API
filegdb/README_filegdb.md

filegdb/linux/libfgdbunixrtl.so
filegdb/linux/libFileGDBAPI.so
filegdb/linux/dist.... (GISCore build goes here.)

filegdb/win64/filegdb.dll
filegdb/win64/filegdb.lib
filegdb/win64/... ( ditto, ESRI and GISCore builds for windows go here )
...
```


