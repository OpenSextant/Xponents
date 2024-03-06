/****************************************************************************************
 *  WKTToken.java
 *
 *  Created: Jan 12, 2012
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
package org.opensextant.giscore.input.wkt;

/**
 * Token read by the lexer
 * 
 * @author DRAND
 */
public class WKTToken {

	public enum TokenType {
		ID, NUMBER, CHAR
	}
	
	private final TokenType type;
	private String identifier;
	private Double nvalue = 0.0;
	private char cvalue = 0;
	
	public WKTToken(String identifier) {
		if (identifier == null || identifier.trim().length() == 0) {
			throw new IllegalArgumentException(
					"identifier should never be null or empty");
		}
		type = TokenType.ID;
		this.identifier = identifier;
	}
	
	public WKTToken(double value) {
		type = TokenType.NUMBER;
		nvalue = value;
	}
	
	public WKTToken(char ch) {
		type = TokenType.CHAR;
		cvalue = ch;
	}

	/**
	 * @return get the type of the token, never {@code null}
	 */
	public TokenType getType() {
		return type;
	}
	
	/**
	 * @return the identifier
	 */
	public String getIdentifier() {
		return identifier;
	}

	/**
	 * @return the nvalue
	 */
	public Double getDouble() {
		return nvalue;
	}

	/**
	 * @return the cvalue
	 */
	public char getChar() {
		return cvalue;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		switch(type) {
		case ID:
			return identifier.hashCode();
		case NUMBER:
			return nvalue.hashCode();
		default:
			// fall through
		}
		return cvalue;
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
		WKTToken other = (WKTToken) obj;
		if (type != other.type)
			return false;
		switch(type) {
		case ID: {
			if (identifier == null) {
				return other.identifier == null;
			} else {
				return identifier.equals(other.identifier);
			}
		} 
		case NUMBER:
			if (nvalue == null) {
				return other.nvalue == null;
			} else {
				return nvalue.equals(other.nvalue);
			}
		default:
			return cvalue == other.cvalue;
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[WKTToken token='");
		switch(type) {
		case ID:
			sb.append(identifier);
			break;
		case NUMBER:
			sb.append(nvalue.toString());
			break;
		default:
			sb.append(cvalue);
		}
		sb.append("']");
		return sb.toString();
	}
	
	
}
