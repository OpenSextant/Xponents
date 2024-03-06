/****************************************************************************************
 *  TestSimpleObjectIO.java
 *
 *  Created: Mar 24, 2009
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
package org.opensextant.giscore.test.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.Test;
import org.opensextant.giscore.events.SimpleField;
import org.opensextant.giscore.utils.IDataSerializable;
import org.opensextant.giscore.utils.IObjectCacher;
import org.opensextant.giscore.utils.SimpleObjectInputStream;
import org.opensextant.giscore.utils.SimpleObjectOutputStream;
import static org.junit.Assert.*;

/**
 * @author DRAND
 * 
 */
public class TestSimpleObjectIO {
	public static class TestSubObject implements IDataSerializable {
		String l1;
		int i1;
		
		/* (non-Javadoc)
		 * @see org.opensextant.giscore
.utils.IDataSerializable#readData(org.opensextant.giscore
.utils.SimpleObjectInputStream)
		 */
		public void readData(SimpleObjectInputStream in) throws IOException,
				ClassNotFoundException, InstantiationException,
				IllegalAccessException {
			l1 = in.readString();
			i1 = in.readInt();
			
		}

		/* (non-Javadoc)
		 * @see org.opensextant.giscore
.utils.IDataSerializable#writeData(org.opensextant.giscore
.utils.SimpleObjectOutputStream)
		 */
		public void writeData(SimpleObjectOutputStream out) throws IOException {
			out.writeString(l1);
			out.writeInt(i1);
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}
	}
	
	public static class TestClass implements IDataSerializable {
		boolean b1;
		List<TestSubObject> subs = new ArrayList<>();
		
		/* (non-Javadoc)
		 * @see org.opensextant.giscore
.utils.IDataSerializable#readData(org.opensextant.giscore
.utils.SimpleObjectInputStream)
		 */
		@SuppressWarnings("unchecked")
		public void readData(SimpleObjectInputStream in) throws IOException,
				ClassNotFoundException, InstantiationException,
				IllegalAccessException {
			b1 = in.readBoolean();
			subs = (List<TestSubObject>) in.readObjectCollection();
		}

		/* (non-Javadoc)
		 * @see org.opensextant.giscore
.utils.IDataSerializable#writeData(org.opensextant.giscore
.utils.SimpleObjectOutputStream)
		 */
		public void writeData(SimpleObjectOutputStream out) throws IOException {
			out.writeBoolean(b1);
			out.writeObjectCollection(subs);
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}
	}
	
	@Test
	public void testBasicValueIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		DataOutputStream dos = new DataOutputStream(bos);
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(dos);
		
		soos.writeBoolean(false);
		soos.writeBoolean(true);
		soos.writeDouble(1.2);
		soos.writeDouble(2.5);
		soos.writeInt(10);
		soos.writeInt(5);
		soos.writeShort((short) 2);
		soos.writeShort((short) 4);
		soos.writeString("How now");
		soos.writeString(null);
		
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		
		assertFalse(sois.readBoolean());
		assertTrue(sois.readBoolean());
		assertEquals(1.2, sois.readDouble(), 1e05);
		assertEquals(2.5, sois.readDouble(), 1e05);
		assertEquals(10, sois.readInt());
		assertEquals(5, sois.readInt());
		assertEquals(2, sois.readShort());
		assertEquals(4, sois.readShort());
		assertEquals("How now", sois.readString());
		assertNull(sois.readString());
		
		sois.close();
	}
	
	@Test
	public void testScalarValueIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
		
		soos.writeScalar(false);
		soos.writeScalar(true);
		soos.writeScalar(1.2);
		soos.writeScalar(2.5);
		soos.writeScalar(10);
		soos.writeScalar(5L);
		soos.writeScalar((short) 2);
		soos.writeScalar((short) 4);
		soos.writeScalar("How now");
		soos.writeScalar(null);
		
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		
		assertFalse((Boolean) sois.readScalar());
		assertTrue((Boolean) sois.readScalar());
		assertEquals(1.2, (Double) sois.readScalar(), 1e05);
		assertEquals(2.5, (Double) sois.readScalar(), 1e05);
		assertEquals(10, sois.readScalar());
		assertEquals(5L, sois.readScalar());
		assertEquals((short) 2, sois.readScalar());
		assertEquals((short) 4, sois.readScalar());
		assertEquals("How now", (String) sois.readScalar());
		assertNull(sois.readScalar());
		
		sois.close();
	}
	
	@Test
	public void testSingleSimpleObjectIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
		
		TestSubObject s1 = new TestSubObject();
		s1.i1 = 10;
		s1.l1 = "Label 1";
		soos.writeObject(s1);
		
		TestSubObject s2 = new TestSubObject();
		s2.i1 = -10;
		s2.l1 = "Label 2";
		soos.writeObject(s2);
		
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		
		TestSubObject r1 = (TestSubObject) sois.readObject();
		assertEquals(s1, r1);
		
		TestSubObject r2 = (TestSubObject) sois.readObject();
		assertEquals(s2, r2);
		
		sois.close();
	}
	
	@SuppressWarnings("unchecked")
	@Test public void testCollectionIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
		
		List<TestSubObject> vals = new ArrayList<>();
		for(int i = 0; i < 100; i++) {
			TestSubObject s = new TestSubObject();
			s.i1 = i;
			s.l1 = "Label " + i;
			vals.add(s);
		}
		soos.writeObjectCollection(vals);
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		
		vals = (List<TestSubObject>) sois.readObjectCollection();
		assertEquals(100, vals.size());
		
		sois.close();
	}
	
	@Test public void testNestedObjectIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos);
		
		TestClass tc1 = new TestClass();
		for(int i = 0; i < 10; i++) {
			TestSubObject s = new TestSubObject();
			s.i1 = i;
			s.l1 = "Label " + i;
			tc1.subs.add(s);
		}
		soos.writeObject(tc1);
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		
		TestClass rc1 = (TestClass) sois.readObject();
		assertEquals(tc1, rc1);
		
		sois.close();
	}
	
	@Test public void testCachedObjectIO() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(2000);
		IObjectCacher cacher = new IObjectCacher() {
			private final Map<Object, Long> objs = new HashMap<>();
			private final AtomicLong counter = new AtomicLong();
			@Override
			public void addToCache(Object outputObject) {
				objs.put(outputObject, counter.incrementAndGet());
			}

			@Override
			public Long getObjectOutputReference(Object outputObject) {
				return objs.get(outputObject);
			}

			@Override
			public boolean hasBeenCached(Object outputObject) {
				return getObjectOutputReference(outputObject) != null;
			}

			@Override
			public boolean shouldCache(Object outputObject) {
				return outputObject instanceof SimpleField;
			}
			
		};
		SimpleObjectOutputStream soos = new SimpleObjectOutputStream(bos, cacher);
		SimpleField fields[] = new SimpleField[40];
		
		for(int i = 0; i < fields.length; i++) {
			String label = "test" + i%5;
			fields[i] = new SimpleField(label);
			soos.writeObject(fields[i]);
		}
		soos.close();
		
		ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
		SimpleObjectInputStream sois = new SimpleObjectInputStream(bis);
		for (SimpleField field : fields) {
			SimpleField test = (SimpleField) sois.readObject();
			assertEquals(field, test);
		}
		sois.close();
	}
}

