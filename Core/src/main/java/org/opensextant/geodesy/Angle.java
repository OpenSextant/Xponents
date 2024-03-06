/****************************************************************************************
 *  Angle.java
 *
 *  (C) Copyright MITRE Corporation 2007-2008
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

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.StringTokenizer;

import org.jetbrains.annotations.NotNull;

/**
 * The Angle class is used to store and manipulate angular quantities. By having
 * conventions for the position of zero on the circle and the direction of increasing
 * angular value, angles can be used to represent either positions on the circle, or
 * angular distances between positions on the circle such that those distances are
 * less than one full revolution. As such, this class maintains the value of an angle
 * internally in normalized radians between -PI inclusive and +PI exclusive, but has
 * constructors for decimal radians or degrees, numeric components of degrees-minutes-
 * seconds, and a String parser for degrees, minutes, and seconds. There are accessor
 * methods for getting the value of this Angle instance in decimal degrees or in radians.
 * Also provided is a method to add another Angle to this Angle instance, a method to
 * find the smallest angular difference and direction from this Angle instance. Longitude
 * and Latitude are objects that extend Angle with additional methods and constraints.
 * The toString methods will pretty print the angle in a degrees, minutes, and seconds
 * String format which the appropriate constructor can parse if needed. An Angle can
 * be constructed from a String, and the degree symbol '&deg;' can be encoded either
 * using the ISO-8859-1 (Latin-1 Western) character encoding or in Unicode. The toString
 * method currently writes the degree symbol using the Latin-1 Western encoding. This
 * usually gets escaped as an HTML entity for proper viewing across platforms.
 *
 * @author Paul Silvey
 */
public class Angle implements Serializable, Comparable<Angle> {
    private static final long serialVersionUID = 1L;

    // Instance variable holds angular value in normalized radians (-PI .. +PI)
    double inRadians;

    // Constant values
    static final double TWO_PI = 2.0 * Math.PI;  // 360 degrees

    /**
     * Unicode degree symbol
     * <br>
     * This is used by {@link #toString()}.
     */
    public static final String DEGSYM = Character.toString('\u00B0'); //

    /**
     * unitType parameter for constructor specifying angle is in decimal radians
     */
    public static final int RADIANS = 0;
    /**
     * unitType parameter for constructor specifying angle is in decimal degrees
     */
    public static final int DEGREES = 1;

    /*
     * Reduce angle in radians to normalized range form [-PI .. +PI)
     *
     * @param rad value in radians
     * @return normalized value in radians (-PI .. PI)
     * @throws IllegalArgumentException - error if angle is too large
     */
    static double normalize(double rad) {
        // This is somewhat arbitrary to limit angles to 4 revolutions
        if (Math.abs(rad) > (4.0 * TWO_PI))
            throw new IllegalArgumentException("Angle " + rad + " radians is too big");
        while (rad >= Math.PI) rad -= TWO_PI;
        while (rad < -Math.PI) rad += TWO_PI;
        return rad;
    }

    /*
     * Base initializer takes angle value and units designator
     */
    protected void init(double value, int unitType) {
        if (unitType == Angle.RADIANS)
            this.inRadians = normalize(value);
        else if (unitType == Angle.DEGREES)
            this.inRadians = normalize(Math.toRadians(value));
        else throw new IllegalArgumentException("Invalid Angle unit type");
    }

    /*
     * This initializer takes integer degrees and minutes with decimal seconds
     * Degrees may be any signed integer value, others are range checked.
     */
    protected void init(double deg, double min, double sec) {
        if ((min < 0.0) || (60.0 <= min))
            throw new IllegalArgumentException("Arc minutes value '" + min +
                    "' is out of legal range");
        else if ((sec < 0.0) || (60.0 <= sec))
            throw new IllegalArgumentException("Arc seconds value '" + sec +
                    "' is out of legal range");
        double ddeg = Math.abs(deg) + (min / 60.0) + (sec / 3600.0);
        double sign = (deg < 0.0) ? -1.0 : +1.0;
        this.init(sign * ddeg, Angle.DEGREES);
    }

    /**
     * This constructor takes angle value and unitType designator.
     *
     * @param value    angular value in specified units (Angle.DEGREES or Angle.RADIANS)
     * @param unitType one of Angle.DEGREES or Angle.RADIANS
     * @throws IllegalArgumentException error if units or value are out of range
     */
    public Angle(double value, int unitType) {
        this.init(value, unitType);
    }

    /**
     * This constructor assumes units are radians.
     *
     * @param valueInRadians angular value in decimal radians
     */
    public Angle(double valueInRadians) {
        this.init(valueInRadians, Angle.RADIANS);
    }

    /**
     * This constructor takes numeric degree, minutes, seconds components.
     *
     * @param degrees signed, integer degree component of angular value
     * @param minutes integer minutes component of angular value
     * @param seconds double seconds component of angular value
     * @throws IllegalArgumentException error if a component value is out of range
     */
    public Angle(int degrees, int minutes, double seconds) {
        this.init(degrees, minutes, seconds);
    }

    /**
     * This constructor takes a string and parses it as an angle.  It breaks the string into
     * one, two, or three numeric components with optional units designation.  Units are assumed
     * to be in degrees unless marked as radians, and components after the first are assumed to
     * be arc minutes and arc seconds.
     *
     * @param valStr the String to be parsed as an instance of Angle
     * @throws IllegalArgumentException error if string can not be successfully parsed.
     */
    public Angle(String valStr) {
        String token;
        String[] components = new String[]{"", "", ""};
        double ang, min, sec;

        // Normalize to upper case chars, allow several possible delimiters
        valStr = valStr.trim().toUpperCase();
        StringTokenizer st = new StringTokenizer(valStr, " \t,;:");

        // Combine unit tokens with their preceding number tokens using optimistic assumptions
        // This allows optional whitespace between numeric values and unit strings to be accepted
        int i = 0;
        while (st.hasMoreTokens() && (i < 3)) {
            token = st.nextToken();
            boolean containsDigits = false;
            for (int j = 0; j < token.length(); j++) {
                if (Character.isDigit(token.charAt(j))) {
                    containsDigits = true;
                    components[i++] = token;
                    break;
                }
            }
            if (!containsDigits && (i > 0)) components[i - 1] += token;
        }
        if (st.hasMoreTokens())
            throw new IllegalArgumentException("Unexpected token '" + st.nextToken() +
                    "' in Angle string \"" + valStr + "\"");

        // Note which components we actually have
        boolean hasAng = (components[0].length() > 0);
        boolean hasMin = (components[1].length() > 0);
        boolean hasSec = (components[2].length() > 0);
        if (hasMin && !hasSec && components[1].endsWith("\"")) {
            // special case: minutes and seconds without whitespace in-between
            // e.g., 37 25'19.07"N
            int ind = components[1].indexOf('\'');
            if (ind > 0) {
                components[2] = components[1].substring(ind + 1);
                components[1] = components[1].substring(0, ind + 1);
                hasSec = true;
            }
        }

        if (!hasAng) throw new IllegalArgumentException("No valid tokens found in Angle string \"" +
                valStr + "\"");

        // No units suffix assumes degrees
        int units = Angle.DEGREES;
        // Look for angle in degrees or radians
        token = components[0];
        // See if there is a units designator string suffix and handle accordingly
        int suffixLen = 0;
        if (token.endsWith(DEGSYM)) suffixLen = 1;
        else if (token.endsWith("D")) suffixLen = 1;
        else if (token.endsWith("DEG")) suffixLen = 3;
        else if (token.endsWith("DEGS")) suffixLen = 4;
        else if (token.endsWith("DEGREES")) suffixLen = 7;
        else if (token.endsWith("R")) {
            units = Angle.RADIANS;
            suffixLen = 1;
        } else if (token.endsWith("RAD")) {
            units = Angle.RADIANS;
            suffixLen = 3;
        } else if (token.endsWith("RADS")) {
            units = Angle.RADIANS;
            suffixLen = 4;
        } else if (token.endsWith("RADIANS")) {
            units = Angle.RADIANS;
            suffixLen = 7;
        }
        // Remove the units suffix string (if any) and parse the numeric part for angle
        token = token.substring(0, token.length() - suffixLen);
        boolean negativeZeroAngle = false;
        if ("-0".equals(token)) {
            //System.out.println("XXX: negative zero");
            // don't want to deal with negative zero in double calculations: flip sign later
            negativeZeroAngle = true;
            ang = 0;
        } else
            ang = Double.parseDouble(token);

        if (hasMin) {
            // Do some additional error checking if there is more than one component
            if (units == Angle.RADIANS)
                throw new IllegalArgumentException("Unexpected tokens following angle in radians, in \"" +
                        valStr + "\"");
            else if (components[0].contains("."))
                throw new IllegalArgumentException("Only least-significant component of angle " +
                        "can be fractional, in \"" + valStr + "\"");

            // Since we now know our units are degrees, look for possible minutes or minutes and seconds
            token = components[1];
            // See if there is a units designator string suffix and handle accordingly
            suffixLen = 0;
            if (token.endsWith("'")) suffixLen = 1;
            else if (token.endsWith("M")) suffixLen = 1;
            else if (token.endsWith("MIN")) suffixLen = 3;
            else if (token.endsWith("MINS")) suffixLen = 4;
            else if (token.endsWith("MINUTES")) suffixLen = 7;
            // Remove the units suffix string (if any) and parse the numeric part for minutes
            token = token.substring(0, token.length() - suffixLen);
            min = Double.parseDouble(token);
            if (min < 0.0)
                throw new IllegalArgumentException("Illegal negative value for arc minutes of angle in \"" +
                        valStr + "\"");

            // Now look for possible additional value of arc seconds
            if (hasSec) {
                if (components[1].contains("."))
                    throw new IllegalArgumentException("Only least-significant component of angle " +
                            "can be fractional, in \"" + valStr + "\"");

                token = components[2];
                // See if there is a units designator string suffix and handle accordingly
                suffixLen = 0;
                if (token.endsWith("\"")) suffixLen = 1;
                else if (token.endsWith("S")) suffixLen = 1;
                else if (token.endsWith("SEC")) suffixLen = 3;
                else if (token.endsWith("SECS")) suffixLen = 4;
                else if (token.endsWith("SECONDS")) suffixLen = 7;
                // Remove the units suffix string (if any) and parse the numeric part for minutes
                token = token.substring(0, token.length() - suffixLen);
                sec = Double.parseDouble(token);
                if (sec < 0.0)
                    throw new IllegalArgumentException("Illegal negative value for arc seconds of angle in \"" +
                            valStr + "\"");
            } else
                sec = 0.0;
            // Initialize Angle instance using degree, minute, second form
            this.init(ang, min, sec);
        } else
            // Initialize Angle instance using decimal degrees or radians
            this.init(ang, units);

        if (negativeZeroAngle) {
            // swap the sign
            inRadians = -inRadians;
        }
    }

    /**
     * Default constructor makes an angle whose value is zero radians.
     */
    public Angle() {
        this.inRadians = 0.0;
    }

    /**
     * This accessor method returns the value of the Angle in decimal radians.
     *
     * @return value of this angle in radians
     */
    public double inRadians() {
        return this.inRadians;
    }

    /**
     * This accessor method returns the value of the Angle in decimal radians. It is
     * equalivent to the inRadians method, but it follows the standard JavaBean getter
     * signature, used by Frameworks like Hibernate and Spring.
     *
     * @return value of this angle in radians
     */
    double getRadians() {
        return this.inRadians;
    }

    /**
     * This settor method is used to update the value of this Angle by directly setting
     * its value in decimal radians. It is used by Frameworks like Hibernate and Spring
     *
     * @param radians new value in decimal radians for this Angle
     */
    void setRadians(double radians) {
        this.inRadians = radians;
    }

    /**
     * This accessor method returns the value of the Angle in decimal degrees.
     *
     * @return value of this angle in degrees
     */
    public double inDegrees() {
        return Math.toDegrees(this.inRadians);
    }

    /**
     * This accessor method returns the value of the Angle in the specified unitType.
     *
     * @param unitType type of units desired (one of Angle.RADIANS or Angle.DEGREES)
     * @return value of this angle in the decimal radians or degrees, depending on unitType specified
     * @throws IllegalArgumentException error if unitType is invalid
     */
    public double getValue(int unitType) {
        if (unitType == Angle.RADIANS) return this.inRadians();
        else if (unitType == Angle.DEGREES) return this.inDegrees();
        else throw new IllegalArgumentException("Invalid Angle unit type");
    }

    /**
     * This method computes and returns a new angle which is the result of adding the
     * specified angle to this one.
     *
     * @param that Angle to add to this one
     * @return a new Angle that is the result of adding input angle to this one
     */
    @NotNull
    public Angle add(Angle that) {
        return new Angle(this.inRadians + that.inRadians);
    }

    /**
     * This method computes and returns a new angle which is the the shortest angular
     * difference between the specified angle and this one.  The sign indicates which
     * direction (+ is counter-clockwise and - is clockwise, by convention) to go to
     * get from this Angle to that Angle.
     *
     * @param that - Angle to compare to this one
     * @return a new Angle that is the angular difference between input angle and this one
     */
    @NotNull
    public Angle difference(Angle that) {
        return new Angle(that.inRadians - this.inRadians);
    }

    // use 1e-8 for ~ 0.1 meter resolution distinction in radians (on earth's surface)
    private static final double EPSILON = 1e-8;

    /**
     * This method returns a hash code for this Angle object. The result depends only on
     * the angluar value in radians, so that equal Angles have equal hash codes.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        // Note we're using approximate equals vs absolute equals on floating point number
        // so must ignore beyond ~7 decimal places in computing the hashCode, otherwise
        // we break the equals-hashCode contract. Changing EPSILON or equals(Angle) may
        // require changing the logic used here also.
        return (int) (inRadians * 1e+7);
    }

    /**
     * equals() method that determines equality of two double precision values that
     * works over a wide range of magnitudes, infinities, NaNs, and positive and
     * negative zero.
     * <p>
     * Because floating point calculations may involve rounding, calculated float and double
     * values may not be accurate. For values that must be precise, such as monetary values,
     * consider using a fixed-precision type such as BigDecimal. For values that need not be
     * precise, consider comparing for equality within some range.
     * <p>
     * See the Java Language Specification, section 4.2.4.
     * <p>
     * See <a href="http://www.oreilly.com/catalog/javacook/errata/javacook.unconfirmed#Example_5-1">...</a>
     * <a href="http://findbugs.sourceforge.net/bugDescriptions.html#FE_FLOATING_POINT_EQUALITY">...</a>
     * <a href="http://bobcat.webappcabaret.net/javachina/faq/08.htm#math_Q19">...</a>
     *
     * @param a the first double to compare with.
     * @param b the second double to compare with.
     * @return {@code true} if the objects are the same;
     *         {@code false} otherwise.
     */
    static boolean equals(double a, double b) {
        /*
        Comparing the difference of two numbers with epsilon requires that the
        magnitude of epsilon change with the magnitude of the numbers being
        compared. But the whole purpose of floating point is that the point floats,
        that is, the programmer need not keep track of the magnitude of the numbers
         */
        return a == b || Math.abs(a - b) < EPSILON * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * equals() method that determines equality of two double precision values that
     * works over a wide range of magnitudes, infinities, NaNs, and positive and
     * negative zero.
     * <p>
     * Because floating point calculations may involve rounding, calculated float and double
     * values may not be accurate. For values that must be precise, such as monetary values,
     * consider using a fixed-precision type such as BigDecimal. For values that need not be
     * precise, consider comparing for equality within some range.
     * <p>
     * See the Java Language Specification, section 4.2.4.
     * <p>
     * See <a href="http://www.oreilly.com/catalog/javacook/errata/javacook.unconfirmed#Example_5-1">...</a>
     * <a href="http://findbugs.sourceforge.net/bugDescriptions.html#FE_FLOATING_POINT_EQUALITY">...</a>
     * <a href="http://bobcat.webappcabaret.net/javachina/faq/08.htm#math_Q19">...</a>
     *
     * @param a       the first double to compare with.
     * @param b       the second double to compare with.
     * @param epsilon the epsilon value for error threshold
     * @return {@code true} if the objects are the same;
     *         {@code false} otherwise.
     */
    static boolean equals(double a, double b, double epsilon) {
        return a == b || Math.abs(a - b) < epsilon * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * This method is used to test whether another Angle is equal to this Angle by testing
     * that their values in radians differ in value by no more than epsilon (1e-7).
     *
     * @param that Angle to compare against this one.
     * @return true if the specified Angle is equal to this Angle.
     */
    public boolean equals(Angle that) {
        return that != null && equals(this.inRadians, that.inRadians);
    }

    // Inherited Javadoc
    @Override
    public boolean equals(Object that) {
        return that instanceof Angle && equals((Angle) that);
    }

    /**
     * This method returns a formatted String representation of the angle value in degree,
     * minute, seconds format, with the specified number of fractional digits of arc seconds.
     *
     * @param fractDigOfSec number of fraction digits of seconds to format
     * @return String formatted as integer degrees, minutes, and seconds with optional fractional part
     */
    @NotNull
    public String toString(int fractDigOfSec) {
        int sign = (this.inRadians < 0) ? -1 : +1;
        double rem = Math.abs(Math.toDegrees(this.inRadians));
        int deg = (int) rem;
        rem = (rem - (double) deg) * 60.0;
        int min = (int) rem;
        double sec = (rem - (double) min) * 60.0;

        String fract = (fractDigOfSec > 0) ? "." : "";
        while (fractDigOfSec-- > 0) fract += "0";
        DecimalFormat secFmt = new DecimalFormat("0" + fract);
        if (secFmt.format(sec).startsWith("60")) {
            min += 1;
            sec = 0.0;
        }
        if (min == 60) {
            deg += 1;
            min = 0;
        }
        return String.format("%d%s %d' %s\"", sign*deg, DEGSYM, min, secFmt.format(sec));
    }

    /**
     * This method returns a formatted String representation of the angle value in degree,
     * minute, seconds format, with no fractional digits of arc seconds.
     *
     * @return String formatted as integer degrees, minutes, and seconds
     */
    @Override
    public String toString() {
        return this.toString(0);
    }

    /**
     * @param that Angle to compare lengths with this one
     * @return a negative integer, zero, or a positive integer as this object is less than,
     *         equal to, or greater than the specified object.
     * @throws NullPointerException if that is null
     */
    public int compareTo(Angle that) {
        return (this.inRadians < that.inRadians) ? -1 :
                (this.inRadians > that.inRadians) ? +1 : 0;
    }
}
