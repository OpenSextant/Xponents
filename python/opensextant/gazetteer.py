import os
import sqlite3
from traceback import format_exc

import arrow
import pysolr
from opensextant import Place
from opensextant.utility import ensure_dirs, is_ascii, ConfigUtility, get_bool
from pygeodesy.geohash import encode as geohash_encode

GAZETTEER_SOURCES = {
    "NGA": "N",
    "USGS": "U",
    "USGS-AUTOFIXED": "UF",
    "NGA-AUTOFIXED": "NF",
    "ADHOC": "OA",  # OpenSextant Adhoc
    "NE": "NE",  # Natural Earth.
    "GEONAMES": "OG",  # OpenSextant geonames
    "Geonames.org": "OG",  # OpenSextant geonames
    "XPONENTS": "X",  # Xponents Adhoc or generated
    "XpGen": "X",
    "XP": "X"
}

SCRIPT_CODES = {
    None: "",
    "LATIN": "L",
    "HAN": "H",
    "COMMON": "C",
    "ARABIC": "A",
    "ARMENIAN": "AM",
    "BENGALI": "BN",
    "CYRILLIC": "CY",
    "DEVANAGARI": "DV",
    "ETHIOPIC": "ET",
    "GEORGIAN": "GE",
    "GREEK": "GK",
    "GURMUKHI": "GM",
    "GUJARATI": "GU",
    "HEBREW": "HE",
    "HANGUL": "HG",
    "HIRAGANA": "HI",
    "KANNADA": "KN",
    "KATAKANA": "KA",
    "KHMER": "KM",
    "MALAYALAM": "MY",
    "SINHALA": "SI",
    "TAMIL": "TA",
    "THAI": "TH",
}


def load_stopterms(project_dir="."):
    """
    Load default stop terms from source tree for project build.
    :param project_dir: The location of Xponents/solr source tree.
    :return:
    """
    loader = ConfigUtility()
    stopterms = set([])
    for f in ["etc/gazetteer/filters/non-placenames.csv",
              "etc/gazetteer/filters/non-placenames,spa.csv",  # SPANISH
              "etc/gazetteer/filters/non-placenames,deu.csv",  # GERMAN
              "etc/gazetteer/filters/non-placenames,acronym.csv"]:
        terms = loader.loadDataFromFile(os.path.join(project_dir, f), ",")
        for t in terms:
            stopterms.add(t[0].lower())
    return stopterms


def run_lookup(url, lookup, parse):
    """ Gazetteer demo mimics some of the logic in XponentsGazetteerQuery
        try "San Francisco, CA, US"
    """

    solr_gaz = pysolr.Solr(url)
    # specific unit tests

    records = None
    places = []
    if parse:
        # See other Java demo, XponentsGazetteerQuery
        # assuming NAME, PROV, COUNTRY
        slots = [a.strip() for a in lookup.split(',')]

        if len(slots) < 3:
            print("NAME, PROV, CC  is required format for --lookup")
            return None

        cityVal = slots[0]
        provVal = slots[1]
        countryVal = slots[2]

        # Find best match for Province. Pass ADM1 code to next query
        query = 'name:"{}" AND feat_class:A AND cc:{}'.format(provVal, countryVal)
        records = solr_gaz.search(query, **{"rows": 100})

        if not records:
            return None

        # Use a Place object to abstract things.
        adm1 = as_place(records.docs[0])
        # Find best match for the tuple NAME/PROV/COUNTRY
        #
        query = 'name:"{}" AND feat_class:A AND cc:{} AND adm1:{}'.format(cityVal, countryVal, adm1.adm1)
        records = solr_gaz.search(query, **{"rows": 1000})
    else:
        query = 'name:"{}" AND feat_class:P'.format(lookup)
        records = solr_gaz.search(query, **{"rows": 1000})

    if not records:
        return None

    for r in records:
        places.append(as_place(r))

    return places


GAZETTEER_TEMPLATE = {
    'id': -1,
    'place_id': -1,
    'name': None,
    # name_ar or name_cjk are filled in only if name is Arabic or CJK name group
    'lat': 0, 'lon': 0,
    # geo is the field to use for index.  lat/lon  are used for database.
    'feat_class': None, 'feat_code': None,
    'FIPS_cc': None, 'cc': None,
    'adm1': None, 'adm2': None,
    'source': None,
    'script': None,
    'name_bias': 0,
    'id_bias': 0,
    'name_type': "N",
    'search_only': False
}


def as_place(r):
    """
    Convert dict to a Place object
    :param r: gazetteer row from Solr or SQlite.
    :return: Place
    """
    keys = {}
    if hasattr(r, "keys"):
        keys = r.keys()

    lat, lon = 0, 0
    if "geo" in r:
        (lat, lon) = r['geo'].split(',')
    else:
        lat, lon = r["lat"], r["lon"]

    p = Place(r['place_id'], r['name'], lat=lat, lon=lon)
    p.country_code = r["cc"]
    p.feature_class = r["feat_class"]
    p.feature_code = r["feat_code"]
    if "id" in r:
        # Required if coming or going into a database:
        p.id = r["id"]
    p.id_bias = r["id_bias"]
    p.name_bias = r["name_bias"]
    # optional fields:
    if "FIPS_cc" in keys:
        p.country_code_fips = r["FIPS_cc"]
    if "adm1" in keys:
        p.adm1 = r["adm1"]
    if "adm2" in keys:
        p.adm2 = r["adm2"]
    if "geohash" in keys:
        p.geohash = r["geohash"]
    if "id" in keys:
        p.id = r["id"]
    if "source" in keys:
        p.source = r["source"]
    if "name_group" in keys:
        p.name_group = r["name_group"]
    if "search_only" in keys:
        p.search_only = get_bool(r["search_only"])
    if "name_type" in keys:
        p.name_type = r["name_type"]

    p.is_ascii = is_ascii(p.name)
    return p


def as_place_record(place, target="index"):
    """
    Given a Place object, serialize it as a dict consistent with the Solr index schema.
    :param place:
    :param target: index or db
    :return:
    """
    if not isinstance(place, Place):
        return None
    # Copy defaults offers nothing.
    # rec = copy(GAZETTEER_TEMPLATE)
    rec = {
        "id": place.id,
        "place_id": place.place_id,
        "name": place.name,
        "name_type": place.name_type,
        "feat_class": place.feature_class,
        "feat_code": place.feature_code,
        "cc": place.country_code,
        "FIPS_cc": place.country_code_fips,
        "source": place.source,
        "script": place.name_script,
        "search_only": place.search_only
    }

    # ADMIN level 1/2 boundary names:
    if place.adm1 is not None:
        rec["adm1"] = place.adm1
    if place.adm2 is not None:
        rec["adm2"] = place.adm2
    # ID BIAS:
    if place.id_bias is None:
        rec["id_bias"] = 0
    else:
        rec["id_bias"] = place.id_bias
    # NAME BIAS
    if place.name_bias is None:
        rec["name_bias"] = 0
    else:
        rec["name_bias"] = place.name_bias

    if target == "index":
        # Preserve innate precision on Lat/Lon: e.g., "4.5,-118.4" is result if only that amount of precision is present
        rec["geo"] = ",".join([str(place.lat), str(place.lon)]),
        # Name Group / Script tests:
        if place.name_group == "ar":
            rec["name_ar"] = place.name
        elif place.name_group == "cjk":
            rec["name_cjk"] = place.name
    elif target == "db":
        # Required fields:
        rec["name_group"] = place.name_group
        rec["lat"] = place.lat
        rec["lon"] = place.lon
        rec["adm1"] = place.adm1
        rec["adm2"] = place.adm2

    return rec


def run_query(url, q):
    """ Expert mode:  Run a solr query to see what you get back. 
        requires you know the schema
    """
    solrGaz = pysolr.Solr(url)
    records = solrGaz.search(q, **{"rows": 100})
    places = []
    for r in records:
        places.append(as_place(r))

    return places


def print_places(arr, limit=25):
    print("FOUND {}. Showing top {}".format(len(arr), limit))
    for p in arr[0:limit]:
        print(str(p))


class DB:
    def __init__(self, dbpath, commit_rate=1000):
        """
        Save items to SQlite db at the commit_rate given.  Call close to finalize any partial batches
        and save database.

        :param dbpath:
        :param commit_rate:
        """
        self.dbpath = dbpath
        self.conn = None
        self.queue = []
        self.queue_count = 0
        self.commit_rate = commit_rate
        if not os.path.exists(dbpath):
            ensure_dirs(dbpath)
            self.reopen()
            self.create()
        else:
            self.reopen()

    def purge(self, q):
        if "source" in q:
            self.conn.execute("delete from placenames where source = ?", (q["source"],))
            self.conn.commit()
            print("Purged")
        else:
            print("Query not implemented ", q)

    def create(self):
        sql_script = """
            create TABLE placenames (
                `id` INTEGER PRIMARY KEY,
                `place_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `name_type` TEXT NOT NULL,
                `name_group` TEXT NULL,
                `source` TEXT NOT NULL,
                `script` TEXT NULL,
                `feat_class` TEXT NOT NULL,
                `feat_code` TEXT NOT NULL,
                `cc` TEXT NULL,
                `FIPS_cc` TEXT  NULL,
                `adm1` TEXT  NULL,
                `adm2` TEXT  NULL,
                `lat` REAL NOT NULL,
                `lon` REAL NOT NULL,
                `geohash` TEXT NOT NULL,
                `duplicate` BOOLEAN DEFAULT 0,
                `name_bias` REAL DEFAULT 0,
                `id_bias` REAL DEFAULT 0,
                `search_only` BIT DEFAULT 0                
            );
            
            create INDEX plid_idx on placenames ("place_id");               
        """
        self.conn.executescript(sql_script)
        self.conn.commit()

    def optimize(self):
        self.reopen()
        self.conn.execute("VACUUM")
        indices = """
            create INDEX IF NOT EXISTS n_idx on placenames ("name");
            create INDEX IF NOT EXISTS s_idx on placenames ("source");
            create INDEX IF NOT EXISTS c_idx on placenames ("cc");
            create INDEX IF NOT EXISTS fc_idx on placenames ("feat_class");
            create INDEX IF NOT EXISTS dup_idx on placenames ("duplicate");
        """
        self.conn.executescript(indices)
        self.conn.commit()

    def reopen(self):
        if self.conn is not None:
            return

        # really close cleanly
        self.close()

        self.conn = sqlite3.connect(self.dbpath)
        self.conn.execute('PRAGMA cache_size =  8092')
        self.conn.execute("PRAGMA encoding = 'UTF-8'")
        self.conn.execute('PRAGMA synchronous = OFF')
        self.conn.execute('PRAGMA journal_mode = MEMORY')
        self.conn.execute('PRAGMA temp_store = MEMORY')
        self.conn.row_factory = sqlite3.Row

    def close(self):
        try:
            if self.conn is not None:
                self.__assess_queue(force=True)
                self.conn.close()
                self.conn = None
        except:
            self.conn = None

    def _prep_place(self, dct):
        """
        REQUIRED fields:  'source', 'lat', 'lon'.
        OPTIONAL fieldsd: 'search_only'
        :param dct:
        :return:
        """
        src = dct["source"]
        dct["source"] = GAZETTEER_SOURCES.get(src, src)
        # 6 geohash prefix is about 100m to 200m error. precision=8 is 1m precision.
        dct["geohash"] = geohash_encode(dct["lat"], dct["lon"], precision=6)

        v = get_bool(dct.get("search_only"))
        dct["search_only"] = 0
        if v:
            dct["search_only"] = 1

    def add_place(self, obj):
        """ Add one place
        :param obj: a place dictionary.  If arg is a Place object it is converted to dictionary first.
        """
        dct = None
        if isinstance(obj, Place):
            dct = as_place_record(obj, target="db")
        else:
            dct = obj
        self._prep_place(dct)
        self.queue.append(dct)
        self.queue_count += 1
        self.__assess_queue()

    def __assess_queue(self, force=False):
        if force or (self.queue_count >= self.commit_rate):
            sql = """
            insert into placenames (
                id, place_id, name, name_type, name_group, 
                lat, lon, geohash, feat_class, feat_code,
                cc, FIPS_cc, adm1, adm2, source, script, name_bias, id_bias, search_only
             ) values (
                :id, :place_id, :name, :name_type, :name_group, 
                :lat, :lon, :geohash, :feat_class, :feat_code,
                :cc, :FIPS_cc, :adm1, :adm2, :source, :script, :name_bias, :id_bias, :search_only)"""
            self.conn.executemany(sql, self.queue)
            self.conn.commit()
            self.queue_count = 0
            self.queue.clear()

    def add_places(self, arr):
        """ Add a list of places. """
        for dct in arr:
            self._prep_place(dct)
        self.queue.extend(arr)
        self.queue_count += len(arr)
        self.__assess_queue()

    def get_places_by_id(self, plid, limit=2):
        """
        Collect places and name_bias for gazetter ETL.
        Lookup place by ID as in "G1234567" for Geonames entry or "N123456789" for an NGA one, etc.

        :param plid: Place ID according to the convention of source initial + identifier
        :param limit: limit queries because if we know we only one 2 or 3 we need not search database beyond that.
        :return:
        """
        name_bias = dict()
        place = None
        for row in self.conn.execute(f"select * from placenames where place_id = ? limit {limit}", (plid,)):
            pl = as_place(row)
            if not place:
                place = pl
            name_bias[pl.name.lower()] = pl.name_bias
        if place:
            # This is first place encountered.
            # This is not idempotent unless SQL query is more explicit
            return place, name_bias

        return None, None

    def list_countries(self):
        arr = []
        for cc in self.conn.execute("select distinct(cc) as CC from placenames"):
            arr.append(cc["CC"])
        return arr

    def list_places(self, cc=None, fc=None, criteria=None, limit=-1):
        """
        Potentially massive array -- so this is just a Place generator.
        :param cc: country code or ''
        :param fc: feat class constraint with "*" wildcard, or ''
        :param criteria: additional clause to constrain search, e.g. " AND duplicate=0 " to find non-dups.
        :limit limit:  non-zero limit
        :return: generator
        """
        sql = ["select * from placenames"]
        _and = ""
        if cc is not None or fc is not None:
            sql.append("where")
        if cc is not None:
            sql.append(f"cc ='{cc}'")
        if fc is not None:
            if cc is not None:
                _and = " and "
            if "*" in fc:
                sql.append(f"{_and}feat_class like '{fc.replace('*', '%')}'")
            else:
                sql.append(f"{_and}feat_class = '{fc}'")
        if criteria:
            # Include the " AND " yourself in critera
            sql.append(criteria)
        if limit > 0:
            sql.append(f"limit {limit}")

        # Query
        sql_script = " ".join(sql)
        for p in self.conn.execute(sql_script):
            yield as_place(p)

    def mark_duplicates(self, dups):
        if not dups:
            return False
        step = 1000
        for x1 in _array_blocks(dups, step=step):
            x2 = x1 + step
            arg = ",".join([str(dup) for dup in dups[x1:x2]])
            sql = f"update placenames set duplicate=1 where id in ({arg})"
            self.conn.execute(sql)
            self.conn.commit()
        return True

    def mark_search_only(self, pid):
        """
        Toggle bit for search only.
        :param pid: Place ID
        """
        sql = f"update placenames set search_only=1 where id=?"
        self.conn.execute(sql, (pid,))
        self.conn.commit()


def _array_blocks(arr, step=1000):
    """
    Break up large arrays so we have predictable updates or queries.
    :param arr:
    :param step:
    :return:
    """
    end = len(arr)
    blocks = [0]
    if end > step:
        for start in range(step, end, step):
            blocks.append(start)
    return blocks


class DataSource:
    """
    Gazetteer Data Source abstraction -- provides guidelines on how to inject
    data into a common, normalized gazetteer.
    """

    def __init__(self, dbf, debug=False):
        self.db = DB(dbf, commit_rate=10)
        self.rate = 1000000
        self.rowcount = 0
        self.source_keys = []
        self.excluded_terms = set([])
        self.quiet = False
        self.source_name = None
        self.debug = debug

    def purge(self):
        print(f"Purging entries for {self.source_name}")
        for k in self.source_keys:
            print(f"\tsource ID = {k}")
            self.db.purge({"source": k})

    def process_source(self, sourcefile):
        """
        generator yielding DB geo dictionary to be stored.
        :param sourcefile: Raw data file
        :return: generator of Place object or dict of Place schema
        """
        yield None

    def normalize(self, sourcefile, limit=-1, optimize=False):
        """
        Given the spreadsheet or source file rip through it, ingesting contents into the master gazetteer.
        :param sourcefile: input file
        :return:
        """

        print("\n============================")
        print(f"Start {self.source_name}. {arrow.now()}")
        for geo in self.process_source(sourcefile):
            if self.rowcount % self.rate == 0 and not self.quiet:
                print(f"Row {self.rowcount}")
            if 0 < limit < self.rowcount:
                print("Reached non-zero limit for testing.")
                break
            try:
                self.db.add_place(geo)
            except sqlite3.IntegrityError as err:
                print("Data integrity issue")
                print(format_exc(limit=5))
                print(self.db.queue)
                break
            except Exception as err:
                print("Error with insertion to DB")
                print(format_exc(limit=5))
        self.db.close()
        if optimize:
            self.db.optimize()

        print("ROWS: ", self.rowcount)
        print("EXCLUSIONS: ", len(self.excluded_terms))
        if self.debug:
            print("EXCLUSIONS:", self.excluded_terms)
        print(f"End {self.source_name}. {arrow.now()}")


class GazetteerIndex:
    """
    GazetteerIndex provides a simple API to inject entries into the Gazetteer.
    - Every 1000 records a batch is sent to Solr
    - Every 1,000,0000 records a commit() call is sent to Solr

    This may provide gazetteer specific functions, but as of v1.3 this is a generic Solr wrapper.
    """

    def __init__(self, server_url, debug=False):

        from pysolr import Solr
        self.server = Solr(server_url)
        self.debug = debug

        self.commit_rate = 1000000
        self.add_rate = 1000

        self._records = []
        self.count = 0

    def optimize(self):
        if self.server and not self.debug:
            self.server.optimize()

    def save(self, done=False):
        if self.debug:
            return

        # Send batch
        if self._records and (done or self.count % self.add_rate == 0):
            self.server.add(self._records)
            self._records = []
        # Commit
        if done or self.count % self.commit_rate == 0:
            self.server.commit()
        return

    def add(self, place):
        """

        :param place: Place object.
        :return:
        """
        rec = as_place_record(place)
        self._records.append(rec)
        self.count += 1
        self.save()


if __name__ == "__main__":

    import argparse

    ap = argparse.ArgumentParser()
    ap.add_argument('--solr')
    ap.add_argument('--output')
    ap.add_argument('--query')
    ap.add_argument('--lookup')
    ap.add_argument('--parse', action="store_true", default=False)
    ap.add_argument('--demo')

    args = ap.parse_args()

    if args.lookup:
        findings = run_lookup(args.solr, args.lookup, args.parse)
        print_places(findings)
    elif args.query:
        findings = run_query(args.solr, args.query)
        print_places(findings)
