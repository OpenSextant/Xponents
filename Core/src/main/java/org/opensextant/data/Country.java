/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Country metadata provided on this class includes:
 * <ul>
 * <li>ISO-3166 country code 2-char and 3-char forms, aligned with US standard
 * FIPS 10-4 codes
 * </li>
 * <li>Country aliases: nick names, variant names, abbreviations
 * </li>
 * <li>Affiliated territories
 * </li>
 * <li>Timezone and UTC offset for temporal calculations
 * </li>
 * <li>Primary and Secondary languages
 * </li>
 * </ul>
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
    private String namenorm = null;

    /** Any list of country alias names. */
    private final Set<String> aliases = new HashSet<>();

    private final Set<String> regions = new HashSet<>();
    private final List<Country> territories = new ArrayList<>();

    /** Map of Geonames.org TZ and UTC offsets per country */
    private final Map<String, TZ> tzdb = new HashMap<>();
    private final Map<String, Double> timezones = new HashMap<>();
    private final Map<String, Double> timezonesVariants = new HashMap<>();

    private final ArrayList<String> languages = new ArrayList<>();
    private final Set<String> languagesSet = new HashSet<>();

    /**
     * A country abstraction that uses ISO 2-alpha as an ID, and any name given
     * as the Place.name
     *
     * @param iso2
     *             ISO 2-alpha code for this country
     * @param nm
     *             Country name
     */
    public Country(String iso2, String nm) {
        super(iso2, nm);
        CC_ISO2 = this.key;
        this.country_id = this.key;
        if (this.name != null) {
            namenorm = name.toLowerCase();
            addAlias(name);
        }
    }

    /**
     * Return name normalized, e.g., lowercase, w/out diacritics. 's, etc.
     */
    @Override
    public String getNamenorm() {
        return namenorm;
    }

    /**
     * Country is also known as some list of aliases
     *
     * @param nm
     *           Country name/alias
     */
    public void addAlias(String nm) {
        aliases.add(nm);
    }

    /**
     * @return set of aliases
     */
    public Set<String> getAliases() {
        return aliases;
    }

    /**
     * Add a timezone and its offset. TZ labels vary, so variant labels are tracked
     * as well.
     * uppercase, lowercase
     *
     * @param label
     *                  TZ label
     * @param utcOffset
     *                  floating point UTC offset in decimal hours. e.g., 7.5, -3.0
     *                  = (GMT-0300), etc.
     */
    public void addTimezone(String label, double utcOffset) {
        timezones.put(label, utcOffset);
        String l = label.toLowerCase();
        timezonesVariants.put(l, utcOffset);
        if (label.contains("/")) {
            String tz = l.split("/", 2)[1];
            timezonesVariants.put(tz, utcOffset);
        }
    }

    /**
     * Refactor -- use JodaTime and the TZDB more formally. For now, tzdb tracks the
     * timezone metadata.
     *
     * @param tz - Country.TZ object
     */
    public void addTimezone(TZ tz) {
        String l = tz.label.toLowerCase();
        timezones.put(tz.label, tz.utcOffset);
        timezonesVariants.put(l, tz.utcOffset);
        tzdb.put(tz.label, tz);
        tzdb.put(l, tz);

        if (tz.label.contains("/")) {
            String labelPart = l.split("/", 2)[1];
            timezonesVariants.put(labelPart, tz.utcOffset);
            tzdb.put(labelPart, tz);
        }
    }

    public static final class TZ {
        public String label = null;
        public double utcOffset = Double.NaN;
        public double dstOffset = Double.NaN;
        public double rawOffset = Double.NaN;
        public boolean usesDST = false;
        public double dstDelta = 0;

        public TZ(String l, double utc, double dst, double raw) {
            label = l;
            utcOffset = utc;
            dstOffset = dst;
            rawOffset = raw;
            dstDelta = utcOffset - dstOffset;
            usesDST = dstDelta != 0;
        }

        /**
         * Parse error will be thrown on invalid data.
         * Nulls or empty fields are allowable.
         *
         * @param l   timezone label
         * @param utc UTC offset
         * @param dst UTC offset for Daylight savings
         * @param raw UTC offset
         */
        public TZ(String l, String utc, String dst, String raw) {
            label = l;
            utcOffset = getValue(utc);
            dstOffset = getValue(dst);
            rawOffset = getValue(raw);
            dstDelta = utcOffset - dstOffset;
            usesDST = dstDelta != 0;
        }

        private Double getValue(String v) {
            if (StringUtils.isEmpty(v)) {
                return Double.NaN;
            }
            return Double.parseDouble(v);
        }

        @Override
        public String toString() {
            return String.format("%s %1.1f, %1.1f", label, utcOffset, dstOffset);
        }
    }

    /**
     * @param tz
     *           any reasonable TZ label. Case-insensitive.
     * @return true if Country has this TZ
     */
    public boolean containsTimezone(String tz) {
        if (tz == null) {
            return false;
        }
        return timezonesVariants.containsKey(tz.toLowerCase());
    }

    /**
     * Test if this Country contains the UTC offset. Make sure you never
     * pass a default of 0 (GMT+0) in, unless you really mean GMT0.
     * No validation on your offset parameter is done.
     *
     * @param offset
     *               UTC offset in hours. Valid values are -12.0 to 12.0
     * @return true if this Country contains the UTC offset.
     */
    public boolean containsUTCOffset(double offset) {
        for (TZ tz : tzdb.values()) {
            if (Math.abs(tz.utcOffset - offset) < 0.10) {
                return true;
            }
        }
        return false;
    }

    public boolean containsDSTOffset(double offset) {
        for (TZ tz : tzdb.values()) {
            if (Math.abs(tz.dstOffset - offset) < 0.10) {
                return true;
            }
        }
        return false;
    }

    /**
     * Country is also known as some list of aliases
     *
     * @param regionid
     *                 Region identifier or name.
     */
    public void addRegion(String regionid) {
        regions.add(regionid);
    }

    /**
     * @return set of regions in which this country belongs.
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
            // Some other country claims this land as a territory.
            return String.format("%s territory of %s", getName(), CC_ISO3);
        }
    }

    /**
     * When adding languages, please add the primary language FIRST.
     * Languages may be langID or langID+locale. TODO: add separate attributes for
     * locales.
     *
     * @param langid
     *               language
     */
    public void addLanguage(String langid) {
        if (!languagesSet.contains(langid)) {
            languages.add(langid);
            languagesSet.add(langid);
        }
    }

    /**
     * @param langid
     *               language ID
     * @return if language (identified by ID l) is spoken
     */
    public boolean isSpoken(String langid) {
        if (langid == null) {
            return false;
        }
        return languagesSet.contains(langid.toLowerCase());
    }

    /**
     * Certain island nations, areas, and territories that have ISO country codes
     * may not have a language.
     *
     * @return first language in languages list (per addLanguage()); null if no
     *         languages present.
     */
    public String getPrimaryLanguage() {
        if (languages.size() > 0) {
            return languages.get(0);
        }
        return null;
    }

    /**
     * @param langid lang ID string
     * @return true if language given matches primary language
     */
    public boolean isPrimaryLanguage(String langid) {

        if (langid != null && languages.size() > 0) {
            return langid.equalsIgnoreCase(languages.get(0));
        }
        return false;
    }

    /**
     * @return collection of language IDs -- some may be unknown langIDs.
     */
    public Collection<String> getLanguages() {
        return languages;
    }

    /**
     * A full list/map of all timezone labels mapped to UTC offsets present in this
     * country.
     * Reference: geonames.org timezone table has timezones.txt; See our
     * GeonamesUtility for how
     * data is populated here on Country object.
     *
     * @return map of TZ labels to UTC offsets.
     */
    public Map<String, Double> getAllTimezones() {
        return timezonesVariants;
    }

    /**
     * Return the full list of TZ.
     *
     * @return full list of TZ as hashmap
     */
    public Map<String, TZ> getTZDatabase() {
        return tzdb;
    }

    public boolean hasTerritories() {
        return !territories.isEmpty();
    }

    public void addTerritory(Country terr) {
        territories.add(terr);
    }

    /**
     * Territory ownership is defined only by the data fed to this API;
     * We do not make any political statements here. You can change the underlying
     * flat file data
     * country-names-xxxx.csv anyway you want.
     *
     * @param n name of country or territory
     * @return true if this country owns the named territory.
     */
    public boolean ownsTerritory(String n) {
        if (hasTerritories()) {
            for (Country C : territories) {
                if (n.equalsIgnoreCase(C.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * List the territories for this country.
     * Returns an empty list if no territories associated.
     *
     * @return list of Territories, that are Country objects flagged with
     *         isTerritory = true
     */
    public Collection<Country> getTerritories() {
        return territories;
    }
}
