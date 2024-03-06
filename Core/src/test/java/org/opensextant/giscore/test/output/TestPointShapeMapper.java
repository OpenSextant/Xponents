/****************************************************************************************
 *  TestPointShapeMapper.java
 *
 *  Created: Jul 22, 2009
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
package org.opensextant.giscore.test.output;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.opensextant.giscore.output.shapefile.PointShapeMapper;
import static org.junit.Assert.assertEquals;

public class TestPointShapeMapper {
	private static final String EX_COM = "http://www.example.com/symbols/";
	public static final short CIRCLE = 0;
	public static final short SQUARE = 1;
	public static final short TRIANGLE = 2;
	public static final short CROSS = 3;
	public static final short STAR = 4;
	
	public static URL turl1;
	public static URL turl2;
	public static URL turl3;
	public static URL turl4;
	public static URL turl5;
	
	public static PointShapeMapper mapper = new PointShapeMapper();
	
	@BeforeClass public static void setup() throws MalformedURLException {
		Map<URL, Short> shapeMap = new HashMap<>();
		
		turl1 = new URL("http://www.example.com/symbols/foo.png");
		turl2 = new URL("http://www.example.com/symbols/bar.png");
		
		shapeMap.put(turl1, TRIANGLE);
		shapeMap.put(turl2, SQUARE);
		
		turl3 = new URL("http://www.example.com/symbols/cross.png");
		turl4 = new URL("http://www.example.com/symbols/star.png");
		turl5 = new URL("http://www.example.com/symbols/zoo.png");
		
		mapper.setShapeMap(shapeMap);
		mapper.setBaseUrl(new URL(EX_COM));
		mapper.setSuffix(".png");
	}
	
	@Test public void test() throws Exception {
		assertEquals(TRIANGLE, mapper.getMarker(turl1));
		assertEquals(SQUARE, mapper.getMarker(turl2));
		assertEquals(CROSS, mapper.getMarker(turl3));
		assertEquals(STAR, mapper.getMarker(turl4));
		assertEquals(CIRCLE, mapper.getMarker(turl5));
	}
	
	@Test public void testUrl() throws Exception {
		assertEquals(turl1, mapper.getURL(TRIANGLE));
		assertEquals(turl2, mapper.getURL(SQUARE));
		assertEquals(turl3, mapper.getURL(CROSS));
		assertEquals(turl4, mapper.getURL(STAR));
		assertEquals(new URL(EX_COM + "circle.png"), mapper.getURL(CIRCLE));
	}
}
