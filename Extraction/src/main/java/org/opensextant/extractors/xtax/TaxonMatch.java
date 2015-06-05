/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *               http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 *
 * **************************************************************************
 * NOTICE This software was produced for the U. S. Government under Contract No.
 * W15P7T-12-C-F600, and is subject to the Rights in Noncommercial Computer
 * Software and Noncommercial Computer Software Documentation Clause
 * 252.227-7014 (JUN 1995)
 *
 * (c) 2012 The MITRE Corporation. All Rights Reserved.
 * **************************************************************************
 *
 * Continue contributions:
 *    Copyright 2013-2015 The MITRE Corporation.
 */
package org.opensextant.extractors.xtax;

import java.util.List;
import java.util.ArrayList;

import org.opensextant.data.Taxon;

/**
 *
 * @author Marc C. Ubaldino, MITRE, ubaldino at mitre dot org
 */
public class TaxonMatch extends org.opensextant.extraction.TextMatch {

    public TaxonMatch(){
        this.type = "taxon";
        this.producer = "XTax";
    }

    private List<Taxon> taxons = null;

    public List<Taxon> getTaxons(){
        return taxons;
    }

    public void addTaxon(Taxon t) {
        if (t == null) {
            return;
        }

        if (taxons == null) {
            taxons = new ArrayList<Taxon>();
        }
        taxons.add(t);
    }

    public boolean hasTaxons(){
        if (taxons == null){
            return false;
        }
        return ! taxons.isEmpty();
    }
}
