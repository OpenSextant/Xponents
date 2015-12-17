

Follow the notes on OpenSextant/giscore/ page for FileGDB support.

I used the following setup in my IDE:
* Env:  
	export FILEGDB=RELEASE
	export DYLD_LIBRARY_PATH=/path/to/Xponents/Examples/filegdb/osx/build/Release

* Java:
        -Djava.library.path=/path/to/Xponents/Examples/filegdb/osx/build/Release

* Working Dir
	GISCore appears to have some use of "user.dir" if your lib path above is not absolute.
        So, in that case you have to have "filegdb/" in  your current working dir.

* Libraries 

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


* Runtime

Put it all together and you can run the main program 

   java opts ...BasicGeoTemporalProcessing -f FileGDB -i /my/dir  -o /output


