# -*- coding: utf-8 -*-
import re

from opensextant.gazetteer import DB, DataSource, as_place, SCRIPT_CODES
from opensextant.utility import get_csv_reader

"""
Name Variant Generator:  create as many valid name variants for popular 
abbreviated phrases in names.

    "S. Pedro"   (instead of San Pedro)
    "Sta Maria"  (instead of Santa Maria)
    
Seems trivial, however we miss lots of potential mentions because 
many times only the short form is used:  St. Louis,...

"""

debug = False
splitter = re.compile(u"[-.`'\u2019\s]+", re.UNICODE | re.IGNORECASE)
ORIGINALS_BLOCK = 20000000


def tester():
    res = splitter.split("Saint Bob's Pond")
    res = splitter.split("Saint Bob' Pond")
    res = splitter.split("Sant' Bob Pond")
    print(res)

    replacements = {}

    term = 'saint'
    replacements[term] = 'st. '
    pat = r"({}[-`'\u2019\s]+)".format(term)
    regex = re.compile(pat, re.UNICODE | re.IGNORECASE)

    test = 'Saint-Pryvé-Saint-Mesmin'
    repl = replacements[term].capitalize()
    nVar = regex.sub(repl, test)
    nVar = nVar.replace('-', ' ').strip()
    nVar = nVar.replace('  ', ' ')
    print(nVar)


class NameGenerator:
    def __init__(self, dbf):
        self.name = "a name generator"
        self.db = DB(dbf)
        self.terms = dict()

    def generate_variants(self):
        pass


# This source ID
XPGEN = "XP"


class GeneralNameVariants(NameGenerator):
    def __init__(self, dbf):
        NameGenerator.__init__(self, dbf)
        self.name = "General Names (fort, mount, n/s/e/w)"

        self.terms = {
            'mount': 'mt. ',
            'fort': 'ft. ',
            'north': 'n. ',
            'south': 's. ',
            'west': 'w. ',
            'east': 'e. '
        }

    def generate_variants(self):
        """
        Ft Worth <<==== Fort Worth
        N. Hampstead <<== North Hampstead
        W. Bedford Falls   <<== West Bedford Falls

        We generate variants that do not already exist.

        Almost as exact as SAINT replacements...
        """
        global count

        print(f"====={self.name}=======")
        for term in self.terms:
            search_term = term.capitalize()
            regex = re.compile(f"({term}\s+)", re.UNICODE | re.IGNORECASE)
            repl = self.terms[term].capitalize()
            print("PREFIX", term)
            termlen = len(term)
            for fc in ["A", "P"]:
                # Find any original names like "Fort ..." or "West ..." etc.
                for pl in self.db.list_places(fc=fc,
                                              criteria=f' AND source != "{XPGEN}" AND name like "{search_term} %"'):
                    nm = pl.name
                    if len(nm) - termlen <= 4:
                        continue

                    count += 1
                    variant = regex.sub(repl, nm)
                    variant = variant.replace('-', ' ').replace('  ', ' ')

                    print(f"\tVariant: {variant}")
                    pl.id = ORIGINALS_BLOCK + count
                    pl.name = variant
                    pl.source = XPGEN
                    self.db.add_place(pl)
        self.db.close()


class SaintNameVariants(NameGenerator):
    def __init__(self, dbf):
        NameGenerator.__init__(self, dbf)
        self.name = "Saintly Name generator"
        self.terms = {
            # 'santos' : 'sto',  have not found good variation on 'santos'
            'sant': 's. ',
            'santa': 'sta. ',
            'santo': 'sto. ',
            'saint': 'st. ',
            'sainte': 'ste. ',
            'san': 's. '
        }

    def generate_variants(self):
        """
        Valid French village abbreviations:
            St Pryvé St Mesmin   <<<--- Saint-Pryvé-Saint-Mesmin)

            Sta Maria           <<---Santa Maria

            Because 'San' is a typical syllable in Asian languages, we'll ignore certain countries

        """
        global count

        print(f"====={self.name}=======")
        ignore_countries = set("TW CN JP LA VN ML PA KR KP".split())
        for term in self.terms:
            regex = re.compile(f"({term}[-`'\u2019\s]+)", re.UNICODE | re.IGNORECASE)
            repl = self.terms[term].capitalize()
            term_ = '{} '.format(term)
            term_dash = '{}-'.format(term)
            search_term = term.capitalize()
            termlen = len(term)

            print(f"PREFIX '{term_}'")
            for fc in ["A", "P"]:
                for pl in self.db.list_places(fc=fc,
                                              criteria=f' AND source != "{XPGEN}" AND name like "{search_term}%"'):
                    if pl.country_code in ignore_countries and term == "san":
                        continue

                    nm = pl.name
                    norm = nm.lower()

                    # Prefix pattern!
                    if not (norm.startswith(term_) or norm.startswith(term_dash)):
                        continue

                    if len(nm) - termlen <= 3:
                        # Avoid variants that produce abbreviations and are very short.
                        # "Saint Oz" --> "S. Oz";  "Saint Oz" - "Saint " is only 2 chars.
                        print("Short variant avoided", norm)
                        continue

                    count += 1
                    variant = regex.sub(repl, nm)
                    variant = variant.replace('-', ' ').replace('  ', ' ')

                    print(f"\tVariant: {variant}")
                    pl.id = ORIGINALS_BLOCK + count
                    pl.name = variant
                    pl.source = XPGEN
                    self.db.add_place(pl)
        self.db.close()


class AdhocNameVariants(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_name = "Xponents Adhoc Names"
        self.source_keys = ["X"]

    def process_source(self, sourcefile):
        """
        ingest the standard merged file from the Gazetteer project
        :param sourcefile: the Merged gazetteer file
        :return:
        """
        header_names = ['place_id', 'name', 'feat_class', 'feat_code', 'adm1',
                        'cc', 'FIPS_cc', 'source', 'name_bias', 'id_bias', 'name_type', 'lat', 'lon']
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            df = get_csv_reader(fh, delim=",", columns=header_names)
            self.purge()
            for row in df:
                if row["place_id"] == "place_id":
                    continue
                self.rowcount += 1
                pl = as_place(row)
                pl.id = ORIGINALS_BLOCK + self.rowcount
                pl.search_only = 0
                pl.name_group = ""
                pl.adm2 = ""
                pl.name_script = SCRIPT_CODES.get("LATIN")
                yield pl


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("--db", default="./tmp/master_gazetteer.sqlite")
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)

    args = ap.parse_args()

    # All "X" Xponents generated names are purged by first class.  NameVariant generators do not purge
    adhocgaz = AdhocNameVariants(args.db)
    adhocgaz.normalize('etc/gazetteer/additions/adhoc-placenames.csv')
    count = adhocgaz.rowcount
    GeneralNameVariants(args.db).generate_variants()
    SaintNameVariants(args.db).generate_variants()
