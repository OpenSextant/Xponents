/**
 *
 *  Copyright 2009-2014 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * **************************************************************************
 *                          NOTICE
 * This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 */
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//
// _____                                ____                     __                       __
///\  __`\                             /\  _`\                  /\ \__                   /\ \__
//\ \ \/\ \   _____      __     ___    \ \,\L\_\      __   __  _\ \ ,_\     __       ___ \ \ ,_\
// \ \ \ \ \ /\ '__`\  /'__`\ /' _ `\   \/_\__ \    /'__`\/\ \/'\\ \ \/   /'__`\   /' _ `\\ \ \/
//  \ \ \_\ \\ \ \L\ \/\  __/ /\ \/\ \    /\ \L\ \ /\  __/\/>  </ \ \ \_ /\ \L\.\_ /\ \/\ \\ \ \_
//   \ \_____\\ \ ,__/\ \____\\ \_\ \_\   \ `\____\\ \____\/\_/\_\ \ \__\\ \__/.\_\\ \_\ \_\\ \__\
//    \/_____/ \ \ \/  \/____/ \/_/\/_/    \/_____/ \/____/\//\/_/  \/__/ \/__/\/_/ \/_/\/_/ \/__/
//            \ \_\
//             \/_/
//
//  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~|
//

package org.opensextant.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class Country extends Place {
    /** ISO 2-character country code */
    public String CC_ISO2 = null;
    /** ISO 3-character country code */
    public String CC_ISO3 = null;
    /** FIPS 10-4 2-character country code */
    public String CC_FIPS = null;

    /** Any list of country alias names. */
    private final Set<String> aliases = new HashSet<>();

    private final Set<String> regions = new HashSet<>();

    /** Map of Geonames.org TZ and UTC offsets per country */
    private final Map<String, Double> timezones = new HashMap<>();
    private final Map<String, Double> timezonesVariants = new HashMap<>();

    /**
     * A country abstraction that uses ISO 2-alpha as an ID, and any name given
     * as the Place.name
     *
     * @param iso2  ISO 2-alpha code for this country
     * @param nm    Country name
     */
    public Country(String iso2, String nm) {
        super(iso2, nm);
        CC_ISO2 = this.key;
        this.country_id = this.key;
    }

    /** Country is also known as some list of aliases
     * @param nm  Country name/alias
     */
    public void addAlias(String nm) {
        aliases.add(nm);
    }

    /**
     *
     * @return  set of aliases
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * Add a timezone and its offset.  TZ labels vary, so variant labels are tracked as well.
     * uppercase, lowercase
     * @param label TZ label
     * @param utcOffset  floating point UTC offset in decimal hours.  e.g., 7.5, -3.0 = (GMT-0300), etc.
     */
    public void addTimezone(String label, double utcOffset) {
        timezones.put(label, utcOffset);
        timezonesVariants.put(label.toLowerCase(), utcOffset);
        if (label.contains("/")) {
            String tz = label.split("/", 2)[1];
            timezonesVariants.put(tz.toLowerCase(), utcOffset);
        }
    }

    /**
     * 
     * @param tz   any reasonable TZ label. Case-insensitive.
     * @return  true if Country has this TZ
     */
    public boolean containsTimezone(String tz) {
        if (tz == null) {
            return false;
        }
        return timezonesVariants.containsKey(tz.toLowerCase());
    }

    /**
     * Test if this Country contains the UTC offset.  Make sure you never
     * pass a default of 0 (GMT+0) in, unless you really mean GMT0.
     * No validation on your offset parameter is done.
     * 
     * @param offset  UTC offset in hours.  Valid values are -12.0 to 12.0
     * @return true if this Country contains the UTC offset.
     */
    public boolean containsUTCOffset(double offset) {
        for (double off : timezones.values()) {
            // What is a good way to evaluate 0.00 - 0.00 == 0? in Java. We see too many rounding errors in Java.
            // Python: >>> (0.000  - 0) == 0    is True.  No floating point rounding errors.
            if (Math.abs(off - offset) < 0.10) {
                return true;
            }
        }
        return false;
    }

    /** Country is also known as some list of aliases
     * @param regionid  Region identifier or name.
     */
    public void addRegion(String regionid) {
        regions.add(regionid);
    }

    /**
     *
     * @return  set of regions in which this country belongs.
     */
    public Set<String> getRegions() {
        return regions;
    }

    private boolean uniqueName = false;

    public void setUniqueName(boolean b) {
        uniqueName = b;
    }

    public boolean hasUniqueName() {
        return uniqueName;
    }

    public boolean isTerritory = false;

    @Override
    public String toString() {
        if (!isTerritory) {
            return String.format("%s (%s,%s,%s)", getName(), CC_ISO3, CC_ISO2, CC_FIPS);
        } else {
            return String.format("%s territory of %s", getName(), CC_ISO3);
        }
    }
}
