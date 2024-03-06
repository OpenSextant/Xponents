/****************************************************************************************
 *  LatLonParser.java
 *
 *  Created: January 30, 2008
 *
 *  @author Duane Taylor
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

import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

/**
 * LatLonParser
 *
 * @author TAYLOR
 */
public class LatLonParser {
    //private static final Logger log = LoggerFactory.getLogger(LatLonParser.class);

    //
    //  Delimiters that separate coordinate tokens in the input string
    //
    private static final String[] separatorDelimiters = {
            "/",
            ",",
            "|",
            Angle.DEGSYM,
            "'",
            "\"",
            "d",
            "m",
            "s",    // seconds
            ":",
            ";",
            "\t",
            "\n",
            "\r",
            " "
    };

    private static final String delimString;           // contains sign delimeters and separator delimiters
    private static final String separatorDelimString;  // contains separator delimiters only

    //
    //  Delimiters that separate coordinate tokens in the input string and
    //  designate sign for a coordinate token
    //
    //  NOTE:  The capital S indicates SOUTH in the signDelimiters array
    //         and the small s indicates SECONDS the separatorDelimiters array
    //
    private static final String[] signDelimiters = {
            "+",
            "-",
            "N",
            "S",   // south
            "E",
            "W"
    };

    //
    //  Array of delimiters that separate coordinate tokens
    //
    private static final String[] delimiters = new String[signDelimiters.length + separatorDelimiters.length];

    //
    //  Indicates that a token does not have sign symbol.
    //
    private static final char noSign = ' ';

    //
    //  Used to efficiently determine if a detected token is one of the valid separator
    //  delimiters  (sign delimiters are not included)
    //
    private static final Set<String> separatorSet = new HashSet<>();

    //
    //  Used to efficiently determine if a detected token is one of the valid sign delimiters
    //
    private static final Set<String> signSet = new HashSet<>();

    static {

        //
        //  Set up the delimiters array used to break the string into tokens
        //
        System.arraycopy(
                signDelimiters, 0,
                delimiters, 0,
                signDelimiters.length);

        System.arraycopy(
                separatorDelimiters, 0,
                delimiters, signDelimiters.length,
                separatorDelimiters.length);

        //
        //  Set up the hash sets used for determining if a parsed symbol is in one of the sets
        //
        separatorSet.addAll(Arrays.asList(separatorDelimiters));
        signSet.addAll(Arrays.asList(signDelimiters));

        //
        //  Create strings of delimiters that will be passed into StringTokenizer.  Each
        //  string will be used by StringTokenizer to separate coordinate tokens in the
        //  input string.
        //  One will consist of the separator delimiters and the sign delimiters.
        //  One will consist of the separator delimiters only
        //
        StringBuilder result = new StringBuilder();
        result.append(delimiters[0]);
        for (int i = 1; i < delimiters.length; i++) {
            result.append(delimiters[i]);
        }
        delimString = result.toString();

        //
        //  skip the 's' delimiter for this Etrex special case.  It wouldn't be used
        //  as a seconds indicator and might be used as a hemispher indicator (s S for South)
        //
        StringBuilder result2 = new StringBuilder();
        result.append(separatorDelimiters[0]);
        for (int i = 1; i < separatorDelimiters.length; i++) {
            if (separatorDelimiters[i].charAt(0) != 's') result2.append(separatorDelimiters[i]);
        }
        separatorDelimString = result2.toString();
    }

    /**
     * LatLonParser
     * <p>
     * constructor for class that parses a lat lon string and normalizes the result.
     * There are two different output forms depending on the value of the sign:
     * <p>
     * Normalized output string form when sign is either N/S/E/W:
     * DD:	"Cs,Cs"  or  DMS: "C C Cs, C C Cs"
     * where	s = sign (N,S,E,W)
     * C = coordinate
     * <p>
     * Normalized output string form when sign is either '+'/' '/'-'
     * DD: "sC,sC  or DMS: "sC C C, sC C C"
     * where	s = sign (+,-)
     * C = coordinate
     */
    public LatLonParser() {
        /*
               *   Empty constructor
               */
    }

    /**
     * signIsAplha
     * <p>
     * Utility method that examines a character and
     * determines if the character is an alpah sign
     *
     * @param sign char to be examined
     * @return true if character is a N/S/E/W sign character
     *         false if character is nota a ' ', '+', '-' character
     */
    private boolean signIsAplha(char sign) {
        return sign == 'N' || sign == 'S' || sign == 'W' || sign == 'E';
    }

    /**
     * signTokenDetected
     * <p>
     * Utility method that examines a character and
     * determines if the character indicates sign.
     *
     * @param sign char to be examined
     * @return true if character is a sign character
     *         false if character is not a sign character
     */
    private boolean signTokenDetected(char sign) {
        return signSet.contains(String.valueOf(sign));
    }

    /**
     * Coordinate
     * <p>
     * private class that stores a coordinate token and it's sign
     */
    private static class Coordinate {

        private char sign;
        private String coordinate;

        Coordinate(char sign, String coordinate) {
            this.sign = sign;
            this.coordinate = coordinate;
        }

        Coordinate(String coordinate) {
            this(noSign, coordinate);
        }

        public String toStringPrefix() {
            if (sign != noSign) return (sign + coordinate);
            return coordinate;
        }

        public String toStringPostfix() {
            if (sign != noSign) return (coordinate + sign);
            return coordinate;
        }

        public void setSign(char newSign) {
            this.sign = newSign;
        }

        public char getSign() {
            return this.sign;
        }

        public void setCoordinate(String newCoordinate) {
            this.coordinate = newCoordinate;
        }

        public String getCoordinate() {
            return this.coordinate;
        }
    }

    /**
     * splitCoordinateString
     * <p>
     * Utility method that splits the coordinate string into an output string
     *
     * @param coord Coordinate
     */
    private void splitCoordinateString(Coordinate coord) {

        String splitString = "";           // coord string split up into D M S or DD
        String[] tokens;                  // digits on left and right of "."

        int digitCount;   // number of digits in the coordinate string
        int tokenCount;   // number of tokens in the coordinate string

        String coordinate = coord.getCoordinate();
        char sign = coord.getSign();

        //
        //  The number of digits that the degree field can have
        //  is 3 for Longitude (-180 to 180).  For Latitude it is 2 (-90 to 90).
        //
        int ddCount = 2;  // default

        //
        //  If the length of the coordinate is 3 digits
        //  or less just exit since it is probably DD
        //
        if (coordinate.length() > 3) {

            //
            //  If there is a decimal point then use it to split
            //  the string up into the whole and fractional parts.
            //
            //  The string can be DD or DMS.
            //
            //  With no decimal point in the input string then all we can do
            //  is count in from the left of the string and count off
            //  the D M S sections.  Any digits past (D)DD MM SS will be
            //  considered fractional seconds digits.  This depends on the
            //  string having E or W indicated to let us know that it is
            //  a longitude string (3 degree digits).  If it is +/- then it
            //  is ambiguous and the code defaults to latitude (2 degree digits).
            //  If it is S or N then it is 2 digits for degree.
            //
            if (coordinate.contains(".")) {
                tokens = coordinate.split("\\.");
                digitCount = tokens[0].length();
                tokenCount = tokens.length;
                if ((sign == 'E') || (sign == 'W') || (digitCount == 7)) {
                    ddCount = 3; // longitude
                }

            } else {
                tokens = new String[1];
                tokens[0] = coordinate;
                digitCount = tokens[0].length();
                tokenCount = 1;
                if ((sign == 'E') || (sign == 'W')) {
                    ddCount = 3; // longitude
                }
            }

            int index = 0;   // points to a character in the string

            //  (d)dd
            if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            if (ddCount == 3) { // look for one more d digit
                if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            }
            if (digitCount > 0) splitString += " ";

            //  mm
            if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            if (digitCount > 0) splitString += " ";

            //  ss
            if (digitCount-- > 0) splitString += tokens[0].charAt(index++);
            if (digitCount > 0) splitString += tokens[0].charAt(index++);

            //
            //  Fractional part
            //
            if (tokenCount == 1) {
                if (index < tokens[0].length()) {
                    splitString += ("." + tokens[0].substring(index));
                }
            } else {
                splitString += ("." + tokens[1]);
            }

            //
            //  Replace coordinate string with the new split string
            //
            coord.setCoordinate(splitString);
        }
    }

    /**
     * getToken
     * <p>
     * Utility method that retrieves the next token from the token stream.
     * Filters for the various separator delimiters and replaces each
     * delimiter detected with the , delimiter.
     *
     * @param st StringTokenizer token stream
     * @return null if there are no more tokens.  Otherwise a string
     *         representing the next token (or comma delimiter) in the token list
     */
    private String getToken(StringTokenizer st) {
        String token = null;
        String delimiter;
        if (st.hasMoreTokens()) {
            token = st.nextToken();
            delimiter = String.valueOf(token.charAt(0));
            if (separatorSet.contains(delimiter)) {
                token = ",";
            } else {
                token = token.toUpperCase();
            }
        }
        return token;
    }


    /**
     * parseEtrexString
     * <p>
     * Utility method to parse a specialized Garmin Etrex lat lon coordinate string from
     * the input string and return a normalized result.
     * <p>
     * This format is described in the M3 data files as DD-MM.MMMH,DDD-MM.MMMH
     * It appears to be a unique format made up the author of the data files.
     * <p>
     * Since this format is unique and conflicts with the other types of lat lon formats
     * it was processed using this specialized parsing method.
     * <p>
     * The result returned will be a lat lon coordinate separated by a comma
     * and decorated with the sign.
     *
     * @param latLonString string to be parsed
     * @return String containing lat/lon coordinate separated by a comma
     * @throws IllegalArgumentException if invalid lat/lon string received
     */
    public String parseEtrexString(String latLonString) {

        StringBuilder outString = new StringBuilder();   // string returned to caller
        String token;             // token received from input string

        //
        //  Used to indicate to StringTokenizer() to return delimiters as tokens
        //
        final boolean returnDelims = true;

        //
        //  Retrieve the test string and tokenize it
        //
        StringTokenizer st = new StringTokenizer(latLonString, separatorDelimString, returnDelims);

        //  lat
        token = getToken(st);
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) != '-') outString.append( token.charAt(i) );  // eliminate the '-' character
        }

        // Skip incoming delimiters and force a single delimiter between the tokens to a ','
        while (st.hasMoreTokens()) {
            token = getToken(st);
            if (token.charAt(0) != ',') break;
        }
        outString.append(',');

        //  lon
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) != '-') outString.append( token.charAt(i) );  // eliminate the '-' character
        }

        //
        //  Now that we have normalized the output, pass through the regular parser
        //  before returning to the caller.
        //

        return parseString(outString.toString());
    }

    /**
     * parseString
     * <p>
     * Utility method to parse lat lon coordinates from the input string
     * and return a normalized result.  Can handle DD format and DMS format.
     * The result returned will be a lat lon coordinate separated by a comma
     * and decorated with the sign.
     *
     * @param latLonString string to be parsed
     * @return String containing lat/lon coordinate separated by a comma
     * @throws IllegalArgumentException if invalid lat/lon string received
     */
	public String parseString(String latLonString) {

		final int splitDMSCount = 6;   // three geo coordinate tokens (DMS) each for lat and lon
		final int regularCount = 2;   // one geo coordinate token each (DD) for lat and lon

		Stack<String> tokenStack = new Stack<>(); // stores incoming string tokens including delimiters
		Stack<Coordinate> coordinateStack = new Stack<>(); // stores parsed coordinate tokens
		Stack<Character> signStack = new Stack<>(); // stores parsed sign symbols

		Character currentSign;  // sign symbol read from input string
		String token;           // token received from input string

		int tokenStackSize;     // number of elements in the token stack before processing
		int tokenCounter = 0;   // for the split DMS case, keep track of how many tokens in
		// in a coordinate group have been seen prior to a delimiter
		//
		//  Clear out the stacks
		//
		tokenStack.clear();
		coordinateStack.clear();
		signStack.clear();

		//
		//  Used to indicate to StringTokenizer() to return delimiters as tokens
		//
		final boolean returnDelims = true;

		//
		//  Retrieve the test string, tokenize it, and push the tokens onto the token stack.
		//  Any of the legal delimiter tokens are converted to a single "," string token.
		//
		StringTokenizer st = new StringTokenizer(latLonString, delimString, returnDelims);
		token = getToken(st);
		while (token != null) {
			tokenStack.push(token);
			token = getToken(st);
		}
		tokenStackSize = tokenStack.size();

		//
		//  Parse the tokens on the token stack into coordinates.
		//  Coordinate tokens are pushed onto the coordinate stack.
		//  Sign tokens are pushed onto the sign stack.
		//
		boolean moreTokens = true;
		while (moreTokens) {

			//
			//  There are two cases to consider.
			//
			//  The first case is DMS where the D M S tokens of the coordinate are separated.
			//
			//  The second case is DD or DMS where the D M S tokens are joined.  In this
			//  case the amount of tokens has to be 5 or less:
			//         e.g.  sC,sC  has 5 tokens (two sign, two coordinates, and 1 separator)
			//
			if (tokenStackSize > 5) {

				try {

					token = tokenStack.pop();
					currentSign = token.charAt(0);

					//
					//   Add the default nosign character to the sign stack if 3 tokens
					//   were detected followed by the delimiter character.  Only add
					//   if the sign stack is empty.  If it is not empty then the sign
					//   for this group of tokens is there already.  This takes care of
					//   one token group.  The second token group is taken care of in the
					//   EmptyStackException processing.
					//
					if (token.equals(",")) {
						if ((tokenCounter == 3) && signStack.empty()) {
							signStack.push(noSign);
						}

					} else if (signTokenDetected(currentSign)) {
						tokenCounter = 0;
						signStack.push(currentSign);

					} else {
						tokenCounter++;
						coordinateStack.push(new Coordinate(token));
					}

				} catch (EmptyStackException e) {
					moreTokens = false;  // exit the outer loop
					//
					//  Make sure there are at least 2 sign characters on the sign stack
					//
					if (signStack.size() < 2) {
						signStack.push(noSign);
					}
				}

			} else {

				try {
					token = tokenStack.pop();
					currentSign = token.charAt(0);
					if (token.equals(",")) {
						//
						// Delimiter found,
						//   either add the default noSign if no sign was detected or
						//   throw an exception if multiple sign characters were detected.
						//   If the stack sizes are equal do nothing
						//
						if (coordinateStack.size() > signStack.size()) {
							signStack.push(noSign);
						} else if (signStack.size() > coordinateStack.size()) {
							throw new IllegalArgumentException("ERROR: multiple sign characters");
						}

					} else if (signTokenDetected(currentSign)) {
						signStack.push(currentSign);

					} else {
						coordinateStack.push(new Coordinate(token));
					}

				} catch (EmptyStackException e) {
					moreTokens = false;  // exit the outer loop
					if (coordinateStack.size() > signStack.size()) {
						signStack.push(noSign);
					} else if (signStack.size() > coordinateStack.size()) {
						throw new IllegalArgumentException("ERROR: multiple sign characters", e);
					}
				}
			}
		}

		//
		//  Now combine the coordinates into one of the normalized output string forms.
		//
		//  If there are only two coordinates in the coordinate stack then just pop off the
		//  sign symbol from the corresponding location in the sign stack.
		//
		//  If there are more than two coordinates in the stack then there must
		//  be at least six tokens representing a split DMS string (e.g. 38 53 20.76N
		//  77 2 6.00W).  In this case we look for the appropriate sign among the sign stack
		//  entries that correspond to the coordinate stack entries.  There are 3 coordinate
		//  stack entries for each sign stack entry.
		//
		int signIndex;
		boolean alphaSign;
		Coordinate coord;
		Character coordSign;
		StringBuilder outString = new StringBuilder();

		int stackCount = coordinateStack.size();
		if (stackCount == splitDMSCount) {
			//
			//  There are split DMS tokens in the stack.  Find the right sign token.
			//
			for (int i = 0; i < 2; i++) {   // two entries in the sign stack

				//
				//  Look for the sign token, retrieve the coordinate values,
				//  and build the output string.
				//
				//  Place the sign token as a prefix or postfix depending on value.  If
				//  the sign is an alpha (N/S/E/W) then set the last token (index is 2) with
				//  the sign.  Otherwise set the first token (index is 0) with the sign.
				//
				coordSign = signStack.pop();
				alphaSign = signIsAplha(coordSign);
				if (alphaSign) {
					signIndex = 2;
				} else {
					signIndex = 0;
				}
				for (int j = 0; j < 3; j++) {
					try {
						coord = coordinateStack.pop();
						if (j == signIndex) {
							coord.setSign(coordSign);
						}
						if (alphaSign) {
                            outString.append(' ').append(coord.toStringPostfix());
						} else {
                            outString.append(' ').append(coord.toStringPrefix());
						}
					} catch (EmptyStackException e) {
						throw new IllegalArgumentException("ERROR: not enough coordinate stack entries", e);
					}
				}
				if (i == 0) {
					outString.append(',');  // put a separator between the output coordinates
				}
			}

		} else if (stackCount == regularCount) {

			//
			//  There should only be two coordinates in the coordinate stack and sign stack
			//
			for (int i = 0; i < 2; i++) {
				try {
					//
					//  Grab the coordinate and the sign from their stacks
					//  The sign can be N/S/E/W/+/- or blank.  If it is N/S/E/W
					//  then keep it that way since it may help parse
					//  out DMS coordinates that have no spaces between them.
					//  Check to see if the coordinate should be split apart.
					//
					coord = coordinateStack.pop();
					coordSign = signStack.pop();
					coord.setSign(coordSign);
					splitCoordinateString(coord);
					alphaSign = signIsAplha(coordSign);
					if (alphaSign) {
						outString.append( coord.toStringPostfix() );
					} else {
						outString.append( coord.toStringPrefix() );
					}

				} catch (EmptyStackException e) {
					throw new IllegalArgumentException("ERROR: not enough coordinate stack entries", e);
				}
				if (i == 0) {
					outString.append(',');  // put a separator between the output coordinates
				}
			}
		} else {
			throw new IllegalArgumentException("ERROR - irregular coordinate count:  " + stackCount);
		}

		return outString.toString();
	}
}

