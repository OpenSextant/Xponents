import os
import re
from time import sleep

from opensextant import Place, load_countries, countries, as_place, is_administrative, is_populated
from opensextant.gazetteer import DB, estimate_name_bias, GazetteerIndex, get_default_db
from opensextant.utility import replace_diacritics, load_list

stopwords = {}


class Finalizer:
    def __init__(self, dbf, debug=False):
        self.db = DB(dbf)
        self.debug = debug
        self.inter_country_delay = 10

    def adjust_place_id(self):
        self.db.create_indices()
        decisions = {}
        for pl in self.db.list_places(criteria=" where place_id = 'N-1'"):
            if not pl.geohash:
                continue
            # NE outputs place id of NULL or "-1".  This should rectify that by pulling in a decent place ID
            # Geohash is a horrible way to filter data -- certain states just sit on the boundary of certain boxes.
            for ghlen in [3, 2, 1, 0]:
                gh = pl.geohash[0:ghlen]
                key = f"{pl.country_code}#{pl.adm1}#{pl.feature_class}#{pl.feature_code}#{gh}"
                distinct_ids = set([])
                if key in decisions:
                    distinct_ids.add(decisions[key])
                else:
                    # Avoid too much SQL queries ... record decisions made
                    criteria = f""" and feat_code='{pl.feature_code}'
                       and adm1 = '{pl.adm1}'
                       and place_id != 'N-1' 
                       and geohash like '{gh}%'
                    """
                    for model_pl in self.db.list_places(cc=pl.country_code, fc=pl.feature_class, criteria=criteria):
                        distinct_ids.add(model_pl.place_id)
                        #
                    print("IDS for:", pl.name, distinct_ids)
                if len(distinct_ids) == 1:
                    plid = distinct_ids.pop()
                    decisions[key] = plid
                    self.db.update_place_id(pl.id, plid)
                    break
                elif len(distinct_ids) > 1:
                    print("Ambiguous place ID resolution - no adjustment: ", pl.name, pl.country_code)
        self.db.commit()

    def adjust_bias(self):
        self.db.create_indices()
        print("Adjust Biasing")
        # Fix significant place names:  When loading gazetteer data for the first time,
        # you do not have a global awareness of names/geography -- so things like biasing "common words" in wordstats
        # leads us to mark valid common city names as "too common" and they are filtered out.
        # Example: if you encounter "Beijing" (P/PPLX) is seen first and is marked as a common word
        # and then "Beijing" (P/PPLC) is seen and exempted.  You have two conflicting conclusions on the name "Beijing".
        # The result being that only the captial Beijing will ever be used in tagging.
        # FIX: loop through all names fc = "A" and P/PPLC, major cities population 200,000 or greater.
        #    re-mark the name_bias to positive if determined to be common words previously.
        names_done = set([])
        count_adjusted = 0

        flag_fix_major_place_names = True
        flag_fix_admin_codes = False  # Addressed in-line through PlaceHueristics

        if flag_fix_major_place_names:
            # ============================================
            # Find Names < 30 chars in general name_group where the names represent significant features.
            # Recode those feature/names so ANY row by that name is not excluded by the "too common" judgement
            sql_clause = """ where
                name_type='N' 
                and name_group='' 
                and LENGTH(name) < 30 
                and feat_class in ('A', 'P') 
                and feat_code in ('ADM1', 'PPLC', 'PCL', 'PCLI') 
                and name NOT like '% %' 
                and name_bias < 0"""

            for pl in self.db.list_places(criteria=sql_clause):
                names = {pl.name, replace_diacritics(pl.name)}
                for name in names:
                    if name.lower() in names_done:
                        continue
                    print(f"ADJUST: {name}")
                    name_bias = estimate_name_bias(name)
                    # ADJUSTED here is an approximation.
                    count_adjusted += 1
                    # For each name, remark each name and it variants.
                    self.db.update_bias_by_name(name_bias, name)
                    names_done.add(name.lower())
            self.db.commit()

        if flag_fix_admin_codes:
            # ============================================
            flip_ids = []
            non_place_codes = os.path.join('etc', 'gazetteer', 'filters', 'non-placenames,admin-codes.csv')
            IGNORE_CODES = set(load_list(non_place_codes))
            for pl in self.db.list_places(fc="A",
                                          criteria=" and name_type='C' and feat_code in ('ADM1','ADM2') and search_only=1"):
                if pl.name in IGNORE_CODES:
                    continue
                flip_ids.append(pl.id)
                print(pl)
            # Any rows found -- flip their search status.
            self.db.update_bias(10, flip_ids)
            self.db.commit()

    def deduplicate(self):
        """
        Finalize the gazetteer database to include an cleanup, deduplication, etc.
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
        self.db.optimize()
        cc_list = self.db.list_countries()
        BASE_SOURCES = {"OA", "OG", "U", "UF", "N", "NF", "ISO"}
        for cc in cc_list:
            # Collect entries with
            print(f"Country '{cc}'")
            # base sources via OpenSextant gazetteer:  OA, OG, U, UF, N, NF

            base_sources = ",".join([f'"{src}"' for src in BASE_SOURCES])
            keys = set([])
            duplicates = []
            # Collect all duplicate names within USGS and NGA base layers
            sql = f"""select id, feat_class, source, geohash, name, place_id 
                           from placenames where cc='{cc}' and source in ({base_sources}) and duplicate=0"""
            self._collect_duplicates(sql, keys, duplicates, label="Base")

            # De-duplicate other sources that leverage USGS/NGA as base sources.
            sql = f"""select id, feat_class, source, geohash, name, place_id 
                           from placenames where cc='{cc}' and source not in ({base_sources}) and duplicate=0"""
            self._collect_duplicates(sql, keys, duplicates, label="Other Sources")
            self.db.mark_duplicates(duplicates)
        print("Complete De-duplicating")

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

    def index_country_codes(self, url, starting_id=20000000):
        """
        :deprecated:  data is used in database, so we'll get it from there anyway.
        :param url:
        :param starting_id:
        :return:
        """
        load_countries()
        indexer = GazetteerIndex(url)
        # offset into postal index:
        count = 0
        for C in countries:
            # We won't use FIPS codes for tagging.
            pl = as_place(C, C.name, oid=starting_id + count, name_type="N")
            indexer.add(pl)
            count += 1
            pl = as_place(C, C.cc_iso2, oid=starting_id + count, name_type="C")
            indexer.add(pl)
            count += 1
            pl = as_place(C, C.cc_iso3, oid=starting_id + count, name_type="C")
            indexer.add(pl)
        indexer.save(done=True)

    def index(self, url, features=None, ignore_features=None, ignore_digits=True, ignore_names=False, limit=-1):

        print("Xponents Gazetteer Finalizer: INDEX")
        indexer = GazetteerIndex(url)
        indexer.commit_rate = 100000
        #
        filters = []
        if ignore_features:
            for f in ignore_features:
                filters.append(re.compile(f))
        inclusion_filters = []
        if features:
            for f in features:
                inclusion_filters.append(re.compile(f))

        default_criteria = " and duplicate=0"
        if ignore_names:
            default_criteria = " and duplicate=0 and name_type!='N'"
        # For each row in DB, index to Solr.  Maybe organize batches by row ID where dup=0.
        cc_list = self.db.list_countries()
        for cc in cc_list:
            print(f"Country '{cc}'")
            for pl in self.db.list_places(cc=cc, criteria=default_criteria, limit=limit):
                if filter_out_feature(pl, filters):
                    continue
                if ignore_digits and pl.name.isdigit():
                    continue
                if not filter_in_feature(pl, inclusion_filters):
                    continue
                indexer.add(pl)
            sleep(self.inter_country_delay)
        print(f"Indexed {indexer.count}")
        indexer.save(done=True)

    def index_codes(self, url):
        print("Xponents Gazetteer Finalizer: INDEX CODES, ABBREV")
        indexer = GazetteerIndex(url)
        indexer.commit_rate = 100000
        default_criteria = " where duplicate=0 and name_type!='N'"
        for pl in self.db.list_places(criteria=default_criteria):
            indexer.add(pl)
        print(f"Indexed {indexer.count}")
        indexer.save(done=True)


class PostalIndexer(Finalizer):
    def __init__(self, dbf, **kwargs):
        Finalizer.__init__(self, dbf, **kwargs)
        self.inter_country_delay = 1

    def finalize(self, limit=-1):
        # No optimization on postal codes.
        pass

    def index(self, url, ignore_digits=False, **kwargs):
        # Finalizer indexes postal data as-is.  No digit or stop filters.
        Finalizer.index(self, url, ignore_digits=False, **kwargs)


def filter_out_term(pl: Place):
    """
    :param pl: Place name or any text
    :return: True if term is present in stopwords.
    """
    txt = pl.name
    txtnorm = txt.lower()
    if txtnorm in stopwords:
        return True

    admin_feat = is_administrative(pl.feature_class)
    pop_feat = is_populated(pl.feature_class)
    # UPPER case abbreviations allowed only for administrative boundaries
    if pl.is_upper and len(pl.name) < 4 and not admin_feat:
        return True

    if replace_diacritics(txtnorm).strip("'") in stopwords:
        return True

    # Ignore trivial feature names: e.g., s"The Spot"
    if not (pop_feat or admin_feat) and txtnorm.startswith("the "):
        tokens = txtnorm.split(" ")
        if tokens[1] in stopwords and len(tokens) == 2:
            return True

    return False


def filter_out_feature(pl: Place, feats):
    """
    Filter out places by their feature type or by their name traits.
    Long names (> 20 chars) and/or (>2 words) are relatively unique and not filtered.
    Otherwise places are filtered if their feature code+class are designated as not useful
    for a particular task.  Eg., we don't want to tag short river names or streams (H/STM)...ever.
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


def filter_in_feature(pl: Place, feats):
    """

    :param pl: Place
    :param feats: feature filters (regex)
    :return:
    """
    if not feats:
        return True
    fc = f"{pl.feature_class}/{pl.feature_code}"
    for feat_filter in feats:
        if feat_filter.match(fc):
            return True
    return False


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("operation", help="adjust-id, dedup, adjust-bias, index -- pick one.  Run all three in that order")

    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--solr", help="Solr URL")
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--postal", action="store_true", default=False)

    args = ap.parse_args()

    gaz = None
    if args.operation == "index" and args.solr:
        #  Features not as present in general data include: WELLS, STREAMS, SPRINGS, HILLS.
        #
        if args.postal:
            # Postal Codes
            gaz = PostalIndexer(args.db, debug=args.debug)
            gaz.stop_filters = None
            gaz.index(args.solr,
                      ignore_digits=False,
                      use_stopfilters=False,
                      limit=int(args.max))
        else:
            gaz = Finalizer(args.db, debug=args.debug)
            gaz.index(args.solr,
                      ignore_features={"H/WLL.*", "H/STM.*", "H/SPNG.*", "T/HLL.*"},
                      ignore_digits=True,
                      limit=int(args.max))

    elif args.operation == "adjust-id":
        gaz = Finalizer(args.db, debug=args.debug)
        gaz.adjust_place_id()
    elif args.operation == "adjust-bias":
        gaz = Finalizer(args.db, debug=args.debug)
        gaz.adjust_bias()
    elif args.operation == "dedup":
        gaz = Finalizer(args.db, debug=args.debug)
        gaz.deduplicate()

    # Finish up.
    if args.optimize and gaz:
        gaz.db.optimize()
        gaz.db.close()