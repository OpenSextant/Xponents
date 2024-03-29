from gaz_finalize import Finalizer, oddball_omissions
from opensextant.gazetteer import get_default_db, GazetteerIndex, PlaceHeuristics


class GazetteerUtility(Finalizer):
    def __init__(self, dbf):
        """
        A tool for adjusting and experimenting with SQLite master or Solr index.
        :param dbf:
        """
        Finalizer.__init__(self, dbf)

    def index_sql(self, url, query, fix=False):
        estimator = PlaceHeuristics(self.db)
        print("Xponents Gazetteer Finalizer: INDEX")
        indexer = GazetteerIndex(url)

        row_ids = []
        fixed_name_bias = None
        for pl in self.db.list_places(criteria=f"WHERE {query}"):
            # if len(pl.name) < 2:
            #    continue

            print("Add PLACE: ", pl)
            nm_bias = estimator.name_bias(pl.name, pl.feature_class, pl.feature_code,
                                          name_group=pl.name_group, name_type=pl.name_type)

            if nm_bias < 0:
                print(f"  --- Non-Taggable place marked search_only |{pl.name}|")
                pl.name_bias = nm_bias
                pl.search_only = True
                row_ids.append(pl.id)
                # Should be just Negative.
                fixed_name_bias = nm_bias

            indexer.add(pl)

        if fix and row_ids:
            self.db.update_bias(fixed_name_bias, row_ids)

        self.db.close()
        indexer.save(done=True)

    def omit(self, url, query):
        """
        Result is to mark omissions as search_only and then to also delete from index
        This is only for adhoc tuning -- the formal solution should be part of the normal Finalizer.index()

        :param url:
        :param query:
        :return:
        """
        print("Xponents Gazetteer Finalizer: INDEX")
        indexer = GazetteerIndex(url)

        row_ids = []
        for pl in self.db.list_places(criteria=f"WHERE {query}"):
            if oddball_omissions(pl):
                print("Omit PLACE: ", pl)
                row_ids.append(pl.id)
            elif pl.feature_code != "RGNE":
                print("Omit PLACE: ", pl)
                row_ids.append(pl.id)

        if row_ids:
            self.db.mark_search_only(row_ids)
            for plid in row_ids:
                indexer.delete(entry_id=plid)

            indexer.save(done=True)
        self.db.close()

    def index(self, url, **kwargs):
        # Disabled.
        pass


if __name__ == "__main__":
    from argparse import ArgumentParser

    ap = ArgumentParser()
    ap.add_argument("query", help="SQL query that starts after ' WHERE '")

    ap.add_argument("--db", default=get_default_db())
    ap.add_argument("--debug", action="store_true", default=False)
    ap.add_argument("--solr", help="Solr URL", required=True)
    ap.add_argument("--fix", action="store_true", default=False)
    ap.add_argument("--omit", action="store_true", default=False,
                    help="To mark an item search only in DB AND delete from index")

    args = ap.parse_args()

    if args.omit:
        # NOTE: The query has to be exact
        GazetteerUtility(args.db).omit(args.solr, args.query)
    else:
        GazetteerUtility(args.db).index_sql(args.solr, args.query, fix=args.fix)
