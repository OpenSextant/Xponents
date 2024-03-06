/****************************************************************************************
 *  Latitude.java
 *
 *  Created: Dec 28, 2006
 *
 *  @author Paul Silvey
 *
 *  (C) Copyright MITRE Corporation 2007
 *
 *  The program is provided "as is" without any warranty express or implied, including 
 *  the warranty of non-infringement and the implied warranties of merchantability and 
 *  fitness for a particular purpose.  The Copyright owner will not be liable for any 
 *  damages suffered by you as a result of using the Program.  In no event will the 
 *  Copyright owner be liable for any special, indirect or consequential damages or 
 *  lost profits even if the Copyright owner has been advised of the possibility of 
 *  their occurrence.
 *
 ***************************************************************************************/
package org.opensextant.geodesy;

/**
 * The Latitude class extends Angle to restrict its value to be between -90&deg; and +90&deg;,
 * and to provide parsing and formatting with hemisphere indicator suffix ('N' or 'S')                 ser
 * instead of numeric sign.
 */
public class Latitude extends Angle {
	
	private static final long serialVersionUID = 1L;

    private static final double PI_OVER_2 = Math.PI / 2.0;

    // Base initializer is extended to perform additional range restriction test
    protected void init(double value, int unitType) {
        super.init(value, unitType);
        // Latitudes are Angles restricted between -90 and +90 degrees
        if (this.inRadians < -PI_OVER_2 || PI_OVER_2 < this.inRadians)
            throw new IllegalArgumentException("Latitude value exceeds pole value");
    }

    /**
     * This constructor takes latitude angle value and units designator.
     *
     * @param value    Latitude angular value in specified units (Angle.DEGREES or Angle.RADIANS)
     * @param unitType Angle.DEGREES or Angle.RADIANS
     * @throws IllegalArgumentException error if units or value are out of range
     */
    public Latitude(double value, int unitType) {
        this.init(value, unitType);
    }

    /**
     * This constructor takes a latitude formatted as a string.  It looks for a 'N' or 'S'
     * direction indicator suffix character and sets the numeric sign accordingly (N is +, S is -).
     * Units are assumed to be in degrees unless marked as radians.
     *
     * @param latStr latitude value string
     * @throws IllegalArgumentException error if syntax is incorrect or value is out of range
     */
    public Latitude(String latStr) {
		if (latStr == null) throw new IllegalArgumentException("latStr cannot be null");
        latStr = latStr.trim().toUpperCase();
        int n = latStr.length() - 1;
		if (n < 0) throw new IllegalArgumentException("Latitude must be non-empty value");
        char suffix = latStr.charAt(n);
        char sign = ' ';
        if (suffix == 'N') sign = '+';
        else if (suffix == 'S') sign = '-';
        else if ((suffix == 'E') || (suffix == 'W'))
            throw new IllegalArgumentException("Latitude with E or W direction indicator ");
        if (sign != ' ') {
            // Make sure there is no numeric sign prefix when direction char suffix is present
            char prefix = latStr.charAt(0);
            if ((prefix == '+') || (prefix == '-'))
                throw new IllegalArgumentException("Latitude with N or S direction indicator " +
                        "should not have numeric sign prefix.");
            // Strip off the suffix and add the sign based on direction indicator
            latStr = sign + latStr.substring(0, n);
        }
        this.init(new Angle(latStr).inRadians(), Angle.RADIANS);
    }

    /**
     * This constructor assumes Latitude angle units are in decimal radians.
     *
     * @param valueInRadians Latitude angular value in radians
     * @throws IllegalArgumentException error if value is out of range (-PI/2 to +PI/2 radians)
     */
    public Latitude(double valueInRadians) {
        this.init(valueInRadians, Angle.RADIANS);
    }

    /**
     * This constructor takes numeric degree, minutes, seconds components for Latitude.
     *
     * @param degrees signed, integer degree component of Latitude angular value
     * @param minutes integer minutes component of Latitude angular value
     * @param seconds double seconds component of Latitude angular value
     * @throws IllegalArgumentException error if a component value is out of range
     */
    public Latitude(int degrees, int minutes, double seconds) {
        this.init(degrees, minutes, seconds);
    }

    /**
     * This constructor takes an Angle object for Latitude.
     *
     * @param angle Angle to use as basis for this Latitude (must be between -90 and +90 degrees)
     */
    public Latitude(Angle angle) {
        this.init(angle.inRadians(), Angle.RADIANS);
    }

    /**
     * The default constructor makes a Latitude object at the Equator (value of zero radians).
     */
    public Latitude() {
        this.inRadians = 0.0;
    }

    /**
     * This accessor method returns the character for the hemisphere of this Latitude ('N' or 'S').
     *
     * @return char indicating southern ('S') or northern ('N') hemisphere
     */
    public char getHemisphere() {
        return (this.inRadians < 0.0) ? 'S' : 'N';
    }

    /**
     * This predicate method determines whether this Latitude is within the test
     * interval specified from southLat to northLat.
     *
     * @param southLat southern most Latitude of the interval being tested
     * @param northLat northern most Latitude of the interval being tested
     * @return boolean indicating whether this Latitude is in the test interval
     */
    public boolean inInterval(Latitude southLat, Latitude northLat) {
        return (southLat.inRadians <= this.inRadians) && (this.inRadians <= northLat.inRadians);
    }

    /**
     * This method returns a string version of this latitude value in degree, minute,
     * seconds format, with hemisphere character suffix.
     */
    public String toString(int fractDigOfSec) {
        String result = super.toString(fractDigOfSec);
        char hemisphere = this.getHemisphere();
        if (result.charAt(0) == '-') result = result.substring(1);
        return result + " " + hemisphere;
    }
}
