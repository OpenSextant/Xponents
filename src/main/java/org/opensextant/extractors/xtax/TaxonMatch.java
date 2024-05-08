/*
 *
 * Copyright 2012-2015 The MITRE Corporation.
 *
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

    public TaxonMatch(int x1, int x2) {
        super(x1, x2);
        this.type = VAL_TAXON;
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
        return VAL_TAXON.equals(type);
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
