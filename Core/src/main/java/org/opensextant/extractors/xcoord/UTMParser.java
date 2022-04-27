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

import java.util.Map;

import org.opensextant.geodesy.UTM;

/**
 * @author ubaldino
 */
public class UTMParser {

    /**
     *
     */
    public static final char UTM_NORTH = 'N';
    /**
     *
     */
    public static final char UTM_SOUTH = 'S';

    /**
     * TODO: Needs lots of false positive filtering mainly using context. Did
     * text indicate this is UTM? Where there units, e.g., "m"?
     * Those things are currently optional.
     *
     * @param text
     * @param elements
     * @return
     */
    public static UTM parseUTM(String text, Map<String, String> elements) {

        String z = elements.get("UTMZone");
        String z1 = elements.get("UTMZoneZZ");  // 0-5\d
        String z2 = elements.get("UTMZoneZ");   // \d
        if (z == null) {
            z = (z1 != null ? z1 : z2);
        }

        if (z == null) {
            return null;
        }

        int ZZ = Integer.parseInt(z);
        String b = elements.get("UTMBand");

        if (b == null) {
            return null;
        }
        // TODO: is 'n' valid for UTM band?

        char h = b.charAt(0);
        if (h != UTM_NORTH && h != UTM_SOUTH) {
            h = UTM.getHemisphere(h);
        }

        String e = elements.get("UTMEasting");
        String n = elements.get("UTMNorthing");
        Integer E = Integer.parseInt(e);
        Integer N = Integer.parseInt(n);

        return new UTM(ZZ, h, E.doubleValue(), N.doubleValue());
    }
}
