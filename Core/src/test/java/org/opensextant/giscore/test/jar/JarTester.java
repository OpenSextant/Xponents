package org.opensextant.giscore.test.jar;

import java.io.File;
import java.io.IOException;

import org.opensextant.giscore.DocumentType;
import org.opensextant.giscore.GISFactory;
import org.opensextant.giscore.events.IGISObject;
import org.opensextant.giscore.input.IGISInputStream;

/**
 * A simple main application class to test that the giscore jar is viable. This was written
 * mainly to make sure that the native dynamic library for filegdb support is working, but
 * isn't a bad thing for other support as well.
 * 
 * Test with a built copy of giscore on the classpath and the native support libraries for
 * filegdb on the LD_LIBRARY_PATH (linux) or DYLD_LIBRARY_PATH (mac) or PATH (windows) depending
 * on the platform.
 * 
 * @author drand
 */
public class JarTester {
	public static void main(String[] args) throws IOException {
		IGISInputStream is = GISFactory.getInputStream(DocumentType.FileGDB, new File(args[0]));
		IGISObject object;
		int i = 0;
		do {
			object = is.read();
			i++;
		} while(object !=  null);
		is.close();
		System.out.println("Completed successfully, read " + i + " objects");
	}
}
