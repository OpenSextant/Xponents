import sqlite3
from copy import copy

import arrow
from opensextant import Place, get_country
from opensextant.gazetteer import DataSource, get_default_db, as_place_record
from opensextant.utility import get_csv_reader, is_ascii

"""

Postal Code Gazetteer

The main sources of codes include:
- Geonames postal dumps
- Variations on the postal codes, e.g., removal of spaces to form a new variation of a postal code.
- ISO and other boundary codes/abbreviations from master gazetteer


Country rules:
* For Canada we have only the first letters of the full postal codes (for copyright reasons)
* For Chile we have only the first digits of the full postal codes (for copyright reasons)
For Ireland we have only the first letters of the full postal codes (for copyright reasons)
For Malta we have only the first letters of the full postal codes (for copyright reasons)
The Argentina data file contains the first 5 positions of the postal code.
For Brazil only major postal codes are available (only the codes ending with -000 and the major code per municipality).

SOURCE: https://download.geonames.org/export/zip/

POSTAL id blocks:

- all geonames, 0-2,000,000
- NLD - 2,000,000 - 2,999,999
- CAN - 3,000,000 - 3,999,999
- GB -  4,000,000 + 

- VARIANTS - normal ID + 10,000,000 ; TODO: VARIANTS are geneated on the fly to inject into Solr.

Postal code features are essential "AREA" types, but they are not just land areas (L/AREA), 
they are human-assigned administrative boundaries for mail delivery.  I chose "A/POST" as a coding here.
"""

header_names = [
    "cc",
    "postal_code",
    "place_name",
    "admin_name1",
    "admin_code1",
    "admin_name2",
    "admin_code2",
    "admin_name3",
    "admin_code3",
    "latitude",
    "longitude",
    "accuracy"
]

VARIANT_BASE = 10000000
GEONAMES_POSTAL = "GP"


def variants(pl: Place):
    """
    Generate trivial name variants such as SY25001 from SY25 001
    :param pl:
    :return:
    """
    if not " " in pl.name:
        return [pl, ]

    nm = pl.name.replace(" ", "")
    pl2 = copy(pl)
    pl2.name = nm
    pl2.id = pl.id + VARIANT_BASE
    return [pl, pl2]


class PostalGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = [GEONAMES_POSTAL]
        self.source_name = "Geonames Postal"
        self.starting_row = 0
        self.place_count = 0

    def done(self, lim):
        return 0 < lim < self.place_count

    def copy_administrative_codes(self, dbf, limit=-1, optimize=False):
        """
        Copy admin codes (country + province level1) from source DBF to this DBF.

        :param dbf:
        :param limit:
        :param optimize:
        :return:
        """
        master_db = DataSource(dbf)
        print("\n============================")
        # Driver: We only want EXISTING countries for which we have postal codes.
        # There is no benefit here to copying over all gazetteer data where we have no postal data.
        #
        # Guidelines -- Copy over source data from DBF, but reassign row ID to be consistent with this
        # postal db
        for cc in self.db.list_countries():
            if self.rowcount % self.rate == 0 and not self.quiet:
                print(f"Row {self.rowcount}")
            if 0 < limit < self.rowcount:
                print("Reached non-zero limit for testing.")
                break
            try:
                # !!! PER SQLITE: https://www.sqlite.org/faq.html  -- use Single Quotes for query on columns.
                #
                # Copy over country and province codes at a high level.
                sub_query = " AND name_group='' AND duplicate=0 AND feat_code='ADM1' AND (name_type='C' OR name_type='A') "
                _ctry_meta = []
                for pl in master_db.db.list_places(cc=cc, fc="A", criteria=sub_query):
                    self.rowcount += 1
                    # Grab admin-level-1
                    entry = as_place_record(pl, target="db")
                    entry["id"] = self.starting_row + self.rowcount
                    _ctry_meta.append(entry)
                # Part II. Countries
                sub_query = " AND name_group='' AND duplicate=0 AND feat_code like 'PCL%' "
                for pl in master_db.db.list_places(cc=cc, fc="A", criteria=sub_query):
                    # Grab countries -- Looking for POSTAL variations of country names.
                    if not is_ascii(pl.name) or len(pl.name) > 25:
                        continue
                    self.rowcount += 1
                    entry = as_place_record(pl, target="db")
                    entry["id"] = self.starting_row + self.rowcount
                    _ctry_meta.append(entry)
                self.db.add_places(_ctry_meta)
                print(f"\tCC {cc} added {len(_ctry_meta)}")
                self.rowcount += len(_ctry_meta)
            except sqlite3.IntegrityError as err:
                print(err)
                break
        master_db.db.close()

        if optimize:
            self.db.optimize()
        self.db.close()
        print("ROWS: ", self.rowcount)
        print(f"End {self.source_name}. {arrow.now()}")

    def process_source(self, sourcefile, limit=-1):
        """
        Sequential file processing -- On the change of each country block emit the cache for that country.
        And then once in the very end for end of file.

        :param sourcefile:
        :param limit:
        :return:
        """
        cache = {}
        cc = None
        adm_codes = {}
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            df = get_csv_reader(fh, delim="\t", columns=header_names)
            for row in df:
                self.rowcount += 1
                if not cc:
                    cc = row["cc"]

                if row["cc"] != cc or self.done(limit):
                    print("Saving country", cc)
                    # When file changes country code we know we are done with a country
                    for postal in cache.values():
                        for var in variants(postal):
                            yield var
                    if self.done(limit):
                        # Return.
                        break
                    adm_codes.clear()
                    cache.clear()
                    cc = row["cc"]

                if not adm_codes:
                    adm_codes = ref.admin_boundaries(cc)

                pl = Place(None, row["postal_code"])
                pl.id = self.starting_row + self.rowcount
                pl.country_code = row["cc"]
                pl.name_bias = 0.1
                pl.id_bias = 0.1
                pl.adm1 = row["admin_code1"]
                pl.place_id = "/".join([pl.country_code, pl.adm1, pl.name])
                if pl.place_id in cache:
                    # Do we track lat/lon?
                    continue

                # Re-map ADM1 code now.
                if pl.adm1 in adm_codes:
                    pl.adm1 = adm_codes[pl.adm1]

                self.place_count += 1
                pl.set_location(row["latitude"], row["longitude"])
                pl.name_group = "postal"
                pl.name_type = "C"  # Abbreviation or code.
                pl.feature_code = "POST"
                pl.feature_class = "A"  # This designates an Administrative region, artifically assigned by humans.
                pl.source = GEONAMES_POSTAL
                pl.name_script = ""
                pl.search_only = False
                pl.country_code_fips = None
                C = get_country(pl.country_code)
                if C:
                    pl.country_code_fips = C.cc_fips
                cache[pl.place_id] = pl

                if self.debug:
                    print("Sample place:", pl.name, pl.id, pl.place_id)

        # Clear the cache before leaving the room.
        if cache:
            for postal in cache.values():
                for var in variants(postal):
                    yield var


class ReferenceGaz(DataSource):
    def __init__(self, country=None):
        DataSource.__init__(self, get_default_db())
        # self.db.debug = True
        print("Collecting consistent ADM1 codes to use internally on postal code entries.")
        self._country_adm1 = dict()
        cclist = []
        if country:
            cclist.append(country)
        else:
            cclist = self.db.list_countries()
        for cc in cclist:
            dct = dict()
            dct["default"] = "default"
            for pl in self.db.list_places(cc, 'A',
                                          criteria=" and name_group is '' and name_type = 'C' and feat_code = 'ADM1' "):
                dct[pl.name] = pl.adm1
            self._country_adm1[cc] = dct

    def admin_boundaries(self, cc):
        return self._country_adm1.get(cc)


if __name__ == "__main__":
    import sys
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("postal")
    ap.add_argument("starting_row", help="Starting row number")
    ap.add_argument("--db", default="./tmp/postal_gazetteer.sqlite")
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--copy-admin", action="store_true", default=False)
    ap.add_argument("--country", help="Country code to focus on")

    args = ap.parse_args()

    # This master gazetteer DB acts as a code book for all other gazetteers.
    # Geonames Postal Codes are ISO Alphanumeric, not ISO numeric, e.g., "SL" instead "16" for ADM1 boundary in Germany
    # This causes great disconnect so we must remap all postal code data where possible from postal to master gaz.
    source = PostalGazetteer(args.db, debug=args.debug)
    source.starting_row = int(args.starting_row)
    if args.copy_admin:
        # DO LAST:
        if source.starting_row == 0:
            print("You need to provide a non-zero starting row for copying other data.")
            sys.exit(1)
        source.copy_administrative_codes(get_default_db(), limit=int(args.max), optimize=args.optimize)
    else:
        ref = ReferenceGaz(country=args.country)
        source.normalize(args.postal, limit=int(args.max), optimize=args.optimize)
