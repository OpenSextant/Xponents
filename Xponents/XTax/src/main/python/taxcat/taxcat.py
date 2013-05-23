'''
  A simple interface to creating a taxonomic catalog ("taxcat") for OpenSextant TaxMatcher  to use.
  prerequisites:   You have a taxcat solrhome setup

  TODO: lots to do.

'''

import os

__API_PATH = os.path.realpath( __file__ )

SOLR_SERVER = "http://localhost:7000/solr/taxcat"

def _scrub_cdata_content(text):
    # return text.replace('<', '(less than)').replace('>','(greater than)').replace('&', '&amp; ')
    return text;
        
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
    if (isinstance(val, str) or isinstance(val, unicode)):
        dct[f] = _scrub_cdata_content(val)
    else:
        dct[f] = val
    
    
def add_value(f, val, case=0):
    ''' trivial wrapper '''
    
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
        
class TaxCatalogBuilder:
    
    def __init__(self, server=None):
        '''
           @param server: solr server http URL; Not solrhome -- this is not SolrEmbedded.
           @param stopwords: file of stopwords
        '''
        
        self.server = server
        if server is None:
            self.server = SOLR_SERVER
        
        self._record_count = 0l
        self._byte_count = 0l
        self._add_byte_count = 0l
        
        self._records = []    
        self.count = 0
        self.set_server(self.server)
        
        from opensextant.OpenSextantCommonsUtils import ConfigUtility
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
               
    def set_server(self, server):
        self.server_url = server
        if not self.server_url:
            return
        
        try:            
            from pysolr import Solr
            self.server = Solr(self.server_url, timeout=600)
            print "SERVER ", self.server_url, self.server
            
        except Exception, err:
            print "Problem with that server %s, ERR=%s" % (self.server_url, err)


    def optimize(self):
        self.server.optimize()

    def save(self, flush=False):
        
        self.server.add(self._records,  sanitize=True)
        self.server.commit()
        self._records = []
        
        return
    
    
    def add_wordlist(self, catalog, fpath, start_id, taxnode=None):
        
        _name = os.path.basename(fpath)
        if taxnode:
            _name = taxnode
        
        _datafile = os.path.join(LEXICA, catalog, fpath)    
        sheet = open(_datafile,'rb')
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
                
            rec = {'catalog':catalog, 'taxnode':_name, 'phrase':_phrase, 'id':_id, "valid":"true" }
            self._records.append( rec )
    
        
        print "COUNT: %d" %( self.count)
        sheet.close()
    
    
    
