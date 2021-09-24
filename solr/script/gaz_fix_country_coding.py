from opensextant.gazetteer import get_default_db, DB
from opensextant import load_us_provinces


class USFixer:

    def __init__(self, dbf, debug=False):
        """
        Convert all US FIPS codes to use ISO US Postal codes.  US.25 => US.MA
        :param dbf:
        :param debug:
        """
        self.db = DB(dbf, debug=debug)

    def fix(self, limit = -1):
        load_us_provinces()
        distinct = set([])
        count = 0
        from opensextant import usstates
        for st in usstates:
            count += 1
            # country, adm1_curr to adm1_new
            us_state = usstates[st]
            if us_state.adm1 in distinct:
                continue
            self.db.update_admin1_code("US", us_state.adm1,  us_state.adm1_postalcode)
            distinct.add(us_state.adm1)
            if 0 < limit < count:
                print("User limit reached")
                break

        self.db.close()


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("country")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--max", help="maximum rows to process for testing", default=-1)
    ap.add_argument("--debug", action="store_true", default=False)
    args = ap.parse_args()

    if args.country == "US":
        USFixer(args.db, debug=args.debug).fix(limit=int(args.max))
    else:
        print("Only country needing fixin' is US.")

