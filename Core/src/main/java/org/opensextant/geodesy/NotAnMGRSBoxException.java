/***************************************************************************
 * $Id$
 * 
 * (C) Copyright MITRE Corporation 2008
 *
 * The program is provided "as is" without any warranty express or implied,
 * including the warranty of non-infringement and the implied warranties of
 * merchantability and fitness for a particular purpose.  The Copyright
 * owner will not be liable for any damages suffered by you as a result of
 * using the Program.  In no event will the Copyright owner be liable for
 * any special, indirect or consequential damages or lost profits even if
 * the Copyright owner has been advised of the possibility of their
 * occurrence.
 *
 ***************************************************************************/

package org.opensextant.geodesy;

/**
 * A specialized exception for MGRS conversions.
 * 
 * @author jgibson
 */
public class NotAnMGRSBoxException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of {@code NotAnMGRSBoxException} with the
	 * specified detail message.
	 * 
     * @param msg the detail message.
     */
    public NotAnMGRSBoxException(final String msg) {
        super(msg);
    }
}
