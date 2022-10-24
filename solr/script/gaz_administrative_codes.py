#!/usr/bin/python

import os
from copy import copy

import shapefile
from opensextant import get_country, Country, parse_admin_code
from opensextant.gazetteer import DataSource, get_default_db, normalize_name, load_stopterms, PlaceHeuristics, add_location
from opensextant.utility import is_code, is_abbreviation, get_list

#
stopterms = load_stopterms()
NOT_LEXICAL_COMPARABLE_SCRIPT = {"ar", "cjk"}

# RESULT OF ADM1 discovery in pycountry and natural earth.
# Map these descriptive names to standard feature coding in Geonames
admin_features = dict()
with open('etc/gazetteer/feature_adhoc_descriptions.csv', 'r', encoding="UTF-8") as fh:
    for featrow in fh:
        feature = get_list(featrow, delim=",")
        desc = feature[0].lower()
        featcode = feature[1].upper()
        admin_features[desc] = featcode

GENERATED_BLOCK = 30000000
SUBDIV_GAZ_TEMPLATE = {
    "id": "",
    "place_id": None,
    "name": None,
    "feat_class": None,
    "feat_code": None,
    "FIPS_cc": None,
    "cc": None,
    "source": "NE",
    # Default bias tuning
    "name_bias": 0.10,
    "id_bias": 0.10,
    "name_type": "N",
    "name_group": ""
}


def parse_feature_type(r, alt_names, debug=False):
    """

    :param r: shapefile row
    :param alt_names: list of names.
    :param debug: debug
    :return: tuple Feature Class and Feature Code e.g., "A", "ADM2" for a county
    """
    adm_type = r["type_en"]
    if not adm_type:
        for nm in alt_names:
            tokens = nm.lower().split()
            for t in tokens:
                if t == "state":
                    return "A", "ADM1"
                if t in {"governorate", "territory", "territories"}:
                    return "P", "PPLA"
                if t in {"zone"}:
                    return "L", "ZONE"
        print("Unknown feature", r["name"], r["ne_id"])
        return "P", "PPLA"
    adm_type = adm_type.lower()
    feat_type = admin_features.get(adm_type)
    if not feat_type and "unitary district" in adm_type or "unitary authority" in adm_type:
        feat_type = "A/ADM2"

    if feat_type:
        if debug: print("\tFeature", feat_type, f"({adm_type})")
        fc, ft = feat_type.split("/", maxsplit=1)
        return fc, ft

    print("ADD FEATURE: ", adm_type)
    return "A", "UNK"


def derive_abbreviations(nameset):
    """

    :param nameset: a set
    :return:
    """
    N = copy(nameset)
    for name in N:
        if "." in name and len(name) < 10:
            toks = name.replace(" ", "").split(".")
            maybe_acronym = True
            for t in toks:
                if len(t) > 1:
                    maybe_acronym = False
                    break
            if maybe_acronym:
                acronym = "".join(toks).upper()
                nameset.add(acronym)


def _approximate_bias(b, name):
    """
    Find a reasonable match for the given name when we have existing biases in gazetteer entry.
    Otherwise if the name is just long enough it should be rather unique and return a high bias ~ 0.3-0.5

    If a name varies in length by a third, we'll approximate the name bias to be similar.

    :param b: dict of name:bias
    :param name: normalize name
    :return:
    """
    if name in b:
        return b.get(name)
    nmlen = len(name)
    diff = int(nmlen / 3)
    for n in b:
        nlen = len(n)
        if abs(nmlen - nlen) < diff:
            return b.get(n)
    if nmlen >= 20:
        return 0.40
    return 0.05


def dump_features():
    import csv
    with open("etc/gazetteer/feature_adhoc_descriptions.csv", "w", encoding="UTF-8") as featfile:
        writer = csv.writer(featfile)
        for k in sorted(admin_features.keys()):
            writer.writerow([k, admin_features[k]])


def _schema(shp):
    print("Schema")
    for f in shp.fields:
        print(f[0], f[1])


def assign_admin_levels(geo, country: Country, adm1: str, alt_adm1: str):
    """
    NaturalEarth has some odd codings.
    For UK/GB it has top level provinces WLS, ENG, SCT, NIR as other codes, but not as ADM1
    :param geo:
    :param country:
    :param adm1:
    :param alt_adm1:
    :return:
    """
    geo["adm1"] = adm1
    geo["adm2"] = ""
    geo["cc"] = country.cc_iso2
    if alt_adm1:
        if "GB" == country.cc_iso2:
            geo["adm1"] = alt_adm1
            geo["adm2"] = adm1


class NatEarthAdminGazetteer(DataSource):
    """
        Objective:  retrieve useful ALT names and abbreviations for province and administrative boundaries.
    """

    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = ["NE"]
        self.rate = 1000
        self.source_name = "NaturalEarth"
        self.estimator = PlaceHeuristics(self.db)

    def process_source(self, sourcefile, limit=-1):
        """
        :param sourcefile: Shapefile from Natural Earth
        :param limit:
        :return:
        """
        if not os.path.exists(sourcefile):
            print("Shapefile not found:", sourcefile)
            return

        with shapefile.Reader(sourcefile) as adm_gaz:
            print(adm_gaz)
            # Grab Shapefile DBF schema
            flds = adm_gaz.fields
            # _schema(adm_gaz)

            # Purge previous records for this source.
            self.purge()
            # Separate available name variants by - Anglo, Chinese/Japanese/Korean, Arabic or General (all else)
            anglo_script = set([])
            arabic_script = set([])
            cjk_script = set([])
            general_script = set([])
            all_script = set([])  # Every possible name.

            count = 0
            for row in adm_gaz.records():
                self.rowcount += 1
                anglo_script.clear()
                all_script.clear()
                general_script.clear()
                arabic_script.clear()
                cjk_script.clear()

                # Retrieve country object -- just easier to work with than to infer from schema.
                cc = row["iso_a2"]
                if cc == "-1":
                    print("Country or area is not clearly addressable")
                    continue

                C = get_country(cc)
                if not C:
                    print("What Country?", cc)
                    continue

                # For a row emit an array of Place objects for each name/code  + name type.
                #   Arizona   N (name)
                #   Ariz.     A (abbrev)
                #   AZ        A (abbrev/postal code)
                #   Metadata across each place is the same as derived from a single row here.
                #   name_ar, name_cjk are populated separately if source is from those scripts.
                #
                # Name arrays are used later.  Geodetic/geographic data is constant -- but the names vary.
                for field_tuple in flds:
                    f = field_tuple[0]
                    if f.startswith("name_") and f != "name_len":
                        nm = row[f]
                        if not nm:
                            continue
                        lang = f.split("_")[1]
                        for possible_nm in nm.split("|"):
                            nm2 = normalize_name(possible_nm)
                            all_script.add(nm2)
                            if lang == "ar":
                                arabic_script.add(nm2)
                            elif lang in {"zh", "ko", "ja"}:
                                cjk_script.add(nm2)
                            elif lang == "en":
                                anglo_script.add(nm2)
                            else:
                                general_script.add(nm2)

                # Primary names and alternates
                alt_names = [row["name"], row["name_alt"], row["gn_name"], row["abbrev"]]
                names = set([])
                for variant in alt_names:
                    if variant:
                        for alt_nm in variant.split("|"):
                            names.add(normalize_name(alt_nm))
                derive_abbreviations(names)
                anglo_script.update(names)

                # Postal code if given is a "name" but coded as (A)abbreviation
                postal = row["postal"]
                if postal:
                    if not postal.isdigit():
                        anglo_script.add(postal)

                all_script.update(anglo_script)

                # ADMIN or other code.
                gu_a3 = row["gu_a3"]
                adm1 = parse_admin_code(row["gn_a1_code"])
                if self.debug: print(names, "/", cc, "ADM1=", adm1)

                # Geographic codings:  Features, location, IDs
                labels = {row["woe_label"], row["woe_name"]}
                labels.update(all_script)
                fc, ft = parse_feature_type(row, labels, debug=self.debug)
                plid = row["gns_id"]
                if plid == "-1":
                    plid = None
                if plid:
                    plid = f"N{plid}"
                else:
                    plid = f"NE{row['ne_id']}"
                    if self.debug: print("Backfill missing GNS ID", names)

                # Create template for new entry from this row -- metadata here is constant
                geo = copy(SUBDIV_GAZ_TEMPLATE)
                add_location(geo, row["latitude"], row["longitude"])
                geo["place_id"] = plid
                assign_admin_levels(geo, C, adm1, alt_adm1=gu_a3)
                geo["feat_class"] = fc
                geo["feat_code"] = ft
                geo["FIPS_cc"] = C.cc_fips

                geo["id_bias"] = self.estimator.location_bias(geo)

                # Name data here is variable -- so create a new entry for each distinct name.
                distinct_names = set([])
                for lang, nameset in [("", anglo_script),
                                      ("xx", general_script),
                                      ("ar", arabic_script),
                                      ("cjk", cjk_script)]:
                    for nm in nameset:
                        if nm.lower() in distinct_names:
                            continue
                        distinct_names.add(nm.lower())
                        g = geo.copy()
                        count += 1

                        g["id"] = GENERATED_BLOCK + count
                        g["name"] = nm
                        name_grp = lang
                        if lang == "xx":
                            name_grp = ""
                        g["name_grp"] = name_grp
                        if is_code(nm):
                            g["name_type"] = "C"
                        elif is_abbreviation(nm):
                            g["name_type"] = "A"
                        g["name_bias"] = self.estimator.name_bias(nm, fc, ft,
                                                                  name_group=name_grp, name_type=g["name_type"])

                        yield g


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("shapefile")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()

    source = NatEarthAdminGazetteer(args.db, debug=args.debug)
    source.normalize(args.shapefile, limit=int(args.max))
