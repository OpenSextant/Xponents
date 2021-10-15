import os
import sqlite3
from math import log as natlog
from traceback import format_exc

import arrow
import pysolr
from opensextant import Place, Country, geohash_encode
from opensextant.utility import ensure_dirs, is_ascii, ConfigUtility, get_bool, trivial_bias, replace_diacritics, \
    parse_float

DEFAULT_MASTER = "master_gazetteer.sqlite"
DEFAULT_COUNTRY_ID_BIAS = 0.49

GAZETTEER_SOURCE_ID = {
    "ISO",  # ISO-3166 metadata
    "N",  # NGA
    "NF",  # NGA fixed
    "U",  # USGS
    "UF",  # USGS fixed
    "OA",  # OpenSextant Adhoc
    "OG",  # OpenSextant geonames.org derived
    "G",  # Geonames.org
    "GP"  # Geonames.org Postal
    "X",  # Xponents
    "NE"  # Natural Earth
}

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
    "XP": "X",
    "GP": "GP",  # Geonames Postal
    "G": "G"
}

# Scripts, not languages per se.
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
    "THAI": "TH"
}

# FIPS code to ISO CC
# Extend to other territory codes
US_TERRITORY_MAP = {
    "FIPS": {
        "AQ": "AS",
        "GQ": "GU",
        "CQ": "MP",
        "RQ": "PR",
        "VI": "VI",
        "FQ": "UM",
        "DQ": "UM",
        "HQ": "UM",
        "JQ": "UM",
        "WQ": "UM",
        "MQ": "UM"
    },
    "ISO": {
        # Reverse is not true for all cases:  ISO to FIPS
        # "UM": "UM",
        "PR": "RQ",
        "MP": "CQ",
        "GU": "CQ",
        "AS": "AQ"
    }
}


def get_default_db():
    return os.path.join(".", "tmp", DEFAULT_MASTER)


def load_stopterms(project_dir=".", lower=True):
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
            if lower:
                stopterms.add(t[0].lower())
            else:
                stopterms.add(t[0])
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
    # 'script': None,
    'name_bias': 0,
    'id_bias': 0,
    'name_type': "N",
    'search_only': False
}


def parse_admin_code(adm1):
    """
    :param adm1: admin level 1 code
    :return: ADM1 code if possible.
    """
    if not adm1:
        return ""

    code = adm1
    if "?" in adm1:
        code = "0"
    elif "." in adm1:
        cc2, code = adm1.split(".")
    # Normalize Country-level.  Absent ADM1 levels are assigned "0" anyway
    if code.strip() in {"", None, "0", "00"}:
        code = "0"
    return code


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
        # "script": place.name_script,
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
    def __init__(self, dbpath, commit_rate=1000, debug=False):
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
        self.debug = debug
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
        except sqlite3.IntegrityError:
            print("Data integrity issue")
            print(format_exc(limit=5))
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
        if "lat" in dct and not dct.get("geohash"):
            dct["geohash"] = geohash_encode(dct["lat"], dct["lon"], precision=6)
        #
        # print("Geoname has no location", dct)
        if "search_only" not in dct:
            dct["search_only"] = 1
            nb = dct.get("name_bias", 0)
            dct["search_only"] = nb < 0

    def add_place(self, obj):
        """
        Add one place
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
                cc, FIPS_cc, adm1, adm2, source, name_bias, id_bias, search_only
             ) values (
                :id, :place_id, :name, :name_type, :name_group, 
                :lat, :lon, :geohash, :feat_class, :feat_code,
                :cc, :FIPS_cc, :adm1, :adm2, :source, :name_bias, :id_bias, :search_only)"""
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
        """
        List distinct country codes in DB.
        :return: list of country codes.
        """
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
        :param limit:  non-zero limit
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
        if self.debug:
            print(sql_script)
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

    def update_name_type(self, arr: list, t: str):
        """
        Change the name type in bulk.
        :param arr: bulk array of placenames to change
        :param t: type code 'A', 'N', 'C'
        :return:
        """
        if not arr:
            return False
        step = 1000
        for x1 in _array_blocks(arr, step=step):
            x2 = x1 + step
            arg = ",".join([str(pl) for pl in arr[x1:x2]])
            sql = f"update placenames set name_type='{t}' where id in ({arg})"
            self.conn.execute(sql)
            self.conn.commit()
        return True

    def update_admin1_code(self, cc, from_code, to_code):
        if not cc:
            print("NULL country code operations must be done manually, carefully.")
            return False
        sql = f"update placenames set adm1='{to_code}' where cc='{cc}' and adm1='{from_code}'"
        if from_code == 'NULL':
            sql = f"update placenames set adm1='{to_code}' where cc='{cc}' and adm1 is NULL"

        if self.debug:
            print(sql)
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
        self.db = DB(dbf, commit_rate=100)
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

    def process_source(self, sourcefile, limit=-1):
        """
        generator yielding DB geo dictionary to be stored.
        :param sourcefile: Raw data file
        :param limit: limit of number of records to process
        :return: generator of Place object or dict of Place schema
        """
        yield None

    def add_location(self, geo, lat, lon):
        if lat and lon:
            geo["lat"] = parse_float(lat)
            geo["lon"] = parse_float(lon)
        else:
            print("No location on ROW", geo.get("place_id"))
        if "lat" in geo:
            geo["geohash"] = geohash_encode(geo["lat"], geo["lon"], precision=6)
        return geo

    def normalize(self, sourcefile, limit=-1, optimize=False):
        """
        Given the spreadsheet or source file rip through it, ingesting contents into the master gazetteer.
        :param sourcefile: input file
        :param limit: non-zero limit for testing
        :param optimize: if database should be optimized when done.
        :return:
        """

        print("\n============================")
        print(f"Start {self.source_name}. {arrow.now()}  FILE={sourcefile}")
        for geo in self.process_source(sourcefile, limit=limit):
            if self.rowcount % self.rate == 0 and not self.quiet:
                print(f"Row {self.rowcount}")
            if 0 < limit < self.rowcount:
                print("Reached non-zero limit for testing.")
                break
            try:
                self.db.add_place(geo)
            except sqlite3.IntegrityError:
                print("Data integrity issue")
                print(format_exc(limit=5))
                print(self.db.queue)
                break
            except Exception:
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

        self.server = pysolr.Solr(server_url)
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


class GazetteerSearch:
    def __init__(self, server_url):
        """
        TODO: BETA - looking to abstract Solr().search() function for common types of queries.
            For now getting a list of country name variants is easy enough.
        :param server_url:  URL with path to `/solr/gazetteer' index
        """
        self.index_url = server_url
        self.server = pysolr.Solr(self.index_url)

    def get_countries(self, max_namelen=30):
        """
        Searches gazetteer for Country metadata
        TODO: dovetail Country metadata (lang, timezone, codes, etc) with
            Country place data.
        TODO: Document different uses for GazetteerSearch.get_countries() from API get_country()
        TODO: Review differences in Place() schema and Country() schema for name variants,
            e.g., Country variants presented as abbreviations, codes or names need to be distinguished as such.
        :param max_namelen:
        :return:
        """
        countries = []
        hits = self.server.search("feat_class:A AND feat_code:PCL*", **{"rows": 30000})
        for country in hits:
            nm = country['name']
            if len(nm) > max_namelen:
                continue
            C = Country()
            C.name = nm
            C.cc_iso2 = country['cc']
            C.cc_fips = country.get('FIPS_cc')
            C.name_type = country.get('name_type')
            feat_code = country.get('feat_code')
            C.is_territory = feat_code != "PCLI" or "territo" in nm.lower()  # is NOT independent
            countries.append(C)
        return countries


class PlaceHeuristics:
    def __init__(self):
        from opensextant import load_major_cities
        from opensextant import make_HASC
        self.debug = False
        self.cities = {}
        self.cities_spatial = {}  # keyed by geohash
        self.stopwords = load_stopterms()
        self.MAX_NAMELEN = 50
        self.stat_charcount = 0
        self.stat_namecount = 0
        self.feature_wt = {
            "A": 10,
            "P": 9,
            "S": 7,
            "T": 6,
            "L": 7,
            "V": 7,
            "R": 5,
            "H": 7,
            "U": 3,
            "P/PPLX": 7,
            "P/PPLH": 7,
            "H/STM": 3,
            "H/WLL": 3
        }

        # Look up cities by CC.ADM1
        for city in load_major_cities():
            key = make_HASC(city.country_code, city.adm1)
            if key in self.cities:
                self.cities[key].append(city)
            else:
                self.cities[key] = [city]

            key = city.geohash
            if city.population > 0:
                if key not in self.cities_spatial:
                    self.cities_spatial[key] = 0
                elif self.debug:
                    print("Dense cities around ", key, city)
                self.cities_spatial[key] += city.population

    def estimate_bias(self, geo, name_group=""):
        """
        Primary Estimator of id_bias and name_bias.

        id_bias   -- a location bias to pre-rank city by feature/population
        name_bias -- a metric ranging from -1 to 1, that represents the validity of a tagging the name/phrase
                     in a general context.  The result is binary  search_only = name_bias < 0. This means
                     that geo names that are search_only are not taggable.
        :param geo:
        :param name_group:
        :return:
        """
        geo["id_bias"] = self.location_bias(geo["geohash"], geo["feat_class"], geo["feat_code"])
        geo["name_bias"] = self.name_bias(geo["name"], geo["feat_class"], name_group)

    def get_feature_scale(self, fc, dsg):
        #  Location bias is 70% population, 30% feature type
        #
        if not dsg:
            return self.feature_wt.get(fc, 5)

        fckey = f"{fc}/{dsg}"
        for l in [6, 5]:
            fc_scale = self.feature_wt.get(fckey[0:l])
            if fc_scale:
                return fc_scale

        return self.feature_wt.get(fc, 5)

    def location_bias(self, loc, fc, dsg):
        """
        A location is pre-disposed by its feature type and population/popularity.
        E.g., large cities are mentioned more often in news or documents than less populated cities.
        Factors:

        Feature gradient     A, P, ..... U.  More populated features have higer bias
        Population gradient  log(pop)  scales bias higher

        :param loc: geohash(5)
        :param fc:  feat class
        :param dsg: feat code designation
        :return:
        """
        pop_wt = 0
        fc_scale = self.get_feature_scale(fc, dsg)

        if loc:
            # Lookup against Major Cities uses geohash 5-char prefix
            lockey = loc[0:5]
            if lockey in self.cities_spatial:
                population = self.cities_spatial[lockey]
                pop_wt = natlog(population) - 10

        # For PLACES  this helps differentiate P/PPL by population
        # Between PLACES and BOUNDARIES the population component may rank places
        # higher than a boundary by the same name.
        #
        # 1/10 of the weighted sums.  Get the ID BIAS to range 0..1
        return ((0.7 * pop_wt) + (0.3 * fc_scale)) / 10

    def name_bias(self, geoname: str, feat_class: str, name_group: str):
        """
        Given a geoname we look at the instance of the name variant and if it is something trivially
        colliding with stopwords in other languages then we consider omitting it.

        very positive bias   - long unique name, diacritic or in non-ASCII script
        positive bias        - normal location name, multiple words or grams
        neutral              - possibly a place name, but is case-dependent, e.g., person name or generic monument name.
        negative bias        - a stopword or trivial version of a stopword, `Åre`
        very negative bias   - a very rare or extremely long version of a place name, nonsense

        Conclusion: Any Negative name_bias term will NOT be tagged, although it is present in gazetteer.

        CODE and ABBREV are not biased -- they are simply not full names.

        :param geoname:
        :param feat:  F/CCC*  formatted string to filter on.
        :return:  floating point number between -1 and 1
        """
        if name_group in {'cjk', 'ar'}:
            return trivial_bias(geoname) + 0.10

        self.stat_namecount += 1
        namelen = len(geoname)
        self.stat_charcount += namelen

        if 20 < namelen < self.MAX_NAMELEN:
            return trivial_bias(geoname)
        elif namelen >= self.MAX_NAMELEN:
            # Name is too long to consider tagging; Unlikely to appear in this form.
            return -0.1

        # Test shorter names
        norm = geoname.lower()
        if norm in self.stopwords:
            return -1

        # "The Bar", "The Point"
        test = f"the {norm}"
        if test in self.stopwords:
            return -0.5

        if feat_class != "A":
            test = replace_diacritics(norm)
            if norm != test:
                if test in self.stopwords:
                    return -0.5

        # Return a positive value.
        return trivial_bias(norm)


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
