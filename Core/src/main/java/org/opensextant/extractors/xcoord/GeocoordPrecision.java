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
package org.opensextant.extractors.xcoord;

/**
 * @author ubaldino
 */
public class GeocoordPrecision {

    /**
     * +/- # of Meters of error
     */
    public double precision = 0.0;
    /**
     * # of decimal places in D.ddd... string for a lat or lon
     */
    public int digits = 0;

    /**
     * Augment number of digits in precision -- choose the maximum amount
     * if in coord (a,b) if a has more digits of precision than b, use a's
     * precision.
     * This is really only a matter of typos, where typist may have added 4 digits
     * instead of 5, for example.
     *
     * @param d
     */
    public void setDigits(int d) {
        if (d > digits) {
            digits = d;
        }
    }
}
