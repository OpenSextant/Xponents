import os
import shapefile
from opensextant.gazetteer import DataSource, PlaceHeuristics, get_default_db
from opensextant import Place, get_country, centroid
from copy import copy

SOURCE_ID = "WFP"
GENERATED_BLOCK = 26000000
SUBDIV_GAZ_TEMPLATE = {
    "id": "",
    "place_id": None,
    "name": None,
    "feat_class": None,
    "feat_code": None,
    "FIPS_cc": None,
    "cc": None,
    "source": SOURCE_ID,
    # Default bias tuning
    "name_bias": 0.10,
    "id_bias": 0.10,
    "name_type": "N",
    "name_group": ""
}


def variant_name(nm):
    if nm:
        return nm.split("(")[0].strip()
    return nm


def _parse_admin_code(a, cc):
    """
    ADMIN codes are typically only XXnnnn..  and do not use 3-char country prefix
    :param a:
    :param cc:
    :return:
    """
    if a and cc and len(cc) == 2:
        if a.startswith(cc):
            return a[2:]
    return a


# [... ['ADM3_EN', 'C', 50, 0], ['ADM3_PCODE', 'C', 50, 0], ['ADM3_REF', 'C', 50, 0],
# ['ADM3ALT1EN', 'C', 50, 0], ['ADM3ALT2EN', 'C', 50, 0], ['ADM2_EN', 'C', 50, 0], ['ADM2_PCODE', 'C', 50, 0],
# ['ADM1_EN', 'C', 50, 0], ['ADM1_PCODE', 'C', 50, 0], ['POINT_X', 'F', 19, 11], ['POINT_Y', 'F', 19, 11]]
#

def render_place(rec) -> Place:
    """

    :param rec: shapefile record.
    :return: Place obj
    """
    pl = Place(None, rec["ADM3_EN"], lat=rec["POINT_Y"], lon=rec["POINT_X"])

    pl.country_code = rec["ADM0_PCODE"]  # TODO: watch this field -- make sure its not 3-char or other countries.
    pl.adm1 = _parse_admin_code(rec["ADM1_PCODE"], pl.country_code)
    pl.adm2 = _parse_admin_code(rec["ADM2_PCODE"], pl.country_code)
    pl.adm2_name = rec["ADM2_EN"]

    # Unofficial attribute: ADM3 is not used in Xponents, but save it for place ID
    pl.adm3 = _parse_admin_code(rec["ADM3_PCODE"], pl.country_code)

    pl.place_id = f"WFP_{pl.country_code}{pl.adm3}"
    pl.feature_class = "A"
    pl.feature_code = "ADM3"

    C = get_country(pl.country_code)
    if C:
        pl.country_code_fips = C.cc_fips

    # Alternate names -- this schema has ALT names, but no ADM3 ALT names appear.
    alt = (rec["ADM3ALT1EN"], rec["ADM3ALT2EN"])
    for alt_name in alt:
        if alt_name:
            print("Variant? ", alt_name)

    return pl


class WFPPakistanAdminGazetteer(DataSource):
    """
    Objective: Augment Pakistan administrative regions
    Source: World Food Program (WFP) shapefile posted on HumData Exchange (HDX)
    """

    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = [SOURCE_ID]
        self.rate = 1000
        self.source_name = "WFP_PAK"
        self.estimator = PlaceHeuristics(self.db)

        # Distill this source's ADM2 boundaries
        self.admin2 = dict()  # ADM2 = point array of ADM3 records
        self.count = 0

    def capture_admin_places(self, pl: Place):
        a2 = None
        if pl.adm2 not in self.admin2:
            adm_place = Place(f"WFP_{pl.country_code}{pl.adm2}", pl.adm2_name)
            adm_place.feature_class = 'A'
            adm_place.feature_code = 'ADM2'
            adm_place.country_code = pl.country_code
            adm_place.country_code_fips = pl.country_code_fips
            adm_place.adm1 = pl.adm1
            adm_place.adm2 = pl.adm2
            a2 = self.admin2.get(pl.adm2, {"place": adm_place, "points": []})
            self.admin2[pl.adm2] = a2
        else:
            a2 = self.admin2[pl.adm2]

        a2["points"].append((pl.lat, pl.lon))

    def normalize_inferred_adm2(self):
        self.db.reopen()

        inferred = []
        for adm in self.admin2:
            self.count += 1
            place = self.admin2[adm]
            pt_arr = place["points"]
            center = centroid(pt_arr)
            # print(pt_arr)
            # print("\tCENTROID", center)

            adm_place = place["place"]
            adm_place.set_location(center.lat, center.lon)
            rid = GENERATED_BLOCK + self.count
            inferred.append(self._as_dict(adm_place, adm_place.name, rid))

        print("INFERRED MORE", len(inferred))
        self.db.add_places(inferred)
        self.db.close()

    def _as_dict(self, pl, name, row_id):
        entry = copy(SUBDIV_GAZ_TEMPLATE)
        entry["name"] = name
        entry["id"] = row_id

        entry["cc"] = pl.country_code
        entry["FIPS_cc"] = pl.country_code_fips
        entry["feat_class"] = pl.feature_class
        entry["feat_code"] = pl.feature_code
        entry["lat"] = pl.lat
        entry["lon"] = pl.lon
        entry["adm1"] = pl.adm1
        entry["adm2"] = pl.adm2

        entry["place_id"] = pl.place_id
        entry["name_bias"] = self.estimator.name_bias(name, "A", "ADM3")
        entry["id_bias"] = self.estimator.location_bias(entry)
        return entry

    def process_source(self, sourcefile, limit=-1):
        """
        :param sourcefile: Shapefile from WFP via HDX
        :param limit:
        :return:
        """
        if not os.path.exists(sourcefile):
            print("Shapefile not found:", sourcefile)
            return

        with shapefile.Reader(sourcefile) as reader:
            self.count = 0
            flds = reader.fields
            print("Shapefile has ", reader.numRecords, " records")
            print("Shapefile has fields:")
            print(flds)

            self.purge()
            for row in reader.records():
                self.rowcount += 1

                pl = render_place(row)
                names = [pl.name]
                self.capture_admin_places(pl)
                if "(" in pl.name:
                    names.append(variant_name(pl.name))

                for nm in names:
                    self.count += 1
                    entry = self._as_dict(pl, nm, GENERATED_BLOCK + self.count)

                    yield entry


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("shapefile", help="WFP Pakistan gazetteer from HDX")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)

    args = ap.parse_args()

    source = WFPPakistanAdminGazetteer(args.db, debug=args.debug)
    source.normalize(args.shapefile, limit=int(args.max))
    source.normalize_inferred_adm2()
