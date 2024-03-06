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
package org.opensextant.data;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Taxon is an entry in a taxonomy, which could be as simple as a flat word
 * list
 * or something with lots of structure. The name attribute represents the
 * hierarchical path for the Taxon.
 * <ul>
 * <li>
 * "terms" are the phrases you wish to find in free text. This is the common
 * vernacular that implies this taxon or concept.
 * e.g., "la piña" may be a term that we want to file under
 * <code>"fruits.tropical.pineapple"</code>
 * </li>
 * <li>
 * "tags" are any metadata items associated with the terms or the taxon, e.g.,
 * source of terms, database identifier,
 * language of terms, contributing staff
 * </li>
 * </ul>
 *
 * @author ubaldino
 */
public class Taxon {

    /** Catalog, for example "fruit" */
    public String catalog = null;

    /** Node name: citrus.tropical */
    public String name = null;
    /** Nod OID: 1.2.3 */
    public String id = null;
    /** Node root: citrus */
    public String rootid = null;
    /** Terms: "pineapple", "mango", ... */
    public Set<String> termset = null;
    public Set<String> tagset = null;
    /**
     * Acronyms are assumed to be ALL UPPER CASE; granted this does not always
     * apply. But this is used to inform post-processing if a match, such as
     * 'abc' matched 'ABC' incorrectly. If the match is lower case, but the
     * Taxon is Acronym, then you have a mismatch of case and semantics likley.
     */
    public boolean isAcronym = false;

    public void setName(String n) {
        name = n;
    }

    public void setId(String i) {
        id = i;
    }

    public void setRootid(String i) {
        rootid = i;
    }

    public void setTerms(String[] t) {
        if (t == null) {
            return;
        }
        if (termset == null) {
            termset = new HashSet<>();
        }
        termset.addAll(Arrays.asList(t));
    }

    public boolean hasTags() {
        return (tagset != null);
    }

    public void addTags(List<String> tlist) {
        if (tlist == null) {
            return;
        }
        if (tagset == null) {
            tagset = new HashSet<>();
        }
        tagset.addAll(tlist);
    }

    public void addTag(String t) {
        if (t == null) {
            return;
        }

        if (tagset == null) {
            tagset = new HashSet<>();
        }
        tagset.add(t);
    }

    public void addTerm(String t) {
        if (termset == null) {
            termset = new HashSet<>();
        }
        termset.add(t);
    }

    public void addTerms(Collection<String> tlist) {
        if (tlist == null) {
            return;
        }
        if (termset == null) {
            termset = new HashSet<>();
        }
        termset.addAll(tlist);
    }

    @Override
    public String toString() {
        if (termset == null) {
            return name;
        }
        return name + " " + termset;
    }
}
