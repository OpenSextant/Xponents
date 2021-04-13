from opensextant.gazetteer import DB, load_stopterms
from opensextant.utility import replace_diacritics

stopwords = {}

class Finalizer:
    def __init__(self, dbf, debug=False):
        self.db = DB(dbf)
        self.debug = debug

    def finalize(self, limit=-1, optimize=False):
        """
        Finalize the gazetteer database to include an cleanup, deduplication, etc.
        :param limit: (Unused) per-country limit used for testing.
        :param optimize: to force a SQLite optimize or not.
        :return:
        """
        #
        # Finalize reviews places by country to identify distinct features to promote as primary
        # entries and any "duplicates" can be marked as duplicate(dup=1).  Primary entries are those such as:
        #
        #  - Unique entries
        #  - Having attributes such as non-zero id or name bias
        #  - NGA & USGS gazetteers by default -- other gazetteers will be overlaid where name variants are offered
        #    for same feature / point ID / location
        #
        # Easiest way to break down gazetteer is:
        #  - by Country
        #    - by feature class or empty non-feature.  ... Resolve any low-quality entries with empty feature.
        countries = self.db.list_countries()
        BASE_SOURCES = {"OA", "OG", "U", "UF", "N", "NF"}
        for cc in countries:
            # Collect entries with
            print(f"Country '{cc}'")
            # base sources via OpenSextant gazetteer:  OA, OG, U, UF, N, NF

            base_sources = ",".join([f'"{src}"' for src in BASE_SOURCES])
            keys = set([])
            duplicates = []
            # Collect all duplicate names within USGS and NGA base layers
            sql = f"""select id, feat_class, feat_code, source, geohash, name, place_id, id_bias, name_bias 
                           from placenames where cc='{cc}' and source in ({base_sources}) and duplicate=0"""
            self._collect_duplicates(sql, keys, duplicates, label="Base")

            # De-duplicate other sources that leverage USGS/NGA as base sources.
            sql = f"""select id, feat_class, feat_code, source, geohash, name, place_id, id_bias, name_bias 
                           from placenames where cc='{cc}' and source not in ({base_sources}) and duplicate=0"""
            self._collect_duplicates(sql, keys, duplicates, label="Other Sources")
            self.db.mark_duplicates(duplicates)
        print("Complete De-duplicating")

        if optimize:
            print("Optimizing SQLite DB")
            self.db.optimize()

    def _collect_duplicates(self, sql, keys, dups, label="NA"):
        for row in self.db.conn.execute(sql):
            fc = row["feat_class"]
            loc = row["geohash"]
            nm = row["name"].lower()
            k = f"{fc}/{loc[0:5]}/{nm}"
            if k in keys:
                if self.debug: print(f"{label} dup: ", row["id"])
                dups.append(row["id"])
            else:
                # Unique entry
                keys.add(k)

    def index(self, url, ignore_features=None, ignore_digits=True, limit=-1):
        import re
        from time import sleep
        from opensextant.gazetteer import GazetteerIndex
        global stopwords

        print("Xponents Gazetteer Finalizer: INDEX")
        stopwords = load_stopterms()
        indexer = GazetteerIndex(url)
        indexer.commit_rate = 100000
        #
        filters = []
        for f in ignore_features:
            filters.append(re.compile(f))
        # For each row in DB, index to Solr.  Maybe organize batches by row ID where dup=0.
        countries = self.db.list_countries()
        for cc in countries:
            print(f"Country '{cc}'")
            for pl in self.db.list_places(cc=cc, criteria=" and duplicate=0", limit=limit):
                if filter_out_feature(pl, filters):
                    continue
                if ignore_digits and pl.name.isdigit():
                    continue
                # Mark generic stopwords as search only
                if not pl.search_only:
                    if filter_out_term(pl):
                        print(f"\tsearch only: {pl.name} (source: {pl.source})")
                        pl.search_only = True
                        self.db.mark_search_only(pl.id)
                indexer.add(pl)
            # 10 second pause
            sleep(10)
        print(f"Indexed {indexer.count}")
        indexer.save(done=True)


def filter_out_term(pl):
    """
    :param pl: Place name or any text
    :return: True if term is present in stopwords.
    """
    txt = pl.name
    txtnorm = txt.lower()
    if txtnorm in stopwords:
        return True

    # UPPER case abbreviations allowed only for administrative boundaries
    if pl.is_upper and len(pl.name) < 4 and pl.feature_class != "A":
        return True

    if replace_diacritics(txtnorm).strip("'") in stopwords:
        return True

    return False


def filter_out_feature(pl, feats):
    """

    :param pl: Place object
    :param feats: Pattern
    :return:
    """
    if not pl.feature_code:
        return False

    plen = len(pl.name)
    # Names of about 20 chars long are non-trivial
    if plen > 20:
        return False

    # Allow 3-word features or longer -- that is relatively unique.
    if len(pl.name.split()) > 2:
        return False

    fc = f"{pl.feature_class}/{pl.feature_code}"
    for feat_filter in feats:
        if feat_filter.match(fc):
            return True
    return False


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("--db", default="./tmp/master_gazetteer.sqlite")
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--solr", help="Solr URL")
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--dedup", action="store_true", default=False)

    args = ap.parse_args()

    gaz = Finalizer(args.db, debug=args.debug)
    if args.solr:
        #  Features not as present in general data include: WELLS, STREAMS, SPRINGS, HILLS.
        #
        gaz.index(args.solr,
                  ignore_features={"H/WLL.*", "H/STM.*", "H/SPNG.*", "T/HLL.*"},
                  ignore_digits=True,
                  limit=int(args.max))
    elif args.dedup:
        gaz.finalize(limit=int(args.max), optimize=args.optimize)
