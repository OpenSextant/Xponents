import re
from copy import copy

from opensextant import is_administrative, is_populated, is_country
from opensextant.gazetteer import DataSource, get_default_db, load_stopterms, parse_admin_code, \
    GAZETTEER_TEMPLATE, SCRIPT_CODES, DEFAULT_COUNTRY_ID_BIAS
from opensextant.utility import get_csv_reader, get_list, is_ascii, squeeze_whitespace, parse_float

short_alphanum = re.compile(r"\w+.+\d+")
punct = re.compile("[-`'\"‘’]")
stopterms = load_stopterms()


def ignore_short_alphanum(nm, feat):
    """

    :param nm:
    :param feat:
    :return:
    """
    if is_administrative(feat) or is_populated(feat):
        return False
    return short_alphanum.match(nm) is not None


def scrub_country_code(loc, adm_field, iso_cc, fips_cc):
    """
    TODO: test idea of cleanup
    :param loc:
    :param adm_field:
    :param iso_cc:
    :param fips_cc:
    :return:
    """
    adm = loc.get(adm_field)
    if not adm:
        loc[adm_field] = ''
        return
    if iso_cc and adm.startswith(iso_cc):
        adm_ = adm[len(iso_cc):]
        loc[adm_field] = parse_admin_code(adm_)
        return
    if fips_cc and adm.startswith(fips_cc):
        # TODO: Not sure this works well.
        adm_ = adm[len(fips_cc):]
        loc[adm_field] = parse_admin_code(adm_)
    return


header_names = ['Record_ID', 'PLACE_ID', 'PLACE_NAME', 'PLACE_NAME_EXPANDED', 'LATITUDE', 'LONGITUDE', 'FEATURE_CLASS',
                'FEATURE_CODE', 'FIPS_COUNTRY_CODE', 'ISO2_COUNTRY_CODE', 'ISO3_COUNTRY_CODE', 'ADMIN_LEVEL_1',
                'ADMIN_LEVEL_2', 'ADMIN_LEVEL_3', 'ADMIN_LEVEL_4', 'ADMIN_LEVEL_5', 'SOURCE', 'SOURCE_FEATURE_ID',
                'SOURCE_NAME_ID', 'SCRIPT', 'PLACE_NAME_BIAS', 'PLACE_ID_BIAS', 'NAME_TYPE', 'NAME_TYPE_SYSTEM',
                'SplitCategory', 'SEARCH_ONLY']

GAZ_MAPPING = {
    'Record_ID': 'id',
    'PLACE_ID': 'place_id',
    'PLACE_NAME': 'name',
    'LATITUDE': 'lat',
    'LONGITUDE': 'lon',
    'FEATURE_CLASS': 'feat_class',
    'FEATURE_CODE': 'feat_code',
    'FIPS_COUNTRY_CODE': 'FIPS_cc',
    'ISO2_COUNTRY_CODE': 'cc',
    'ADMIN_LEVEL_1': 'adm1',
    'ADMIN_LEVEL_2': 'adm2',
    'SOURCE': 'source',
    'SCRIPT': 'script',
    'PLACE_NAME_BIAS': 'name_bias',
    'PLACE_ID_BIAS': 'id_bias',
    'NAME_TYPE': 'name_type',
    'SEARCH_ONLY': 'search_only'
}

CJK_SCRIPTS = {"CJK", "HANGUL", "KATAKANA", "HIRAGANA"}
NAME_TYPE = {
    None: "N",
    "name": "N",
    "abbrev": "A",
    "code": "C"
}

DEFAULT_ADM_NAME_BIAS = 0.10


def correct_bias(pl):
    """
    Adjust the name_bias of administrative names if they have been previoiusly marked as negative
    :param pl:
    :return:
    """
    if is_administrative(pl.get("feat_class")) and len(pl["name"]) >= 5:
        nm_bias = pl.get("name_bias")
        if nm_bias and nm_bias < 0:
            if is_country(pl.get("feat_code")):
                pl["name_bias"] = DEFAULT_COUNTRY_ID_BIAS
            else:
                pl["name_bias"] = DEFAULT_ADM_NAME_BIAS
            print("Corrected bias: ", pl["name"], nm_bias)


class OpenSextantGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_name = "OpenSextant Gazetteer"
        self.source_keys = [
            "U", "UF",  # USGS items
            "N", "NF",  # NGA items
            "OG", "OA"  # Geonames and Adhoc items derived by OpenSextant Gazetteer
        ]

    def process_source(self, sourcefile, limit=-1):
        """
        ingest the standard merged file from the Gazetteer project
        :param sourcefile: the Merged gazetteer file
        :return:
        """
        with open(sourcefile, "r", encoding="UTF-8") as fh:
            df = get_csv_reader(fh, delim="\t", columns=header_names)
            self.purge()  # Remove all previous records.
            for row in df:
                self.rowcount += 1
                if self.rowcount == 1:
                    continue
                geo = copy(GAZETTEER_TEMPLATE)

                for k in GAZ_MAPPING:
                    v = row.get(k)
                    field = GAZ_MAPPING.get(k)
                    if v:
                        geo[field] = v

                geo_id = int(geo["id"])
                geo["id"] = geo_id
                nm = geo["name"]

                # Essential metadata - Script and type of geo name.
                nt = NAME_TYPE.get(geo.get("name_type"))
                geo["name_type"] = nt
                is_name = nt == "N"

                plid = geo["place_id"]
                geo["place_id"] = plid.replace("NGA", "N").replace("USGS", "U")
                script = geo["script"]
                name_grp = ""
                if script:
                    scripts = get_list(script.strip("[").strip("]"), delim=",")
                    scripts_code = set([])
                    for scr in scripts:
                        scripts_code.add(SCRIPT_CODES.get(scr, scr))
                        if scr == "ARABIC":
                            geo["name_ar"] = nm
                            name_grp = "ar"
                        elif scr in CJK_SCRIPTS:
                            geo["name_cjk"] = nm
                            name_grp = "cjk"
                    geo['script'] = ','.join(scripts_code)

                norm = nm.replace(".", "").strip().lower()
                normlen = len(norm)
                if self.debug:
                    print(nm, norm, script)

                search_only = False
                if norm in stopterms:
                    search_only = True
                    if self.debug: print("Stop: ", nm)

                if not search_only and is_name:
                    if len(norm.split()) > 6:
                        # Ignore long multi-word names -- unlikely to be tagged, but still useful to search
                        # Allow names from Latin-languages that may have multiple articles "Amal de la Ave al Sobre" ...
                        # But anything longer  is not likely to be tagged.
                        search_only = True
                    elif len(norm) <= len(nm) <= 2 and is_ascii(nm):
                        # "Stop trivial short phrases"
                        search_only = True

                # Ignore short words followed by digit  "Bld #19"
                if not search_only and normlen < 10:
                    search_only = ignore_short_alphanum(norm, geo["feat_class"])

                # Mark short names that resolve as stop terms as search_only
                if not search_only and normlen < 15:
                    non_diacritic = punct.sub(" ", norm).strip()
                    non_diacritic = squeeze_whitespace(non_diacritic).lower()
                    if non_diacritic in stopterms:
                        if self.debug: print("Stop: ", nm, non_diacritic)
                        search_only = True

                geo["search_only"] = search_only
                if search_only:
                    self.excluded_terms.add(nm)

                scrub_country_code(geo, "adm1", geo["cc"], geo["FIPS_cc"])
                scrub_country_code(geo, "adm2", geo["cc"], geo["FIPS_cc"])

                geo['name_group'] = name_grp
                geo['lat'] = parse_float(geo['lat'])
                geo['lon'] = parse_float(geo['lon'])
                geo['id_bias'] = parse_float(geo['id_bias'])
                geo['name_bias'] = parse_float(geo['name_bias'])
                # TODO: this bias and universal gazetteer model needs to be fixed.
                correct_bias(geo)

                yield geo


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("mergedfile")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()

    source = OpenSextantGazetteer(args.db, debug=args.debug)
    source.normalize(args.mergedfile, limit=int(args.max), optimize=args.optimize)
