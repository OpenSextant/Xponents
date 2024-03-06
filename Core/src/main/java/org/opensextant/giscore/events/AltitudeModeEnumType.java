package org.opensextant.giscore.events;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps to altitudeModeEnumType in KML.
 * see <a href="http://code.google.com/apis/kml/documentation/kmlreference.html#altitudemode">...</a>
 *
 * Also includes altitude types from gx:altitudeMode KML extension schema xmlns:gx=<a href="http://www.google.com/kml/ext/2.2">...</a>
 * see <a href="http://code.google.com/apis/kml/schema/kml22gx.xsd">...</a>
 *
 * @author Jason Mathews, MITRE Corp.
 * Date: May 28, 2009 2:07:37 PM
 */
public enum AltitudeModeEnumType {

    clampToGround, // default
    relativeToGround,
    absolute,
	clampToSeaFloor, 	// gx extension
	relativeToSeaFloor; // gx extension

    private static final Logger log = LoggerFactory.getLogger(AltitudeModeEnumType.class);

	/**
	 * Get normalized altitude Mode such that if altitudeMode value is valid
	 * then associated AltitudeModeEnumType enumeration is returned otherwise
	 * null. Null or empty string also returns null. 
	 * @param altitudeMode ([clampToGround], relativeToGround, absolute)
	 * 		also including gx:extensions (clampToSeaFloor, relativeToSeaFloor)
	 * @return Enumerated type instance if valid otherwise null
	 */
    public static AltitudeModeEnumType getNormalizedMode(String altitudeMode) {
        if (StringUtils.isNotBlank(altitudeMode))
            try {
                return AltitudeModeEnumType.valueOf(altitudeMode);
            } catch (IllegalArgumentException e) {
                log.warn("Ignoring invalid altitudeMode value: " + altitudeMode); // use default value
            }
        return null;
    }
}
