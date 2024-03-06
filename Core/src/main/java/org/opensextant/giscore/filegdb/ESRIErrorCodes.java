/****************************************************************************************
 *  ESRIErrorCodes.java
 *
 *  Created: Apr 9, 2013
 *
 *  @author DRAND
 *
 *  (C) Copyright MITRE Corporation 2013
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translate from numeric values to string messages if possible.
 * 
 * @author DRAND
 */
public class ESRIErrorCodes {

	private static final Pattern regex;
	private static final Map<Integer, String> mm = Collections.synchronizedMap(new HashMap<>());
	private static final int E_FAIL = 0x80004005;
	
	static {
		regex = Pattern.compile("message is\\s+([\\p{Alpha}\\s])\\s+error number is\\s+(\\d+)");
		
		add(-2147209212, "Domain name already in use");
		add(-2147209215, "The domain was not found");
		add(-2147024809, "Invalid function arguments");
		add(-2147211775, "The item was not found");
		add(-2147219118, "A reqested row object could not be located");
		add(-2147219707, "The Fields collection contained multiple OID fields");
		add(-2147219879, "The field is not nullable");
		add(-2147219884, "The Field already exists");
		add(-2147219885, "An expected Field was not found or could not be retrieved properly");
		add(-2147220109, "FileGDB compression is not installed");
		add(-2147220645, "Invalid name");
		add(-2147220653, "The table already exists");
		add(-2147220655, "The table was not found");
		add(-2147220733, "The dataset already exists");
		add(-2147220985, "An invalid SQL statement was used");
		add(-2147024894, "Failed to connect to datbase, GDB_SystemCatalog");
	}
	
	/**
	 * Add an entry
	 * @param errorCode
	 * @param message
	 */
	private static void add(int errorCode, String message) {
		mm.put(errorCode, message);
	}
	
	/**
	 * Translate message of form "message is\s+([\p{Alpha}\s])\s+error number is\s+(\d+)"
	 * To "Error code ####: esri message" if we find a match. Otherwise leave alone.
	 * 
	 * @param message
	 * @return translated message
	 */
	public String translate(String message) {
		if (message != null && !message.isEmpty()) {
			Matcher m = regex.matcher(message);

			if (m.find()) {
				//String orig = m.group(1);
				int code = Integer.parseInt(m.group(2));
				String newm = mm.get(code);
				if (newm != null) {
					return "problem: " + newm + " [code: " + m.group(2) + "]";
				}
			}
		}
		
		return message;
	}
	
	/**
	 * Rethrows the passed exception after translating the contained
	 * message. The thrown exception will be a IllegalStateException.
	 * 
	 * @param ex Original Exception, never null
	 * @throws java.lang.NullPointerException
	 * 				if ex argument is null
	 * @throws java.lang.IllegalStateException
	 * 				wrapping original ESRI exception
	 */
	public void rethrow(Exception ex) {
		String message = ex.getLocalizedMessage();
		message = translate(message);
		throw new IllegalStateException(message, ex);
	}

}
