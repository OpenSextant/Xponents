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

/**
 * An interface that describes any data that can be geocoded -- the metadata
 * behind deriving
 * location is as important as the actual location is. Important features
 * include location precision, confidence and method.
 * For confidence, the Xponents convention is a 100 point integer scale.
 * For precision, report the accuracy of your geocoding in terms of meters of
 * error, e.g., <code>precision  = 5000 (m)</code>
 * suggests your geocoding around point (Y,X) has an error of 5 KM.
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public interface Geocoding extends LatLon {

    // -------------------
    // High level flags: These attributes outline what this geocoding represents - a
    // place, landmark, site, coordinate, etc.
    // -------------------
    /**
     * @return true if geocoding represents a named place
     */
    boolean isPlace();

    /**
     * isCoordinate: if this object represents a coordinate
     *
     * @return true if geocoding represents a coordinate
     */
    boolean isCoordinate();

    /**
     * has Coordinate: if this named place object has a coordinate.
     *
     * @return true if geocoding represents has a valid lat, lon
     */
    boolean hasCoordinate();

    boolean isCountry();

    boolean isAdministrative();

    /**
     * Precision - radius in meters of possible error
     *
     * @return precision error radius
     */
    int getPrecision();

    /**
     * Precision - radius in meters of possible error
     *
     * @param m meters of error
     */
    void setPrecision(int m);

    // ---------------------
    // entity metadata:
    // ---------------------
    String getCountryCode();

    void setCountryCode(String cc);

    void setCountry(Country c);

    String getAdmin1();

    String getAdmin2();

    String getAdminName();

    String getAdmin1Name();

    String getAdmin2Name();

    String getFeatureClass();

    String getFeatureCode();

    String getPlaceID();

    String getPlaceName();

    void setPlaceName(String n);

    /**
     * State-level postal code, the corresponds usually to ADM1
     *
     * @return optional postal code
     */
    String getAdmin1PostalCode();

    /**
     * City-level postal code, that may be something like a zip.
     * Thinking world-wide, not everyone calls these zipcodes, as in the US.
     *
     * @return optional postal code
     */
    String getPlacePostalCode();

    /**
     * @return Method for determining geocoding
     */
    String getMethod();

    void setMethod(String m);

    /**
     * Confidence metric is a normalized 100-point scale.
     * Xponents conventions are simple, so value of confidence is an integer.
     *
     * @return value on a 100 point scale.
     */
    int getConfidence();

    /**
     * Set confidence, a value on a 100 point scale, 0-100.
     * Yes values above or below scale are allowed, however
     * it may be difficult to compare such values. The intent is to normalize
     * all confidence metrics to this relative scale for your application.
     *
     * @param c confidence
     */
    void setConfidence(int c);
}
