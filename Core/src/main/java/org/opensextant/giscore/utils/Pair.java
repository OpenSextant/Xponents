/****************************************************************************************
 *  Pair.java
 *
 *  Created: Oct 3, 2011
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2011
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
package org.opensextant.giscore.utils;

/**
 * Represents a pair, a duplicate of the basic functionality from javautil to 
 * avoid including the entire jar. Unlike the original the fields are 
 * protected from modification after create by anything but a subclass.
 * 
 * @author DRAND
 *
 * @param <T1>
 * @param <T2>
 */
public class Pair<T1, T2> {
	private T1 first;
	private T2 second;
	

	public Pair(T1 firstVal, T2 secondVal) {
		setFirst(firstVal);
		setSecond(secondVal);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((getFirst() == null) ? 0 : getFirst().hashCode());
		result = prime * result + ((getSecond() == null) ? 0 : getSecond().hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Pair other = (Pair) obj;
		if (getFirst() == null) {
			if (other.getFirst() != null)
				return false;
		} else if (!getFirst().equals(other.getFirst()))
			return false;
		if (getSecond() == null) {
			return other.getSecond() == null;
		} else return getSecond().equals(other.getSecond());
	}

	public T1 getFirst() {
		return first;
	}

	protected void setFirst(T1 first) {
		this.first = first;
	}

	public T2 getSecond() {
		return second;
	}

	protected void setSecond(T2 second) {
		this.second = second;
	}
}
