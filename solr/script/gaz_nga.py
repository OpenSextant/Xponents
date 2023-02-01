import os
from opensextant import load_countries, get_country, parse_admin_code
from opensextant.gazetteer import get_default_db, DataSource, PlaceHeuristics, \
    name_group_for, normalize_name, add_location, export_admin_mapping, gaz_resource

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


def lang_script(lc):
    # TODO: cleanup language mappings.
    return LANGCODE_SCRIPTS.get(lc, "")


def render_distinct_names(entry, ver=2022):
    """

    :param entry: geoname record
    :param ver: year version number
    :return: list
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
    return sorted(names.values())


def render_entry(entry, ver=None, location=True):
    """

    :param entry:  raw dict from source
    :param ver:  version number (year) of file. 2022 is a new formt
    :param location:  True if you wish to parse location
    :return:
    """
    geo = GEONAMES_GAZ_TEMPLATE.copy()

    # SCHEMA variances
    # -----------------
    adm1, lat, lon = None, None, None
    if ver == 2022:
        geo["place_id"] = f"N{entry['ufi']}"
        adm1_codes = entry["adm1"]
        primary_adm1 = adm1_codes
        secondary_adm1 = None
        if "," in adm1_codes:
            primary_adm1, secondary_adm1 = adm1_codes.split(",", 1)
        adm1 = parse_admin_code(primary_adm1, delim="-")
        # Unscrubbed ADM1 additional
        geo["adm1_alt"] = secondary_adm1
        geo["feat_class"] = entry["fc"]
        geo["feat_code"] = entry["desig_cd"]
        lat, lon = entry["lat_dd"], entry["long_dd"]
        # NOTE this has to be replaced with valid value before saving to DB
        geo["cc"] = entry["cc_ft"]
    else:
        geo["place_id"] = f"N{entry['UFI']}"
        adm1 = parse_admin_code(entry["ADM1"])
        geo["feat_class"] = entry["FC"]
        geo["feat_code"] = entry["DSG"]
        lat, lon = entry["LAT"], entry["LONG"]

    if location:
        add_location(geo, lat, lon, add_geohash=False)
    if adm1 == 'NULL':
        adm1 = None
    geo["adm1"] = adm1

    # This lang code and name group end up not being useful, as they are not consistent.
    # lang_code = row["LC"]
    # name_grp = lang_script(lang_code)
    # geo["name_group"] = name_grp
    # if lang_code and not geo["name_group"] and self.debug:
    #    print("Language", row["LC"])
    return geo


def is_multi_country_feature(ft, dsg, given_cc):
    """
    :param ft: feature class
    :param dsg: feature code
    :param given_cc:  raw string for country codes
    :return:
    """
    if ft == "H" and dsg.startswith("STM") and "," in given_cc:
        return True
    # Other situations?
    return False


def get_raw_country(entry, ver):
    if ver == 2022:
        return entry["cc_ft"]
    else:
        return entry["CC1"]


UNRESOLVED_COUNTRY = set([])


def parse_country(val, ver):
    if not val:
        return []

    # SCHEMA variances
    # -----------------
    std = "FIPS"
    if ver == 2022:
        std = "ISO"
    # -----------------

    arr = []
    for cc in val.split(","):
        C = get_country(cc, standard=std)
        if C:
            arr.append(C)
        else:
            if cc not in UNRESOLVED_COUNTRY:
                print("Missing ISO country code for ", cc)
                UNRESOLVED_COUNTRY.add(cc)
    if not arr:
        # International.
        # print("Using ZZ for", val)
        arr.append(get_country("ZZ"))
    return arr


class NGAGeonamesScanner:
    def __init__(self, ver):
        self.ver = ver
        self.HEADER = HEADER
        self.rowcount = 0
        self.rate = 1000000
        if self.ver == 2022:
            self.HEADER = HEADER_2022

    def scan_adm1(self, sourcefile, limit=-1):
        """
        RUN as:
            cd ./solr
            # v2021
            python3 gaz_nga.py ./tmp/Countries.txt --adm1
            # v2022 and forward.
            python3 gaz_nga.py ./tmp/Whole_World.txt --adm1

        Exports nga ADM1 mapping with primarily FIPS numerics.

        :param sourcefile:
        :param limit: number of found ADM1 entries
        :return:
        """

        adm1_ids = []
        delim = "\t"
        found = 0
        adm1_id_missing = 0
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            for line in fh:
                data = line.strip().split(delim)
                row = dict(zip(self.HEADER, data))

                self.rowcount += 1
                if self.rowcount == 1:
                    continue

                if self.rowcount % self.rate == 0:
                    print(f"Row# {self.rowcount}")

                geo = render_entry(row, ver=self.ver, location=True)
                adm1 = geo.get("adm1")
                ft_class = geo.get("feat_class")
                ft_code = geo.get("feat_code")
                if not (ft_class == "A" and ft_code == "ADM1"):
                    continue

                found += 1
                if 0 < limit < found:
                    print("Reached Limit", limit)
                    break

                if not adm1:
                    print("Mapping out CC.0 for missing ADM1 code?", data[1:14], "...")
                    adm1_id_missing += 1
                    continue
                cc_list = get_raw_country(row, self.ver)
                countries = parse_country(cc_list, self.ver)
                if countries and len(countries) > 1:
                    # Sorry -- not mapping out administrative codes
                    print("Places associated with multiple countries are not included", geo)
                    continue
                C = countries[0]
                geo["cc"] = C.cc_iso2
                names = render_distinct_names(row, self.ver)
                geo["name"] = names[0]

                adm1_ids.append(geo)

            print("General counts", "Place Variants", found, "Distinct ADM1", len(adm1_ids), "No Admin1 code on ADM1",
                  adm1_id_missing)
            export_admin_mapping(adm1_ids, gaz_resource(f"nga_{self.ver}_admin1_mapping.csv"))


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

        #     NEW NGA data file is unreadable fully by Python 3.x CSV DICT reader.
        #     NoTE: very problematic managing generator here if caller has other generators involved.
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            for line in fh:
                data = line.rstrip('\n').split(delim)
                row = dict(zip(self.HEADER, data))

                self.rowcount += 1
                if self.rowcount == 1:
                    continue

                distinct_names = render_distinct_names(row, ver=self.ver)
                geo = render_entry(row, ver=self.ver, location=True)
                adm1 = geo.get("adm1")
                # About 50,000 entries in NGA GNIS are dual-country.
                cc_list = get_raw_country(row, self.ver)
                countries = parse_country(cc_list, self.ver)

                # Annoyingly, certain boundaries or line features appear in multiple countries, but
                # There's no easy way to represent them here -- TODO: determine the nature and volume of these features
                if is_multi_country_feature(geo["feat_class"], geo["feat_code"], cc_list) and len(countries) > 1:
                    countries = countries[0:1]

                for nm in distinct_names:
                    for ctry in countries:
                        name_count += 1
                        if ctry.cc_iso2 != 'ZZ' and not adm1:
                            geo["adm1"] = '0'

                        g = geo.copy()
                        g["name"] = nm
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
    ap.add_argument("--adm1", action="store_true", default=False)
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()
    schema_ver = 2021

    # In 2022 NGA revamps geonames dissemination and schema thoroughly.
    if "whole_world" in args.countriesfile.lower():
        schema_ver = 2022

    if args.adm1:
        # Step 1.  Run this at least once, to further pre-populate adm1_codes mapping, empirically.
        # source.collect_iso_adm1(args.countriesfile, limit=int(args.max))
        source = NGAGeonamesScanner(schema_ver)
        source.scan_adm1(args.countriesfile, limit=int(args.max))
    else:
        source = NGAGeonames(args.db, debug=args.debug, ver=schema_ver)
        source.normalize(args.countriesfile, limit=int(args.max), optimize=args.optimize)
        print("=====================")
        print("Unresolved Countries:", len(UNRESOLVED_COUNTRY), "\n", UNRESOLVED_COUNTRY)
