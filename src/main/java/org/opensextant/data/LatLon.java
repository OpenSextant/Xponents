/**
 *
 *  Copyright 2012-2013 The MITRE Corporation.
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
 */
package org.opensextant.data;

/**
 *
 * @author ubaldino
 */
public interface LatLon {

    /**
     * @return lat in degrees
     */
    public double getLatitude();

    /**
     * @return lon in degrees
     */
    public double getLongitude();

    /**
     * @param latitude in degrees
     */
    public void setLatitude(double latitude);

    /**
     * @param longitude in degrees
     */
    public void setLongitude(double longitude);

}
