# -*- coding: utf-8 -*-
"""
 
                Copyright 2014 The MITRE Corporation.
 
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy of
  the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.
 
  =============================================================================

@date: 2015-dec
@author: ubaldino

USAGE:
    PYTHONPATH=<...include libs according to README>
    python nationalities.py  --taxonomy /path/to/nationalities.csv --solr  http://localhost:7000/solr/taxcat --starting-id 0

The TaxCat/XTax strategy here is to use the following mapping:

    catalog  = nationality
    taxnode =  nationality.CCC
    phrase = name variant
    tags  = { 'cc+ISO' }

    Example:
    { 
      catalog = 'nationality',
      taxnode = 'nationality.British',
      phrase =  'brit'
      tags  = [ 'cc+GBR' ]
    }
"""

from opensextant.TaxCat import Taxon, TaxCatalogBuilder
from opensextant.utility import get_text


class Nationality(Taxon):
    def __init__(self, eid, phr, cc, is_valid):
        Taxon.__init__(self)

        self.entity_id = eid
        self.country = cc
        self.phrase = phr
        self.phrasenorm = phr.lower()
        self.is_valid = is_valid

        # taxon ID/name:
        self.name = eid
        self.tags = self._make_tags()

    def _make_tags(self):
        if self.country:
            return [f'cc+{self.country}']
        return []

    def __str__(self):
        return f"{self.id} / {self.name} ({self.phrase}) {self.tags}"


def create_entities(line):
    """
    Create a taxon entry for this nationality, which may have diacritics. All phrases are unicode.
    """
    if not line: return []
    parts = line.strip().split(',')
    name = get_text(parts[0])
    cc = parts[1].upper()

    #
    # done with aliasing.
    #            
    # is_valid = len(cc)>0
    is_valid = True
    is_ethnicity = len(cc) == 0
    taxons = []

    # Preserve this logic for now. Not officially declaring a difference between ethnicity and nationality.
    # This data catalogues a mix.
    if is_ethnicity:
        n = f"nationality.{name}"
        taxons.append(Nationality(n, name, None, is_valid))
    else:
        if ';' in cc:
            codes = cc.split(';')
            for c in codes:
                n = f'nationality.{c}'
                taxons.append(Nationality(n, name, c, is_valid))
        else:
            n = f'nationality.{cc}'
            taxons.append(Nationality(n, name, cc, is_valid))

    return taxons


if __name__ == '__main__':
    start_id = 0
    catalog_id = 'nationality'

    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument('taxonomy', help="nationalities file")
    ap.add_argument('--starting-id')
    ap.add_argument('--solr')
    ap.add_argument('--max')

    args = ap.parse_args()

    if args.starting_id:
        start_id = int(args.starting_id)

    test = args.solr is None
    row_max = -1
    builder = TaxCatalogBuilder(server=args.solr, test=test)

    if args.max:
        row_max = int(args.max)

    row_id = 0
    with open(args.taxonomy, 'r', encoding="UTF-8") as fh:
        for row in fh:
            if row.startswith("#"):
                continue

            for taxon in create_entities(row):
                # increment
                row_id = row_id + 1
                # ".id" must be an Integer for text tagger
                taxon.id = start_id + row_id

                builder.add(catalog_id, taxon)

                if row_id % 100 == 0:
                    print("Row # ", row_id)
                if test:
                    print(str(taxon))

                if 0 < row_max < row_id:
                    break

        print(f"Created total of {row_id} nationality tags")
        builder.save(flush=True)
        builder.optimize()
