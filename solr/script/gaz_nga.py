from opensextant import load_countries, get_country, parse_admin_code
from opensextant.gazetteer import get_default_db, DataSource, PlaceHeuristics, name_group_for, normalize_name, \
    add_location

# from opensextant.utility import get_csv_reader

load_countries()

NGA_SOURCE_ID = "N"

# COPY ROW from Countries.txt:
HEADER = "RC	UFI	UNI	LAT	LONG	DMS_LAT	DMS_LONG	MGRS	JOG	FC	DSG	PC	CC1	ADM1	POP	ELEV	CC2	NT	LC	SHORT_FORM	GENERIC	SORT_NAME_RO	FULL_NAME_RO	FULL_NAME_ND_RO	SORT_NAME_RG	FULL_NAME_RG	FULL_NAME_ND_RG	NOTE	MODIFY_DATE	DISPLAY	NAME_RANK	NAME_LINK	TRANSL_CD	NM_MODIFY_DATE	F_EFCTV_DT	F_TERM_DT".split(
    "\t")
HEADER_2022 = "rk	ufi	uni	full_name	nt	lat_dd	long_dd	efctv_dt	term_dt_f	term_dt_n	desig_cd	fc	cc_ft	adm1	ft_link	name_rank	lang_cd	transl_cd	script_cd	name_link	cc_nm	generic	full_nm_nd	sort_gen	sort_name	lat_dms	long_dms	mgrs	mod_dt_ft	mod_dt_nm	dialect_cd	display	gis_notes".split(
    "\t")

NAME_FLDS_2022 = ["full_nm_nd", "full_name"]
NAME_FLDS = ["SHORT_FORM", "FULL_NAME_ND_RO", "FULL_NAME_RO"]

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


def csv_alternative(fpath, header, delim="\t"):
    """
    NEW NGA data file is unreadable fully by Python 3.x CSV DICT reader.
    NoTE: very problematic managing generator here if caller has other generators involved.

    :param fpath:
    :return:
    """
    with open(fpath, "r", encoding="UTF-8") as fh:
        for line in fh:
            data = line.rstrip('\n').split(delim)
            yield dict(zip(header, data))


def lang_script(lc):
    # TODO: cleanup language mappings.
    return LANGCODE_SCRIPTS.get(lc, "")


def render_distinct_names(entry, ver=2022):
    """

    :param entry: geoname record
    :param ver: year version number
    :return:
    """
    # SCHEMA variances
    # -----------------
    # Trivial remapping - replace unicode diacritics with normal ASCII
    # These conventions can be source specific....
    fl = NAME_FLDS
    if ver == 2022:
        fl = NAME_FLDS_2022
    # -----------------

    names = {}
    for fld in fl:
        nm = entry.get(fld)
        if nm:
            nm = normalize_name(nm)
            names[nm.lower()] = nm
    return names


def render_entry(entry, ver=None):
    geo = GEONAMES_GAZ_TEMPLATE.copy()

    # SCHEMA variances
    # -----------------
    adm1, lat, lon = None, None, None
    if ver == 2022:
        geo["place_id"] = f"N{entry['ufi']}"
        adm1 = parse_admin_code(entry["adm1"])
        geo["feat_class"] = entry["fc"]
        geo["feat_code"] = entry["desig_cd"]
        lat, lon = entry["lat_dd"], entry["long_dd"]
    else:
        geo["place_id"] = f"N{entry['UFI']}"
        adm1 = parse_admin_code(entry["ADM1"])
        geo["feat_class"] = entry["FC"]
        geo["feat_code"] = entry["DSG"]
        lat, lon = entry["LAT"], entry["LONG"]

    add_location(geo, lat, lon)
    if adm1 == 'NULL': adm1 = None
    geo["adm1"] = adm1

    # This lang code and name group end up not being useful, as they are not consistent.
    # lang_code = row["LC"]
    # name_grp = lang_script(lang_code)
    # geo["name_group"] = name_grp
    # if lang_code and not geo["name_group"] and self.debug:
    #    print("Language", row["LC"])
    return geo


def parse_country(entry, ver=None):
    val = None
    std = "FIPS"

    # SCHEMA variances
    # -----------------
    if ver == 2022:
        val = entry["cc_ft"]
        std = "ISO"
    else:
        val = entry["CC1"]
    if not val:
        return []
    # -----------------

    arr = []
    for cc in val.split(","):
        C = get_country(cc, standard=std)
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
        self.HEADER = HEADER
        if self.ver == 2022:
            self.HEADER = HEADER_2022

    def process_source(self, sourcefile, limit=-1):
        self.purge()
        name_count = GENERATED_BLOCK
        delim = "\t"
        # for row in csv_alternative(sourcefile, self.HEADER, delim="\t"):
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            for line in fh:
                data = line.rstrip('\n').split(delim)
                row = dict(zip(self.HEADER, data))

                self.rowcount += 1
                if self.rowcount == 1:
                    continue

                distinct_names = render_distinct_names(row, ver=self.ver)
                geo = render_entry(row, ver=self.ver)
                adm1 = geo.get("adm1")
                # About 50,000 entries in NGA GNIS are dual-country.
                countries = parse_country(row, ver=self.ver)

                # Notable lack of synchrony with for loop and yield:  recipient of dict here sees stale values.
                # Solution -- create a new copy and update it.
                for nm in distinct_names:
                    for ctry in countries:
                        name_count += 1
                        if ctry.cc_iso2 != 'ZZ' and not adm1:
                            geo["adm1"] = '0'

                        g = geo.copy()
                        g["name"] = distinct_names[nm]
                        g["id"] = name_count
                        g["cc"] = ctry.cc_iso2
                        g["FIPS_cc"] = ctry.cc_fips
                        verify_name_grp = name_group_for(nm)
                        g["name_group"] = verify_name_grp
                        g["id_bias"] = self.estimator.location_bias(geo)
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
    ver = 2021
    # In 2022 NGA revamp geonames dissemination thoroughly.
    if "whole_world" in args.countriesfile.lower():
        ver = 2022

    source = NGAGeonames(args.db, debug=args.debug, ver=ver)
    source.normalize(args.countriesfile, limit=int(args.max), optimize=args.optimize)
