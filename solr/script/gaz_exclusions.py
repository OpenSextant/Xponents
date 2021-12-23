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
                # LIKE is not Case-sensitive:
                query = f" where name like '{name}' AND feat_class='{cls}' AND feat_code='{code}'"
                print("\tDELETE", name)
                self.db.delete_places(query)
        print("DONE")

    def exclude_nonsense(self, query):
        print("DELETE names for query: ", query)
        row_ids = []
        names = set([])
        for pl in self.db.list_places(criteria=f"WHERE {query}"):
            names.add(pl.name.lower())
            row_ids.append(pl.id)

        print("Names Marked search only", names)
        if row_ids:
            self.db.mark_search_only(row_ids)


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("exclusions", help="CSV file with named features to remove")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    args = ap.parse_args()

    excluder = Exclusionistic(args.db)

    print("Remove specific confusing named features that are much less common")
    excluder.exclude(args.exclusions)
    print("Mark search-only short 3-char names X-X")
    excluder.exclude_nonsense("name LIKE '%-%' and LENGTH(name)=3 and name_group = ''")
    print("Mark search-only short 4-char names X-XX or XX-X")
    excluder.exclude_nonsense("name LIKE '%-%' and LENGTH(name)=4 and name_group = ''")

    print("Mark search-only numerous short and obscure transliterations of names from Asian countries")
    excluder.exclude_nonsense("name like 'do to' OR name like 'do-to'")
    excluder.exclude_nonsense("name like 'do do' OR name like 'do-do'")
    excluder.exclude_nonsense("name like 'to to' OR name like 'to-to'")
    excluder.exclude_nonsense("name in ('he he', 'He-he', 'He-oh', 'He-ha', 'he can')")
    excluder.exclude_nonsense("name like 'man %' and LENGTH(name) < 6")
    excluder.exclude_nonsense("name like 'we we'")

    excluder.db.close()
