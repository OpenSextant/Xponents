/*
 *
 * Copyright 2012-2013 The MITRE Corporation.
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
package org.opensextant.extractors.xcoord;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ubaldino
 */
public final class XConstants {

    /**
     *
     */
    public static final int UNK_PATTERN = 0;
    /**
     *
     */
    public static final int DD_PATTERN = 1;
    /**
     *
     */
    public static final int DM_PATTERN = 2;
    /**
     *
     */
    public static final int DMS_PATTERN = 3;
    /**
     *
     */
    public static final int MGRS_PATTERN = 4;
    /**
     *
     */
    public static final int UTM_PATTERN = 5;
    /**
     *
     */
    public static final int ALL_PATTERNS = -1;

    /**
     *
     */
    public static final int UNFILTERED = -1;
    /**
     *
     */
    public static final int FILTERED_OUT = 0;
    /**
     *
     */
    public static final int FILTERED_IN = 1;

    /** RUNTIME FLAGS: filter DMS coordinates */
    public static final int DMS_FILTERS_ON = 0x01;
    /**
     * RUNTIME FLAGS: filter Decimal Degree coordinates -- primarily for bare
     * floating point numbers w/out hemisphere or other symbols
     */
    public static final int DD_FILTERS_ON = 0x02;
    /**
     * RUNTIME FLAGS: filter MGRS coordinates -- date patterns and NUM PER NUM
     * patterns e.g., the ratio "4 per 4000" is not MGRS
     */
    public static final int MGRS_FILTERS_ON = 0x04;

    /** Strict MGRS parsing does not allow typos or mismatched Northing/Easting and possibly other parsing problems.*/
    public static final int MGRS_STRICT_ON = 0x08;
    /**
     * RUNTIME FLAGS: filter out coordinate matches that appear embedded in other
     * text, e.g., ABC45.44,77.1W
     */
    public static final int CONTEXT_FILTERS_ON = 0x20;
    /** RUNTIME FLAGS: filter all coordinate patterns that have filters */
    public static final int FLAG_ALL_FILTERS = (MGRS_FILTERS_ON |MGRS_STRICT_ON| DD_FILTERS_ON | DMS_FILTERS_ON | CONTEXT_FILTERS_ON);

    /** RUNTIME FLAGS: extract context or not */
    public static final int FLAG_EXTRACT_CONTEXT = 0x10;

    /**
     *
     */
    public static final Map<String, Integer> familyInt = new HashMap<>();
    /**
     *
     */
    public static final Map<Integer, String> familyLabel = new HashMap<>();

    static {

        familyLabel.put(UNK_PATTERN, "UNK");
        familyLabel.put(DD_PATTERN, "DD");
        familyLabel.put(DM_PATTERN, "DM");
        familyLabel.put(DMS_PATTERN, "DMS");
        familyLabel.put(MGRS_PATTERN, "MGRS");
        familyLabel.put(UTM_PATTERN, "UTM");

        familyInt.put("UNK", UNK_PATTERN);
        familyInt.put("DD", DD_PATTERN);
        familyInt.put("DM", DM_PATTERN);
        familyInt.put("DMS", DMS_PATTERN);
        familyInt.put("MGRS", MGRS_PATTERN);
        familyInt.put("UTM", UTM_PATTERN);

    }

    /**
     * Get the CCE family for the given string/key
     *
     * @param nm
     * @return
     */
    public static int get_CCE_family(String nm) {

        Integer id = familyInt.get(nm);
        if (id != null) {
            return id;
        }

        return UNK_PATTERN;
    }

    /**
     * Get the CCE family for the given XConstants enum id
     *
     * @param id
     * @return
     */
    public static String get_CCE_family(int id) {

        String nm = familyLabel.get(id);
        if (nm != null) {
            return nm;
        }

        return "UNK";
    }

}
