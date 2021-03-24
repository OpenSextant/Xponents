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
 * Filtering matches is a matter of practicality.
 * This does not validate matches, per se
 * You can implement this interface as a ValidationFilter(?)
 * Implementer is left to choose if pass or stop is implemented;
 * ideally, pass(m) == !stop(m)
 *
 * @author ubaldino
 */
public interface GeocoordMatchFilter {

    /**
     * pass a match
     *
     * @param m match
     * @return true if instance m is valid coordinate
     */
    boolean pass(GeocoordMatch m);

    /**
     * stop a match
     *
     * @param m match
     * @return true if instance m is NOT valid coordinate
     */
    boolean stop(GeocoordMatch m);
}
