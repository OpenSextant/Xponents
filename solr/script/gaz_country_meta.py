from opensextant.gazetteer import DataSource, get_default_db
from opensextant import  load_countries, countries, as_place


SOURCE_ID = "ISO" # Really "ISO plus"

class CountryGazetteer(DataSource):
    def __init__(self, dbf, **kwargs):
        DataSource.__init__(self, dbf, **kwargs)
        self.source_keys = [SOURCE_ID]
        self.source_name = "ISO Country Meta"
        self.starting_row = -1
        self.place_count = 0

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
        for C in countries:
            # We won't use FIPS codes for tagging.
            pl = as_place(C, C.name.lower().capitalize(), oid=self.starting_row + count, name_type="N")
            pl.name_bias = 0.20
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            pl = as_place(C, C.cc_iso2, oid=self.starting_row + count, name_type="C")
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            pl = as_place(C, C.cc_iso3, oid=self.starting_row + count, name_type="C")
            pl.source = SOURCE_ID
            self.db.add_place(pl)
            count += 1

            if count > limit > 0:
                break
        self.db.close()


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    # ap.add_argument("postal")
    ap.add_argument("starting_row", help="Starting row number")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--optimize", action="store_true", default=False)

    args = ap.parse_args()

    source = CountryGazetteer(args.db, debug=args.debug)
    source.starting_row = int(args.starting_row)
    source.normalize(None, limit=int(args.max), optimize=args.optimize)
