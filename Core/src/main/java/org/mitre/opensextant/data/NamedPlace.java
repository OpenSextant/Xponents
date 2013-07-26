/*
 * Copyright 2013 ubaldino.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mitre.opensextant.data;

/**
 * This is a place instance that has a hook for a PlaceName object, which
 * expands on the linguistics of the name itself, e.g., name tokens, ngrams,
 * language, etc. 
 * 
 * As well, a named place is more than just a coordinate, it is more likely to 
 * have other metadata such as Country and Province, so those object pointers are 
 * offered here.  This keeps the very basic Place parent class as light as can be 
 * for the most general uses.
 *
 * Still in incubation.
 *
 * @author ubaldino
 */
public class NamedPlace extends Place {

    private PlaceName placeName = null;
    /**
     * If you can retrieve the Province by ID, attach it here for future
     * reference.
     */
    public Place province = null;

    /**
     *
     */
    public Country country = null;
        
    public NamedPlace(String id, String name) {
        super(id, name);
    }

    public void setPlaceNameObject(PlaceName pname) {
        placeName = pname;
    }

    public PlaceName getPlaceNameObject() {
        return placeName;
    }
}
