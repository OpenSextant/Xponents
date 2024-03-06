/****************************************************************************************
 *  Longitude.java
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
 * The Longitude class extends Angle to provide parsing and formatting with direction indicator
 * suffix ('E' or 'W') from the Prime Meridian instead of numeric sign. Note that the normalized
 * form for Angles maps +180 degrees to its equivalent -180 degree form at the International
 * Date Line.
 */
public class Longitude extends Angle {

	private static final long serialVersionUID = 1L;

    /**
     * This constructor takes longitude angle value and units designator.
     *
     * @param value    Longitude angular value in specified units (Angle.DEGREES or Angle.RADIANS)
     * @param unitType Angle.DEGREES or Angle.RADIANS
     * @throws IllegalArgumentException error if units or value are out of range
     */
    public Longitude(double value, int unitType) {
        this.init(value, unitType);
    }

    /**
     * This constructor takes a longitude formatted as a string.  It looks for an 'E' or 'W'
     * direction indicator suffix character and sets the numeric sign accordingly (E is +, W is -).
     * Units are assumed to be in degrees unless marked as radians.
     *
     * @param lonStr longitude value string
     * @throws IllegalArgumentException error if syntax is incorrect or value is out of range
     */
    public Longitude(String lonStr) {
		if (lonStr == null) throw new IllegalArgumentException("lonStr cannot be null");
        lonStr = lonStr.trim().toUpperCase();
        int n = lonStr.length() - 1;
		if (n < 0) throw new IllegalArgumentException("Longitude must be non-empty value");
        char suffix = lonStr.charAt(n);
        char sign = ' ';
        if (suffix == 'E') sign = '+';
        else if (suffix == 'W') sign = '-';
        else if ((suffix == 'N') || (suffix == 'S'))
            throw new IllegalArgumentException("Longitude with N or S direction indicator");
        if (sign != ' ') {
            // Make sure there is no numeric sign prefix when direction char suffix is present
            char prefix = lonStr.charAt(0);
            if ((prefix == '+') || (prefix == '-'))
                throw new IllegalArgumentException("Longitude with E or W direction indicator " +
                        "should not have numeric sign prefix.");
            // Strip off the suffix and add the sign based on direction indicator
            lonStr = sign + lonStr.substring(0, n);
        }
        this.init(new Angle(lonStr).inRadians(), Angle.RADIANS);
    }

    /**
     * This constructor assumes Longitude angle units are in decimal radians.
     * Longitude value is normalized to the (-PI .. PI) range and by convention
     * +180 becomes -180.
     *
     * @param valueInRadians Longitude angular value in radians
     */
    public Longitude(double valueInRadians) {
        this.init(valueInRadians, Angle.RADIANS);
    }

    /**
     * This constructor takes numeric degree, minutes, seconds components for Longitude.
     *
     * @param degrees signed, integer degree component of Longitude angular value
     * @param minutes integer minutes component of Longitude angular value
     * @param seconds double seconds component of Longitude angular value
     * @throws IllegalArgumentException error if a component value is out of range
     */
    public Longitude(int degrees, int minutes, double seconds) {
        this.init(degrees, minutes, seconds);
    }

    /**
     * This constructor takes an Angle object for Longitude.
     *
     * @param angle Angle to use as basis for this Longitude
     */
    public Longitude(Angle angle) {
        this.init(angle.inRadians(), Angle.RADIANS);
    }

    /**
     * The default constructor makes a Longitude object at the Prime Meridian (value of zero radians).
     */
    public Longitude() {
        this.inRadians = 0.0;
    }

    /**
     * This accessor method returns the character for the direction from the
     * Prime Meridian of this Longitude ('E' or 'W').
     *
     * @return char indicating westerly ('W') or easterly ('E') direction
     */
    public char getDirectionFromPrimeMeridian() {
        return (this.inRadians < 0) ? 'W' : 'E';
    }

    /**
     * This method computes the distance in radians from this Longitude to the specified
     * eastLon Longitude by travelling eastward (positive increasing angular direction).
     *
     * @param eastLon destination Longitude around globe
     * @return positive radians needed to sweep east to get from this Longitude to eastLon
     */
    public double radiansEast(Longitude eastLon) {
        double diff = this.difference(eastLon).inRadians;
        return (diff < 0.0) ? Angle.TWO_PI + diff : diff;
    }

    /**
     * This predicate method determines whether this Longitude is within the test
     * interval specified from westLon to eastLon.
     *
     * @param westLon western most Longitude of the interval being tested
     * @param eastLon eastern most Longitude of the interval being tested
     * @return boolean indicating whether this Longitude is in the interval
     */
    public boolean inInterval(Longitude westLon, Longitude eastLon) {
        double westRad = westLon.inRadians;
        double eastRad = eastLon.inRadians;
        double testRad = this.inRadians;
        // If interval wraps around, break it into two parts and test each separately
        if (westRad <= eastRad) {
            return ((westRad <= testRad) && (testRad <= eastRad));
        } else {
            return (((westRad <= testRad) && (testRad < Math.PI)) ||
                    ((-Math.PI <= testRad) && (testRad <= eastRad)));
        }
    }

    /**
     * This method returns a string version of this longitude value in degree, minute,
     * seconds format, with direction character suffix.
     */
    public String toString(int fractDigOfSec) {
        String result = super.toString(fractDigOfSec);
        char dirFromMeridian = this.getDirectionFromPrimeMeridian();
        if (result.charAt(0) == '-') result = result.substring(1);
        // Normalize rounded +180 deg values to be -180 deg by display convention
        if ((result.startsWith("180")) && (dirFromMeridian == 'E'))
            dirFromMeridian = 'W';
        return result + " " + dirFromMeridian;
    }
}
