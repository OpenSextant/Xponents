# -*- coding: utf-8 -*-
from opensextant.TaxCat import Taxon, get_taxnode

builder = None

catalog = "WFB"  # WFB = World FactBook
min_len = 4
min_len_acronym = 3


def evaluate_text(txn, stop):
    """
    Consolidate evaluations of text if it is valid to tag or not.
    :param txn: Taxon
    :param stop:
    :return:
    """
    if stop:
        txn.is_valid = False
    elif txn.is_acronym:
        txn.is_valid = len(txn.phrase) >= min_len_acronym
    else:
        txn.is_valid = len(txn.phrase) >= min_len


GENERIC_PHRASES = {"commonwealth", "north"}


class FactBookTaxon(Taxon):
    def __init__(self, rowdata, row_id=None, stopwords=[]):
        Taxon.__init__(self)

        self._entity_type = rowdata['entity_type']
        self.id = row_id
        self.name = rowdata['name']
        self.is_valid = False

        # WFB custom metadata.
        self.variants = []
        self.variant_count = 1
        self.distinct = set([])

        if not self.name or not self._entity_type:
            return

        self.phrase = self.name.lower().strip()
        if not self.phrase:
            return

        self.is_acronym = self.name.isupper()

        ## If the entire value is a stop word we'll mark it as invalid.
        stop = self.phrase in stopwords or self.phrase in GENERIC_PHRASES

        self.distinct.add(self.phrase)
        # Taxon.name is now reassigned to a normalized taxon key,  name = TYPE.NAME
        self.name = get_taxnode(self._entity_type, self.name.title())
        evaluate_text(self, stop)
        if not self.is_valid:
            print("Ignore generic term ", self.phrase)

        # Othermetadata for Factbook: Country and Orgs associated.
        # Normalize country to country code?
        attrs = {
            "country": "co",
            "official_title": "official_title",
            "personal_title": "personal_title"
        }
        self.tags = []
        for src in attrs:
            dst = attrs[src]
            if src in rowdata:
                val = rowdata.get(src)
                fld = '{}+{}'.format(dst, val.lower())
                self.tags.append(fld)

        self.variants.append(self)
        self.add_alias(rowdata.get("aliases"), stopwords=stopwords)

    def add_alias(self, aliases, stopwords=[]):
        """
        Parse and add names from "Name1; Name2; ..."
        associating each alias with this Taxon.

        :param aliases: delimited list of aliases for a taxon.
        :return:
        """
        if not aliases:
            return
        for A in aliases:
            _phrase = A.lower()
            if _phrase in self.distinct:
                continue

            alias = Taxon()
            # Taxon ID is computed on export to Solr.
            # Taxon name:
            alias.name = self.name
            # This variant on the taxon:
            alias.phrase = _phrase
            alias.is_acronym = A.isupper()
            alias.tags = self.tags
            _is_stopwd = _phrase in stopwords or _phrase in GENERIC_PHRASES
            evaluate_text(alias, _is_stopwd)

            if not alias.is_valid:
                print("Ignore stop word variant '{}' on {}".format(alias.phrase, self.name))

            self.variants.append(alias)
            self.variant_count += 1


def ingest_wfb_leaders(dct, fpath, stopwords=[]):
    """
    Add taxonomy in file to builder.
    TODO: merge multiple files into dct.
    :param taxcat:
    :param fpath:
    :return:
    """
    with open(fpath, "rU", encoding="UTF-8") as fh:
        countries = json.load(fh)
        for cc in countries:
            leaders = countries.get(cc)
            print("{} count {}".format(cc, len(leaders)))
            leaders_nodes = []
            for leader in leaders:
                leader['entity_type'] = "person"
                leader['country'] = cc
                leader['name'] = leader['name'].title()
                node = FactBookTaxon(leader, stopwords=stopwords)
                leaders_nodes.extend(node.variants)
            dct[cc] = leaders_nodes
    return


def ingest_wfb_orgs(dct, fpath, stopwords=[]):
    """
    organize taxons as
        { taxon_id : [ taxon_variant, taxon_variant,... ]
        }
    TODO: resolve multiple files that may have duplicative taxons/updates.
    :param dct:
    :param fpath:
    :return:
    """
    with open(fpath, "rU", encoding="UTF-8") as fh:
        parent = json.load(fh)
        org_nodes = {}
        orgs = parent['orgs_and_groups']
        print("Orgs count {}".format(len(orgs)))

        for org in orgs:
            org['entity_type'] = "org"
            node = FactBookTaxon(org, stopwords=stopwords)
            org_nodes[node.name] = node.variants
        dct.update(org_nodes)
    return


if __name__ == '__main__':

    import json
    import sys
    # import os
    import argparse
    import glob
    from opensextant.TaxCat import TaxCatalogBuilder, get_starting_id
    from opensextant.CommonsUtils import ConfigUtility

    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    args = ap.parse_args()
    util = ConfigUtility()
    stopwords = util.loadListFromFile("etc/taxcat/stopwords.txt")
    start_id = get_starting_id(catalog)

    args.solr

    builder = TaxCatalogBuilder(server=args.solr, test=args.solr is None)
    if not args.solr:
        print("Running in Test mode only -- No indexing to Solr")

    # Major TODO: if we have multiple cached versions of WFB leaders, they need to be folded into a superset,
    # deconflicted by name/taxon and then indexed.
    files = glob.glob("etc/taxcat/data/wfb-leaders*json")
    master = {}
    for fpath in files:
        ingest_wfb_leaders(master, fpath, stopwords=stopwords)

    tid = start_id
    for cc in master:
        taxons = master[cc]
        for taxon in taxons:
            taxon.id = tid
            builder.add("WFB", taxon)
            tid += 1

    files = glob.glob("etc/taxcat/data/wfb-orgs*json")
    master = {}
    #
    # Load Taxons as a flat dictionary of 'taxon1' = [ taxon1_a, taxon1_b,... ]
    # so each taxon1_* represents a variation on taxon1
    for fpath in files:
        ingest_wfb_orgs(master, fpath, stopwords=stopwords)

    for taxon_name in master:
        for taxon in master[taxon_name]:
            taxon.id = tid
            builder.add("WFB", taxon)
            tid += 1

    builder.save(flush=True)
    builder.optimize()
