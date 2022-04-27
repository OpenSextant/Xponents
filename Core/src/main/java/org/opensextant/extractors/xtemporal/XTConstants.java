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
package org.opensextant.extractors.xtemporal;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ubaldino
 */
public class XTConstants {

    /**
     *
     */
    public static final int MDY_FAMILY = 0;

    public static final int DMY_FAMILY = 2;
    /**
     *
     */
    public static final int DATETIME_FAMILY = 1;
    /**
     *
     */
    public static final int UNK_PATTERN = -2;

    /**
     *
     */
    public static final int ALL_PATTERNS = -1;

    /**
     *
     */
    protected static final Map<String, Integer> familyInt = new HashMap<>();
    /**
     *
     */
    protected static final Map<Integer, String> familyLabel = new HashMap<>();

    static {

        familyLabel.put(UNK_PATTERN, "UNK");
        familyLabel.put(MDY_FAMILY, "MDY");
        familyLabel.put(DATETIME_FAMILY, "DTM");
        familyLabel.put(DMY_FAMILY, "DMY");

        familyInt.put("UNK", UNK_PATTERN);
        familyInt.put("DTM", DATETIME_FAMILY);
        familyInt.put("MDY", MDY_FAMILY);
        familyInt.put("DMY", DMY_FAMILY);
    }

    /**
     * @param key
     * @return
     */
    public static int getPatternFamily(String key) {
        Integer i = familyInt.get(key);
        if (i != null) {
            return i;
        }
        return UNK_PATTERN;
    }

    /**
     * @param id
     * @return
     */
    public static String getPatternFamily(int id) {
        String key = familyLabel.get(id);
        if (key != null) {
            return key;
        }
        return "UNK";
    }

}
