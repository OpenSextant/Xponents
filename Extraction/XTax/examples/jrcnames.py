# -*- coding: utf-8 -*-
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

Created on Nov 19, 2014

@author: ubaldino

Demonstration of how to ingest a catalog of data to use with XTax.
Here JRC Names (published by JRC in the EU) is a collection of 
name/org entities that lists name variants in various languages.

REFERENCE: 
    https://ec.europa.eu/jrc/en/language-technologies/jrc-names
    Data is the resource file download, entities.gzip

USAGE:
    PYTHONPATH=<...include libs according to README>
    python jrcnames.py  /my/data/JRCNames/entities.txt   http://localhost:7000/solr/taxcat

    # python jrcnames.py  /patht/to/entities.txt   <solr url>

'entities.txt' file is a simple, TAB delimited file containing a
universal ID, entity type, language and name variant.

    1000595 P       u       Dani+Ábalo
    
The TaxCat / XTax strategy here is to use the following mapping:

    catalog  = JRC
    taxnode =  name type + primaryname
    phrase = name variant
    { 
      catalog = JRC,
      taxnode = 'Person.Dani Abalo',
      phrase =  'Dani Ábalo'
      tags  = [ 'jrc=1000595', 'lang=u' ]
      
    }
      
Apparently lang = 'u' implies the name is universal in all languages, as spelled.
Where possible the first universal name variant found will act as the taxon primary name, 
creating the taxnode value.

'''

from opensextant.TaxCat import Taxon, TaxCatalogBuilder

# distinct primary names act as a JRC root entry; all others would be variants
#    That is just the convention here for demo sake.
nameset = {}

# ID map -- distinct JRC IDs must be reproducible and unique for each variant.
idset = {}

# Ambiguous phrases or noisy ones I would prefer not to tag, so
# they are marked valid=false, but retained in the names taxonomy.
#
AMBIGUOUS = set(
        [
         'times',
         'the times',
         'news agency',
         'daily news',
         'press agency',
         'military intelligence',
         'international studies', 
         'international trade',
         'will meet', 
         'the nation',
         'facebook',
         'twitter',
         'youtube',
         'they are',
         'reach',
         'armed forces',
         'canal', 
         'the age',
         'read more',
         'presedential office',
         'set fire', 
         'emergency',
         'nature',
         'status quo',
         'the independent',
         'gross domestic product'
        ])

# Fixes are any entries that need to be remapped to entity type, p, o, etc. 
#
FIXES = {
         # Somehow this entry was marked as a "p" (Person)
         # So it is remapped to "-" (Thing, default)
         'Improvised Explosive Device' : '-'
         }

def check_validity(e):
    '''
    @param e: a JRCEntity 
    '''
    phr = e.phrase.lower().replace(',', ' ')
    if phr in AMBIGUOUS:
        e.is_valid = False
    return

    
class JRCEntity(Taxon):
    def __init__(self, eid, variant_id, etype, lang, primary_name, ename):
        Taxon.__init__(self)

        # JRC original entity ID and type
        self.entity_id = eid
        self.entity_type = etype.upper()
        self.lang = lang
        self.phrase = ename
        
        if self.phrase in FIXES:
            self.entity_type = FIXES.get(self.phrase)
        
        if self.entity_type in entity_map:
            self.entity_type = entity_map[self.entity_type]
        
        self.variant_id = variant_id
        # solr record ID:
        self.id = self._make_id()
        self.is_valid = True
                
        # taxon ID/name:
        self.name = '%s.%s' % (self.entity_type, primary_name)
        
        self.tags = self._make_tags()
        
    def _make_id(self):
        return str(self.entity_id) + "#" + self._variant_id()
            
    def _variant_id(self):
        if not self.variant_id:
            return '0'
        return '%04x' % (self.variant_id)   
        
    def _make_tags(self):
        
        return [
                'jrc_id+' + self.entity_id,
                'lang_id+' + self.lang,
                ]
        
    def __str__(self):
        return "%s (%s)" % (self.name, self.phrase)

other_types = set(['U', 'T', 'E', 'p'])

entity_map = {
              'U':'Unknown',
              'T':'Place',
              'E':'Event',
              'O':'Org',
              'P':'Person',
              '-':'Thing'
              }

def create_entity(line, scan=False):
    ''' Create a entry for this entity that has the primary name. 
    @keyword scan: scan=True means we are looking for root entries to which 
    other variants are associated.  Each variant has an entity ID, but there
    is no single primary variant to represent the entity, e.g., for presentation purposes.
    '''
    parts = line.split('\t')
    _id = parts[0].strip()
    if _id == '0':
        print "Ignore line, ", line
        return None
    
    name = unicode(parts[3].replace('+', ' ')).strip()
    lang = parts[2]
    etype = parts[1]
    
    if not etype:
        return None
    
    if scan:
        # if etype in other_types:
        #    print line
        #    return None
        lookup_id = lang + _id
        if lookup_id not in nameset:
            nameset[lookup_id] = name            
            
        return None
    else:
        lookup_id = 'u' + _id
        if lookup_id in nameset:
            primary_name = nameset[lookup_id]
        else:
            lookup_id = lang + _id
            if lookup_id in nameset:
                primary_name = nameset[lookup_id]
            else:
                print "Failed to find primary name for ", line
                primary_name = name
            
        count = 1
        if _id in idset:
            count = idset.get(_id)
            count = count + 1
        idset[_id] = count
     
    variant_id = count   
    #
    # done with aliasing.
    #            
    e = JRCEntity(_id, variant_id, parts[1], lang, primary_name, name)
    check_validity(e)
    return e


if __name__ == '__main__':
    '''
    Testing Usage:  This will take upto the first 100K rows of JRC and print to console.
        jrcnames.py   file

    Actual Usage: All data will be ingested to solr-url
        jrcnames.py   file   solr-url
    '''

    import sys
    taxonomy = sys.argv[1]
    start_id = 3000000
    catalog_id = 'JRC'
    
    test = False
    builder = None
    row_max = -1
    if len(sys.argv) == 3:
        solr_url = sys.argv[2]
        builder = TaxCatalogBuilder(server=solr_url)
    else:
        test = True
        row_max = 100000 
        builder = TaxCatalogBuilder(server=None)
        
    builder.commit_rate = 1000
    builder.stopwords = set([])
    
    # Completely arbitrary starting row ID 
    # Manage your own Catalog regsitry for starting rows
    # As solr has no notion of row ID and your uniqueness requirements.
    #
    row_id = 0
    fh = open(taxonomy, 'rb')
    for row in fh:
        row_id = row_id + 1
        create_entity(row, scan=True)
        if row_id % 100000 == 0:
            print "Row # ", row_id
            
        if row_max > 0 and row_id > row_max:
            break
        
    # Done with scan    
    fh.close()

    row_id = 0
    fh = open(taxonomy, 'rb')    
    for row in fh:       
        node = create_entity(row)
        row_id = row_id + 1
        if not node:
            continue
        
        # ".id" must be an Integer for text tagger
        node.id = start_id + row_id        
        builder.add(catalog_id, node)
        builder.save()

        if row_id % 10000 == 0:
            print "Row # ", row_id
        if test:
            print str(node)
        if row_max > 0 and row_id > row_max:
            break
            
            
    builder.save(flush=True)
    builder.optimize()
    
    fh.close()
            
