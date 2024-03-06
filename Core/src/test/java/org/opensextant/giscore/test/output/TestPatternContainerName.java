/****************************************************************************************
 *  TestPatternContainerName.java
 *
 *  Created: May 25, 2010
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2010
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
package org.opensextant.giscore.test.output;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensextant.giscore.events.Feature;
import org.opensextant.giscore.events.Schema;
import org.opensextant.giscore.geometry.Circle;
import org.opensextant.giscore.geometry.Geometry;
import org.opensextant.giscore.geometry.Line;
import org.opensextant.giscore.geometry.Point;
import org.opensextant.giscore.output.FeatureKey;
import org.opensextant.giscore.output.gdb.PatternContainerNameStrategy;
import static org.junit.Assert.assertEquals;

public class TestPatternContainerName {
	@Test public void testP1() throws Exception {
		Map<Class<? extends Geometry>, String> patterns = new HashMap<>();
		patterns.put(Point.class, "{0}-PNT");
		patterns.put(Line.class, "LINE-{0}");
		PatternContainerNameStrategy pcns = new PatternContainerNameStrategy(patterns, '-');
		
		List<String> path = new ArrayList<>();
		path.add("A");
		path.add("B");
		Schema schema = new Schema();
		FeatureKey key = new FeatureKey(schema, "", Point.class, Feature.class);
		String result = pcns.deriveContainerName(path, key);
		assertEquals("A-B-PNT", result);
		
		key = new FeatureKey(schema, "", Line.class, Feature.class);
		result = pcns.deriveContainerName(path, key);
		assertEquals("LINE-A-B", result);
		
		key = new FeatureKey(schema, "", Circle.class, Feature.class);
		result = pcns.deriveContainerName(path, key);
		assertEquals("A-B-Circle", result);
		
		patterns.put(Point.class, "X X X {0} PNT");
		new PatternContainerNameStrategy(patterns, '-');
		key = new FeatureKey(schema, "", Point.class, Feature.class);
		result = pcns.deriveContainerName(path, key);
		assertEquals("X-X-X-A-B-PNT", result);
		
	}
}
