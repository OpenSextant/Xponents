import os

from opensextant.gazetteer import get_default_db, DataSource, PlaceHeuristics, US_TERRITORY_MAP, add_location
from opensextant.utility import get_csv_reader, get_list

HEADER = get_list(
    "FEATURE_ID|FEATURE_NAME|FEATURE_CLASS|STATE_ALPHA|STATE_NUMERIC|COUNTY_NAME|COUNTY_NUMERIC|PRIMARY_LAT_DMS|PRIM_LONG_DMS|PRIM_LAT_DEC|PRIM_LONG_DEC|SOURCE_LAT_DMS|SOURCE_LONG_DMS|SOURCE_LAT_DEC|SOURCE_LONG_DEC|ELEV_IN_M|ELEV_IN_FT|MAP_NAME|DATE_CREATED|DATE_EDITED",
    delim="|")

# id = 'U' + FEATURE_ID
# name = FEATURE_NAME
# feat_class, feat_code  = lookup(FEATURE_CLASS)
# adm1 = STATE_ALPHA
# lat, lon = PRIM_LAT_DEC, PRIM_LONG_DEC
# cc = "US" .... infer other territory country codes, e.g., PR, RQ, UM, etc. from STATE_ALPHA
#

USGS_SOURCE_ID = "U"
GENERATED_BLOCK = 20000000

USGS_GAZ_TEMPLATE = {
    "id": -1,
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
    "source": USGS_SOURCE_ID,
    # Script -- not used beyond opensextant gazetteer; Unused in tagger.
    # "script": None,
    "name_group": "",
    # Default bias tuning
    "name_bias": 0,
    # ID Bias is not used much
    "id_bias": 0,
    "name_type": "N"
}

#
# UM (US.74) territory Islands  are these FIPS codes:, which in turn ADM1 codes should be converted to postal ID
UMI_GEOHASH_MAP = {
    "FQ": "8049",
    "HQ": "804k",
    "DQ": "2ryj",
    "JQ": "84zr",
    "KQ": "83hs",
    "MQ": "8j1b",
    "BQ": "d768",
    "LQ": "83h9",
    "WQ": "xeqt"
}

# Bidirection FIPS => geohash
for k in UMI_GEOHASH_MAP.copy().keys():
    v = UMI_GEOHASH_MAP[k]
    UMI_GEOHASH_MAP[v] = k

FEAT_MAP = {}


def load_feature_map():
    with open(os.path.join("etc", "gazetteer", "usgs2gnis-feature-map.csv"), "r", encoding="UTF-8") as ftmap:
        ftreader = get_csv_reader(ftmap, ["USGS_FEATURE_CLASS", "FEATURE_CLASS", "FEATURE_CODE"])
        for feat in ftreader:
            fin = feat["USGS_FEATURE_CLASS"]
            fc = feat["FEATURE_CLASS"]
            dsg = feat["FEATURE_CODE"]
            FEAT_MAP[fin.lower()] = (fc.upper(), dsg.upper())
    if not FEAT_MAP:
        raise Exception("Failed to load Feature Mapping.")


def lookup_feature(fc: str):
    lookup = fc.lower()
    if lookup in FEAT_MAP:
        return FEAT_MAP.get(lookup)
    return None, None


def adjust_country_territory(entry, debug=False):
    # NationalFile uses ISO territory codes.
    adm1 = entry["adm1"]

    if adm1 == "UM":
        geolookup = entry["geohash"][0:4]
        fips_cc = UMI_GEOHASH_MAP.get(geolookup)
        entry["cc"] = adm1
        entry["FIPS_cc"] = "*"
        entry["adm1"] = entry["adm2"]
        entry["adm2"] = ""
        if fips_cc:
            entry["FIPS_cc"] = fips_cc

    elif adm1 in US_TERRITORY_MAP["ISO"]:
        if debug: print("Correct Country/Territory", adm1)
        entry["cc"] = adm1
        entry["FIPS_cc"] = US_TERRITORY_MAP["ISO"][adm1]


def adjust_feature(entry):
    n = entry["name"]
    if "(historical)" in n:
        entry["name"] = n.replace("(historical)", "").strip()
        fc = entry["feat_code"]
        entry["feat_code"] = f"{fc}H"
    elif "(" in n:
        x = n.index("(")
        entry["name"] = n[0:x].strip()


class USGSGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_name = "USGS Gazetteer"
        self.source_keys = [USGS_SOURCE_ID]
        self.estimator = PlaceHeuristics(self.db)
        self.rate = 1000000
        load_feature_map()

    def process_source(self, sourcefile, limit=-1):
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            reader = get_csv_reader(fh, columns=HEADER, delim="|")
            self.purge()
            name_count = GENERATED_BLOCK
            for row in reader:
                self.rowcount += 1
                if self.rowcount == 1:
                    continue

                name_count += 1

                geo = USGS_GAZ_TEMPLATE.copy()
                geo["id"] = name_count
                geo["name"] = row["FEATURE_NAME"]
                geo["place_id"] = f"U{row['FEATURE_ID']}"
                fc, designation = lookup_feature(row["FEATURE_CLASS"])
                geo["feat_class"] = fc
                geo["feat_code"] = designation
                geo["cc"] = "US"
                geo["FIPS_cc"] = "US"
                geo["adm1"] = row["STATE_ALPHA"]
                geo["adm2"] = row["COUNTY_NUMERIC"]
                add_location(geo, row["PRIM_LAT_DEC"], row["PRIM_LONG_DEC"])
                adjust_country_territory(geo)
                adjust_feature(geo)
                self.estimator.estimate_bias(geo)

                yield geo


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("nationalfile")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()

    source = USGSGazetteer(args.db, debug=args.debug)
    source.normalize(args.nationalfile, limit=int(args.max), optimize=args.optimize)
