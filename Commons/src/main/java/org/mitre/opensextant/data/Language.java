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
 * Simple mapping of ISO 639 id to display name for languages
 *
 * @author ubaldino
 */
public class Language {

    private String iso2 = null;
    private String iso3 = null;
    private String name = null;

    /** A normalize view of a Language  - ISO 639-2 and -1 codes and display name
     *  codes are lower cased.
     */
    public Language( String id3, String id2, String name) {
        iso3 = id3;
        iso2 = id2;
        if (id3!=null){
            iso3 = id3.toLowerCase();
        }
        if (id2!=null){
            iso2 = id2.toLowerCase();
        }
        this.name = name;
    }

    /** Provide display name; English */
    public String getName() {
        return this.name;
    }

    /** Returns ISO 639-2 3-char code 
     */
    public String getCode() {
        return this.iso3;
    }
    
    public String getISO639_1_Code(){
        return this.iso2;
    }
    
    @Override
    public String toString(){
        return getName();
    }
}
