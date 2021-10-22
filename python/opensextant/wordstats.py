import glob
import gzip
import os
import sqlite3
import traceback

from opensextant.utility import ensure_dirs

CATALOGS = {"googlebooks": "G"}


def _ignoreable(text, mn, mx):
    if len(text) < mn or len(text) > mx:
        # print("Ignore short or long")
        return True

    if text[-1].isdigit():
        # Ignore numeric nonsense
        return True
    return False


class WordStats:

    def __init__(self, db, minlen=2, maxlen=30):
        """

        :param db:  DB path
        :param minlen:  min length of tracked words
        :param maxlen:  max length of tracked words
        """
        self.dbpath = db
        self.counter = 0
        self.ignored = 0
        self.minlen = minlen
        self.maxlen = maxlen
        self.conn = None
        self.commit_rate = 100000
        self.cache = set([])
        self.cache_loaded = False
        if self.createdb():
            self.reopen()

    def reopen(self):
        if self.conn is not None:
            return

        # really close cleanly
        self.close()

        self.conn = sqlite3.connect(self.dbpath)
        self.conn.execute('PRAGMA cache_size =  8092')
        self.conn.execute("PRAGMA encoding = 'UTF-8'")
        self.conn.execute('PRAGMA synchronous = OFF')
        self.conn.execute('PRAGMA journal_mode = MEMORY')
        self.conn.execute('PRAGMA temp_store = MEMORY')
        self.conn.row_factory = sqlite3.Row

    def save(self, rows):
        try:
            sql = """insert into wordstats (word, pos, count, catalog) values (:w, :pos, :cnt, :cat)"""
            self.conn.executemany(sql, rows)
            self.conn.commit()
        except:
            print("Failed to save words")
            print(traceback.format_exc(limit=5))

    def createdb(self):
        if os.path.exists(self.dbpath):
            return True

        ensure_dirs(self.dbpath)
        self.reopen()
        sql_script = """
             create TABLE wordstats (
                     `word` TEXT NOT NULL,
                     `pos` TEXT NOT NULL,
                     `count` INTEGER DEFAULT 0,
                     `catalog` TEXT NOT NULL                     
                 );
                 create INDEX wd_idx on wordstats ("word");               
                 create INDEX pos_idx on wordstats ("pos");               
                 create INDEX cat_idx on wordstats ("catalog");               
             """
        self.conn.executescript(sql_script)
        self.conn.commit()
        return True

    def purge(self, cat):
        sql = "delete from wordstats where catalog = ?"
        self.conn.execute(sql, (cat,))
        self.conn.commit()

    def close(self):
        if self.conn:
            self.conn.close()
            del self.conn

    def ingest(self, statsfile, cat):
        files = []
        if os.path.exists(statsfile) and os.path.isdir(statsfile):
            files = glob.glob(f"{statsfile}/*.gz")
        else:
            files.append(statsfile)

        for f in files:
            print(f"INGEST WORDS from {cat}: FILE {f}")
            with gzip.open(f, "rt", encoding="UTF-8") as fh:
                linecount = 0
                terms = {}
                self.purge(cat)
                for line in fh:
                    linecount += 1
                    term = line.strip().split("\t")
                    termtext = term[0].lower()
                    pos = ""
                    curr = termtext
                    if "_" in termtext:
                        curr, pos = termtext.rsplit("_", 1)
                        if not pos:
                            curr = termtext
                    if _ignoreable(curr, self.minlen, self.maxlen):
                        self.ignored += 1
                        continue

                    subcount = int(term[2])
                    key = f"{curr}#{pos}"
                    if key not in terms:
                        terms[key] = {"cnt": 0, "w": curr, "pos": pos, "cat": cat}
                        self.counter += 1
                        if self.counter % self.commit_rate == 0:
                            self.save(terms.values())
                            terms.clear()
                            terms[key] = {"cnt": 0, "w": curr, "pos": pos, "cat": cat}

                    terms[key]["cnt"] += subcount
                # Flush last batch.
                self.save(terms.values())
                print(f"LINES {linecount}  WORDS {self.counter}  IGNORED {self.ignored}")

    def find(self, word, threshold, catalog="googlebooks"):
        """
        EXPERIMENTAL
        Word look up.  for Catalog lookup this is catalog prefix + word initial, e.g., Gp
        is catalog ID in database when looking for "philadelphia" in googlebooks.

        Threshold is a cut off -- all word counts above this will be returned.
        If "word" contains "%", we assume this is a wildcard search.

        Word stats include:
            WORD "_" PARTOFSPEECH
            WORD                     -- This query only uses bare word counts.

        The bare WORD counts appear to be a sum of all sub-counts for WORD+POS occurrences.

        :param word:
        :param threshold:
        :param catalog:
        :return:
        """
        cat = CATALOGS.get(catalog)
        if cat:
            cat = f"{cat}{word[0]}"
        else:
            cat = ""

        word_clause = " word = ?"
        if "%" in word:
            word_clause = "word like ?"
        sql = f"""select word, count as CNT from wordstats where  pos = '' and catalog = ? and CNT > ?
                        and {word_clause} order by CNT desc"""
        # Avoid making the SQL summation too difficult.  For some reason there are multiple entries for certain
        # word patterns -- POS may be NULL or "" or something else.  But here we sum all bare word patterns
        wordstats = {}
        for row in self.conn.execute(sql, (cat, threshold, word)):
            wd = row["word"]
            if wd not in wordstats:
                wordstats[wd] = 0
            wordstats[wd] += row["CNT"]
        return wordstats

    def load_common(self, threshold=10000000):
        """
        Find all commmon words.  The discrete counts of words may have to be added up
        as part-of-speech accounting confuses things a bit.  There are no ground truth numbers in GoogleBooks Ngrams
        about total counts.

        :param threshold:
        :return:
        """
        sql = f"""select word, count as CNT from wordstats where  pos = '' and CNT > 1000000 order by CNT desc"""
        wordstats = {}
        # Sum by word (which is already been lowercased, normalized)
        for row in self.conn.execute(sql):
            wd = row["word"]
            if wd not in wordstats:
                wordstats[wd] = 0
            wordstats[wd] += row["CNT"]
        # Filter by count
        for wd in wordstats:
            if wordstats[wd] > threshold:
                self.cache.add(wd)
        self.cache_loaded = True

    def is_common(self, word, threshold=10000000):
        """
        Check if a word is common.  Threshold is ignored if cache was pre-loaded using load_common()
        If not pre-loaded, then a query is made for each term not in the cache.

        :param word: word lookup.  Ideally caller has lowercased/normalized this
        :param threshold: default 10mil or more occurrence is a common NGram in GoogleBooks
        :return:
        """
        if word in self.cache:
            return True
        if self.cache_loaded:
            return False

        found = False
        # find() cursor returns a dict of found terms. Counts are not used here.
        for wordnorm in self.find(word, threshold=threshold):
            self.cache.add(wordnorm)
            found = True
        return found
