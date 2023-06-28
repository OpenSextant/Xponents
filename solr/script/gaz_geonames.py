from copy import copy

from opensextant import get_country, parse_admin_code
from opensextant.gazetteer import DataSource, get_default_db, normalize_name, \
    load_stopterms, PlaceHeuristics, AdminLevelCodes, add_location, gaz_resource, export_admin_mapping
from opensextant.utility import get_list, has_cjk, has_arabic, is_value, is_code, is_abbreviation, get_csv_reader

from gaz_etl import is_valid_admin

"""
from http://Geonames.org/export/dump

The main 'geoname' table has the following fields :
---------------------------------------------------
"""
schema = """
geonameid         : integer id of record in geonames database
name              : name of geographical point (utf8) varchar(200)
asciiname         : name of geographical point in plain ascii characters, varchar(200)
alternatenames    : alternatenames, comma separated, ascii names automatically transliterated, convenience attribute from alternatename table, varchar(10000)
latitude          : latitude in decimal degrees (wgs84)
longitude         : longitude in decimal degrees (wgs84)
feature class     : see http://www.geonames.org/export/codes.html, char(1)
feature code      : see http://www.geonames.org/export/codes.html, varchar(10)
country code      : ISO-3166 2-letter country code, 2 characters
cc2               : alternate country codes, comma separated, ISO-3166 2-letter country code, 200 characters
admin1 code       : fipscode (subject to change to iso code), see exceptions below, see file admin1Codes.txt for display names of this code; varchar(20)
admin2 code       : code for the second administrative division, a county in the US, see file admin2Codes.txt; varchar(80)
admin3 code       : code for third level administrative division, varchar(20)
admin4 code       : code for fourth level administrative division, varchar(20)
population        : bigint (8 byte int)
elevation         : in meters, integer
dem               : digital elevation model, srtm3 or gtopo30, average elevation of 3''x3'' (ca 90mx90m) or 30''x30'' (ca 900mx900m) area in meters, integer. srtm processed by cgiar/ciat.
timezone          : the iana timezone id (see file timeZone.txt) varchar(40)
modification date : date of last modification in yyyy-MM-dd format
"""
"""
AdminCodes:
Most adm1 are FIPS codes. ISO codes are used for US, CH, BE and ME. UK and Greece are using an additional level between country and fips code. The code '00' stands for general features where no specific adm1 code is defined.
The corresponding admin feature is found with the same countrycode and adminX codes and the respective feature code ADMx.

"""

GENERATED_BLOCK = 40000000
GEONAMES_SOURCE = "G"
GEONAMES_GAZ_TEMPLATE = {
    "id": "",
    "place_id": None,
    "name": None,
    "adm1": None,
    "adm2": None,
    "feat_class": None,
    "feat_code": None,
    "FIPS_cc": None,
    "cc": None,
    "lat": None,
    "lon": None,
    "geohash": None,

    # Inject constant data at ingest time -- "source" = G for Geonames.org
    "source": GEONAMES_SOURCE,
    # Default bias tuning
    "name_bias": 0,
    # ID Bias is not used much
    "id_bias": 0,
    "name_type": "N"
}

MAP_GN_OPENSEXTANT = {
    # "geonameid": "place_id",
    "name": "name",
    "latitude": "lat",
    "longitude": "lon",
    "feature_class": "feat_class",
    "feature_code": "feat_code",
    "country_code": "cc",
    "admin1_code": "adm1",
    "admin2_code": "adm2"
}

# Do not remap ADM1 for these:
GN_ISO_USAGE = {"US",  # USA
                "CH",  # Switzerland
                "BE",  # Belgium
                "ME"  # Montenegro
                }

stopterms = load_stopterms()


def render_distinct_names(arr):
    """

    :param arr:
    :return: set of names
    """
    # Trivial remapping - replace unicode diacritics with normal ASCII
    # These conventions can be source specific....
    names = {}
    for nm in arr:
        if nm:
            if "(historical)" in nm:
                nm = nm.replace("(historical)", "").strip()
            nm = normalize_name(nm)
            names[nm.lower()] = nm
    return set(names.values())


def _format(f):
    return float("{:0.3}".format(f))


def add_country(geo: dict):
    cc = geo["cc"]
    if cc:
        C = get_country(cc)
        if C:
            geo["FIPS_cc"] = C.cc_fips
        else:
            print(f"Unknown code '{cc}'")
    else:
        cc = "ZZ"
        geo["cc"] = cc
        geo["FIPS_cc"] = cc
        geo["adm1"] = "0"


FEATURE_GUESSES = {
    "rock": ("T", "RK"),
    "station": ("S", "RSTN"),
    "statistical": ("L", "RGNE"),
    "edifice": ("T", "CLF"),
    "point": ("L", "PNT"),
    "pont": ("L", "PNT"),
    "port": ("L", "PORT"),
    "bibliotheque": ("S", "LIBR"),
    "peak": ("T", "PK"),
    "bridge": ("S", "BDG"),
    "basin": ("T", "UNK")
}


def guess_feature(name):
    toks = name.lower().replace("-", " ").split()
    for t in toks:
        if t in FEATURE_GUESSES:
            return FEATURE_GUESSES.get(t)
    return "S", "UNK"


def parse_geonames_schema():
    """
    Use a copy of geonames documented schema to parse and create column headings.
    :return:
    """
    hdr = []
    for col in schema.split("\n"):
        columns = get_list(col, delim=":")
        if columns:
            hdr.append(columns[0].replace(" ", "_"))
    return hdr


GN_ISO_REMAP = {
    # ALL are FIPS --> ISO
    "AE": {"11": "0", "10": "01", "09": "0"},
    "AF": {"04": "19"}
}


def fix_geonames_admin1_iso(cc, adm):
    if cc in GN_ISO_REMAP:
        return GN_ISO_REMAP[cc].get(adm)
    return None


def render_entry(gn, columns):
    geo = copy(GEONAMES_GAZ_TEMPLATE)
    # Gather name variants
    for f in columns:
        # Gather Fields from source row and copy to destination schema.
        k = MAP_GN_OPENSEXTANT.get(f)
        if k:
            geo[k] = gn[f]

    plid = gn["geonameid"]
    geo["place_id"] = f"G{plid}"
    return geo


ERROR_CACHE = set([])


class GeonamesOrgScanner:
    def __init__(self):
        self.rowcount = 0

    def scan_adm1(self, sourcefile, limit=-1):
        header_names = parse_geonames_schema()
        admin_master = []
        found = 0
        # NOTE: Attempted pandas read_csv() operation. Failed.
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            df = get_csv_reader(fh, delim="\t", columns=header_names)
            for gn in df:
                self.rowcount += 1
                if self.rowcount % 1000000 == 0:
                    print(f"Row# {self.rowcount}")

                geo = render_entry(gn, header_names)
                nm = gn["asciiname"]
                if not nm:
                    continue

                geo["name"] = nm
                fclass, fcode = geo["feat_class"], geo["feat_code"]
                if fclass != "A":
                    continue

                add_country(geo)

                if not is_valid_admin(geo["cc"], fcode):
                    continue

                adm1 = parse_admin_code(geo.get("adm1"))
                geo["adm1"] = adm1
                add_location(geo, geo["lat"], geo["lon"])

                admin_master.append(geo)

                found += 1
                if 0 < limit < found:
                    print("Reached Limit", limit)
                    break

        export_admin_mapping(admin_master, gaz_resource("geonames_admin1_mapping.csv"))


class GeonamesOrgGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        # self.rate = 10
        self.source_keys = [GEONAMES_SOURCE]
        self.source_name = "Geonames.org"
        self.estimator = PlaceHeuristics(self.db)
        self.admin_lookup = AdminLevelCodes(filepath=gaz_resource('global_admin1_mapping.json'))

    def populate_admin1(self, geo):

        # GIVEN:
        adm1 = parse_admin_code(geo.get("adm1"))
        cc = geo["cc"]

        if cc in GN_ISO_USAGE or adm1 == "0":
            geo["adm1"] = adm1
            return

        # REMAP geonames FIPS code to ISO
        try:
            adm1_iso = self.admin_lookup.get_alternate_admin1(cc, adm1, "ISO")
            if adm1_iso:
                if isinstance(adm1_iso, str):
                    geo["adm1"] = adm1_iso
                elif isinstance(adm1_iso, list):
                    geo["adm1"] = adm1_iso[0]
            else:
                print("XXXX NOT FOUND", cc, adm1)
        except Exception as lookup_err:
            if str(lookup_err) not in ERROR_CACHE:
                print(lookup_err)
                ERROR_CACHE.add(str(lookup_err))

            # RESCUE oddities for unknown codings.
            adm1_iso = fix_geonames_admin1_iso(cc, adm1)
            if adm1_iso:
                geo["adm1"] = adm1_iso

    def process_source(self, sourcefile, limit=-1):
        """
        :param sourcefile: Geonames allCountries source file or any other file of the same schema.
        :param limit: limit of number of records to process
        :return: Yields geoname dict
        """
        header_names = parse_geonames_schema()

        # NOTE: Attempted pandas read_csv() operation. Failed.
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            df = get_csv_reader(fh, delim="\t", columns=header_names)
            self.purge()  # Remove all previous records.
            namecount = 0
            for gn in df:
                self.rowcount += 1
                geo = render_entry(gn, header_names)

                names = set([])
                variants = gn["alternatenames"]
                if is_value(variants):
                    names = render_distinct_names(get_list(variants, delim=","))
                nm = gn["asciiname"]
                if is_value(nm):
                    names.add(nm)

                # From the top: Country metadata
                # ================================
                add_country(geo)

                # ADM1 - tweak so code is ISO, if possible.
                # ================================
                self.populate_admin1(geo)

                # Detailed location and name data
                # ================================
                add_location(geo, geo["lat"], geo["lon"], add_geohash=False)
                geo["id_bias"] = self.estimator.location_bias(geo)
                for nm in names:
                    if not nm:
                        print("Encoding error with name", geo)
                        continue

                    namecount += 1
                    g = geo.copy()
                    g["name"] = nm
                    nt = g["name_type"]
                    if nt == "N":
                        if is_code(nm):
                            g["name_type"] = "C"
                        elif is_abbreviation(nm):
                            g["name_type"] = "A"
                    grp = ""
                    if has_cjk(nm):
                        grp = "cjk"
                    elif has_arabic(nm):
                        grp = "ar"

                    fc = g["feat_class"]
                    fcode = g["feat_code"]
                    if not fc:
                        fc, fcode = guess_feature(nm)
                        g["feat_class"] = fc
                        g["feat_code"] = fcode

                    # IGNORE: Odd name convention -- "i-Japan" -- etc.  Not clear of language or source. Geonames.org.
                    if fcode == "PCLI" and nm.lower().startswith("i-"):
                        print("Ignore rare name pattern i-CTRY")
                        continue

                    g["name_group"] = grp
                    g["name_bias"] = self.estimator.name_bias(nm, fc, fcode, name_group=grp)
                    g["id"] = GENERATED_BLOCK + namecount
                    yield g


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("geonames")
    ap.add_argument("--adm1", action="store_true", default=False)
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)

    args = ap.parse_args()

    if args.adm1:
        scanner = GeonamesOrgScanner()
        scanner.scan_adm1(args.geonames, limit=int(args.max))
    else:
        source = GeonamesOrgGazetteer(args.db, debug=args.debug)
        source.normalize(args.geonames, limit=int(args.max), optimize=args.optimize)
