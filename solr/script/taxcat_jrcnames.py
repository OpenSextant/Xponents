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

Created on Nov 19, 2014

@author: ubaldino

Demonstration of how to ingest a catalog of data to use with XTax.
Here JRC Names (published by JRC in the EU) is a collection of 
name/org entities that lists name variants in various languages.

REFERENCE: 
    https://ec.europa.eu/jrc/en/language-technologies/jrc-names
    Data is the resource file download, entities.gzip

USAGE:
    PYTHONPATH=<...include libs according to README>
    python jrcnames.py  /my/data/JRCNames/entities.txt   http://localhost:7000/solr/taxcat

    # python jrcnames.py  /patht/to/entities.txt   <solr url>

"entities.txt" file is a simple, TAB delimited file containing a
universal ID, entity type, language and name variant.

    1000595 P       u       Dani+Ábalo
    
The TaxCat / XTax strategy here is to use the following mapping:

    catalog  = JRC
    taxnode =  name type + primaryname
    phrase = name variant
    { 
      catalog = JRC,
      taxnode = "Person.Dani Abalo",
      phrase =  "Dani Ábalo"
      tags  = [ "jrc=1000595", "lang=u" ]
      
    }
      
Apparently lang = "u" implies the name is universal in all languages, as spelled.
Where possible the first universal name variant found will act as the taxon primary name, 
creating the taxnode value.

"""
import re
import os

from opensextant.utility import is_ascii, has_cjk
from opensextant.TaxCat import Taxon, TaxCatalogBuilder, get_starting_id
from opensextant import load_us_provinces

load_us_provinces()
from opensextant import usstates

ignore_provinces = []
for adm1 in usstates:
    adm1_place = usstates[adm1]
    ignore_provinces.append(adm1_place.name.lower())

# distinct primary names act as a JRC root entry; all others would be variants
#    That is just the convention here for demo sake.
nameset = {}
names = set([])
# ID map -- distinct JRC IDs must be reproducible and unique for each variant.
idset = {}

jrc_line_split = re.compile(r"\s+")

NO_ID_BASE = 800000
no_id_counter = 0

# Ambiguous phrases or noisy ones I would prefer not to tag, so
# they are marked valid=false, but retained in the names taxonomy.
# These are all valid JRC entities, however they do not seem to add value due to their
# popular use or ambiguity.  On very rare occasions there are instances of person names
# That coincide with popular place names.  The person name is marked is not valid for tagging.
NOISE = {
    "north", "south", "east", "west",
    "start", "end", "total",
    "times",
    "the sun",
    "the times",
    "news agency",
    "daily news",
    "press agency",
    "military intelligence",
    "international studies",
    "international trade",
    "will meet",
    "the nation",
    "facebook",
    "google",
    "internet explorer",
    "yahoo",
    "twitter",
    "youtube",
    "people",
    "they are",
    "our own",
    "just want",
    "are you", "all you",
    "ps",
    "pp",
    "reach",
    "armed forces",
    "canal",
    "the age",
    "read more",
    "presedential office",
    "set fire",
    "emergency",
    "nature",
    "status quo",
    "the independent",
    "gross domestic product",
    "privacy policy",
    "adobe reader",
    "guiding principles",
    "lessons learned",
    "better life",
    u"san diegó",  # not San Diego
    "san diego",  # not San Diego
    u"san franciscó",
    "san francisco",
    "corpus christi",
    u"nuevo león",
    u"nuevo léon",
    "nuevo leon",
    "san pedro",
    "umm qasr",
    "windows"
}

# Fixes are any entries that need to be remapped to entity type, p, o, etc. 
#
FIXES = {
    # Somehow this entry was marked as a "p" (Person)
    # So it is remapped to "-" (Thing, default)
    "Improvised Explosive Device": "-",
    "Liberty Reserve": "O",
}

apply_default_fixes = True


def check_validity(e):
    """
    @param e: a JRCEntity 
    """
    phr = e.phrasenorm.replace(",", " ")
    if phr in NOISE:
        e.is_valid = False
    return


# Entries starting or ending with place markers, will be recategorized as a Place.xxxxx
#
PLACE_ENDING_FIXES = {"province", "island", "islands", "district", "peninsula", "valley",
                      "territory", "county", "city", "state", "township", "village",
                      "roads", "avenue", "avenida", "prefecture", "heights", "springs", "falls",
                      "airport", "aeropuerto", "aeroporto", "station", "harbor", "harbour", "port"}

PLACE_STARTING_FIXES = {"city", "spin", "town", "fort", "port"}

# A large number of entities are marked as Person unnecessarily.
# Org fixes: find any of these terms in a phrase and declare the phrase an org, if not already.
ORG_FIXES = {"tribunal", "council", "shura", "group", "committee", "senate", " of state",
             "departamento", "department", "transport", "transit", "musee", "museum",
             "museums", "organization", "authority", "enterprise", "institute", "inc", "llc"}


class JRCEntity(Taxon):
    def __init__(self, eid, variant_id, etype, lang, primary_name, variant):
        Taxon.__init__(self)

        # JRC original entity ID and type
        self.entity_id = eid
        self.entity_type = etype.upper()
        self.lang = lang
        self.phrase = variant
        self.phrasenorm = variant.lower()

        if self.phrase in FIXES:
            self.entity_type = FIXES.get(self.phrase)

        if apply_default_fixes:
            tokens = self.phrasenorm.split()
            if tokens[-1] in PLACE_ENDING_FIXES:
                # Place (T=terrain)
                self.entity_type = "T"
                print("Place Phrase fixed", self.phrase)
            elif tokens[0] in PLACE_STARTING_FIXES:
                self.entity_type = "T"
                print("Place Phrase fixed", self.phrase)
            elif tokens[-1] in ignore_provinces:
                self.entity_type = "T"
                print("Ignore Province name in token", self.phrase)

            if self.entity_type == "P":
                if self.phrasenorm.startswith("liberty "):
                    # JRC tagging got this wrong 98% of the time. Most Liberty... phrases are Orgs, not Pers.
                    self.entity_type = "O"
                else:
                    for tok in tokens:
                        if tok in ORG_FIXES:
                            self.entity_type = "O"
                            print("Org Phrase fixed", self.phrase)
                            break

        if self.entity_type in entity_map:
            self.entity_type = entity_map[self.entity_type]

        self.variant_id = variant_id
        # solr record ID:
        self.id = self._make_id()
        l = len(variant)
        # General validity
        self.is_valid = l > 2 or has_cjk(variant)
        # specifically acronyms.
        self.is_acronym = variant.isupper() and is_ascii(variant) and " " not in variant and l <= 12
        if self.is_acronym:
            print("Acronym?", variant)

        # taxon ID/name:
        self.name = "%s.%s" % (self.entity_type, primary_name)

        self.tags = self._make_tags()

    def _make_id(self):
        return f"{self.entity_id}#{self._variant_id()}"

    def _variant_id(self):
        if not self.variant_id:
            return "0"
        return "%04x" % (self.variant_id)

    def _make_tags(self):

        return [
            f"jrc_id+{self.entity_id}",
            f"lang_id+{self.lang}"
        ]

    def __str__(self):
        return f"{self.name} ({self.phrase})"


other_types = {"U", "T", "E", "p"}
entity_map = {
    "U": "Unknown",
    "T": "Place",
    "E": "Event",
    "O": "Org",
    "P": "Person",
    "-": "Thing"
}


def create_entity(line, scan=False):
    """ Create a entry for this entity that has the primary name.
    the "line" of data must be already UTF-8 or ASCII.  We avoid using a default encoding.
    @keyword scan: scan=True means we are looking for root entries to which 
    other variants are associated.  Each variant has an entity ID, but there
    is no single primary variant to represent the entity, e.g., for presentation purposes.
    """
    global no_id_counter
    line = line.strip()
    if not line:
        return None
    parts = jrc_line_split.split(line, maxsplit=3)
    _id = parts[0]
    if _id == "0":
        if test:
            print("Ignore line? ID=0, ", line)
        no_id_counter += 1
        _id = no_id_counter + NO_ID_BASE
    else:
        _id = int(_id)

    if len(parts) != 4:
        print(f"Must be 4 distinct whitespace-delimited fields '{line}'")
        return None

    name = parts[3]
    for a, b in [("+", " "), ("&amp;", "&"), ("&quot;", '"')]:
        name = name.replace(a, b)
    name = name.strip().strip("!").strip(";")
    lang = parts[2]
    etype = parts[1]

    if not etype:
        print("No valid type; Ignoring line, ", line)
        return None

    if scan:
        # if etype in other_types:
        #    print line
        #    return None
        lookup_id = f"{lang}{_id}"
        if lookup_id not in nameset:
            nameset[lookup_id] = name

        if test:
            if name in names:
                print("Duplicate", name, lookup_id)
            else:
                names.add(name)

        return None

    primary_name = None
    for lookup_id in [f"u{_id}", f"{lang}{_id}"]:
        primary_name = nameset.get(lookup_id)
        if primary_name: break

    if not primary_name:
        print("Failed to find primary name for ", line)
        primary_name = name

    count = 1
    if _id in idset:
        count = idset.get(_id) + 1
    idset[_id] = count
    variant_id = count
    # if variant_id > 1:
    #    print("Multiple ~ ", name)
    #
    # done with aliasing.
    #            
    e = JRCEntity(_id, variant_id, parts[1], lang, primary_name, name)
    check_validity(e)
    return e


if __name__ == "__main__":
    """
    Index JRC Names entities as a lexicon useful for tagging text.
    """
    catalog_id = "JRC"
    start_id = get_starting_id("JRC")

    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument("--taxonomy", required=True, help="Taxonomy file in JRCNames format")
    ap.add_argument("--starting-id", help="Solr index row ID start")
    ap.add_argument("--solr", help="Solr URL for taxcat index")
    ap.add_argument("--max", help="Max # of rows to index; for testing")
    ap.add_argument("--invalidate", action="store_true", default=False)
    ap.add_argument("--no-fixes", action="store_true", default=False)

    args = ap.parse_args()

    print(f"""
    TaxCat Builder for Taxonomy: {args.taxonomy}
    """)

    if args.starting_id:
        start_id = int(args.starting_id)

    only_mark_invalid = args.invalidate
    apply_default_fixes = not args.no_fixes

    row_max = -1
    test = args.solr is None
    builder = TaxCatalogBuilder(server=args.solr, test=test)

    if args.max:
        row_max = int(args.max)
    elif test:
        row_max = 100000

    # Commit rows every 10,000 entries.
    builder.commit_rate = 10000

    stopterms_file = os.path.join("etc", "taxcat", "stopwords.txt")
    builder.add_stopwords(stopterms_file)

    # Completely arbitrary starting row ID 
    # Manage your own Catalog regsitry for starting rows
    # As solr has no notion of row ID and your uniqueness requirements.
    #
    row_id = 0
    with open(args.taxonomy, "r", encoding="UTF-8") as fh:
        for row in fh:
            if row.startswith("#") or len(row.strip()) == 0: continue

            row_id = row_id + 1
            create_entity(row, scan=True)
            if row_id % 100000 == 0:
                print("Row # ", row_id)

            if 0 < row_max < row_id:
                break

    # Zero these counters:
    row_id = 0
    no_id_counter = 0
    with open(args.taxonomy, "r", encoding="UTF-8") as fh:
        for row in fh:
            if row.startswith("#") or len(row.strip()) == 0: continue

            node = create_entity(row)
            row_id = row_id + 1
            if not node:
                continue

            # ".id" must be an Integer for text tagger
            node.id = start_id + row_id

            if only_mark_invalid and node.is_valid:
                continue

            if node.phrasenorm in builder.stopwords:
                # Keep the term, but indicate it is not valid for tagging
                node.is_valid = False

            builder.add(catalog_id, node)

            if row_id % 100000 == 0:
                print("Row # ", row_id)
            if test:
                print(str(node))
            if 0 < row_max < row_id:
                break

        builder.save(flush=True)
        builder.optimize()
