from opensextant.gazetteer import get_default_db, DB
from opensextant.utility import get_csv_reader


class Exclusionistic:
    def __init__(self, dbf):
        self.db = DB(dbf)
        print(f"Exclude Entries [{dbf}]")

    def exclude(self, filepath):
        print("\tFILE", filepath)
        with open(filepath, "r", encoding="UTF-8") as fh:
            csvread = get_csv_reader(fh, columns=["CLASS", "CODE", "NAME"])
            for row in csvread:
                if row["CLASS"].startswith("#"):
                    continue
                cls = row["CLASS"]
                code = row["CODE"]
                name = row["NAME"]
                # Looking for EXACT spelling and CASE on name.
                query = f" where name='{name}' AND feat_class='{cls}' AND feat_code='{code}'"
                print("\tDELETE", name)
                self.db.delete_places(query)
        self.db.close()
        print("DONE")


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("exclusions", help="CSV file with named features to remove")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    args = ap.parse_args()

    Exclusionistic(args.db).exclude(args.exclusions)
