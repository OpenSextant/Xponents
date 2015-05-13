'''
 
                Copyright 2014 The MITRE Corporation.
 
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain a copy of
  the License at
 
  http://www.apache.org/licenses/LICENSE-2.0
 
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
  License for the specific language governing permissions and limitations under
  the License.
 
  =============================================================================
  A simple interface to creating a taxonomic catalog ("taxcat") for OpenSextant TaxMatcher  to use.
  prerequisites:    See XTax README

'''

import os

__API_PATH = os.path.realpath( __file__ )

SOLR_SERVER = "http://localhost:7000/solr/taxcat"

def _scrub_cdata_content(text):
    ''' User should scrub data themselves; but this gives ideas of what goes wrong when adding text to Solr
       <,>,& all must be escaped.
    '''
    return text.replace('<', '(less than)').replace('>','(greater than)').replace('&', '&amp; ')

def get_taxnode(t, val):
    return t.lower() + "." + val.strip()


_FALSE_VAL = set(['f', 'false', '0', 'n', 'no'])
_TRUE_VAL = set(['t', 'true', '1', 'y', 'yes'])

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
    ''' add_text offers a basic idea of how to add values to dict 
        before sending to solr.   TEXT strings may need scrubbing
        but you just add non-TEXT values.
    '''
    if (isinstance(val, str) or isinstance(val, unicode)):
        dct[f] = val
    else:
        dct[f] = val


def add_value(f, val, case=0):
    ''' add  a value to a given field, f;  And normalize case if non-zero.
        case = CASE_LOWER | CASE_UPPER | 0(default) no change
    '''

    if val is None:
        f.append(u'')
        return

    if (isinstance(val, str) or isinstance(val, unicode)):
        v = val
        #if "&" in val or "<" in val:
        #    print "SCRUB THIS:", val
        # val.replace('&','+').replace('<', ' lt ')
        if not case:
            f.append(v)
        elif case == CASE_LOWER:
            f.append(  v.lower() )
        elif case == CASE_UPPER:
            f.append(  v.upper() )
    else:
        f.append(str(val))

    return


CASE_LOWER=1
CASE_UPPER=2

'''
# Catalogs must be registered -- Solr has no concept of how to manage string-based record IDs
# that is something you must manage as you create your combined catalog,
#
# Catalog Registry maps your catalog ID to a starting offset for solr records
# If you think your reference data for catalog X will have 1 million entries, then
# start catalog X at 1,000,000 and let other smaller catalogs start at 0 or at less than 1 million
# start the next catalog at 3,000,000 to give X some breathing room.
#
'''
CATALOG_REGISTRY = {

     "DEFAULT" : 0
                    }

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

    def __init__(self, server=None):
        '''
           @param server: solr server http URL; Not solrhome -- this is not SolrEmbedded.
           @param stopwords: file of stopwords
        '''

        self.server = None
        self.set_server(server)

        self._record_count = 0l
        self._byte_count = 0l
        self._add_byte_count = 0l
        self.commit_rate = -1

        self._records = []
        self.count = 0

        from CommonsUtils import ConfigUtility
        ## Load file
        self.utility = ConfigUtility(None)
        self.stopwords = set( [] )

    def add_stopwords( self, stopfile ):

        if not os.path.exists(stopfile):
            raise Exception("No stopwords found at " + stopfile)

        print "Loading stopwords ", stopfile
        _stopwords_list = self.utility.loadListFromFile(stopfile)
        self.stopwords.add( _stopwords_list )

    def get_starting_id(self, cat):
        offset = CATALOG_REGISTRY.get(cat)
        if not offset:
            raise Exception("Catalog is not registered: " + cat)

        return offset

    def set_server(self, svr):
        self.server_url = svr
        if not self.server_url:
            return

        try:
            from pysolr import Solr
            self.server = Solr(self.server_url, timeout=600)
            print "SERVER ", self.server_url, self.server

        except Exception, err:
            print "Problem with that server %s, ERR=%s" % (self.server_url, err)


    def optimize(self):
        if self.server:
            self.server.optimize()

    def save(self, flush=False):
        if not self.server:
            print "No server"
            return

        if not flush:
            qty  = len(self._records)
            if self.commit_rate>0 and  qty % self.commit_rate != 0:
                return
            if qty < self.commit_rate:
                return
            
        self.server.add(self._records)
        self.server.commit()
        self._records = []

        return
    
    def add(self, catalog, taxon):
        ''' 
        @param catalog ID of catalog where this taxon lives
        @param taxon   Taxon obj
        '''
        self.count = self.count + 1
        rec = {'catalog':catalog, 'taxnode':taxon.name, 'phrase':taxon.phrase, 'id':taxon.id, 'valid': taxon.is_valid, 
               'name_type':'N' }
        if taxon.tags:
            rec['tag'] = taxon.tags
        if taxon.is_acronym:
            rec['name_type']  = 'A'
            
        self._records.append( rec )

    def add_wordlist(self, catalog, datafile, start_id, taxnode=None, minlen=1):
        ''' Given a simple one column word list file, each row of data is added
           to catalog as a Taxon; taxnode may be used as a prefix for the words

         Add a series of organized word lists to a single Catalog, but manage 
         each wordlist with some prefix taxon path.

            add_wordlist('CAT', f1, 400, taxonode='first')
            add_wordlist('CAT', f2, 500, taxonode='second')
            add_wordlist('CAT', f3, 600, taxonode='third')
            add_wordlist('CAT', f4, 700, taxonode='fourth')
        '''

        _name = os.path.basename(datafile)
        if taxnode:
            _name = taxnode

        sheet = open(datafile,'rb')
        words = set([])

        for row in sheet:

            _phrase = row.strip()
            if not _phrase:
                continue

            if _phrase.startswith("#"):
                # is a comment or commented out word.
                continue

            self.count += 1
            _id = start_id + self.count

            key = _phrase.lower()
            if key in words:
                print "Not adding ", key
                continue

            words.add(key)

            t = Taxon()
            t.id = _id
            t.is_valid = len(key) >= minlen
            t.name = _name
            t.phrase = _phrase
            # Allow case-sensitve entries.  IFF input text contains UPPER
            # case data, we'll mark it as acronym.
            if t.phrase.isupper():
                t.is_acronym = True
            
            self.add(catalog, t)


        print "COUNT: %d" %( self.count)
        sheet.close()

