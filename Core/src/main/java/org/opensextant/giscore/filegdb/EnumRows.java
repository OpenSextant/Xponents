/****************************************************************************************
 *  EnumRows.java
 *
 *  Created: Nov 9, 2012
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2012
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
package org.opensextant.giscore.filegdb;

import java.util.Iterator;

/**
 * The row enumerator, extended to act as a Java iterator
 * @author DRAND
 *
 */
public class EnumRows extends GDB implements Iterator<Row> {
	private Row nextBuffer = null;
	private Table table;
	
	@Override
	public boolean hasNext() {
		if (nextBuffer == null) {
			nextBuffer = next1();
			if (nextBuffer != null) {
				nextBuffer.table = table;
			}
		}
		return nextBuffer != null;
	}
	
	public Row next() {
		if (hasNext()) {
			Row val = nextBuffer;
			nextBuffer = null;
			return val;
		} else {
			return null;
		}
		
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
	/**
	 * @return the next row
	 */
	private native Row next1();
	
	/**
	 * Close
	 */
	public native void close();

	protected void setTable(Table table) {
		this.table = table;
	}
}
