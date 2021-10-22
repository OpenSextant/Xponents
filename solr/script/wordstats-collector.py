#
#  WordStats app -- ingest terms from GoogleBooks NGram data sets
#  v0.1:  load unigram counts for terms 2-30 chars.  Identify most common > 10 million occurrences that are also in Gaz
#
if __name__ == "__main__":
    from argparse import ArgumentParser
    from opensextant.wordstats import WordStats

    ap = ArgumentParser()
    ap.add_argument("catalog")
    ap.add_argument("input")
    ap.add_argument("--db", default="./tmp/wordstats.sqlite")

    args = ap.parse_args()

    collector = WordStats(args.db, minlen=2, maxlen=30)
    collector.ingest(args.input, args.catalog)
