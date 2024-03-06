/****************************************************************************************
 *  TestSDEConnect.java
 *
 *  Created: Sep 18, 2009
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

public class TestSDEGdb {
	
	/* @Test */public void try1() throws Exception {
		/* not sure this server is actually up and available right now	
		Properties pset = new Properties();
		pset.setProperty("SERVER", "arcsrvsde.mitre.org");
		pset.setProperty("INSTANCE", "5151");
		pset.setProperty("USER", "gis_prod");
		pset.setProperty("PASSWORD", "gis_prodpw");
		pset.setProperty("VERSION", "SDE.DEFAULT");
		
		IGISInputStream istream = GISFactory.getSdeInputStream(pset, new IAcceptSchema() {
			@Override
			public boolean accept(Schema schema) {
				return ! schema.getName().equals("GIS_VIEW.CRS_FLIGHTS_20090515");
			}
		});

		System.out.println("Feature classes:");
		Iterator<Schema> schemaiter = istream.enumerateSchemata();
		while(schemaiter.hasNext()) {
			System.out.println("" + schemaiter.next());
		}
		IGISObject obj = istream.read();
		while(obj != null) {
			System.out.println("gisobject: " + obj);
			obj = istream.read();
		}
		*/
	}

}
