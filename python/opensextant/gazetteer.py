import json
import os
import sqlite3
from traceback import format_exc

import arrow
import pysolr
from opensextant import Place, Country, distance_haversine, load_major_cities, make_HASC, popscale, \
    geohash_cells_radially, bbox, point2geohash, geohash2point, pkg_resource_path
from opensextant.utility import ensure_dirs, is_ascii, has_cjk, has_arabic, \
    ConfigUtility, get_bool, trivial_bias, replace_diacritics, strip_quotes, parse_float, load_list
from opensextant.wordstats import WordStats

DEFAULT_SOLR_URL="127.0.0.1:7000"
DEFAULT_MASTER = "master_gazetteer.sqlite"
DEFAULT_COUNTRY_ID_BIAS = 49
DEFAULT_WORDSTATS = "wordstats.sqlite"

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

# IGNROE Historical names and Zones, and Unknowns *H
MAJOR_ADMIN_CODES = {'ADM1', 'ADMD', 'ADM2', 'ADM3', 'ADM4', 'PRSH', 'TERR'}


def coord_grid(geo: dict) -> str:
    """
    A less dissatisfying grid than geohash. Its just returning Y,X in low resolution. LLL.l,LLL.l
    """
    if "lat" not in geo:
        return None
    x, y = geo["lon"], geo["lat"]
    return f"{y:0.1f},{x:0.1f}"


def get_default_db():
    return os.path.join(".", "tmp", DEFAULT_MASTER)


def get_default_wordstats():
    return os.path.join(".", "tmp", DEFAULT_WORDSTATS)


def load_stopterms(project_dir=".", lower=True):
    """
    Load default stop terms from source tree for project build.
    :param project_dir: The location of Xponents/solr source tree.
    :param lower: default case to load data as. If not lower, then terms are loaded as-is
    :return:
    """
    loader = ConfigUtility()
    stopterms = set([])
    for f in ["etc/gazetteer/filters/non-placenames.csv",
              "etc/gazetteer/filters/non-placenames,spa.csv",  # SPANISH
              "etc/gazetteer/filters/non-placenames,rus,ukr.csv",  # Cyrillic languages
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


def normalize_name(nm: str):
    """
    convenience method that ensures we have some consistency on normalization of name
    :param nm:
    :return:
    """
    return nm.replace("\u2019", "'").replace("\xa0", " ").strip().strip("'")


def name_group_for(nm: str):
    """
    Determine the major language "name group" for the input
    :param nm: name or any text
    :return:
    """
    if has_cjk(nm):
        return "cjk"
    elif has_arabic(nm):
        return "ar"
    return ""


def as_admin_place(r):
    """
    Convert dict to a Place object
    :param r: gazetteer row from Solr or SQlite.
    :return: Place
    """
    keys = {}
    if hasattr(r, "keys"):
        keys = r.keys()

    p = Place(r['place_id'], r['name'])
    p.country_code = r["cc"]
    p.adm1 = r["adm1"]
    p.source = r["source"]
    p.geohash = r["geohash"]
    if "adm1_iso" in keys:
        p.adm1_iso = r["adm1_iso"]

    p.lat = p.lon = p.X = p.Y = None
    return p


def as_place(r, source="index"):
    """
    Convert dict to a Place object
    :param source: db or index (solr)
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
    if source == "db":
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
    if place.adm1:
        rec["adm1"] = place.adm1
    if place.adm2:
        rec["adm2"] = place.adm2
    # ID BIAS:
    rec["id_bias"] = 0 if place.id_bias is None else place.id_bias

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
        rec["name_bias"] = 0 if place.name_bias is None else place.name_bias
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
        print(str(p), f"\tfeature: {p.feature_class}/{p.feature_code}")


def capitalize(name: dict):
    """ Capitalize all city and major admin boundaries """
    nm = name["name"]
    if nm and not nm[0].isupper():
        return

    grp = name.get("name_group")
    nt = name.get("name_type")
    ft = name["feat_class"]
    if nm and grp == '' and nt == 'N' and ft in {'A', 'P'}:
        # Because we don't like altering data much:
        name["name"] = nm[0].upper() + nm[1:]


def gaz_resource(fname):
    """
    Formats the relative path for an item in the ./solr/etc/gazetteer/ metadata
    :param fname:
    :return:
    """
    return os.path.join("etc", "gazetteer", fname)


def export_admin_mapping(admin_ids, filepath):
    """
    Experimental:  Map all source place IDs => ADM ids
                   Map all standard ADM ids => place IDs
    :param admin_ids:  dict for JSON or array for CSV
    :param filepath:
    :return:
    """
    with open(filepath, "w", encoding="UTF-8") as fio:
        fio.write("\t".join(["ADM1", "PLACE_ID", "LAT", "LON", "NAME"]))

        for a1 in admin_ids:
            cc = a1["cc"]
            adm1 = a1["adm1"]
            hasc = f"{cc}.{adm1}"
            y, x = a1["lat"], a1["lon"]
            entry = [hasc, a1["place_id"], f"{y:0.1f}", f"{x:0.1f}", a1["name"]]
            fio.write("\t".join(entry))
            fio.write("\n")

class ISO3166Registry:
    def __init__(self):
        self.hierarchy = {}

    def is_iso_country(self, cc):
        return cc in self.hierarchy

    def has_admin1(self, cc, adm1):
        registry = self.hierarchy.get(cc)
        if not registry:
            # No such country here.
            return None
        return adm1 in registry["admin1"]

    def get_admin1_for(self, cc, adm2):
        registry = self.hierarchy.get(cc)
        if not registry:
            # No such country here.
            return None
        return registry["admin2"].get(adm2)

    @staticmethod
    def export_admin_mapping():
        """
        Generate initial file using pycountry.  pycountry is not a library dependency
        so it is externalized here.
        :return:
        """
        import pycountry
        fpath = gaz_resource("iso3166_admin1_admin2_hierarchy.csv")
        with open(fpath, "w", encoding="UTF-8") as fio:
            for subd in pycountry.subdivisions:
                fio.write("\t".join([subd.country_code, subd.parent_code or "-", subd.code]))
                fio.write("\n")

    def load_admin_mapping(self):
        from opensextant import parse_admin_code
        fpath = gaz_resource("iso3166_admin1_admin2_hierarchy.csv")
        with open(fpath, "r", encoding="UTF-8") as fio:
            for line in fio:
                hasc = line.strip().split("\t")
                cc_iso = hasc[0]
                adm1, adm2 = None, None
                parent = hasc[1]
                if parent == "-":
                    adm1 = parse_admin_code(hasc[2])
                else:
                    adm1, adm2 = parse_admin_code(hasc[1]), parse_admin_code(hasc[2])

                registry = self.hierarchy.get(cc_iso)
                if not registry:
                    registry = {"admin1": set([]), "admin2": {}}
                    self.hierarchy[cc_iso] = registry
                registry["admin1"].add(adm1)
                if adm2:
                    registry["admin2"][adm2] = adm1


class AdminLevelCodes:

    # CC = { places = { PLACE_ID: { 'f':FIPS_ADM1, 'i':ISO_ADM1 }},
    #        coords = { COORD_ID: { 'f':FIPS_ADM1, 'i':ISO_ADM1 }},
    #        admin1 = { 'f':{FIPS_ADM1:ISO_ADM1, ...},
    #                   'i':{ISO_ADM1:FIPS_ADM1, ....}}
    #
    #  Step 1a. Inventory all places by ID across all data sources and standards.
    #  Step 1b.       at that time also calculate the relevant codings at the coordinate grid by standard
    #  Step 2.  Reindex mapping all paired ADM1-ADM1 equivalents.
    #
    # NOT every PLACE is represented by both codings, Not every coordinate has both codings
    # NOT every ADM1 has a mapping
    # Questions:
    #     Given an ADM1 in a known standard, what is the alternative code in other standard?
    #     Given a place ID (but no ADM1) locate the relevant ADM1 code

    def __init__(self, filepath=None):
        self.places = {}
        self.coords = {}
        self.admin1 = {}
        self.admin2 = {}  # ADM2 contained in ADM1. {ADM2 : ADM1,...}
        self.countries = set([])
        self.admin_hierarchy = {}  # Set before use.
        self.admin_hierarchy_cc = {}
        if filepath:
            self.load(filepath)
        else:
            self.load(pkg_resource_path("global_admin1_mapping.json"))

    def adjust_admin1(self, cc, adm1):
        if cc in self.admin_hierarchy_cc:
            container_adm1 = self.admin_hierarchy[cc].get(adm1)
            if container_adm1:
                # New mapping:  ADM1, ADM2
                return container_adm1, adm1
        return None, None

    def set_admin_hierarchy(self, countries, adm1_containment):
        self.admin_hierarchy_cc = set(countries)
        self.admin_hierarchy = adm1_containment

    def get_alternate_admin1(self, cc, adm1, std):
        """
        EXPERIMENTAL still.

        :param cc: ISO country code
        :param adm1: ADM1 in the given standard
        :param std: standard "FIPS" or "ISO"
        :return:
        """
        if cc in self.admin1:
            country_registry = self.admin1[cc]
            if country_registry:
                return country_registry[std].get(adm1)
        # No country
        return None

    def add_country(self, cc):
        self.countries.add(cc)
        if cc not in self.places:
            self.places[cc] = {}
        if cc not in self.coords:
            self.coords[cc] = {}
        if cc not in self.admin1:
            self.admin1[cc] = {}
        if cc not in self.admin2:
            self.admin2[cc] = {}

    def add_place(self, place_id, cc, std, adm1, grid):
        """
        Accumulate discrete ADM1 codings by place instance and location
        :param place_id:
        :param cc:
        :param std:
        :param adm1:
        :param adm2: optional ADM2 mapping
        :param grid:
        :return:
        """
        alt_adm1, adm2 = self.adjust_admin1(cc, adm1)
        if adm2:
            print("CORRECTION:", cc, alt_adm1, adm2)

        obj = self.places[cc].get(place_id, {})
        if not obj:
            self.places[cc][place_id] = obj
        # TODO: detect errors where a place has a standard set already, but the adm1 value conflicts
        obj[std] = adm1

        crd = self.coords[cc].get(grid, {})
        if not crd:
            self.coords[cc][grid] = crd
        crd[std] = adm1

        if adm2:
            mapping = self.admin2[cc].get(adm2, {})
            mapping[adm2] = adm1

    @staticmethod
    def _update_adminset(given, iso=None, fips=None):
        """
        update the iso[i]=f
        update the fips[f]=i
        from the given (f=i, i=f,)

        :param given:  a record of codings {f:val, i:val}
        :param iso:  accumulating iso map
        :param fips:  accumulating fips map
        :return:
        """
        f = given.get("f", "-")
        i = given.get("i", "-")
        curr_f = iso.get(i)
        curr_i = fips.get(f)
        missing_f = not curr_f or curr_f == "-"
        missing_i = not curr_i or curr_i == "-"

        if f != "-" and i != "-":
            if missing_f:
                iso[i] = f
            if missing_i:
                fips[f] = i
        else:
            if f != "-" and missing_i:
                fips[f] = "-"
            if i != "-" and missing_f:
                iso[i] = "-"

    def align_admin1(self):
        for cc in self.countries:
            # TODO: we'll keep this experimental layer of ADM2s.
            # Usage: user will have to consult the admin2 lookup first.
            fips = {}
            iso = {}
            registry = self.places[cc]  # ADMIN places only please.
            for plid in registry:
                self.update_admin_containment(registry[plid])

            for plid in registry:
                AdminLevelCodes._update_adminset(registry[plid], iso=iso, fips=fips)

            registry = self.coords[cc]
            for crd in registry:
                AdminLevelCodes._update_adminset(registry[crd], iso=iso, fips=fips)

            # Mappings:
            self.admin1[cc] = {"FIPS": fips, "ISO": iso}

    def as_json(self):
        result = {}
        for cc in self.countries:
            result[cc] = {"places": self.places.get(cc),
                          "coords": self.coords.get(cc),
                          "admin1": self.admin1.get(cc)}
        return result

    def save(self, fpath):
        with open(fpath, "w", encoding="UTF-8") as fout:
            json.dump(self.as_json(), fout)

    def load(self, fpath):
        if not os.path.exists(fpath):
            raise Exception("File does not exist", fpath)

        with open(fpath, "r", encoding="UTF-8") as fin:
            content = json.load(fin)
            self.countries = set(content.keys())
            for cc in self.countries:
                self.places[cc] = content[cc].get("places")
                self.coords[cc] = content[cc].get("coords")
                self.admin1[cc] = content[cc].get("admin1")


def load_major_cities_iso():
    print("Popstats - Load Major Cities / as ISO coded")
    admin_lookup = None
    try:
        admin_lookup = AdminLevelCodes()
    except Exception as config_err:
        print("Try generating Admin level codes first with \"build prep admin1\" script")
        print(str(config_err))
        return

    # ADM1 hierarchy
    #  NGA used FIPS, now uses ISO
    #  Geonames uses FIPS, except a few:
    #
    already_iso = {"US", "BE", "CH", "ME"}

    cities = load_major_cities()
    problem_countries = {}
    all_countries = {}
    for city in cities:
        cc = city.country_code
        all_countries[cc] = 1 + all_countries.get(cc, 0)
        if cc in already_iso:
            continue

        adm1_iso = admin_lookup.get_alternate_admin1(cc, city.adm1, "FIPS")
        if adm1_iso == "-":
            print("No match for FIPS", cc, city.adm1)
            problem_countries[cc] = 1 + problem_countries.get(cc, 0)
        elif adm1_iso:
            city.adm1 = adm1_iso  # this attr represents the default internal ADM1
            city.adm1_iso = adm1_iso  # Yes, this attr represents the ISO ADM1
    print("Countries with missing ISO ADM1")
    for cc in problem_countries:
        if problem_countries[cc] > 1:
            print(f"{cc}\t{problem_countries[cc]:4}  /  {all_countries[cc]:4}")
    return cities


class DB:
    def __init__(self, dbpath, commit_rate=1000, debug=False, add_geohash=False):
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
        self.geohash_default = add_geohash
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

    def delete_places(self, q):
        """
        :param q: query starting with "WHERE...."
        :return:
        """
        if not q:
            raise Exception("Query required silly")
        self.conn.execute(f"delete from placenames {q}")

    def create(self):
        """
        Create the placenames table and default indices used for ETL - place_id, source, country, and ADM1
        :return:
        """
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
                `duplicate` BIT DEFAULT 0,
                `name_bias` INTEGER DEFAULT 0,
                `id_bias` INTEGER DEFAULT 0,
                `search_only` BIT DEFAULT 0                
            ) without rowid;
            
            create INDEX plid_idx on placenames ("place_id");               
            create INDEX s_idx on placenames ("source");
            create INDEX c_idx on placenames ("cc");
            create INDEX a1_idx on placenames ("adm1");
        """
        self.conn.executescript(sql_script)
        self.conn.commit()

        # Population statistics that use location (geohash) as primary key
        sql_script = """
        create TABLE popstats (
                `grid` TEXT NOT NULL, 
                `population` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `feat_class` TEXT NOT NULL,
                `cc` TEXT NOT NULL,
                `FIPS_cc` TEXT  NULL,
                `adm1` TEXT  NULL, 
                `adm1_path` TEXT NOT NULL,        
                `adm2` TEXT  NULL, 
                `adm2_path` TEXT NOT NULL        
        );
        
        create INDEX IF NOT EXISTS idx1 on popstats (`grid`);
        create INDEX IF NOT EXISTS idx2 on popstats (`source`);
        create INDEX IF NOT EXISTS idx3 on popstats (`cc`);
        create INDEX IF NOT EXISTS idx4 on popstats (`adm1`);
        create INDEX IF NOT EXISTS idx5 on popstats (`adm2`);
        
        """
        self.conn.executescript(sql_script)
        self.conn.commit()

    def create_indices(self):
        """
        Create additional indices that are used for advanced ETL functions and optimization.
        :return:
        """
        self.reopen()
        indices = """
            create INDEX IF NOT EXISTS n_idx on placenames ("name");
            create INDEX IF NOT EXISTS nt_idx on placenames ("name_type");
            create INDEX IF NOT EXISTS ng_idx on placenames ("name_group");
            create INDEX IF NOT EXISTS s_idx on placenames ("source");
            create INDEX IF NOT EXISTS c_idx on placenames ("cc");
            create INDEX IF NOT EXISTS a1_idx on placenames ("adm1");
            create INDEX IF NOT EXISTS fc_idx on placenames ("feat_class");
            create INDEX IF NOT EXISTS ft_idx on placenames ("feat_code");
            create INDEX IF NOT EXISTS dup_idx on placenames ("duplicate");
            create INDEX IF NOT EXISTS so_idx on placenames ("search_only");
            create INDEX IF NOT EXISTS lat_idx on placenames ("lat");
            create INDEX IF NOT EXISTS lon_idx on placenames ("lon");
                        
        """
        self.conn.executescript(indices)
        self.conn.commit()

    def optimize(self):
        self.reopen()
        self.conn.execute("VACUUM")
        self.conn.commit()

    def reopen(self):
        if self.conn is not None:
            return

        # really close cleanly
        self.close()

        self.conn = sqlite3.connect(self.dbpath)
        self.conn.execute('PRAGMA cache_size = 8092')
        self.conn.execute('PRAGMA page_size =  8092')  # twice default. Cache = 8092 x 8KB pages ~ 64MB
        self.conn.execute('PRAGMA mmap_size =  1048576000')  # 1000 MB
        self.conn.execute("PRAGMA encoding = 'UTF-8'")
        self.conn.execute('PRAGMA synchronous = OFF')
        self.conn.execute('PRAGMA locking_mode = EXCLUSIVE')
        self.conn.execute('PRAGMA journal_mode = MEMORY')
        self.conn.execute('PRAGMA temp_store = MEMORY')
        self.conn.row_factory = sqlite3.Row

    def commit(self):
        if self.conn:
            self.conn.commit()

    def close(self):
        try:
            if self.conn is not None:
                self.__assess_queue(force=True)
                self.conn.close()
                self.conn = None
        except sqlite3.IntegrityError as sql_err:
            print("Data integrity issue")
            print(format_exc(limit=5))
        except Exception as err:
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
        if self.geohash_default:
            # 6 geohash prefix is about 100m to 200m error. precision=8 is 1m precision.
            if "lat" in dct and not dct.get("geohash"):
                dct["geohash"] = point2geohash(dct["lat"], dct["lon"], precision=6)
        #
        # print("Geoname has no location", dct)
        if "search_only" not in dct:
            nb = dct.get("name_bias", 0)
            dct["search_only"] = 1 if nb < 0 else 0

        capitalize(dct)

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

    def list_places_by_id(self, plid, limit=2):
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
            pl = as_place(row, source="db")
            if not place:
                place = pl
            name_bias[pl.name.lower()] = pl.name_bias
        if place:
            # This is first place encountered.
            # This is not idempotent unless SQL query is more explicit
            return place, name_bias

        return None, None

    def add_population_stats(self, source="G"):
        """
        Population stats are record by populated area (P-class features) and rolled up
        to provide an ADM1 population approximation.
        """
        print("Purge Popstats")
        self.conn.execute("delete from popstats where source = ?", (source,))
        self.conn.commit()

        sql = """insert into popstats (grid, population, source, feat_class, 
                cc, FIPS_cc, adm1, adm1_path, adm2, adm2_path) 
            values (:grid, :population, :source, :feat_class, 
                :cc, :FIPS_cc, :adm1, :adm1_path, :adm2, :adm2_path)"""
        #
        for city in load_major_cities_iso():
            adm2_path = ""
            if city.adm2:
                adm2_path = make_HASC(city.country_code, city.adm1, adm2=city.adm2)
            city_entry = {
                "grid": coord_grid({"lat": city.lat, "lon": city.lon}),
                "population": city.population,
                "source": source,
                "feat_class": city.feature_class,
                "FIPS_cc": city.country_code_fips,
                "cc": city.country_code,
                "adm1": city.adm1,
                "adm1_path": make_HASC(city.country_code, city.adm1),
                "adm2": city.adm2,
                "adm2_path": adm2_path
            }
            self.conn.execute(sql, city_entry)
        self.conn.commit()
        print("Popstats - Complete")

    def list_all_popstats(self):
        """
        :return: map of population by geohash only
        """
        sql = """select sum(population) AS POP, grid from popstats group by grid order by POP"""
        population_map = {}
        for popstat in self.conn.execute(sql):
            loc = popstat["grid"]
            population_map[loc] = popstat["POP"]
        return population_map

    def list_adm1_popstats(self):
        """
        Provides a neat lookup of population stats by HASC path,
           e.g., "US.CA" is califronia; Reported at 35 million in major cities (where state total is reported
           at 39 million in 2021.)  Population stats only cover major cities of 15K or more people.
        :return: map of population stats by ADM1 path
        """
        sql = "select sum(population) AS POP, adm1_path from popstats where adm1 != '0' group by adm1_path order by POP"
        population_map = {}
        for popstat in self.conn.execute(sql):
            adm1 = popstat["adm1_path"]
            population_map[adm1] = popstat["POP"]
        return population_map

    def list_adm2_popstats(self):
        """
        Get approximate county-level stats
        """
        sql = "select sum(population) AS POP, adm2_path from popstats where adm2 != '' group by adm2_path order by POP"
        population_map = {}
        for popstat in self.conn.execute(sql):
            adm2 = popstat["adm2_path"]
            population_map[adm2] = popstat["POP"]
        return population_map

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
            yield as_place(p, source="db")

    def _list_places_at_geohash(self, lat: float = None, lon: float = None, geohash: str = None,
                                cc: str = None, radius: int = 5000, limit=10):
        """
        A best effort guess at spatial query. Returns an array of matches, thinking most location queries are focused.
        This is a geohash-backed hack at search.  Even with SQLite indexing this is still very slow.

        Use geohash_precision accordingly and approximately:
        - geohash_precision=6 implies +/-  500m
        - geohash_precision=5 implies +/-  2500m
        - geohash_precision=4 implies +/- 20000m

        This approach uses an approximation of finding the relevant neighbor cells using a geodetic (not geohash)
        assessment on the radial range.  This method hopefully gets past the limitations below.

        General limitations of using Geohash for spatial query:
        Given the nature of geohash you might have locations in different cells "xxxx" and "xxxy" that are
        close to each other, i.e. within your specified radius.  E.g.,

        "9q5f"  and "9qh4"  are neighbor cells
        "9q5fr" and "9qh42" are neighbor cells.

        "9q5fp" is a LR (south-east) corner of "9q5".  Searching that 2x2 KM box by geohash will only search from that
        corner north and westward.
        :param lat:
        :param lon:
        :param geohash:
        :param cc:
        :param radius:
        :param limit:
        :return: dict of matches,  { DIST = PLACE, ... }
        """
        if geohash:
            # Postpend "sss" to create a default centroid in a shorter geohash.
            gh = f"{geohash}sss"[0:6]
            (lat, lon) = geohash2point(geohash)
        elif lat is None and lon is None:
            raise Exception("Provide lat/lon or geohash")

        cells = geohash_cells_radially(lat, lon, radius)
        sql_script = []
        for gh in cells:
            if len(gh) >= 6:
                sql_script.append(f"select * from placenames where duplicate=0 and geohash = '{gh[0:6]}'")
            else:
                sql_script.append(f"select * from placenames where duplicate=0 and geohash like '{gh}%'")

        found = {}
        # Search the entire grid space
        for script in sql_script:
            for p in self.conn.execute(script):
                if cc:
                    if p["cc"] != cc:
                        continue
                place = as_place(p)
                dist = distance_haversine(lon, lat, place.lon, place.lat)
                if dist < radius:
                    found[dist] = place
            if len(found) >= limit:
                # Return after first round of querying.
                break

        return found

    def _list_places_at_2d(self, lat: float, lon: float,
                           cc: str = None, radius: int = 5000, limit=10):
        found = {}
        sw, ne = bbox(lon, lat, radius)
        sql_script = [f"""select * from placenames where 
            (lat < {ne.lat:0.6} and lon < {ne.lon:0.6}) and 
            (lat > {sw.lat:0.6} and lon > {sw.lon:0.6})"""]
        if cc:
            sql_script.append(f" and cc = '{cc}'")

        script = " ".join(sql_script)
        for p in self.conn.execute(script):
            place = as_place(p)
            dist = distance_haversine(lon, lat, place.lon, place.lat)
            if dist < radius:
                found[dist] = place

        return found

    def list_places_at(self, lat: float = None, lon: float = None, geohash: str = None,
                       cc: str = None, radius: int = 5000, limit=10, method="2d"):
        """

        :param lat: latitude
        :param lon: longitude
        :param cc:  ISO country code to filter.
        :param geohash:  optionally, use precomputed geohash of precision 6-chars instead of lat/lon.
        :param radius:  in METERS, radial distance from given point to search, DEFAULT is 5 KM
        :param limit: count of places to return
        :param method: bbox or geohash
        :return: array of tuples, sorted by distance.
        """
        found = {}
        if method == "geohash" or geohash and lat is None:
            found = self._list_places_at_geohash(lat=lat, lon=lon, geohash=geohash, cc=cc, radius=radius, limit=limit)
        elif method == "2d":
            found = self._list_places_at_2d(lat=lat, lon=lon, cc=cc, radius=radius, limit=limit)
        if not found:
            return []

        # Sort by distance key
        result = [(dist, found[dist]) for dist in sorted(found.keys())]
        return result[0:limit]

    def list_admin_names(self, sources=['U', 'N', 'G'], cc=None) -> set:
        """
        Lists all admin level1 names.
        :param cc: country code filter.
        :param sources: list of source IDs defaulting to those for USGS, NGA, Geonames.org
        :return: set of names, lowerased
        """
        source_criteria = ','.join([f"'{s}'" for s in sources])
        sql = f"""select distinct(name) AS NAME from placenames where  feat_class = 'A' and feat_code = 'ADM1' 
              and source in ({source_criteria}) and name_group='' and name_type='N'"""

        if cc:
            sql += f" and cc='{cc}'"
        names = set([])
        for nm in self.conn.execute(sql):
            # To list names, we normalize lowercase and remove dashes.
            names.add(nm['NAME'].lower().replace("-", " "))
        return names

    def update_place_id(self, rowid, plid):
        sql = "update placenames set place_id=? where rowid=?"
        self.conn.execute(sql, (plid, rowid,))

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
        return True

    def mark_search_only(self, pid):
        """
        Toggle bit for search only.
        :param pid: Place ID int or list
        """
        if isinstance(pid, int):
            sql = "update placenames set search_only=1 where id=?"
            self.conn.execute(sql, (pid,))
        elif isinstance(pid, list):
            idset = ", ".join([str(x) for x in pid])
            sql = f"update placenames set search_only=1 where id in ({idset})"
            self.conn.execute(sql)
        else:
            raise Exception("Place ID integer or list of integers is required")

    def update_bias(self, name_bias, rowids):
        arg = ",".join([str(pid) for pid in rowids])
        flag = 1 if name_bias < 0 else 0
        sql = f"update placenames set name_bias=?, search_only=? where id in ({arg})"
        self.conn.execute(sql, (name_bias, flag,))

    def update_bias_by_name(self, name_bias, name):
        flag = 1 if name_bias < 0 else 0
        sql = "update placenames set name_bias=?, search_only=? where name = ?"
        self.conn.execute(sql, (name_bias, flag, name,))


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


def add_location(geo, lat, lon, add_geohash=False):
    """
    Insert validated location coordinate and geohash
    :param add_geohash: due to performance, add this if needed
    :param geo: dict
    :param lat: latitude value, str or float
    :param lon: longitude value, str or float
    :return: geo dict with location
    """
    if lat and lon:
        geo["lat"] = parse_float(lat)
        geo["lon"] = parse_float(lon)
        if add_geohash and "lat" in geo:
            geo["geohash"] = point2geohash(geo["lat"], geo["lon"], precision=6)
        return True

    print("No location on ROW", geo.get("place_id"))
    return False


class DataSource:
    """
    Gazetteer Data Source abstraction -- provides guidelines on how to inject
    data into a common, normalized gazetteer.
    """

    def __init__(self, dbf, debug=False, ver=None):
        self.db = DB(dbf, commit_rate=100)
        self.ver = ver
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

        self.server_url = server_url
        if not self.server_url.startswith("http"):
            self.server_url = f"http://{self.server_url}/solr/gazetteer"

        self.server = pysolr.Solr(self.server_url)
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
        if done or (self.count % self.commit_rate == 0 and self.commit_rate > 0):
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

    def delete(self, entry_id=None):
        """
        Awaiting other kwdargs for deletion use cases.
        :param entry_id: master gazetteer row ID in sqlite or solr.  Deletes solr entry
        :return:
        """
        if entry_id:
            self.server.delete(id=entry_id)
            return True
        return False


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


def estimate_name_bias(nm):
    return 100 * trivial_bias(nm)


class PlaceHeuristics:
    # Population scale 0 = 16K, 1=32K, 2=64K, 3=128K
    LARGE_CITY = 3

    def __init__(self, dbref: DB):
        """

        :param dbref: DB instance
        """
        self.debug = False
        self.cities = set([])
        self.cities_large = set([])
        self.cities_spatial = {}  # keyed by geohash
        self.provinces = {}
        # These should only be used as relative rankings of size of admin boundaries.
        self.adm1_population = {}
        self.adm2_population = {}
        self.stopwords = load_stopterms()
        self.POPULATION_THRESHOLD = 200000
        self.MAX_NAMELEN = 50
        self.stat_charcount = 0
        self.stat_namecount = 0
        # Terms appearing in GoogleBooks 8,000,000 or more are consider not tag-worthy for geography, in general
        self.wordlookup = WordStats(get_default_wordstats())
        self.wordlookup.load_common(threshold=6000000)

        # Path relative to ./solr/
        fpath = os.path.join('etc', 'gazetteer', 'filters', 'non-placenames,admin-codes.csv')
        self.stopwords_admin_codes = set(load_list(fpath))

        self.exempt_features = {"PPLC", "ADM1", "PCLI", "PCL"}
        self.exempted_names = {}
        self.feature_wt = {
            "A": 11,
            "A/ADM1": 16,
            "A/ADM2": 14,
            "A/PCL": 16,
            "P": 10,
            "P/PPL": 10,  # Most common
            "P/PPLC": 15,
            "P/PPLA": 10,
            "P/PPLG": 9,
            "P/PPLH": 8,
            "P/PPLQ": 7,
            "P/PPLX": 7,
            "P/PPLL": 8,
            "L": 6,
            "R": 6,
            "H": 7,
            "H/SPNG": 2,
            "H/RSV": 2,
            "H/STM": 2,
            "H/WLL": 2,
            "V": 7,
            "S": 8,
            "U": 2,
            "T": 5,
            "T/ISL": 6,
            "T/ISLS": 6
        }

        # This is a set (list) of distinct names for ADM1 level names.
        # This obviously changes as you build out the master gazetteer as in the beginning it has NOTHING.
        self.provinces = dbref.list_admin_names()

        # Pop stats are primarily for P/PPL.
        for city in load_major_cities():
            self.cities.add(city.name.lower())
            if city.population_scale >= PlaceHeuristics.LARGE_CITY:
                self.cities_large.add(city.name.lower())

        self.cities_spatial = dbref.list_all_popstats()
        # These should only be used to score specific feature types ADM1 or ADM2
        self.adm1_population = dbref.list_adm1_popstats()
        self.adm2_population = dbref.list_adm2_popstats()

    def is_large_city(self, name):
        return name in self.cities_large

    def is_significant(self, feat) -> bool:
        return feat in self.exempt_features

    def is_province_name(self, name) -> bool:
        """
        Report if a name is that of a province, regardless of whether the location repreents something else.
        E.g.
            "Florida" is a city (lesser known) or a state (well known).   Therefore it is a popular name.
        :param name:
        :return:
        """
        return name in self.provinces

    def is_stopword(self, name: str) -> bool:
        if name in self.stopwords:
            return True

        if name.replace("-", " ") in self.stopwords:
            return True

        # Name is "Bar"...
        # test if "The Bar" is a stopword
        if f"the {name}" in self.stopwords:
            return True

        # Name is "The Bar"
        # test if "bar" is a stopword
        if name.startswith("the "):
            if name[4:].strip() in self.stopwords:
                return True
        return False

    def estimate_bias(self, geo, name_group=""):
        """
        Primary Estimator of id_bias and name_bias.

        id_bias   -- a location bias to pre-rank city by feature/population
        name_bias -- a metric ranging from -1 to 1, that represents the validity of a tagging the name/phrase
                     in a general context.  The result is eventually binary  search_only = name_bias < 0. This means
                     that geo names that are search_only are not taggable.
        :param geo:
        :param name_group:
        :return:
        """
        geo["id_bias"] = self.location_bias(geo)
        geo["name_bias"] = self.name_bias(geo["name"], geo["feat_class"], geo["feat_code"],
                                          name_group=name_group, name_type=geo["name_type"])

    def get_feature_scale(self, fc, dsg):
        """

        :param fc: feature class
        :param dsg: feature code
        :return:
        """
        #  Location bias is 70% population, 30% feature type
        #
        if not dsg:
            return self.feature_wt.get(fc, 5)

        fckey = f"{fc}/{dsg}"
        for glen in [6, 5]:
            fc_scale = self.feature_wt.get(fckey[0:glen])
            if fc_scale:
                return fc_scale

        return self.feature_wt.get(fc, 5)

    def location_bias(self, geo):
        """
        See estimate_bias()

        A location is pre-disposed by its feature type and population/popularity.
        E.g., large cities are mentioned more often in news or documents than less populated cities.
        Factors:

        Feature gradient     A, P, ..... U.  More populated features have higer bias
        Population gradient  log(pop)  scales bias higher

        :param geo:  standard ETL geo dict
        :return:  score on 100 point scale.
        """
        return int(10 * self._location_bias(geo))

    def _location_bias(self, geo):
        """
        dict with parts:

        :param geo:  standard ETL geo dict
        :return:  A number on the range of 0 to 10 approximately.
        """
        fc = geo["feat_class"]
        dsg = geo["feat_code"]
        pop_wt = 0
        fc_scale = self.get_feature_scale(fc, dsg)

        if fc == 'P':
            lockey = coord_grid(geo)
            population = self.cities_spatial.get(lockey, 0)
            pop_wt = popscale(population, feature="city")
        if fc == 'A':
            cc = geo["cc"]
            a1 = geo["adm1"]
            pop_wt = 1
            if dsg == 'ADM1':
                adm_path = make_HASC(cc, a1)
                population = self.adm1_population.get(adm_path, 0)
                pop_wt = popscale(population, feature="province")
            if dsg == 'ADM2' and "adm2" in geo:
                adm_path = make_HASC(cc, a1, geo["adm2"])
                population = self.adm2_population.get(adm_path, 0)
                pop_wt = popscale(population, feature="district")

        # For PLACES  this helps differentiate P/PPL by population
        # Between PLACES and BOUNDARIES the population component may rank places
        # higher than a boundary by the same name.
        #
        # Weighted sums -- Population has more information than the feature, so we weight that higher.
        return (0.75 * pop_wt) + (0.25 * fc_scale)

    def name_bias(self, geoname: str, feat_class: str, feat_code: str, name_group="", name_type="N"):
        """
        See estimate_bias()

        Given a geoname we look at the instance of the name variant and if it is something trivially
        colliding with stopwords in other languages then we consider omitting it.

        very positive bias   - long unique name, diacritic or in non-ASCII script
        positive bias        - normal location name, multiple words or grams
        neutral              - possibly a place name, but is case-dependent, e.g., person name or generic monument name.
        negative bias        - a stopword or trivial version of a stopword, `Åre`
        very negative bias   - a very rare or extremely long version of a place name, nonsense
        -1                   - WordStats reports as a "common" word.

        Conclusion: Any Negative name_bias term will NOT be tagged, although it is present in gazetteer.

        CODE and ABBREV are not biased -- they are simply not full names.

        TODO: ONLY unigrams are tracked, so
            "Alabama" -> not common,
            "Need" -> common,
            "New York" -> not tracked. This is a bi-gram

        :param geoname:
        :param feat_class:
        :param feat_code:
        :param name_group:
        :param name_type:
        :return:  floating point number between -100 and 100
        """
        return int(100 * self._name_bias(geoname, feat_class, feat_code, name_group=name_group, name_type=name_type))

    def _name_bias(self, geoname, feat_class, feat_code, name_group="", name_type="N"):
        """
        Details on assessing a name against common word stats, feature metadata, lang script
        :param geoname: name str
        :param feat_class:  UNUSED
        :param feat_code:
        :param name_group:
        :return:
        """

        if name_group in {'cjk', 'ar'}:
            # TODO: Should look up Stopwords here, but that likely happens in tagger.
            return trivial_bias(geoname) + 0.10

        self.stat_namecount += 1
        namelen = len(geoname)
        self.stat_charcount += namelen

        # if name_type == "C" and is_administrative(feat_class):
        # Quick checks:
        if namelen < 5:
            # Check for administrative codes that are most commonly stopterms or other meanings
            if geoname.upper() in self.stopwords_admin_codes:
                return -1
            # Omit pure digit names
            if geoname.isdigit():
                return -1

        if namelen < 2:
            return -0.1
        elif 30 < namelen < self.MAX_NAMELEN:
            return trivial_bias(geoname)
        elif namelen >= self.MAX_NAMELEN:
            # Name is too long to consider tagging; Unlikely to appear in this form.
            return -0.1

        # Test shorter names:  Combine feature, stopwords, and other tests.
        # ==============================================================
        # FIRST -- see if a judgement was made on a name already.
        norm = geoname.lower()
        if norm in self.exempted_names:
            return self.exempted_names[norm]

        # SECOND -- figure out if name is significant and popular because it is a popular place
        #           rather than just a common word.

        # TODO: add non-diacritic name to this test?
        norm2 = strip_quotes(replace_diacritics(norm))
        norm2 = norm2.replace("-", " ")
        is_popular_place = self.is_significant(feat_code) or self.is_large_city(norm) or \
                           self.is_province_name(norm) or self.is_province_name(norm2)

        # Example: "Moscow (P/PPLC)" significant. Name is exempted (significant feature)
        #          "Moscow (A/ADM2)" not significant; But it is flagged as "common"...and omitted without this:
        #          "Florida (P/PPL)" is not a common place
        #          "Florida (A/ADM1)" is a significant place.
        #                Note "Large Cities" vs. ADMIN-LEVEL1 boundaries are different lookups
        if is_popular_place:
            self.exempted_names[norm] = trivial_bias(geoname)
            return self.exempted_names[norm]
        elif self.is_stopword(norm):
            return -1
        elif self.wordlookup.is_common(norm):
            # is a common word, but not associated often with a location
            return -1
        else:
            # Much deeper checks on about 90% of the names
            # Omit short diacritic names that are typically stopwords.  These are partial biases
            # since we are now checking if the non-diacritic version is filtered.
            if norm != norm2:
                if self.wordlookup.is_common(norm2):
                    return -0.9

                if norm2 in self.stopwords:
                    return -0.5

                if norm2.upper() in self.stopwords_admin_codes:
                    return -0.6

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
