from opensextant import load_countries, get_country
from opensextant.gazetteer import get_default_db, parse_admin_code, DataSource, PlaceHeuristics, name_group_for
from opensextant.utility import get_csv_reader

load_countries()

NGA_SOURCE_ID = "N"

# COPY ROW from Countries.txt:
HEADER = "RC	UFI	UNI	LAT	LONG	DMS_LAT	DMS_LONG	MGRS	JOG	FC	DSG	PC	CC1	ADM1	POP	ELEV	CC2	NT	LC	SHORT_FORM	GENERIC	SORT_NAME_RO	FULL_NAME_RO	FULL_NAME_ND_RO	SORT_NAME_RG	FULL_NAME_RG	FULL_NAME_ND_RG	NOTE	MODIFY_DATE	DISPLAY	NAME_RANK	NAME_LINK	TRANSL_CD	NM_MODIFY_DATE	F_EFCTV_DT	F_TERM_DT".split(
    "\t")

# Mapping:
#  UFI => 'N'+value   // source feature ID
#  LAT, LONG => lat, lon
#  CC1 => cc // FIPS => ISO2
#  CC2 ?     // evaluate
#  LC => script
#  NT => name_type = "N"   // Assumption is that we will not see codes or unofficial abbreviations.
#  SHORT_FORM, FULL_NAME_ND_RO, FULL_NAME_RO  => emit unique name
#  POP ? // assess how often population is filled in
#  FC => feat_class
#  DSG => feat_code
#  ADM1 => adm1

GEONAMES_GAZ_TEMPLATE = {
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
    "source": NGA_SOURCE_ID,
    # Script -- not used beyond opensextant gazetteer; Unused in tagger.
    # "script": None,
    "name_group": "",
    # Default bias tuning
    "name_bias": 0,
    # ID Bias is not used much
    "id_bias": 0,
    "name_type": "N"
}

GENERATED_BLOCK = 0
# Name groups that map name entries to Lucene fields name_cjk and name_ar; default is name
LANGCODE_SCRIPTS = {
    # Asian Han-scripts are mapped to CJK tokenizer
    "zho": "cjk",
    "kor": "cjk",
    "jpn": "cjk",
    # Middle-eastern scripts are mapped to Arabic tokenizer
    "ara": "ar",
    "per": "ar",
    "kur": "ar",
    "urd": "ar"
}


def lang_script(lc):
    # TODO: cleanup language mappings.
    return LANGCODE_SCRIPTS.get(lc, "")


def render_distinct_names(entry):
    # Trivial remapping - replace unicode diacritics with normal ASCII
    # These conventions can be source specific....
    names = {}
    for fld in ["SHORT_FORM", "FULL_NAME_ND_RO", "FULL_NAME_RO"]:
        nm = entry.get(fld)
        if nm:
            nm = nm.replace("\u2019", "'").strip()
            names[nm.lower()] = nm
    return names


def parse_country(val):
    if not val:
        return []
    arr = []
    for fips_cc in val.split(","):
        C = get_country(fips_cc, standard="FIPS")
        if C:
            arr.append(C)
        else:
            print("Missing ISO country code for ", val)
    if not arr:
        # International.
        print("Using ZZ for", val)
        arr.append(get_country("ZZ"))
    return arr


class NGAGeonames(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = [NGA_SOURCE_ID]
        self.source_name = "NGA GNIS"
        self.estimator = PlaceHeuristics(self.db)

    def process_source(self, sourcefile, limit=-1):
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            reader = get_csv_reader(fh, columns=HEADER, delim="\t")
            self.purge()
            name_count = GENERATED_BLOCK
            for row in reader:
                self.rowcount += 1
                if self.rowcount == 1:
                    continue
                distinct_names = render_distinct_names(row)
                geo = GEONAMES_GAZ_TEMPLATE.copy()

                geo["place_id"] = f"N{row['UFI']}"
                adm1 = parse_admin_code(row["ADM1"])
                geo["feat_class"] = row["FC"]
                geo["feat_code"] = row["DSG"]
                self.add_location(geo, row["LAT"], row["LONG"])
                # This lang code and name group end up not being useful, as they are not consistent.
                # lang_code = row["LC"]
                # name_grp = lang_script(lang_code)
                # geo["name_group"] = name_grp
                # if lang_code and not geo["name_group"] and self.debug:
                #    print("Language", row["LC"])

                # About 50,000 entries in NGA GNIS are dual-country.
                countries = parse_country(row["CC1"])

                geo["id_bias"] = self.estimator.location_bias(geo["geohash"], geo["feat_class"], geo["feat_code"])

                # Notable lack of synchrony with for loop and yield:  recipient of dict here sees stale values.
                # Solution -- create a new copy and update it.
                for nm in distinct_names:
                    for ctry in countries:
                        name_count += 1
                        if ctry.cc_iso2 != 'ZZ' and not adm1:
                            geo["adm1"] = '0'
                        else:
                            geo["adm1"] = adm1

                        g = geo.copy()
                        g["name"] = distinct_names[nm]
                        g["id"] = name_count
                        g["cc"] = ctry.cc_iso2
                        g["FIPS_cc"] = ctry.cc_fips
                        verify_name_grp = name_group_for(nm)
                        g["name_group"] = verify_name_grp
                        g["name_bias"] = self.estimator.name_bias(g["name"],
                                                                  g["feat_class"], g["feat_code"],
                                                                  name_group=verify_name_grp)

                        yield g


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("countriesfile")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()

    source = NGAGeonames(args.db, debug=args.debug)
    source.normalize(args.countriesfile, limit=int(args.max), optimize=args.optimize)
