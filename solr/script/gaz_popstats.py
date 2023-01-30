from opensextant.gazetteer import get_default_db, DB


class PopStats:
    def __init__(self, dbf):
        self.db = DB(dbf)

    def load(self):
        self.db.add_population_stats()


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)

    args = ap.parse_args()

    PopStats(args.db).load()
