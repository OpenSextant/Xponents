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
package org.opensextant.extractors.xtax;

import java.util.ArrayList;
import java.util.List;

import org.opensextant.data.Taxon;
import org.opensextant.extraction.TextMatch;
import org.opensextant.util.TextUtils;

/**
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TaxonMatch extends TextMatch {

    public static final String TAXON_LABEL = "taxon";

    public TaxonMatch(int x1, int x2) {
        super(x1, x2);
        this.type = TAXON_LABEL;
        this.producer = "XTax";
    }

    @Override
    public void setText(String t) {
        super.setText(t);
        if (t != null) {
            this.hasDiacritics = TextUtils.hasDiacritics(t);
        }
    }

    public boolean isDefault(){
        return TAXON_LABEL.equals(type);
    }

    public boolean hasDiacritics = false;
    private List<Taxon> taxons = null;

    public List<Taxon> getTaxons() {
        return taxons;
    }

    public void addTaxon(Taxon t) {
        if (t == null) {
            return;
        }

        if (taxons == null) {
            taxons = new ArrayList<>();
        }
        taxons.add(t);
    }

    public boolean hasTaxons() {
        if (taxons == null) {
            return false;
        }
        return !taxons.isEmpty();
    }
}
