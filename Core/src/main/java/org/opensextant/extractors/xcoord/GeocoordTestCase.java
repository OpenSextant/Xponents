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

import org.opensextant.extractors.flexpat.PatternTestCase;

/**
 * @author ubaldino
 */
public class GeocoordTestCase extends PatternTestCase {

    /**
     * @param _id
     * @param _family
     * @param _text
     */
    public GeocoordTestCase(String _id, String _family, String _text) {

        super(_id, _family, _text);

        this.family_id = XConstants.get_CCE_family(_family);
        this.match = new GeocoordMatch(-1, -1);
    }

    public GeocoordMatch getGeo() {
        return (GeocoordMatch) this.match;
    }
}
