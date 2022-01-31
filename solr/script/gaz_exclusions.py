from opensextant.gazetteer import get_default_db, DB, GazetteerIndex
from opensextant.utility import get_csv_reader


class Exclusionistic:
    def __init__(self, dbf, solr_url=None):
        self.db = DB(dbf)
        print(f"Exclude Entries [{dbf}]")
        self.indexer = None
        if solr_url:
            self.indexer = GazetteerIndex(server_url=solr_url)

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

        # IF you are using this .... the search_only aspect is minimal.  We'll just delete
        # these entries from gazetteer.
        print("Names Marked search only", names)
        if row_ids:
            self.db.mark_search_only(row_ids)
            # Delete from index as well.
            if self.indexer:
                for row_id in row_ids:
                    self.indexer.delete(row_id)


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("exclusions", help="CSV file with named features to remove")
    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--solr", help="Solr URL", required=False)
    ap.add_argument("--debug", action="store_true", default=False)
    args = ap.parse_args()

    excluder = Exclusionistic(args.db, solr_url=args.solr)

    print("Remove specific confusing named features that are much less common")
    excluder.exclude(args.exclusions)
    print("Mark search-only short 3-char names X-X")
    excluder.exclude_nonsense("name LIKE '%-%' and LENGTH(name)=3 and name_group = ''")
    print("Mark search-only short 4-char names X-XX or XX-X")
    excluder.exclude_nonsense("name LIKE '%-%' and LENGTH(name)=4 and name_group = ''")

    print("Mark search-only short 5-char names starting with vowels A, I, O U -  A-xx, A xx, or A-xxx, A xxx")
    for vowel in [ 'a', 'e', 'i', 'o', 'u', 'y']:
        excluder.exclude_nonsense(f"(name LIKE '{vowel} %' OR name LIKE '{vowel}-%') and LENGTH(name)=4 and name_group = ''")
    # Additionally:
    excluder.exclude_nonsense("name in ('A Toi', 'A Toy', 'A Tin')")

    print("Mark search-only numerous short and obscure transliterations of names from Asian countries")
    # Avoid tagging for these phrases as they are common appearances in text, but are not easy to
    # create a codified rule to omit.
    #
    # "*Do to* you...."
    excluder.exclude_nonsense("(name like '% to' OR name like '%-to') AND LENGTH(name)=5")
    excluder.exclude_nonsense("(name like '% do' OR name like '%-do') AND LENGTH(name)=5")
    # "*he he* the kid laughed"
    excluder.exclude_nonsense("name in ('he he', 'He-he', 'He-oh', 'He-ha', 'he can', 'she can')")
    # *man X*
    excluder.exclude_nonsense("name like 'man %' and LENGTH(name) < 6")
    # *we X*
    excluder.exclude_nonsense("name like 'we we'")
    # *open a* dialog ....
    excluder.exclude_nonsense("name like 'open a'")
    # *put in* the canoe...
    excluder.exclude_nonsense("name like 'put in' OR name like 'put-in'")
    # *as said* in the document...
    # *has said* before
    excluder.exclude_nonsense("name like 'as said' OR name like 'has-said'")

    print("Mark search only for generic enumerations ~ Division 4, Number 6, etc.")
    # *enumerations* of generic things
    excluder.exclude_nonsense("name like 'division %' AND LENGTH(name) < 13")
    excluder.exclude_nonsense("name like 'number %' AND LENGTH(name) < 10")
    excluder.exclude_nonsense("name like 'numero %' AND LENGTH(name) < 10")

    excluder.db.close()
