from opensextant import load_countries, countries, country_as_place, Place
from opensextant.gazetteer import DataSource, PlaceHeuristics, get_default_db, DEFAULT_COUNTRY_ID_BIAS

SOURCE_ID = "ISO"  # Really "ISO plus"
GENERATED_BLOCK = 19000000


class CountryGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = [SOURCE_ID]
        self.source_name = "ISO Country Meta"
        self.starting_row = -1
        self.place_count = 0
        print("Loading metadata")
        self.estimator = PlaceHeuristics(self.db)

    def name_bias(self, pl: Place):
        if pl.country_code == "ZZ":
            pl.name_bias = -100
        else:
            pl.name_bias = self.estimator.name_bias(pl.name, pl.feature_class, pl.feature_code, name_type=pl.name_type)
        if pl.name_bias < 0:
            pl.search_only = True

    def normalize(self, sourcefile, limit=-1, optimize=False):
        """

        :param sourcefile: Ignored.
        :param limit:
        :param optimize:
        :return:
        """
        load_countries()
        count = 0
        self.purge()
        print("Assessing countries")
        for C in countries:

            # We won't use FIPS codes for tagging.  ID values are faked up.
            pl = country_as_place(C, C.name.lower().capitalize(), oid=self.starting_row + count, name_type="N")
            self.name_bias(pl)
            pl.id_bias = DEFAULT_COUNTRY_ID_BIAS
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            pl = country_as_place(C, C.cc_iso2, oid=self.starting_row + count, name_type="C")
            self.name_bias(pl)
            pl.id_bias = DEFAULT_COUNTRY_ID_BIAS
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            pl = country_as_place(C, C.cc_iso3, oid=self.starting_row + count, name_type="C")
            self.name_bias(pl)
            pl.id_bias = DEFAULT_COUNTRY_ID_BIAS
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            if count > limit > 0:
                break
        self.db.close()


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)

    args = ap.parse_args()

    source = CountryGazetteer(args.db, debug=args.debug)
    source.starting_row = GENERATED_BLOCK
    source.normalize(None, limit=int(args.max), optimize=args.optimize)
