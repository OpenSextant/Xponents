/****************************************************************************************
 *  TestPrj.java
 *
 *  Created: Jul 28, 2009
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2009
 *
 *  The program is provided "as is" without any warranty express or implied, including
 *  the warranty of non-infringement and the implied warranties of merchantibility and
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any
 *  damages suffered by you as a result of using the Program.  In no event will the
 *  Copyright owner be liable for any special, indirect or consequential damages or
 *  lost profits even if the Copyright owner has been advised of the possibility of
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.giscore.test.input;

import org.junit.Test;
import org.opensextant.giscore.input.shapefile.PrjReader;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test WKT format utility
 * 
 * @author DRAND
 */
public class TestPrj {
	String WKT1 = "PROJCS[\"NAD_1983_StatePlane_Pennsylvania_South_FIPS_3702_Feet\","
			+ "GEOGCS[\"GCS_North_American_1983\","
			+ "DATUM[\"D_North_American_1983\","
			+ "SPHEROID[\"GRS_1980\",6378137.0,298.257222101]],"
			+ "PRIMEM[\"Greenwich\",0.0],"
			+ "UNIT[\"Degree\",0.0174532925199433]],"
			+ "PROJECTION[\"Lambert_Conformal_Conic\"],"
			+ "PARAMETER[\"False_Easting\",1968500.0],"
			+ "PARAMETER[\"False_Northing\",0.0],"
			+ "PARAMETER[\"Central_Meridian\",-77.75],"
			+ "PARAMETER[\"Standard_Parallel_1\",39.93333333333333],"
			+ "PARAMETER[\"Standard_Parallel_2\",40.96666666666667],"
			+ "PARAMETER[\"Latitude_Of_Origin\",39.33333333333334],"
			+ "UNIT[\"Foot_US\",0.3048006096012192]]";

	String WKT2 = "GEOGCS[\"GCS_North_American_1983\","
			+ "DATUM[\"D_North_American_1983\","
			+ "SPHEROID[\"GRS_1980\",6378137,298.257222101]],"
			+ "PRIMEM[\"Greenwich\",0],"
			+ "UNIT[\"Degree\",0.017453292519943295]]";
	
	String WKT3 = "PROJCS[\"WGS_1984_UTM_Zone_35S\",GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137,298.257223563]],PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.017453292519943295]],PROJECTION[\"Transverse_Mercator\"],PARAMETER[\"Central_Meridian\",27],PARAMETER[\"Latitude_Of_Origin\",0],PARAMETER[\"Scale_Factor\",0.9996],PARAMETER[\"False_Easting\",500000],PARAMETER[\"False_Northing\",10000000],UNIT[\"Meter\",1]]";
	
	/* @Test */public void test1() throws Exception {
		PrjReader reader = new PrjReader(WKT1);
		PrjReader.Entry ent = reader.getEntry("PROJCS");
		assertNotNull(ent);
		ent = reader.getEntry("PROJCS","GEOGCS");
		assertNotNull(ent);
		ent = reader.getEntry("PROJCS","GEOGCS","DATUM");
		assertNotNull(ent);
		ent = reader.getEntry("FOO");
		assertNull(ent);
		ent = reader.getEntry("PROJCS","UNIT");
		assertNotNull(ent);
		
	}
	
	/* @Test */public void test2() throws Exception {
		PrjReader reader = new PrjReader(WKT2);
		PrjReader.Entry ent = reader.getEntry("GEOGCS");
		assertNotNull(ent);
		ent = reader.getEntry("GEOGCS","DATUM");
		assertNotNull(ent);
		ent = reader.getEntry("GEOGCS","UNIT");
		assertNotNull(ent);
	}
	
	/* @Test */public void test3() throws Exception {
		PrjReader reader = new PrjReader(WKT3);
		PrjReader.Entry ent = reader.getEntry("PROJCS","GEOGCS");
		assertNotNull(ent);
		ent = reader.getEntry("PROJCS","GEOGCS","DATUM");
		assertNotNull(ent);
		ent = reader.getEntry("PROJCS","GEOGCS","UNIT");
		assertNotNull(ent);
	}
}
