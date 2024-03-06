/****************************************************************************************
 *  TestFeatureSorter.java
 *
 *  Created: Feb 10, 2009
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
package org.opensextant.giscore.test;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import org.junit.Test;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.LinearRing;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.FeatureSorter;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.ObjectBuffer;

/**
 * @author DRAND
 * 
 */
public class TestFeatureSorter extends TestGISBase {
	@Test
	public void testSort1() throws Exception {
		FeatureSorter sorter = new FeatureSorter();
		
		String names[] = {"hole", "distance"};
		Object values[];
		for(int i = 0; i < 3; i++) {
			values = new Object[2];
			values[0] = random.nextInt(18) + 1;
			values[1] = random.nextInt(40) * 10;
			Feature f = createFeature(Point.class, names, values);
			sorter.add(f, null);
		}
		for(int i = 0; i < 10; i++) {
			values = new Object[2];
			values[0] = random.nextInt(18) + 1;
			values[1] = random.nextInt(40) * 10;
			Feature f = createFeature(Line.class, names, values);
			sorter.add(f, null);
		}
		for(int i = 0; i < 5; i++) {
			values = new Object[2];
			values[0] = random.nextInt(18) + 1;
			values[1] = random.nextInt(40) * 10;
			Feature f = createFeature(LinearRing.class, names, values);
			sorter.add(f, null);
		}
		int totalcount = 0;
		Assert.assertEquals(3, sorter.keys().size());
		for(FeatureKey key : sorter.keys()) {
			ObjectBuffer buffer = sorter.getBuffer(key);
			Assert.assertNotNull(buffer);
			Assert.assertTrue(buffer.count() > 0);
			totalcount += buffer.count();
		}
		Assert.assertEquals(18, totalcount);
		
		// Read data from sorter, counting elements retrieved
		int read = 0;
		for(FeatureKey key : sorter.keys()) {
			ObjectBuffer buf = sorter.getBuffer(key);
			IDataSerializable ser = buf.read();
			while(ser != null) {
				read++;
				ser = buf.read();
			}
		}
		Assert.assertEquals(18, read);
		sorter.cleanup(); // Delete temp files
	}
	
	@Test
	public void testSort2() throws Exception {
		Schema s1 = new Schema();
		SimpleField field = new SimpleField("category");
		field.setDisplayName("Category");
		field.setType(SimpleField.Type.STRING);
		s1.put(field);
		field.setName("subcategory");
		field.setDisplayName("Sub Category");
		field.setType(SimpleField.Type.STRING);
		s1.put(field);
		
		Schema s2 = new Schema();
		field = new SimpleField("phylum");
		field.setDisplayName("Phylum");
		field.setType(SimpleField.Type.STRING);
		s1.put(field);
		field.setName("species");
		field.setDisplayName("Species");
		field.setType(SimpleField.Type.STRING);
		s1.put(field);
		field.setName("year");
		field.setDisplayName("Year discovered");
		field.setType(SimpleField.Type.INT);
		s1.put(field);
		
		FeatureSorter sorter = new FeatureSorter();
		Map<String,Object> values = new HashMap<>();
		for(int i = 0; i < 20; i++) {
			values.put("category", "building");
			values.put("subcategory", "house");
			Feature f = createFeature(Point.class, s1, values);
			sorter.add(f, null);
		}
		for(int i = 0; i < 20; i++) {
			values.put("phylum", "Mollusca");
			values.put("species", "Oyster");
			values.put("year", -1000);
			Feature f = createFeature(Point.class, s2, values);
			sorter.add(f, null);
		}
		for(int i = 0; i < 20; i++) {
			values.put("phylum", "Cordata");
			values.put("species", "Cobra");
			values.put("year", -4000);
			Feature f = createFeature(Line.class, s2, values);
			sorter.add(f, null);
		}		
		int totalcount = 0;
		Assert.assertEquals(3, sorter.keys().size());
		for(FeatureKey key : sorter.keys()) {
			ObjectBuffer buffer = sorter.getBuffer(key);
			Assert.assertNotNull(buffer);
			Assert.assertTrue(buffer.count() > 0);
			totalcount += buffer.count();
		}
		Assert.assertEquals(60, totalcount);
		sorter.cleanup(); // Delete temp files
	}
}
