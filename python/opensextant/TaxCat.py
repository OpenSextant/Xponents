# -*- coding: utf-8 -*-
"""
  A simple interface to creating a taxonomic catalog ("taxcat") for OpenSextant TaxMatcher  to use.
  prerequisites:    See XTax README
"""
import os

from opensextant.utility import is_text, ConfigUtility

__API_PATH = os.path.realpath(__file__)
SOLR_SERVER = "http://127.0.0.1:7000/solr/taxcat"
DEFAULT_SOLR_SERVER = "127.0.0.1:7000"


def _scrub_cdata_content(text):
    """ User should scrub data themselves; but this gives ideas of what goes wrong when adding text to Solr
       <,>,& all must be escaped.
    """
    return text.replace('<', '(less than)').replace('>', '(greater than)').replace('&', '&amp; ')


def get_taxnode(t, val):
    """

    :param t:
    :param val:
    :return:
    """
    name_value = val.strip().title().replace("'S", "'s")
    # Title case capitalizes "'s"..geesh.
    tx = "{}.{}".format(t.lower(), name_value)
    return tx


_FALSE_VAL = {'f', 'false', '0', 'n', 'no'}
_TRUE_VAL = {'t', 'true', '1', 'y', 'yes'}


def add_bool(dct, f, val, default=None):
    if not val:
        if default is not None:
            dct[f] = default
        return

    if val.lower() in _FALSE_VAL:
        dct[f] = 'false'
    elif val.lower() in _TRUE_VAL:
        dct[f] = 'true'
    return


def add_text(dct, f, val):
    """ add_text offers a basic idea of how to add values to dict
        before sending to solr.   TEXT strings may need scrubbing
        but you just add non-TEXT values.
    """
    if is_text(val):
        dct[f] = val
    else:
        dct[f] = val


def add_value(f, val, case=0):
    """ add  a value to a given field, f;  And normalize case if non-zero.
        case = CASE_LOWER | CASE_UPPER | 0(default) no change
    """

    if val is None:
        f.append(u'')
        return

    if is_text(val):
        v = val
        # if "&" in val or "<" in val:
        #    print "SCRUB THIS:", val
        # val.replace('&','+').replace('<', ' lt ')
        if not case:
            f.append(v)
        elif case == CASE_LOWER:
            f.append(v.lower())
        elif case == CASE_UPPER:
            f.append(v.upper())
    else:
        f.append(str(val))

    return


CASE_LOWER = 1
CASE_UPPER = 2

# Catalogs must be registered -- Solr has no concept of how to manage string-based record IDs
# that is something you must manage as you create your combined catalog,
#
# Catalog Registry maps your catalog ID to a starting offset for solr records
# If you think your reference data for catalog X will have 1 million entries, then
# start catalog X at 1,000,000 and let other smaller catalogs start at 0 or at less than 1 million
# start the next catalog at 3,000,000 to give X some breathing room.
#
CATALOG_REGISTRY = {
    "DEFAULT": 0,
    "WFB": 100000,
    "JRC": 3000000
}


def get_starting_id(cat):
    """
    For well-known catalogs, determine the default catatag ID range.
    :param cat:
    :return:
    """
    offset = CATALOG_REGISTRY.get(cat)
    if not offset:
        raise Exception("Catalog is not registered: " + cat)

    return offset


class Taxon:
    def __init__(self):
        self.name = None
        self.phrase = None
        self.id = None
        self.is_valid = True
        # An array of additional tags.
        self.tags = None
        self.is_acronym = False


class TaxCatalogBuilder:

    def __init__(self, server=None, test=False):
        """
        API to assist in building taxon nodes and storing them in Solr.
        :param server: solr server http URL
        """

        self.server = None
        self.server_url = None
        self.set_server(server)
        self.test = test

        self._record_count = 0
        self._byte_count = 0
        self._add_byte_count = 0
        self.add_rate = 1000
        self.commit_rate = -1

        self._records = []
        self.count = 0

        # Load file
        self.utility = ConfigUtility(None)
        self.stopwords = set([])

    def add_stopwords(self, stopfile):

        if not os.path.exists(stopfile):
            raise Exception("No stopwords found at " + stopfile)

        print("Loading stopwords ", stopfile)
        _stopwords_list = self.utility.loadListFromFile(stopfile)
        self.stopwords.update(_stopwords_list)

    def purge(self, catalog):
        if not catalog:
            raise Exception("Catalog name is required")
        print("Purging catalog", catalog)
        self.server.delete(f"catalog:{catalog}", commit=True)

    def set_server(self, svr):
        self.server_url = svr
        if not self.server_url:
            return

        if not self.server_url.startswith("http"):
            self.server_url = f"http://{self.server_url}/solr/taxcat"

        try:
            from pysolr import Solr
            self.server = Solr(self.server_url, timeout=600)
            print("SERVER ", self.server_url, self.server)

        except Exception as err:
            print(f"Problem with that server {self.server_url}, ERR={err}")

    def optimize(self):
        if self.server and not self.test:
            self.server.optimize()

    def save(self, flush=False):
        if self.test:
            return
        if not self.server:
            print("No server")
            return

        ready = 0 < self.count and (self.count % self.add_rate == 0)
        if flush or ready:
            self.server.add(self._records)
            self._records.clear()

            if flush:
                self.server.commit(expungeDeletes=True)
        return

    def add(self, catalog, taxon: Taxon):
        """
        Add the given taxon to the index, increment the internal counter.
        :param catalog:  catalog ID
        :param taxon:
        :return:
        """
        self.count += 1
        rec = {'catalog': catalog, 'taxnode': taxon.name, 'phrase': taxon.phrase, 'id': taxon.id,
               'valid': taxon.is_valid,
               'name_type': 'N'}
        if taxon.tags:
            rec['tag'] = taxon.tags
        if taxon.is_acronym:
            rec['name_type'] = 'A'

        self._records.append(rec)
        self.save()

    def add_wordlist(self, catalog, datafile, start_id, taxnode=None, minlen=1):
        """ Given a simple one column word list file, each row of data is added
           to catalog as a Taxon; taxnode may be used as a prefix for the words

         Add a series of organized word lists to a single Catalog, but manage 
         each wordlist with some prefix taxon path.

            add_wordlist('CAT', f1, 400, taxonode='first')
            add_wordlist('CAT', f2, 500, taxonode='second')
            add_wordlist('CAT', f3, 600, taxonode='third')
            add_wordlist('CAT', f4, 700, taxonode='fourth')
        """
        _name = os.path.basename(datafile)
        if taxnode:
            _name = taxnode

        words = set([])
        with open(datafile, 'r', encoding="UTF-8") as sheet:
            for row in sheet:
                _phrase = row.strip()
                if not _phrase:
                    continue

                if _phrase.startswith("#"):
                    # is a comment or commented out word.
                    continue

                _id = start_id + self.count

                key = _phrase.lower()
                if key in words:
                    print("Not adding ", key)
                    continue

                words.add(key)

                t = Taxon()
                t.id = _id
                t.is_valid = len(key) >= minlen
                t.name = _name
                t.phrase = _phrase
                # Allow case-sensitive entries.  IFF input text contains UPPER
                # case data, we'll mark it as acronym.
                if t.phrase.isupper():
                    t.is_acronym = True

                self.add(catalog, t)
            self.save(flush=True)
            print(f"COUNT: {self.count}")


def create_taxcat(solr_server):
    """

    :param solr_server: URL or host:port
    :return:
    """
    server = solr_server

    if not solr_server:
        server = "localhost:7000"
    if server and not server.lower().startswith("http"):
        server = f"http://{server}/solr/taxcat"

    return TaxCatalogBuilder(server=server)
